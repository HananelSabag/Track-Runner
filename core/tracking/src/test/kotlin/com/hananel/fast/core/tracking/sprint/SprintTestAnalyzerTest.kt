package com.hananel.fast.core.tracking.sprint

import com.hananel.fast.core.tracking.sprint.SprintTestOutcome.Measured
import com.hananel.fast.core.tracking.sprint.SprintTestOutcome.Rejected
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SprintTestAnalyzerTest {

    private fun measure(build: SprintBuilder.() -> Unit): Measured =
        SprintTestAnalyzer.analyze(sprint(build)) as Measured

    private fun reject(build: SprintBuilder.() -> Unit): SprintRejection =
        (SprintTestAnalyzer.analyze(sprint(build)) as Rejected).reason

    // --- A good sprint is recognised as one

    @Test
    fun aCleanSprintGetsItsTopSpeedAndHighConfidence() {
        val result = measure { cleanSprint() }

        assertEquals(26.0, result.topSpeed.kmh, 1e-9)
        assertEquals(26.0, result.instantMax.kmh, 1e-9)
        assertEquals(ConfidenceLevel.HIGH, result.level)
        assertTrue("confidence was ${result.confidence}", result.confidence >= 95)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun theFastPhaseCoversTheTimeAboveSeventyPercentOfTopSpeed() {
        val result = measure { cleanSprint() }

        // 21, 25, 26×5, 20 km/h are all at or above 18.2: eight readings, seven seconds.
        assertEquals(7_000, result.fastPhaseMs)
        assertEquals(48.75, result.fastPhaseMeters, 0.01)
    }

    @Test
    fun uncertaintyComesFromTheReceiverAndShrinksWithTheWindow() {
        val result = measure { cleanSprint() }

        // Three steady readings, each good to 0.2 m/s: 0.2 / √3.
        assertEquals(0.1155, result.uncertainty!!.metersPerSecond, 1e-4)
    }

    // --- The top speed is a held speed, not the fastest reading

    @Test
    fun aSpeedTouchedForOneReadingIsNotTheTopSpeed() {
        val result = measure { speeds(0.0, 10.0, 18.0, 22.0, 24.0, 27.0, 24.0, 20.0, 12.0) }

        // 27 was reached for a single reading; what was held is 24.
        assertEquals(27.0, result.instantMax.kmh, 1e-9)
        assertEquals(24.0, result.topSpeed.kmh, 1e-9)
    }

    // --- Each way a measurement can be bad lowers the score and names the reason

    @Test
    fun aGlitchIsKeptOutOfTheTopSpeedAndStillCostsTrust() {
        val result = measure { speeds(0.0, 10.0, 18.0).hold(20.0, 3).speeds(31.0).hold(20.0, 3).speeds(10.0) }

        // The runner held 20; one reading claimed 31. The headline is the 20.
        assertEquals(20.0, result.topSpeed.kmh, 1e-9)
        assertEquals(31.0, result.instantMax.kmh, 1e-9)
        assertEquals(listOf(SprintIssue.SPIKE), result.issues)
        assertEquals(ConfidenceLevel.MEDIUM, result.level)
    }

    @Test
    fun aGlitchInsideTheOnlyFastStretchLeavesNothingToTrust() {
        val result = measure { speeds(0.0, 10.0, 18.0, 20.0, 31.0, 20.0, 10.0, 4.0) }

        assertEquals(20.0, result.topSpeed.kmh, 1e-9)
        assertEquals(ConfidenceLevel.LOW, result.level)
        assertTrue(SprintIssue.SPIKE in result.issues)
    }

    @Test
    fun aGlitchAboveHumanSpeedDoesNotRejectAnHonestSprint() {
        val result = measure { speeds(0.0, 10.0, 18.0).hold(22.0, 3).speeds(70.0).hold(22.0, 3).speeds(10.0) }

        assertEquals(22.0, result.topSpeed.kmh, 1e-9)
        assertTrue(SprintIssue.SPIKE in result.issues)
    }

    @Test
    fun aSpeedThatWasTouchedButNotHeldIsFlagged() {
        val result = measure { speeds(0.0, 8.0, 14.0, 20.0, 26.0, 20.0, 14.0, 8.0) }

        assertEquals(ConfidenceLevel.LOW, result.level)
        assertEquals(listOf(SprintIssue.NOT_HELD), result.issues)
    }

    @Test
    fun aFixTheReceiverItselfCallsPoorIsNotTrusted() {
        val result = measure {
            speedAccuracy = 1.6
            cleanSprint()
        }

        assertEquals(ConfidenceLevel.LOW, result.level)
        assertEquals(listOf(SprintIssue.WEAK_SIGNAL), result.issues)
    }

    @Test
    fun speedThatTheGroundCoveredDoesNotSupportIsNotTrusted() {
        // The receiver reports 26 km/h while the positions cover only a third of that distance.
        val result = measure {
            groundCovered = 0.33
            cleanSprint()
        }

        assertEquals(ConfidenceLevel.LOW, result.level)
        assertEquals(listOf(SprintIssue.DISTANCE_MISMATCH), result.issues)
    }

    @Test
    fun aMissingReadingInTheFastPhaseLowersCoverage() {
        val complete = measure { cleanSprint() }
        val withHole = SprintTestAnalyzer.analyze(sprint { cleanSprint() }.filterIndexed { index, _ -> index != 5 }) as Measured

        assertTrue(withHole.confidence < complete.confidence)
    }

    @Test
    fun aVeryShortBurstHasTooFewReadingsToTrust() {
        val result = measure { speeds(0.0, 6.0, 12.0).hold(24.0, 3).speeds(8.0, 0.0) }

        assertTrue("confidence was ${result.confidence}", result.level != ConfidenceLevel.HIGH)
        assertTrue(SprintIssue.FEW_SAMPLES in result.issues)
    }

    // --- A check that cannot be made caps the score instead of passing

    @Test
    fun withoutSpeedAccuracyTheScoreIsCappedAndNoUncertaintyIsClaimed() {
        val result = measure {
            speedAccuracy = null
            cleanSprint()
        }

        assertEquals(85, result.confidence)
        assertNull(result.uncertainty)
    }

    @Test
    fun withoutPositionsTheScoreIsCapped() {
        val result = measure {
            groundCovered = null
            cleanSprint()
        }

        assertEquals(80, result.confidence)
    }

    @Test
    fun aReceiverThatReportsNoQualityAtAllCannotScoreHigh() {
        val result = measure {
            speedAccuracy = null
            positionAccuracy = null
            groundCovered = null
            cleanSprint()
        }

        assertTrue("confidence was ${result.confidence}", result.level != ConfidenceLevel.HIGH)
    }

    // --- A receiver that fills in its accuracy without measuring it (the real watch)

    // Satellite counts the watch logged: under open sky, and on a balcony.
    private val openSky = listOf(SatelliteView(visible = 29, strong = 6, usedInFix = 12), SatelliteView(visible = 35, strong = 14, usedInFix = 14))
    private val balcony = listOf(SatelliteView(visible = 8, strong = 1, usedInFix = 5))

    // The parameter is not called "build": inside the builder that name is the builder's own function.
    private fun measureOnTheWatch(satellites: List<SatelliteView>, scenario: SprintBuilder.() -> Unit): Measured =
        SprintTestAnalyzer.analyze(
            sprint {
                // On every fix, whatever the sky: 25 m/s and 125 m.
                speedAccuracy = 25.0
                positionAccuracy = 125.0
                scenario()
            },
            satellites = satellites,
        ) as Measured

    @Test
    fun aPlaceholderAccuracyDoesNotCondemnACleanSprint() {
        val result = measureOnTheWatch(openSky) { cleanSprint() }

        // Capped, because the receiver still never said how accurate its speed was.
        assertEquals(85, result.confidence)
        assertTrue(SprintIssue.WEAK_SIGNAL !in result.issues)
        // And no "±90 km/h" built on the placeholder.
        assertNull(result.uncertainty)
    }

    @Test
    fun aPlaceholderAccuracyDoesNotExcuseAPoorSky() {
        val result = measureOnTheWatch(balcony) { cleanSprint() }

        assertTrue(SprintIssue.WEAK_SIGNAL in result.issues)
        assertEquals(ConfidenceLevel.LOW, result.level)
    }

    @Test
    fun oneStretchOfPoorReceptionDuringTheSprintIsEnoughToCount() {
        val result = measureOnTheWatch(openSky + balcony) { cleanSprint() }

        assertTrue(SprintIssue.WEAK_SIGNAL in result.issues)
    }

    // What the real watch did on 2026-10-13: twenty-odd satellites in use throughout, and for
    // one report in dozens not one of them heard clearly.
    private val blink = SatelliteView(visible = 30, strong = 0, usedInFix = 22)

    @Test
    fun aBlinkInReceptionDoesNotCondemnACleanSprint() {
        val result = measureOnTheWatch(List(9) { openSky[1] } + blink) { cleanSprint() }

        assertTrue(SprintIssue.WEAK_SIGNAL !in result.issues)
        assertEquals(85, result.confidence)
    }

    @Test
    fun poorReceptionForAQuarterOfTheSprintIsNotABlink() {
        val result = measureOnTheWatch(List(6) { openSky[1] } + blink + blink) { cleanSprint() }

        assertTrue(SprintIssue.WEAK_SIGNAL in result.issues)
        assertEquals(ConfidenceLevel.LOW, result.level)
    }

    @Test
    fun withAPlaceholderAndNoSatelliteReportTheScoreCannotBeHigh() {
        val result = measureOnTheWatch(emptyList()) { cleanSprint() }

        assertTrue("confidence was ${result.confidence}", result.level != ConfidenceLevel.HIGH)
    }

    @Test
    fun aPlaceholderPositionAccuracyDoesNotExcuseADistanceMismatch() {
        // Believing "125 m" would forgive the positions for covering half the distance the speeds claim.
        val result = measureOnTheWatch(openSky) {
            groundCovered = 0.5
            cleanSprint()
        }

        assertTrue(SprintIssue.DISTANCE_MISMATCH in result.issues)
        assertTrue("confidence was ${result.confidence}", result.level != ConfidenceLevel.HIGH)
    }

    // --- Things that are not a sprint are refused outright

    @Test
    fun walkingIsNotASprint() {
        assertEquals(SprintRejection.NO_SPRINT, reject { hold(5.0, 12) })
    }

    @Test
    fun aSpeedNoRunnerReachesIsRefused() {
        assertEquals(SprintRejection.TOO_FAST_FOR_A_RUNNER, reject { speeds(0.0, 20.0, 40.0).hold(60.0, 4) })
    }

    @Test
    fun aHandfulOfReadingsIsNotEnough() {
        assertEquals(SprintRejection.NOT_ENOUGH_DATA, reject { speeds(0.0, 20.0, 26.0) })
    }

    @Test
    fun readingsTooFarApartToFormAWindowAreNotEnough() {
        assertEquals(
            SprintRejection.NOT_ENOUGH_DATA,
            reject {
                intervalSeconds = 3
                cleanSprint()
            },
        )
    }

    @Test
    fun aMeasuredSprintAlwaysHasAConfidenceBetweenZeroAndAHundred() {
        val result = measure {
            speedAccuracy = 3.0
            groundCovered = 0.1
            speeds(0.0, 14.0, 28.0, 14.0, 28.0, 14.0)
        }

        assertTrue(result.confidence in 0..100)
        assertNotNull(result.issues)
    }
}
