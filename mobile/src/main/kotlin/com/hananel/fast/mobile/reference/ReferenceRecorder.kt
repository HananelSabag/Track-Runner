package com.hananel.fast.mobile.reference

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.core.data.DeviceComparison
import com.hananel.fast.core.data.DeviceComparisons
import com.hananel.fast.core.data.RawRecord
import com.hananel.fast.core.data.RecordedSession
import com.hananel.fast.core.data.SatelliteRecord
import com.hananel.fast.core.data.SessionHeader
import com.hananel.fast.core.data.SessionStore
import com.hananel.fast.core.data.SessionSummary
import com.hananel.fast.core.data.SessionWriter
import com.hananel.fast.core.data.sync.SyncProtocol
import com.hananel.fast.core.gnss.GnssFix
import com.hananel.fast.core.gnss.gnssFixes
import com.hananel.fast.core.gnss.satelliteViews
import com.hananel.fast.core.gnss.toRecord
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.tracking.sprint.SatelliteView
import com.hananel.fast.mobile.data.PhoneData
import java.io.File
import java.io.IOException
import java.util.UUID
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
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface ReferenceState {
    /** [entries] are the measurements stored on the phone, newest first. */
    data class Idle(val entries: List<ReferenceEntry> = emptyList(), val last: Finished? = null) : ReferenceState

    data class Recording(
        val elapsedMs: Long,
        val fixes: Int,
        /** Fixes per second over the last few seconds: what the receiver really delivers. */
        val fixesPerSecond: Double,
        val speed: Speed?,
        /** The receiver's own estimate of its speed error, when it gives one. */
        val speedAccuracy: Speed?,
        val positionAccuracyMeters: Double?,
        val satellites: SatelliteView?,
        /** Started by the watch, or as a joint run: either way it stops with the watch. */
        val withWatch: Boolean = false,
        /** A joint run started on the phone, and the watch has not started yet. */
        val waitingForWatch: Boolean = false,
        /** Null until known: whether the invitation reached a watch at all. */
        val watchReached: Boolean? = null,
    ) : ReferenceState

    data class Finished(val durationMs: Long, val fixes: Int, val fixesPerSecond: Double, val reason: StopReason = StopReason.BY_HAND)
}

/** Why a phone measurement ended. The automatic ones are said on screen, so a stop is never a mystery. */
enum class StopReason { BY_HAND, WITH_WATCH, NOT_MOVED, DRIVING, NO_GPS, TOO_LONG }

/** One stored measurement, and the watch recording it can be compared with. */
data class ReferenceEntry(
    val id: String,
    val startedUtcMs: Long,
    val durationMs: Long,
    val fixes: Int,
    val fixesPerSecond: Double,
    /** False when the recording was cut off — the app closed or killed before Stop — and recovered afterwards. */
    val endedCleanly: Boolean,
    /** The watch recording that ran at the same time, when there was one. */
    val watchKind: ActivityKind? = null,
    val watchStartedUtcMs: Long? = null,
    val comparison: DeviceComparison? = null,
)

/**
 * Records the phone's own GPS, so that one run can be measured by the watch and
 * the phone at once and the two compared.
 *
 * It is a measuring instrument, not a feature: its recordings are kept apart
 * from the history, are never synced, and are read on the computer. It asks the
 * receiver for fixes far more often than once a second, because how fast this
 * receiver can actually deliver them is one of the things being measured.
 *
 * Everything runs on the main thread except the file, which has its own.
 */
object ReferenceRecorder {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val disk = Dispatchers.IO.limitedParallelism(1)

    private val _state = MutableStateFlow<ReferenceState>(ReferenceState.Idle())
    val state: StateFlow<ReferenceState> = _state.asStateFlow()

    /** Called when a recording has been saved, so whoever keeps it alive can let go. */
    var onStopped: (() -> Unit)? = null

    /** Called when a recording starts or stops, so the notification can say which. */
    var onRecordingChanged: (() -> Unit)? = null

