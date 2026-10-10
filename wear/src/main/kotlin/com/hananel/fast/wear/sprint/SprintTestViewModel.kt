package com.hananel.fast.wear.sprint

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.model.SpeedSample
import com.hananel.fast.core.tracking.sprint.GnssReadiness
import com.hananel.fast.core.tracking.sprint.GnssReadinessCheck
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

    data class Locating(val readiness: GnssReadiness) : SprintUiState

    data class Countdown(val secondsLeft: Int) : SprintUiState

    data class Running(val speed: Speed, val peak: Speed, val started: Boolean) : SprintUiState

    data class Result(val outcome: SprintTestOutcome) : SprintUiState
}

/**
 * Runs one sprint test: wait for a good fix, count down, record until the
 * runner has clearly slowed, then score the readings.
 *
 * Everything here runs on the main thread — the location callbacks are
 * delivered there too — so the phase and the sample lists need no locking.
 */
class SprintTestViewModel(application: Application) : AndroidViewModel(application) {

    private val haptics = Haptics(application)
    private val log = SprintLog(application)

    private val _state = MutableStateFlow<SprintUiState>(SprintUiState.NeedsPermission(denied = false))
    val state: StateFlow<SprintUiState> = _state.asStateFlow()

    private enum class Phase { LOCATING, COUNTDOWN, RUNNING, RESULT }

    private var phase = Phase.LOCATING
    private var hasPermission = false
    private var visible = false

    private var listening: Job? = null
    private var test: Job? = null

    /** The last few seconds of readings, for judging whether the fix is good enough to start. */
    private val recent = ArrayDeque<SpeedSample>()

    /** Every fix since "go", and the subset that carried a speed. */
    private val runFixes = mutableListOf<GnssFix>()
    private val runSamples = mutableListOf<SpeedSample>()
    private var goElapsedMs = 0L
    private var goUtcMs = 0L

    fun onPermissionResult(granted: Boolean) {
        hasPermission = granted
        if (granted) {
            if (_state.value is SprintUiState.NeedsPermission) publishReadiness()
            listenIfPossible()
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
        listening?.cancel()
        listening = null
        test?.cancel()
        test = null
        if (phase == Phase.COUNTDOWN || phase == Phase.RUNNING) {
            phase = Phase.LOCATING
            recent.clear()
            if (hasPermission) publishReadiness()
        }
    }

    fun start() {
        if (phase != Phase.LOCATING) return
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
        publishReadiness()
    }

    private fun listenIfPossible() {
        if (!hasPermission || !visible || listening != null) return
        listening = viewModelScope.launch {
            launch {
                // Readiness must also fall back to "searching" when fixes stop arriving,
                // which no incoming fix can report.
                while (isActive) {
                    delay(1_000)
                    if (phase == Phase.LOCATING) publishReadiness()
                }
            }
            gnssFixes(getApplication<Application>()).collect(::onFix)
        }
    }

    private fun onFix(fix: GnssFix) {
        if (fix.hasSpeed) {
            recent.addLast(fix.sample)
            while (recent.size > RECENT_CAPACITY) recent.removeFirst()
        }
        when (phase) {
            Phase.LOCATING -> publishReadiness()
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

    private fun publishReadiness() {
        val readiness = GnssReadinessCheck.evaluate(recent.toList(), SystemClock.elapsedRealtime())
        _state.value = SprintUiState.Locating(readiness)
    }

    private fun finish() {
        if (phase != Phase.RUNNING) return
        phase = Phase.RESULT
        val outcome = SprintTestAnalyzer.analyze(runSamples.toList())
        log.save(goUtcMs, runFixes.toList(), outcome)
        haptics.done()
        _state.value = SprintUiState.Result(outcome)
    }

    private companion object {
        const val COUNTDOWN_SECONDS = 3
        const val WATCHDOG_INTERVAL_MS = 250L

        /** Nobody ran: give up rather than hold the receiver on indefinitely. */
        const val NO_START_TIMEOUT_MS = 15_000L

        /** Longer than any sprint; a test still going by now is not one. */
        const val MAX_TEST_MS = 40_000L
        const val RECENT_CAPACITY = 12
    }
}
