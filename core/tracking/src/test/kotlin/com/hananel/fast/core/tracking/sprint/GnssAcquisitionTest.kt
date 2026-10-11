package com.hananel.fast.core.tracking.sprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssAcquisitionTest {

    private val now = 5_000L

    private fun evaluate(
        build: (SprintBuilder.() -> Unit)? = null,
        fixTimes: List<Long>? = null,
        satellites: SatelliteView? = null,
    ): GnssAcquisition {
        val samples = build?.let { sprint(it) }.orEmpty()
        return GnssAcquisitionCheck.evaluate(samples, fixTimes ?: samples.map { it.timeMs }, satellites, now)
    }

    @Test
    fun aPlaceholderAccuracyIsNotShownAndTheSatellitesDecide() {
        val placeholder: SprintBuilder.() -> Unit = {
            speedAccuracy = 25.0
            positionAccuracy = 125.0
            hold(0.0, 6)
        }

        val openSky = evaluate(placeholder, satellites = SatelliteView(visible = 35, strong = 14, usedInFix = 14))
        val balcony = evaluate(placeholder, satellites = SatelliteView(visible = 8, strong = 1, usedInFix = 5))

        assertEquals(AcquisitionStage.READY, openSky.stage)
        assertEquals(4, openSky.bars)
        // "±125 m" on the screen would look like a measurement.
        assertEquals(null, openSky.positionAccuracyMeters)

        assertEquals(AcquisitionStage.WEAK_FIX, balcony.stage)
        assertTrue(balcony.canStart)
    }

    @Test
    fun hearingNothingIsTheFirstStage() {
        val none = evaluate()
        val silentReport = evaluate(satellites = SatelliteView(visible = 0, strong = 0, usedInFix = 0))

        assertEquals(AcquisitionStage.NO_SATELLITES, none.stage)
        assertEquals(0, none.bars)
        assertFalse(none.canStart)
        assertEquals(AcquisitionStage.NO_SATELLITES, silentReport.stage)
    }

    @Test
    fun aFewFaintSatellitesAreProgressButNotEnough() {
        val result = evaluate(satellites = SatelliteView(visible = 6, strong = 2, usedInFix = 0))

        assertEquals(AcquisitionStage.SATELLITES_FOUND, result.stage)
        assertEquals(1, result.bars)
        assertFalse(result.canStart)
    }

    @Test
    fun fourClearSatellitesMeanAFixIsMomentsAway() {
        val result = evaluate(satellites = SatelliteView(visible = 9, strong = 4, usedInFix = 0))

        assertEquals(AcquisitionStage.LOCKING, result.stage)
        assertEquals(2, result.bars)
    }

    @Test
    fun theFirstFixesAreStillLocking() {
        val result = evaluate({ hold(0.0, 2) }, fixTimes = listOf(4_000, 5_000))

        assertEquals(AcquisitionStage.LOCKING, result.stage)
        assertFalse(result.canStart)
    }

    @Test
    fun aSteadyGoodFixIsReady() {
        val result = evaluate({ hold(0.0, 6) })

        assertEquals(AcquisitionStage.READY, result.stage)
        assertEquals(4, result.bars)
        assertTrue(result.canStart)
        assertEquals(3.0, result.positionAccuracyMeters!!, 1e-9)
    }

    @Test
    fun aSteadyButPoorFixCanStartAndSaysItIsWeak() {
        val result = evaluate({
            positionAccuracy = 30.0
            hold(0.0, 6)
        })

        assertEquals(AcquisitionStage.WEAK_FIX, result.stage)
        assertEquals(3, result.bars)
        assertTrue(result.canStart)
    }

    @Test
    fun positionsWithoutSpeedAreNamedAsSuchInsteadOfSearchingForever() {
        val result = evaluate(fixTimes = listOf(1_000, 2_000, 3_000, 4_000, 5_000))

        assertEquals(AcquisitionStage.FIX_WITHOUT_SPEED, result.stage)
        assertFalse(result.canStart)
    }

    @Test
    fun fixesThatStoppedArrivingNoLongerCount() {
        // Readings from 0–5 s, judged a minute later: the fix has been lost.
        val stale = sprint { hold(0.0, 6) }
        val result = GnssAcquisitionCheck.evaluate(stale, stale.map { it.timeMs }, satellites = null, nowMs = 60_000)

        assertEquals(AcquisitionStage.NO_SATELLITES, result.stage)
    }
}
