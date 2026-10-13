package com.hananel.fast.wear.record

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.core.data.FixRecord
import com.hananel.fast.core.data.HeartRateRecord
import com.hananel.fast.core.data.RawRecord
import com.hananel.fast.core.data.RecordedSession
import com.hananel.fast.core.data.SatelliteRecord
import com.hananel.fast.core.data.SessionHeader
import com.hananel.fast.core.data.SessionInsights
import com.hananel.fast.core.data.SessionSummary
import com.hananel.fast.core.data.SessionWriter
import com.hananel.fast.core.data.TrackAssembler
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.tracking.ActivityProfile
import com.hananel.fast.core.tracking.HeartRateZones
import com.hananel.fast.core.tracking.SegmentKind
import com.hananel.fast.core.tracking.live.LiveAnalyzer
import com.hananel.fast.core.tracking.live.LiveState
import com.hananel.fast.core.tracking.sprint.GnssAcquisition
import com.hananel.fast.wear.data.WearData
import com.hananel.fast.wear.data.toRecord
import com.hananel.fast.wear.gnss.AcquisitionTracker
import com.hananel.fast.wear.gnss.GnssFix
import com.hananel.fast.wear.gnss.gnssFixes
import com.hananel.fast.wear.gnss.satelliteViews
import com.hananel.fast.wear.sensors.heartRates
import com.hananel.fast.wear.sync.SyncEngine
import com.hananel.fast.wear.ui.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface RecordingUiState {
    data object Idle : RecordingUiState

    /** The receiver is on and warming up; nothing is being recorded yet. */
    data class Locating(val kind: ActivityKind, val acquisition: GnssAcquisition, val searchingForMs: Long) : RecordingUiState

    data class Recording(val kind: ActivityKind, val live: LiveState, val signalBars: Int) : RecordingUiState

    /**
     * [summary] is null when there was no training to keep, and [notSaved] says why.
     * [autoEnded] is true when the watch gave up on a session nobody finished.
     */
    data class Finished(
        val kind: ActivityKind,
        val summary: SessionSummary?,
        val autoEnded: Boolean,
        val notSaved: NotSaved? = null,
    ) : RecordingUiState
}

/** Why a recording did not become a training. */
enum class NotSaved {
    /** The receiver never produced a speed. */
    NO_GPS,

    /** There were speeds, and none of them was movement: nobody went anywhere. */
    NO_MOVEMENT,
}

/**
 * One run or football training, from GPS warm-up to saved session, living
 * outside any screen — the display on a watch is off most of the time, and a
 * recording must not care.
 *
 * [RecordingService] keeps the process alive and entitled to the sensors while
 * a session is open; this object holds the state the screens observe.
 *
 * Sensor callbacks and all state changes happen on the main thread, so nothing
 * here needs locking. The two slow jobs are handed off: writing to disk goes to
 * a single writer thread, and the once-a-second analysis to a background one.
 */
object RecordingSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** One thread, so records reach the file in the order they were measured. */
    private val disk = Dispatchers.IO.limitedParallelism(1)

    private val _state = MutableStateFlow<RecordingUiState>(RecordingUiState.Idle)
    val state: StateFlow<RecordingUiState> = _state.asStateFlow()

    /** Called once a session has been saved, so whoever keeps it alive can let go. */
    var onFinished: (() -> Unit)? = null

    /** Called when a warm-up nobody started has gone on too long. */
    var onIdleTimeout: (() -> Unit)? = null

    private enum class Phase { IDLE, LOCATING, RECORDING, FINISHING, FINISHED }

    private class Tools(val context: Context) {
        val haptics = Haptics(context)

        /** Keeps the processor awake for the whole session, so fixes are taken once a second with the display off. */
        val wakeLock: PowerManager.WakeLock =
            context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fast:recording")
    }

    private var tools: Tools? = null
    private var phase = Phase.IDLE
    private var kind = ActivityKind.RUN
    private var listening: Job? = null
    private val tracker = AcquisitionTracker()
    private var searchStartedMs = 0L
    private var announcedReady = false

    // The session being recorded.
    private var header: SessionHeader? = null
    private var writer: SessionWriter? = null
    private var records: Channel<RawRecord>? = null
    private var writing: Job? = null
    private val fixes = mutableListOf<FixRecord>()
    private val heartRates = mutableListOf<HeartRateRecord>()
    private var profile = ActivityProfile.RUN
    private var heartRateZones: HeartRateZones? = null

    // The heart-rate sensor reports on change, so the last value stands until replaced.
    private var latestBpm: Int? = null
    private var latestBpmAtMs = 0L
    private var heartRateRecordedAtMs = 0L
    private var analysing = false
    private var lastRefreshMs = 0L
    private var previousLive = LiveState.EMPTY
    private var peakBeforeSprint = Speed.ZERO

    val isRecording: Boolean get() = phase == Phase.RECORDING || phase == Phase.FINISHING

    /** The id of the session being written, which crash recovery must leave alone. */
    val activeId: String? get() = header?.id.takeIf { isRecording }

    /** Switches the sensors on and starts the GPS warm-up. Does nothing if a session is already under way. */
    fun open(context: Context, kind: ActivityKind) {
        if (phase == Phase.LOCATING || isRecording) return
        val tools = Tools(context.applicationContext).also { tools = it }
        this.kind = kind
        phase = Phase.LOCATING
        tracker.reset()
        announcedReady = false
        latestBpm = null
        searchStartedMs = SystemClock.elapsedRealtime()
        publishLocating()

        listening = scope.launch {
            launch { tick() }
            launch {
                satelliteViews(tools.context).collect {
                    tracker.onSatellites(it)
                    when (phase) {
                        Phase.LOCATING -> publishLocating()
                        // Kept with the session: on a receiver that does not rate its own fixes,
                        // the satellites are the only record of how good the signal was.
                        Phase.RECORDING -> records?.trySend(SatelliteRecord(SystemClock.elapsedRealtime(), it))
                        else -> Unit
                    }
                }
            }
            launch { heartRates(tools.context).collect(::onHeartRate) }
            gnssFixes(tools.context).collect(::onFix)
        }
    }

    /** Starts recording. Allowed before the GPS is ready: the track simply begins when the first fix arrives. */
    fun start() {
        val tools = tools ?: return
        if (phase != Phase.LOCATING) return

        val settings = WearData.settings(tools.context)
        val newHeader = WearData.newHeader(tools.context, kind, System.currentTimeMillis(), SystemClock.elapsedRealtime())
        val newWriter = try {
            WearData.store(tools.context).newWriter(newHeader)
        } catch (e: IOException) {
            Log.w(TAG, "Could not start a recording", e)
            return
        }
        val channel = Channel<RawRecord>(Channel.UNLIMITED)

        header = newHeader
        writer = newWriter
        records = channel
        writing = scope.launch(disk) {
            try {
                for (record in channel) newWriter.append(record)
            } catch (e: IOException) {
                Log.w(TAG, "Recording stopped reaching the disk", e)
            }
        }
        fixes.clear()
        heartRates.clear()
        profile = SessionInsights.profileFor(kind, settings.topSpeed)
        heartRateZones = settings.maxBpm?.let(::HeartRateZones)
        previousLive = LiveState.EMPTY
        peakBeforeSprint = Speed.ZERO
        analysing = false

        phase = Phase.RECORDING
        // Released when the session ends; the limit is only a backstop against a leak.
        tools.wakeLock.acquire(WAKE_LOCK_LIMIT_MS)
        tools.haptics.go()
        _state.value = RecordingUiState.Recording(kind, LiveState.EMPTY, tracker.current().bars)
    }

    /** Ends the session on purpose. */
    fun finish() = end(autoEnded = false, trimAfterMs = null)

    /** Leaves the summary: there is nothing more to show. */
    fun dismiss() {
        if (phase != Phase.FINISHED) return
        phase = Phase.IDLE
        _state.value = RecordingUiState.Idle
    }

    /** Whoever kept the session alive is going away. A warm-up is abandoned; a recording is saved first. */
    fun close() {
        when (phase) {
            Phase.RECORDING -> finish()
            Phase.LOCATING -> {
                stopSensors()
                phase = Phase.IDLE
                _state.value = RecordingUiState.Idle
            }
            Phase.IDLE, Phase.FINISHING, Phase.FINISHED -> Unit
        }
    }

    private suspend fun tick() {
        // The stopwatch has to move, and a lost signal has to show, even when no fix arrives.
        while (true) {
            delay(TICK_MS)
            when (phase) {
                Phase.LOCATING -> {
                    publishLocating()
                    if (SystemClock.elapsedRealtime() - searchStartedMs >= WARM_UP_LIMIT_MS) onIdleTimeout?.invoke()
                }
                Phase.RECORDING -> {
                    recordHeartRate()
                    if (SystemClock.elapsedRealtime() - lastRefreshMs >= TICK_MS) refreshLive()
                }
                else -> Unit
            }
        }
    }

    private fun onFix(fix: GnssFix) {
        tracker.onFix(fix)
        when (phase) {
            Phase.LOCATING -> publishLocating()
            Phase.RECORDING -> {
                val record = fix.toRecord()
                // A fix measured just before "start" belongs to the warm-up, not the session.
                if (record.timeMs < (header?.startedElapsedMs ?: return)) return
                fixes += record
                records?.trySend(record)
                refreshLive()
            }
            else -> Unit
        }
    }

    /** [bpm] is null when the sensor has lost contact with the skin. */
    private fun onHeartRate(bpm: Int?) {
        latestBpm = bpm
        latestBpmAtMs = SystemClock.elapsedRealtime()
    }

    /** The heart rate right now, or null if there is none or the sensor has been silent too long to trust. */
    private fun currentBpm(): Int? =
        latestBpm.takeIf { SystemClock.elapsedRealtime() - latestBpmAtMs <= HEART_RATE_MAX_SILENCE_MS }

    /**
     * Writes the standing heart rate once a second. Sampling it on our own clock
     * turns an on-change signal into the even series the analysis expects.
     */
    private fun recordHeartRate() {
        val bpm = currentBpm() ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - heartRateRecordedAtMs < HEART_RATE_MIN_INTERVAL_MS) return
        heartRateRecordedAtMs = now
        val record = HeartRateRecord(now, bpm)
        heartRates += record
        records?.trySend(record)
    }

    private fun publishLocating() {
        val acquisition = tracker.current()
        // The warm-up can take minutes and nobody watches a watch that long: say when it is done.
        if (acquisition.canStart && !announcedReady) {
            announcedReady = true
            tools?.haptics?.ready()
        }
        _state.value = RecordingUiState.Locating(kind, acquisition, SystemClock.elapsedRealtime() - searchStartedMs)
    }

    /**
     * Reads the session so far and tells the screen. The reading is the same one a
     * finished session gets, over everything recorded up to now; at one fix a
     * second that is a few thousand points, cheap enough to redo each time.
     */
    private fun refreshLive() {
        val header = header ?: return
        // If the last reading is still being worked out, this one is skipped; the next tick catches up.
        if (analysing) return
        analysing = true
        val now = SystemClock.elapsedRealtime()
        lastRefreshMs = now

        val soFar = RecordedSession(header, fixes.toList(), heartRates.toList(), endedCleanly = false)
        val nowInSession = now - header.startedElapsedMs
        val freshBpm = currentBpm()
        val profile = profile
        val zones = heartRateZones

        scope.launch {
            val live = withContext(Dispatchers.Default) {
                LiveAnalyzer.snapshot(TrackAssembler.assemble(soFar), profile, zones, nowInSession, freshBpm)
            }
            analysing = false
            if (phase != Phase.RECORDING) return@launch

            signalChanges(previousLive, live)
            previousLive = live
            _state.value = RecordingUiState.Recording(kind, live, tracker.current().bars)
            endIfForgotten(live, header)
        }
    }

    /** Tells the wrist what changed, so the player knows without looking. */
    private fun signalChanges(before: LiveState, now: LiveState) {
        val haptics = tools?.haptics ?: return
        when {
            !before.sprinting && now.sprinting -> {
                peakBeforeSprint = before.maxSpeed
                haptics.sprint()
            }
            // The first sprint of a session is a "record" by default, which is not news.
            before.sprinting && !now.sprinting && now.sprintCount > 1 && (now.lastSprint?.peak ?: Speed.ZERO) > peakBeforeSprint ->
                haptics.record()
            before.segment == SegmentKind.REST && now.segment == SegmentKind.WORK -> haptics.workStarted()
            before.segment == SegmentKind.WORK && now.segment == SegmentKind.REST -> haptics.restStarted()
        }
    }

    /**
     * Ends a session whose owner has plainly gone home: resting, or without any
     * GPS, for longer than any break in a training. The session is cut back to
     * the moment the standing began, so the forgotten time does not count as rest.
     */
    private fun endIfForgotten(live: LiveState, header: SessionHeader) {
        val lastFixMs = fixes.lastOrNull()?.timeMs ?: header.startedElapsedMs
        val sinceLastFixMs = header.startedElapsedMs + live.elapsedMs - lastFixMs
        when {
            live.segment == SegmentKind.REST && live.segmentElapsedMs >= FORGOTTEN_AFTER_MS ->
                end(autoEnded = true, trimAfterMs = header.startedElapsedMs + live.elapsedMs - live.segmentElapsedMs)
            sinceLastFixMs >= FORGOTTEN_AFTER_MS -> end(autoEnded = true, trimAfterMs = lastFixMs)
        }
    }

    private fun end(autoEnded: Boolean, trimAfterMs: Long?) {
        if (phase != Phase.RECORDING) return
        val tools = tools ?: return
        val header = header ?: return
        val writer = writer ?: return
        phase = Phase.FINISHING
        val endMs = SystemClock.elapsedRealtime()
        val hasSpeed = fixes.any { it.hasSpeed }
        stopSensors()

        scope.launch {
            records?.close()
            writing?.join()
            val summary = withContext(disk) {
                try {
                    writer.finish(endMs, trimAfterMs)
                    // Rereads the library so this session can raise the top speed that sets the bands.
                    val recorded = WearData.loadLibrary(tools.context).summaries.firstOrNull { it.id == header.id }
                    if (recorded?.showsMovement == true) {
                        recorded
                    } else {
                        // Nobody went anywhere, or the GPS never spoke. An empty entry in the history
                        // helps nobody; the raw file is put aside in case the cause needs finding.
                        WearData.store(tools.context).setAside(header.id)
                        null
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "Could not finish the recording", e)
                    null
                }
            }
            if (tools.wakeLock.isHeld) tools.wakeLock.release()
            // Two different endings, told apart on the wrist: a saved training, or nothing to save.
            if (summary != null) tools.haptics.done() else tools.haptics.notCompleted()
            phase = Phase.FINISHED
            val notSaved = when {
                summary != null -> null
                hasSpeed -> NotSaved.NO_MOVEMENT
                else -> NotSaved.NO_GPS
            }
            _state.value = RecordingUiState.Finished(kind, summary, autoEnded, notSaved)
            SyncEngine.syncPending(tools.context)
            onFinished?.invoke()
        }
    }

    private fun stopSensors() {
        listening?.cancel()
        listening = null
    }

    private const val TAG = "FastRecording"
    private const val TICK_MS = 1_000L
    private const val HEART_RATE_MIN_INTERVAL_MS = 900L

    /**
     * A steady heart can leave the sensor silent for a while, but not this long
     * during exercise. Past it the sensor is assumed dead rather than the value constant.
     */
    private const val HEART_RATE_MAX_SILENCE_MS = 60_000L

    /** A warm-up left alone this long is abandoned, so a forgotten screen cannot drain the battery. */
    private const val WARM_UP_LIMIT_MS = 10 * 60_000L

    /** Longer than any break inside a training. */
    private const val FORGOTTEN_AFTER_MS = 20 * 60_000L
    private const val WAKE_LOCK_LIMIT_MS = 6 * 60 * 60_000L
}
