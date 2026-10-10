package com.hananel.fast.wear.sprint

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.model.SpeedSample
import com.hananel.fast.core.tracking.sprint.AcquisitionStage
import com.hananel.fast.core.tracking.sprint.GnssAcquisition
import com.hananel.fast.core.tracking.sprint.GnssAcquisitionCheck
import com.hananel.fast.core.tracking.sprint.SatelliteView
import com.hananel.fast.core.tracking.sprint.SprintProgress
import com.hananel.fast.core.tracking.sprint.SprintTestAnalyzer
import com.hananel.fast.core.tracking.sprint.SprintTestOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
 * Runs one sprint test: wait for a fix, count down, record until the runner
 * has clearly slowed, then score the readings.
 *
 * Everything here runs on the main thread — the location callbacks are
 * delivered there too — so the phase and the sample lists need no locking.
 */
class SprintTestViewModel(application: Application) : AndroidViewModel(application) {

    private val haptics = Haptics(application)
    private val log = SprintLog(application)
    private val acquisitionLog = AcquisitionLog(application)

    private val _state = MutableStateFlow<SprintUiState>(SprintUiState.NeedsPermission(denied = false))
    val state: StateFlow<SprintUiState> = _state.asStateFlow()

    private enum class Phase { LOCATING, COUNTDOWN, RUNNING, RESULT }

    private var phase = Phase.LOCATING
    private var hasPermission = false
    private var visible = false

    private var listening: Job? = null
    private var test: Job? = null

    // What the receiver has delivered in the last few seconds, for judging how the search is going.
    private val recentSamples = ArrayDeque<SpeedSample>()
    private val recentFixTimes = ArrayDeque<Long>()
    private var satellites: SatelliteView? = null
    private var satellitesAtMs = 0L
    private var searchStartedMs = 0L

    // For the acquisition log: each milestone is recorded once per search.
    private var loggedSatellites = false
    private var loggedFirstFix = false
    private var loggedStage: AcquisitionStage? = null

    /** Every fix since "go", and the subset that carried a speed. */
    private val runFixes = mutableListOf<GnssFix>()
    private val runSamples = mutableListOf<SpeedSample>()
    private var goElapsedMs = 0L
    private var goUtcMs = 0L

    fun onPermissionResult(granted: Boolean) {
        hasPermission = granted
        if (granted) {
            listenIfPossible()
            if (_state.value is SprintUiState.NeedsPermission) publishAcquisition()
        } else {
            _state.value = SprintUiState.NeedsPermission(denied = true)
        }
    }

    fun onVisible() {
        visible = true
        listenIfPossible()
    }

    /** Leaving the screen ends location use at once, and abandons a test in progress. */
    fun onHidden() {
        visible = false
        if (listening != null) acquisitionLog.record("search stopped after ${searchSeconds()}s at stage $loggedStage")
        listening?.cancel()
        listening = null
        test?.cancel()
        test = null
        if (phase == Phase.COUNTDOWN || phase == Phase.RUNNING) phase = Phase.LOCATING
        clearReceiverState()
        if (hasPermission && phase == Phase.LOCATING) publishAcquisition()
    }

    fun start() {
        if (phase != Phase.LOCATING || !currentAcquisition().canStart) return
        phase = Phase.COUNTDOWN
        test = viewModelScope.launch {
            for (secondsLeft in COUNTDOWN_SECONDS downTo 1) {
                _state.value = SprintUiState.Countdown(secondsLeft)
                haptics.tick()
                delay(1_000)
            }
            haptics.go()
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
        publishAcquisition()
    }

    private fun listenIfPossible() {
        if (!hasPermission || !visible || listening != null) return
        clearReceiverState()
        searchStartedMs = SystemClock.elapsedRealtime()
        acquisitionLog.record("search started")
        listening = viewModelScope.launch {
            launch {
                // The search must also move when nothing arrives: the clock ticks, and a
                // fix that stops coming has to fall back to "searching".
                while (isActive) {
                    delay(1_000)
                    if (phase == Phase.LOCATING) publishAcquisition()
                }
            }
            launch { satelliteViews(getApplication<Application>()).collect(::onSatellites) }
            gnssFixes(getApplication<Application>()).collect(::onFix)
        }
    }

    private fun onSatellites(view: SatelliteView) {
        satellites = view
        satellitesAtMs = SystemClock.elapsedRealtime()
        if (!loggedSatellites && view.visible > 0) {
            loggedSatellites = true
            acquisitionLog.record("first satellites after ${searchSeconds()}s: visible=${view.visible} strong=${view.strong}")
        }
        if (phase == Phase.LOCATING) publishAcquisition()
    }

    private fun onFix(fix: GnssFix) {
        recentFixTimes.addLast(fix.sample.timeMs)
        while (recentFixTimes.size > RECENT_CAPACITY) recentFixTimes.removeFirst()
        if (fix.hasSpeed) {
            recentSamples.addLast(fix.sample)
            while (recentSamples.size > RECENT_CAPACITY) recentSamples.removeFirst()
        }
        if (!loggedFirstFix) {
            loggedFirstFix = true
            acquisitionLog.record(
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

    private fun currentAcquisition(): GnssAcquisition {
        val now = SystemClock.elapsedRealtime()
        // A satellite report that has stopped being refreshed describes the past.
        val freshSatellites = satellites?.takeIf { now - satellitesAtMs <= SATELLITE_REPORT_MAX_AGE_MS }
        return GnssAcquisitionCheck.evaluate(recentSamples.toList(), recentFixTimes.toList(), freshSatellites, now)
    }

    private fun publishAcquisition() {
        val acquisition = currentAcquisition()
        if (listening != null && acquisition.stage != loggedStage) {
            loggedStage = acquisition.stage
            acquisitionLog.record(
                "stage ${acquisition.stage} after ${searchSeconds()}s: satellites=${acquisition.satellites} " +
                    "positionAccuracy=${acquisition.positionAccuracyMeters}",
            )
        }
        val searchingFor = if (listening != null) SystemClock.elapsedRealtime() - searchStartedMs else 0L
        _state.value = SprintUiState.Locating(acquisition, searchingFor)
    }

    private fun finish() {
        if (phase != Phase.RUNNING) return
        phase = Phase.RESULT
        val outcome = SprintTestAnalyzer.analyze(runSamples.toList())
        log.save(goUtcMs, runFixes.toList(), outcome)
        haptics.done()
        _state.value = SprintUiState.Result(outcome)
    }

    private fun clearReceiverState() {
        recentSamples.clear()
        recentFixTimes.clear()
        satellites = null
        loggedSatellites = false
        loggedFirstFix = false
        loggedStage = null
    }

    private fun searchSeconds(): Long = (SystemClock.elapsedRealtime() - searchStartedMs) / 1000

    private companion object {
        const val COUNTDOWN_SECONDS = 3
        const val WATCHDOG_INTERVAL_MS = 250L

        /** Nobody ran: give up rather than hold the receiver on indefinitely. */
        const val NO_START_TIMEOUT_MS = 15_000L

        /** Longer than any sprint; a test still going by now is not one. */
        const val MAX_TEST_MS = 40_000L
        const val RECENT_CAPACITY = 12
        const val SATELLITE_REPORT_MAX_AGE_MS = 5_000L
    }
}