    private val _armed = MutableStateFlow(false)

    /**
     * "Measure together with the watch" is on and its service is waiting: the next
     * time the watch starts measuring, so does the phone (D43).
     */
    val armed: StateFlow<Boolean> = _armed.asStateFlow()

    /** Whether the recording now running was started by the watch, which then also ends it. */
    private var startedByWatch = false

    /** A joint run started on the phone, before the watch has started its run. */
    private var waitingForWatch = false

    /** This measurement is a joint run started on the phone, not one that followed the watch. */
    private var jointFromPhone = false
    private var watchReached: Boolean? = null

    // The safety net for a measurement nobody stops (one ran for three and a half hours on 2026-10-24).
    private var lastMovingMs = 0L
    private var lastFixMs = 0L

    /** Counts recordings, so the end of one cannot clear the state of the next. */
    private var generation = 0
    private var drivingSinceMs: Long? = null

    /** The measurement being written right now; the only unfinished file that is not an orphan. */
    private var activeId: String? = null
    private var listening: Job? = null
    private var writing: Job? = null
    private var writer: SessionWriter? = null
    private var records: Channel<RawRecord>? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var startedElapsedMs = 0L
    private var fixCount = 0
    private val recentFixTimes = ArrayDeque<Long>()
    private var latest: GnssFix? = null
    private var satellites: SatelliteView? = null

    val isRecording: Boolean get() = listening != null

    fun store(context: Context): SessionStore = SessionStore(File(context.applicationContext.filesDir, DIRECTORY))

    /**
     * Lists the stored measurements, each set against the watch recording made at the
     * same time. A measurement cut off before Stop — the app closed or killed — is
     * finished here first, so it is listed like any other instead of sitting unseen.
     * Reads files: call it off the main thread.
     */
    fun refresh(context: Context) {
        if (isRecording) return
        val appContext = context.applicationContext
        val store = store(appContext)
        store.recoverOrphans(activeId)
        val watchStore = PhoneData.store(appContext)
        val watchSummaries = watchStore.summaries()
        val entries = store.ids().mapNotNull { id ->
            val recording = store.read(id) ?: return@mapNotNull null
            entryFor(recording, watchSummaries, watchStore)
        }.sortedByDescending { it.startedUtcMs }
        _state.value = ReferenceState.Idle(entries, (_state.value as? ReferenceState.Idle)?.last)
    }

    /**
     * The phone measurement made alongside a watch recording, set against it.
     * Null when the phone measured nothing at that time. Reads files: call it off the main thread.
     */
    fun comparisonFor(context: Context, watch: RecordedSession): DeviceComparison? {
        val store = store(context.applicationContext)
        return store.ids().asSequence()
            .mapNotNull { store.read(it) }
            .filter { DeviceComparisons.overlap(watch, it) >= DeviceComparisons.MIN_SHARED_MS }
            .mapNotNull { DeviceComparisons.of(watch, it) }
            .maxByOrNull { it.sharedMs }
    }

    private fun entryFor(recording: RecordedSession, watchSummaries: List<SessionSummary>, watchStore: SessionStore): ReferenceEntry {
        val fixes = recording.fixes
        val durationMs = if (fixes.size > 1) fixes.last().timeMs - fixes.first().timeMs else 0
        val started = recording.header.startedUtcMs
        val ended = started + recording.durationMs
        // Only watch recordings that ran near the same time are read; the rest are judged by their summaries.
        val (summary, comparison) = watchSummaries
            .asSequence()
            .filter { it.startedUtcMs <= ended + SEARCH_MARGIN_MS && it.startedUtcMs + it.durationMs >= started - SEARCH_MARGIN_MS }
            .mapNotNull { candidate -> watchStore.read(candidate.id)?.let { DeviceComparisons.of(it, recording) }?.let { candidate to it } }
            .maxByOrNull { it.second.sharedMs }
            ?: (null to null)
        return ReferenceEntry(
            id = recording.header.id,
            startedUtcMs = started,
            durationMs = durationMs,
            fixes = fixes.size,
            fixesPerSecond = if (durationMs > 0) (fixes.size - 1) / (durationMs / 1000.0) else 0.0,
            endedCleanly = recording.endedCleanly,
            watchKind = summary?.kind,
            watchStartedUtcMs = summary?.startedUtcMs,
            comparison = comparison,
        )
    }

