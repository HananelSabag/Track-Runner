package com.hananel.fast.core.tracking.sprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SprintLiveTest {

    // --- Readiness

    @Test
    fun noRecentReadingsMeansStillSearching() {
        val old = sprint { hold(0.0, 5) }

        assertEquals(GnssReadiness.SEARCHING, GnssReadinessCheck.evaluate(old, nowMs = 60_000))
        assertEquals(GnssReadiness.SEARCHING, GnssReadinessCheck.evaluate(emptyList(), nowMs = 0))
    }

    @Test
    fun aSteadyGoodFixIsReady() {
        val samples = sprint { hold(0.0, 6) }

        assertEquals(GnssReadiness.READY, GnssReadinessCheck.evaluate(samples, nowMs = 5_000))
    }

    @Test
    fun tooFewReadingsAreWeak() {
        val samples = sprint { hold(0.0, 2) }

        assertEquals(GnssReadiness.WEAK, GnssReadinessCheck.evaluate(samples, nowMs = 1_000))
    }

    @Test
    fun aPoorFixIsWeakHoweverManyReadingsArrive() {
        val poorSpeed = sprint {
            speedAccuracy = 1.4
            hold(0.0, 6)
        }
        val poorPosition = sprint {
            positionAccuracy = 30.0
            hold(0.0, 6)
        }

        assertEquals(GnssReadiness.WEAK, GnssReadinessCheck.evaluate(poorSpeed, nowMs = 5_000))
        assertEquals(GnssReadiness.WEAK, GnssReadinessCheck.evaluate(poorPosition, nowMs = 5_000))
    }

    @Test
    fun aReceiverThatReportsNoAccuracyIsJudgedOnItsReadingRateAlone() {
        val samples = sprint {
            speedAccuracy = null
            positionAccuracy = null
            hold(0.0, 6)
        }

        assertEquals(GnssReadiness.READY, GnssReadinessCheck.evaluate(samples, nowMs = 5_000))
    }

    // --- Progress

    @Test
    fun nothingHasStartedWhileStandingStill() {
        val progress = SprintProgress.of(sprint { hold(0.0, 5) })

        assertFalse(progress.started)
        assertFalse(progress.finished)
    }

    @Test
    fun theTestIsRunningWhileTheRunnerIsStillFast() {
        val progress = SprintProgress.of(sprint { speeds(0.0, 10.0, 20.0, 26.0, 26.0, 25.0) })

        assertTrue(progress.started)
        assertEquals(26.0, progress.peak.kmh, 1e-9)
        assertFalse(progress.finished)
    }

    @Test
    fun theTestEndsItselfOnceTheRunnerHasClearlySlowed() {
        val progress = SprintProgress.of(sprint { speeds(0.0, 10.0, 20.0, 26.0, 26.0, 18.0, 10.0, 6.0) })

        assertTrue(progress.finished)
    }

    @Test
    fun oneSlowReadingDoesNotEndTheTest() {
        val progress = SprintProgress.of(sprint { speeds(0.0, 10.0, 20.0, 26.0, 26.0, 4.0) })

        assertFalse(progress.finished)
    }

    @Test
    fun aGlitchDoesNotEndTheTestEarly() {
        // Without the glitch filter 45 would be the peak, and the honest 20s after it
        // would read as "slowed to under half" and stop the test mid-sprint.
        val progress = SprintProgress.of(sprint { speeds(0.0, 10.0, 18.0, 20.0, 45.0, 20.0, 20.0, 20.0) })

        assertEquals(20.0, progress.peak.kmh, 1e-9)
        assertFalse(progress.finished)
    }

    @Test
    fun aJogThatSlowsDownIsNotAFinishedSprint() {
        val progress = SprintProgress.of(sprint { speeds(0.0, 6.0, 9.0, 9.0, 3.0, 2.0) })

        assertTrue(progress.started)
        assertFalse(progress.finished)
    }
}
