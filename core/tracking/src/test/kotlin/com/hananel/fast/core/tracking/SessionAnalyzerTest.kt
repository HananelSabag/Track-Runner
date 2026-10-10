package com.hananel.fast.core.tracking

import com.hananel.fast.core.model.Speed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAnalyzerTest {

    private val football = ActivityProfile.football()

    @Test
    fun averageSpeedCountsWorkingTimeOnly() {
        val points = track {
            stand(90)
            run(12.0, 120)
            stand(150)
            run(12.0, 60)
        }

        val analysis = SessionAnalyzer.analyze(points, football)

        assertEquals(240_000, analysis.restMs)
        assertEquals(179_000, analysis.workMs)
        // Over the whole 419 s the average would be about 5 km/h; over the work it is the 12 it was run at.
        assertEquals(12.0, analysis.workAverageSpeed.kmh, 0.1)
        assertEquals(595.0, analysis.workDistanceMeters, 0.5)
    }

    @Test
    fun speedZoneTimeAddsUpToTheWorkingTime() {
        val points = track {
            run(5.0, 30)
            run(10.0, 30)
            run(17.0, 30)
            stand(120)
            run(22.0, 10)
            run(27.0, 5)
        }

        val analysis = SessionAnalyzer.analyze(points, football)

        assertEquals(analysis.workMs, analysis.zoneTimeMs.values.sum())
        assertEquals(analysis.workDistanceMeters, analysis.zoneDistanceMeters.values.sum(), 1e-6)
        assertTrue(analysis.zoneTimeMs.getValue(SpeedZone.SPRINT) > 0)
        assertTrue(analysis.highSpeedDistanceMeters > 0)
    }

    @Test
    fun oneNoisySampleSetsTheMaximumButNotTheThreeSecondPeak() {
        val points = track {
            run(12.0, 20)
            second(30.0)
            run(12.0, 20)
        }

        val analysis = SessionAnalyzer.analyze(points, football)

        assertEquals(30.0, analysis.maxSpeed.kmh, 1e-9)
        assertEquals(20_000L, analysis.maxSpeedAtMs)
        assertEquals(16.5, analysis.peak3s.kmh, 1e-9)
    }

    @Test
    fun aHeldTopSpeedIsTheThreeSecondPeak() {
        val points = track {
            run(12.0, 10)
            run(26.0, 4)
            run(12.0, 10)
        }

        assertEquals(26.0, SessionAnalyzer.analyze(points, football).peak3s.kmh, 1e-9)
    }

    @Test
    fun sprintsAreListedWithTheirPeaks() {
        val points = track {
            run(10.0, 20)
            run(26.0, 4)
            run(10.0, 20)
            run(28.0, 3)
            run(10.0, 20)
        }

        val sprints = SessionAnalyzer.analyze(points, football).sprints

        assertEquals(listOf(26.0, 28.0), sprints.map { it.peak.kmh })
    }

    @Test
    fun heartRateIsLeftOutWhenNoZonesAreGiven() {
        val points = track {
            heartRate(150)
            run(12.0, 60)
        }

        assertNull(SessionAnalyzer.analyze(points, football).heartRate)
    }

    @Test
    fun trainingLoadIsMinutesInZoneTimesTheZoneWeight() {
        val points = track {
            heartRate(152) // 80% of 190: zone 4
            run(12.0, 601)
        }

        val heartRate = SessionAnalyzer.analyze(points, football, HeartRateZones(maxBpm = 190)).heartRate!!

        assertEquals(40.0, heartRate.trainingLoad, 1e-9)
        assertEquals(152, heartRate.workAverageBpm)
        assertEquals(152, heartRate.maxBpm)
    }

    @Test
    fun recoveryIsTheHeartRateDropInTheFirstMinuteOfARest() {
        val points = track {
            heartRate(170)
            run(12.0, 120)
            // Standing still while the heart rate falls half a beat per second for a minute, then holds.
            repeat(120) { second ->
                heartRate(170 - minOf(second, 60) / 2)
                stand(1)
            }
        }

        val heartRate = SessionAnalyzer.analyze(points, football, HeartRateZones(maxBpm = 190)).heartRate!!
        val recovery = heartRate.recoveries.single()

        assertEquals(120_000L, recovery.restStartMs)
        assertEquals(170, recovery.startBpm)
        assertEquals(140, recovery.afterOneMinuteBpm)
        assertEquals(30, recovery.dropBpm)
    }

    @Test
    fun aPlayerWithAKnownTopSpeedGetsPersonalZones() {
        val profile = ActivityProfile.football(topSpeed = Speed.ofKmh(24.0))

        assertEquals(20.4, profile.zones.lowerBound(SpeedZone.SPRINT).kmh, 1e-9)
    }
}
