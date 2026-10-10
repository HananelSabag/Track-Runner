package com.hananel.fast.wear.sprint

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.model.SpeedSample
import com.hananel.fast.core.tracking.sprint.AcquisitionStage
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.core.tracking.sprint.GnssAcquisition
import com.hananel.fast.core.tracking.sprint.SatelliteView
import com.hananel.fast.core.tracking.sprint.SprintProgress
import com.hananel.fast.core.tracking.sprint.SprintTestAnalyzer
import com.hananel.fast.core.tracking.sprint.SprintTestOutcome
import com.hananel.fast.wear.data.WearData
import com.hananel.fast.wear.data.toRecord
import com.hananel.fast.wear.gnss.AcquisitionTracker
import com.hananel.fast.wear.gnss.GnssFix
import com.hananel.fast.wear.gnss.gnssFixes
import com.hananel.fast.wear.gnss.satelliteViews
import com.hananel.fast.wear.sync.SyncEngine
import com.hananel.fast.wear.ui.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface SprintUiState {
    /** [denied] is true once the runner has turned the request down. */
    data class NeedsPermission(val denied: Boolean) : SprintUiState

    /** [searchingForMs] is how long the receiver has been on, so a slow search visibly progresses. */
    data class Locating(val acquisition: GnssAcquisition, val searchingForMs: Long) : SprintUiState

    data class Countdown(val secondsLeft: Int) : SprintUiState

    data class Running(val speed: Speed, val peak: Speed, val started: Boolean) : SprintUiState

    data class Result(val outcome: SprintTestOutcome) : SprintUiState
}

/**
 * One sprint test from GPS search to result, living outside any screen.
 *
 * It has to. A watch turns its display off within seconds and whenever the
 * wrist drops, and the first version, which ran inside the screen, switched
 * the receiver off each time that happened: on the real watch no search lasted
 * longer than half a minute and none ever reached a fix. A sprint would have
 * been cut the same way, by the runner's own arm swing.
 *
 * [SprintService] keeps the process alive and entitled to location while a
 * session is open; this object holds the state the screens observe.
 *
 * Everything runs on the main thread — the location callbacks are delivered
 * there too — so the phase and the sample lists need no locking.
 */
object SprintSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<SprintUiState>(idle())
    val state: StateFlow<SprintUiState> = _state.asStateFlow()

    /** Called when a session has sat unused for too long, so whoever keeps it alive can let go. */
    var onIdleTimeout: (() -> Unit)? = null

    private enum class Phase { LOCATING, COUNTDOWN, RUNNING, RESULT }

    private class Tools(val context: Context) {
        val haptics = Haptics(context)
        val sprintLog = SprintLog(context)
        val acquisitionLog = AcquisitionLog(context)

        /** Keeps the processor running through the countdown and the sprint while the display is off. */
        val wakeLock: PowerManager.WakeLock =
            context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fast:sprint")
    }

    private var tools: Tools? = null
    private var phase = Phase.LOCATING
    private var listening: Job? = null
    private var test: Job? = null

    private val tracker = AcquisitionTracker()
    private var searchStartedMs = 0L
    private var idleSinceMs = 0L

    // Each milestone of a search is logged, and signalled on the wrist, once.
    private var loggedSatellites = false
    private var loggedFirstFix = false
    private var loggedStage: AcquisitionStage? = null
    private var announcedReady = false

    /** Every fix since "go", and the subset that carried a speed. */
    private val runFixes = mutableListOf<GnssFix>()
    private val runSamples = mutableListOf<SpeedSample>()
    private var goElapsedMs = 0L
    private var goUtcMs = 0L

    /** Switches the receiver on and starts the search. Safe to call again while already open. */
    fun open(context: Context) {
        if (listening != null) return
        val appContext = context.applicationContext
        val tools = Tools(appContext).also { tools = it }
        phase = Phase.LOCATING
        clearReceiverState()
        searchStartedMs = SystemClock.elapsedRealtime()
        idleSinceMs = searchStartedMs
        tools.acquisitionLog.record("search started")
        publishAcquisition()

        listening = scope.launch {
            launch {
                // The search must also move when nothing arrives: the clock ticks, and a
                // fix that stops coming has to fall back to "searching".
                while (isActive) {
                    delay(1_000)
                    if (phase == Phase.LOCATING) publishAcquisition()
                    val waiting = phase == Phase.LOCATING || phase == Phase.RESULT
                    if (waiting && SystemClock.elapsedRealtime() - idleSinceMs >= IDLE_LIMIT_MS) onIdleTimeout?.invoke()
                }
            }
            launch { satelliteViews(appContext).collect(::onSatellites) }
            gnssFixes(appContext).collect(::onFix)
        }
    }

    /** Switches the receiver off and abandons whatever was in progress. */
    fun close() {
        if (listening != null) tools?.acquisitionLog?.record("search stopped after ${searchSeconds()}s at stage $loggedStage")
        listening?.cancel()
        listening = null
        test?.cancel()
        test = null
        releaseWakeLock()
        phase = Phase.LOCATING
        clearReceiverState()
        tools = null
        _state.value = idle()
    }

    fun start() {
        val tools = tools ?: return
        if (phase != Phase.LOCATING || !currentAcquisition().canStart) return
        phase = Phase.COUNTDOWN
        // Released when the test ends; the timeout is only a backstop against a leak.
        tools.wakeLock.acquire(WAKE_LOCK_LIMIT_MS)
        test = scope.launch {
            for (secondsLeft in COUNTDOWN_SECONDS downTo 1) {
                _state.value = SprintUiState.Countdown(secondsLeft)
                tools.haptics.tick()
                delay(1_000)
            }
            tools.haptics.go()
            runFixes.clear()
            runSamples.clear()
            goElapsedMs = SystemClock.elapsedRealtime()
            goUtcMs = System.currentTimeMillis()
            phase = Phase.RUNNING
            _state.value = SprintUiState.Running(Speed.ZERO, Speed.ZERO, started = false)

            // The readings end the test when the runner slows. This loop covers the
            // cases where they cannot: nobody set off, or the test has run too long.
            while (isActive && phase == Phase.RUNNING) {
                delay(WATCHDOG_INTERVAL_MS)
                val elapsed = SystemClock.elapsedRealtime() - goElapsedMs
                val neverStarted = !SprintProgress.of(runSamples).started && elapsed >= NO_START_TIMEOUT_MS
                if (neverStarted || elapsed >= MAX_TEST_MS) finish()
            }
        }
    }

    fun stop() = finish()

    fun retry() {
        if (phase != Phase.RESULT) return
        phase = Phase.LOCATING
        idleSinceMs = SystemClock.elapsedRealtime()
        publishAcquisition()
    }

    private fun onSatellites(view: SatelliteView) {
        tracker.onSatellites(view)
        if (!loggedSatellites && view.visible > 0) {
            loggedSatellites = true
            tools?.acquisitionLog?.record("first satellites after ${searchSeconds()}s: visible=${view.visible} strong=${view.strong}")
        }
        if (phase == Phase.LOCATING) publishAcquisition()
    }

    private fun onFix(fix: GnssFix) {
        tracker.onFix(fix)
        if (!loggedFirstFix) {
            loggedFirstFix = true
            tools?.acquisitionLog?.record(
                "first fix after ${searchSeconds()}s: hasSpeed=${fix.hasSpeed} " +
                    "speedAccuracy=${fix.sample.speedAccuracy?.metersPerSecond} positionAccuracy=${fix.sample.horizontalAccuracyMeters}",
            )
        }
        when (phase) {
            Phase.LOCATING -> publishAcquisition()
            Phase.RUNNING -> {
                runFixes += fix
                if (!fix.hasSpeed) return
                runSamples += fix.sample
                val progress = SprintProgress.of(runSamples)
                _state.value = SprintUiState.Running(fix.sample.speed, progress.peak, progress.started)
                if (progress.finished) finish()
            }
            Phase.COUNTDOWN, Phase.RESULT -> Unit
        }
    }

    private fun currentAcquisition(): GnssAcquisition = tracker.current()

    private fun publishAcquisition() {
        val acquisition = currentAcquisition()
        if (acquisition.stage != loggedStage) {
            loggedStage = acquisition.stage
            tools?.acquisitionLog?.record(
                "stage ${acquisition.stage} after ${searchSeconds()}s: satellites=${acquisition.satellites} " +
                    "positionAccuracy=${acquisition.positionAccuracyMeters}",
            )
        }
        // The search can take minutes and nobody watches a watch that long: say when it is done.
        if (acquisition.canStart && !announcedReady) {
            announcedReady = true
            tools?.haptics?.ready()
        }
        _state.value = SprintUiState.Locating(acquisition, SystemClock.elapsedRealtime() - searchStartedMs)
    }

    private fun finish() {
        if (phase != Phase.RUNNING) return
        val tools = tools ?: return
        phase = Phase.RESULT
        idleSinceMs = SystemClock.elapsedRealtime()
        val outcome = SprintTestAnalyzer.analyze(runSamples.toList())
        tools.sprintLog.save(goUtcMs, runFixes.toList(), outcome)
        saveForSync(tools.context)
        tools.haptics.done()
        releaseWakeLock()
        _state.value = SprintUiState.Result(outcome)
    }

    /**
     * Stores the test as a session too, so it reaches the phone like any training.
     * Only the raw fixes travel; the phone scores them again for itself.
     */
    private fun saveForSync(context: Context) {
        if (runFixes.isEmpty()) return
        try {
            // A fix can be stamped a moment before "go" and still arrive after it; the session starts at whichever is earlier.
            val origin = minOf(goElapsedMs, runFixes.first().sample.timeMs)
            val writer = WearData.store(context).newWriter(
                WearData.newHeader(context, ActivityKind.SPRINT_TEST, goUtcMs, origin),
            )
            runFixes.forEach { writer.append(it.toRecord()) }
            writer.finish(SystemClock.elapsedRealtime())
            SyncEngine.syncPending(context)
        } catch (e: IOException) {
            // The result is already on screen and in the log; only the copy for the phone is lost.
            Log.w("FastSprint", "Could not store the test for sync", e)
        }
    }

    private fun releaseWakeLock() {
        tools?.wakeLock?.let { if (it.isHeld) it.release() }
    }

    private fun clearReceiverState() {
        tracker.reset()
        loggedSatellites = false
        loggedFirstFix = false
        loggedStage = null
        announcedReady = false
    }

    private fun searchSeconds(): Long = (SystemClock.elapsedRealtime() - searchStartedMs) / 1000

    private fun idle(): SprintUiState = SprintUiState.Locating(AcquisitionTracker.NOTHING_YET, 0)

    private const val COUNTDOWN_SECONDS = 3
    private const val WATCHDOG_INTERVAL_MS = 250L

    /** Nobody ran: give up rather than keep recording nothing. */
    private const val NO_START_TIMEOUT_MS = 15_000L

    /** Longer than any sprint; a test still going by now is not one. */
    private const val MAX_TEST_MS = 40_000L
    private const val WAKE_LOCK_LIMIT_MS = 70_000L

    /** A search or a result left alone this long is abandoned, so a forgotten session cannot drain the battery. */
    private const val IDLE_LIMIT_MS = 10 * 60_000L
}