    /** Set by [ReferenceService] while it is waiting for the watch, and cleared when it stops waiting. */
    fun setArmed(armed: Boolean) {
        _armed.value = armed
    }

    /** The watch has started measuring. A phone that is waiting for it starts too. Any thread. */
    fun onWatchStarted(context: Context, watchNode: String?) {
        val appContext = context.applicationContext
        scope.launch {
            val following = when {
                // Already measuring — a joint run waiting for this start, or a measurement by hand.
                isRecording -> {
                    if (waitingForWatch) {
                        waitingForWatch = false
                        publish()
                    }
                    true
                }
                _armed.value -> {
                    if (!isRecording) {
                        start(appContext)
                        startedByWatch = isRecording
                    }
                    isRecording
                }
                // Wanted, but the waiting service is gone (the system ended the app). Android will not
                // let a background app start measuring, so the user is asked, with one tap.
                PhoneData.settings(appContext).measureWithWatch -> {
                    LinkNotice.offerToJoin(appContext)
                    false
                }
                // "Measure together" is off: there is nothing to tell the watch.
                else -> return@launch
            }
            answerWatch(appContext, watchNode, following)
        }
    }

    /**
     * The user tapped "measure together" after the watch had already started: arm, measure,
     * and let the watch know. Only from the app or its notification, while the app is in front.
     */
    fun joinWatch(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            LinkNotice.dismiss(appContext)
            if (!isRecording) {
                start(appContext)
                startedByWatch = isRecording
            }
            answerWatch(appContext, watchNode = null, following = isRecording)
        }
    }

    /**
     * A joint run, started on the phone (D47): measure now, and invite the watch, which shows
     * a notice that opens a run. It stops when the watch's run ends. Only while the app is in front.
     */
    fun startJoint(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            if (!isRecording) start(appContext)
            if (!isRecording) return@launch
            startedByWatch = true
            jointFromPhone = true
            waitingForWatch = true
            watchReached = null
            publish()
            watchReached = sendToWatch(appContext, SyncProtocol.LINK_INVITE_PATH)
            publish()
        }
    }

    /** Sends one message to every watch within reach. False when there was none, or it failed. */
    private suspend fun sendToWatch(context: Context, path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val watches = Wearable.getNodeClient(context).connectedNodes.await().filter { it.isNearby }
            for (watch in watches) Wearable.getMessageClient(context).sendMessage(watch.id, path, ByteArray(0)).await()
            watches.isNotEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Could not reach the watch: ${e.message}")
            false
        }
    }

    /** Tells the watch whether the phone is measuring alongside it; the watch shows it. */
    private fun answerWatch(context: Context, watchNode: String?, following: Boolean) {
        val path = if (following) SyncProtocol.LINK_FOLLOWING_PATH else SyncProtocol.LINK_NOT_FOLLOWING_PATH
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = watchNode?.let { listOf(it) }
                    ?: Wearable.getNodeClient(context).connectedNodes.await().filter { it.isNearby }.map { it.id }
                for (node in nodes) Wearable.getMessageClient(context).sendMessage(node, path, ByteArray(0)).await()
            } catch (e: Exception) {
                Log.w(TAG, "Could not answer the watch: ${e.message}")
            }
        }
    }

    /**
     * The watch has stopped. A measurement it started ends with it; one started by hand does not.
     * A joint run started here outlives a watch that only searched for GPS and was left (D47):
     * it goes back to waiting for the watch's run. Any thread.
     */
    fun onWatchStopped(context: Context, measured: Boolean) {
        val appContext = context.applicationContext
        scope.launch {
            if (!isRecording || !startedByWatch) return@launch
            if (jointFromPhone && !measured) {
                waitingForWatch = true
                publish()
                return@launch
            }
            stop(appContext, StopReason.WITH_WATCH)
        }
    }

    fun start(context: Context) {
        if (isRecording) return
        startedByWatch = false
        jointFromPhone = false
        waitingForWatch = false
        watchReached = null
        generation++
        val appContext = context.applicationContext
        startedElapsedMs = SystemClock.elapsedRealtime()
        val header = SessionHeader(
            id = UUID.randomUUID().toString(),
            kind = ActivityKind.RUN,
            startedUtcMs = System.currentTimeMillis(),
            startedElapsedMs = startedElapsedMs,
            device = Build.MODEL.orEmpty(),
            appVersion = appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty(),
        )
        val newWriter = try {
            activeId = header.id
            store(appContext).newWriter(header)
        } catch (e: IOException) {
            Log.w(TAG, "Could not start a reference recording", e)
            return
        }
        val channel = Channel<RawRecord>(Channel.UNLIMITED)
        writer = newWriter
        records = channel
        writing = scope.launch(disk) {
            try {
                for (record in channel) newWriter.append(record)
            } catch (e: IOException) {
                Log.w(TAG, "Reference recording stopped reaching the disk", e)
            }
        }
        fixCount = 0
        recentFixTimes.clear()
        latest = null
        satellites = null
        // The phone will be in a pocket with its screen off; the fixes must keep their rhythm.
        wakeLock = appContext.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fast:reference").apply { acquire(WAKE_LOCK_LIMIT_MS) }
        lastMovingMs = startedElapsedMs
        lastFixMs = startedElapsedMs
        drivingSinceMs = null
        publish()
        onRecordingChanged?.invoke()

        listening = scope.launch {
            launch {
                // The clock on screen has to move even between fixes.
                while (true) {
                    delay(1_000)
                    publish()
                    // A phone in a bag or indoors gets no fixes at all, so the fix-driven checks never run:
                    // these two end such a measurement anyway (L2).
                    val now = SystemClock.elapsedRealtime()
                    when {
                        now - lastFixMs >= NO_FIX_LIMIT_MS -> stop(appContext, StopReason.NO_GPS)
                        now - startedElapsedMs >= MAX_DURATION_MS -> stop(appContext, StopReason.TOO_LONG)
                    }
                }
            }
            launch {
                satelliteViews(appContext).collect {
                    satellites = it
                    channel.trySend(SatelliteRecord(SystemClock.elapsedRealtime(), it))
                }
            }
            gnssFixes(appContext, intervalMs = FASTEST_INTERVAL_MS).collect { fix ->
                lastFixMs = SystemClock.elapsedRealtime()
                if (stopsByItself(appContext, fix)) return@collect
                fixCount++
                latest = fix
                recentFixTimes.addLast(fix.sample.timeMs)
                while (recentFixTimes.size > RATE_WINDOW_FIXES) recentFixTimes.removeFirst()
                channel.trySend(fix.toRecord())
                publish()
            }
        }
    }

    fun stop(context: Context, reason: StopReason = StopReason.BY_HAND) {
        if (!isRecording) return
        val appContext = context.applicationContext
        val endMs = SystemClock.elapsedRealtime()
        val count = fixCount
        listening?.cancel()
        listening = null
        // Everything that belongs to this recording, taken now: a new one may start before this one is saved.
        val ending = generation
        val finishing = writer
        val closingRecords = records
        val closingWrite = writing
        val lock = wakeLock
        val started = startedElapsedMs
        scope.launch {
            closingRecords?.close()
            closingWrite?.join()
            withContext(disk) {
                try {
                    // A recording without a single fix has nothing to compare; it is not kept.
                    if (count > 0) finishing?.finish(endMs) else finishing?.discard()
                } catch (e: IOException) {
                    Log.w(TAG, "Could not finish the reference recording", e)
                }
            }
            lock?.let { if (it.isHeld) it.release() }
            // A newer recording already running keeps its own state.
            if (generation != ending || isRecording) return@launch
            activeId = null
            wakeLock = null
            val durationMs = endMs - started
            val finished = ReferenceState.Finished(durationMs, count, if (durationMs > 0) count / (durationMs / 1000.0) else 0.0, reason)
            _state.value = ReferenceState.Idle(last = finished.takeIf { count > 0 || reason != StopReason.BY_HAND })
            startedByWatch = false
            jointFromPhone = false
            waitingForWatch = false
            onRecordingChanged?.invoke()
            onStopped?.invoke()
            // The new measurement takes its place in the list, next to the watch recording it belongs with.
            withContext(disk) { refresh(appContext) }
        }
    }

    /**
     * Ends a measurement that has plainly outlived its purpose: the phone is in a car,
     * or nobody has moved it for twenty minutes. The phone's own speed is believable
     * (it says how accurate it is, and it is), so it can be trusted for this.
     */
    private fun stopsByItself(context: Context, fix: GnssFix): Boolean {
        val now = fix.sample.timeMs
        val speed = fix.sample.speed.takeIf { fix.hasSpeed } ?: return false
        if (speed >= MOVING_SPEED) lastMovingMs = now
        drivingSinceMs = if (speed >= DRIVING_SPEED) drivingSinceMs ?: now else null
        val driving = drivingSinceMs?.let { now - it >= DRIVING_HOLD_MS } == true
        val abandoned = now - lastMovingMs >= STILL_LIMIT_MS
        if (!driving && !abandoned) return false
        Log.i(TAG, if (driving) "In a vehicle: measurement stopped" else "Not moved for 20 minutes: measurement stopped")
        stop(context, if (driving) StopReason.DRIVING else StopReason.NOT_MOVED)
        return true
    }

    private fun publish() {
        if (!isRecording) return
        val fix = latest
        _state.value = ReferenceState.Recording(
            elapsedMs = SystemClock.elapsedRealtime() - startedElapsedMs,
            fixes = fixCount,
            fixesPerSecond = recentRate(),
            speed = fix?.sample?.speed?.takeIf { fix.hasSpeed },
            speedAccuracy = fix?.sample?.speedAccuracy,
            positionAccuracyMeters = fix?.sample?.horizontalAccuracyMeters,
            satellites = satellites,
            withWatch = startedByWatch,
            waitingForWatch = waitingForWatch,
            watchReached = watchReached,
        )
    }

    /** How fast fixes have been arriving lately, from the spacing of the last few. */
    private fun recentRate(): Double {
        if (recentFixTimes.size < 2) return 0.0
        val spanMs = recentFixTimes.last() - recentFixTimes.first()
        return if (spanMs > 0) (recentFixTimes.size - 1) / (spanMs / 1000.0) else 0.0
    }

    private const val TAG = "FastReference"
    private const val DIRECTORY = "reference"

    /** Far faster than any phone receiver delivers; it answers with whatever its own best is. */
    private const val FASTEST_INTERVAL_MS = 100L
    private const val RATE_WINDOW_FIXES = 12

    /** Walking pace: anything slower is the phone lying still. */
    private val MOVING_SPEED = Speed.ofKmh(3.0)

    /** Faster than anyone runs for long; held for a minute, it is a car or a bus. */
    private val DRIVING_SPEED = Speed.ofKmh(25.0)
    private const val DRIVING_HOLD_MS = 60_000L
    private const val STILL_LIMIT_MS = 20 * 60_000L

    /** No fix at all for this long: the phone is in a bag or indoors, and is measuring nothing. */
    private const val NO_FIX_LIMIT_MS = 20 * 60_000L

    /** Longer than any training it is meant to accompany. */
    private const val MAX_DURATION_MS = 4 * 60 * 60_000L

    /** How far around a measurement to look for the watch recording made with it. */
    private const val SEARCH_MARGIN_MS = 60_000L
    private const val WAKE_LOCK_LIMIT_MS = 3 * 60 * 60_000L
}
