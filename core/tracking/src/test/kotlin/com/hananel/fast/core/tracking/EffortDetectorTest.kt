package com.hananel.fast.core.tracking

import com.hananel.fast.core.model.Speed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffortDetectorTest {

    private val standard = SpeedZones.STANDARD

    @Test
    fun aBurstThatReachesTheSprintZoneIsOneSprint() {
        val points = track {
            run(10.0, 10)
            listOf(18.0, 22.0, 26.0, 27.0, 24.0, 21.0, 15.0).forEach { second(it) }
            run(10.0, 10)
        }

        val effort = EffortDetector.detect(points, standard).single()

        assertEquals(EffortKind.SPRINT, effort.kind)
        assertEquals(27.0, effort.peak.kmh, 1e-9)
        // From the first sample above 19.8 km/h (22) to the last (21).
        assertEquals(4_000, effort.durationMs)
        assertEquals(27.36, effort.distanceMeters, 0.01)
    }

    @Test
    fun aFastStretchBelowTheSprintLineIsAHighSpeedRun() {
        val points = track {
            run(10.0, 5)
            listOf(20.0, 21.0, 20.0).forEach { second(it) }
            run(10.0, 5)
        }

        assertEquals(EffortKind.HIGH_SPEED, EffortDetector.detect(points, standard).single().kind)
    }

    @Test
    fun aSingleFastSampleIsNotAnEffort() {
        val points = track {
            run(10.0, 5)
            second(30.0)
            run(10.0, 5)
        }

        assertTrue(EffortDetector.detect(points, standard).isEmpty())
    }

    @Test
    fun aBriefDipDoesNotSplitOneEffortInTwo() {
        val points = track {
            run(10.0, 5)
            listOf(21.0, 22.0, 18.0, 22.0, 21.0).forEach { second(it) }
            run(10.0, 5)
        }

        val effort = EffortDetector.detect(points, standard).single()

        assertEquals(4_000, effort.durationMs)
    }

    @Test
    fun aSignalGapSplitsEfforts() {
        val points = track {
            second(22.0)
            second(22.0)
            gap(10)
            second(22.0)
            second(22.0)
        }

        assertEquals(2, EffortDetector.detect(points, standard).size)
    }

    @Test
    fun personalZonesCountASprintTheStandardZonesMiss() {
        val points = track {
            run(10.0, 5)
            listOf(19.0, 21.0, 21.0, 19.0).forEach { second(it) }
            run(10.0, 5)
        }
        val personal = SpeedZones.relativeTo(Speed.ofKmh(24.0))

        // 21 km/h is 87.5% of this player's top speed, but under the professional 25.2 line.
        assertEquals(EffortKind.SPRINT, EffortDetector.detect(points, personal).single().kind)
        assertEquals(EffortKind.HIGH_SPEED, EffortDetector.detect(points, standard).single().kind)
    }
}
