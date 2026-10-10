package com.hananel.fast.core.tracking

import com.hananel.fast.core.model.Speed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ZonesAndIntensityTest {

    @Test
    fun standardSpeedZonesUseTheUsualFootballBands() {
        val zones = SpeedZones.STANDARD

        assertEquals(SpeedZone.WALK, zones.zoneOf(Speed.ofKmh(7.1)))
        assertEquals(SpeedZone.JOG, zones.zoneOf(Speed.ofKmh(7.2)))
        assertEquals(SpeedZone.RUN, zones.zoneOf(Speed.ofKmh(19.7)))
        assertEquals(SpeedZone.HIGH_SPEED, zones.zoneOf(Speed.ofKmh(19.8)))
        assertEquals(SpeedZone.SPRINT, zones.zoneOf(Speed.ofKmh(25.2)))
        assertEquals(SpeedZone.WALK, zones.zoneOf(Speed.ZERO))
    }

    @Test
    fun personalSpeedZonesScaleWithTopSpeed() {
        val zones = SpeedZones.relativeTo(Speed.ofKmh(26.0))

        assertEquals(22.1, zones.lowerBound(SpeedZone.SPRINT).kmh, 1e-9)
        assertEquals(17.16, zones.lowerBound(SpeedZone.HIGH_SPEED).kmh, 1e-9)
    }

    @Test
    fun speedZoneBoundsMustIncrease() {
        assertThrows(IllegalArgumentException::class.java) {
            SpeedZones.ofKmh(jog = 7.0, run = 6.0, highSpeed = 20.0, sprint = 25.0)
        }
    }

    @Test
    fun heartRateZonesAreTenthsOfMaximum() {
        val zones = HeartRateZones(maxBpm = 190)

        assertEquals(HeartRateZone.BELOW, zones.zoneOf(94))
        assertEquals(HeartRateZone.Z1, zones.zoneOf(95))
        assertEquals(HeartRateZone.Z3, zones.zoneOf(133))
        assertEquals(HeartRateZone.Z4, zones.zoneOf(152))
        assertEquals(HeartRateZone.Z5, zones.zoneOf(171))
    }

    @Test
    fun maximumHeartRateEstimateFollowsTanaka() {
        assertEquals(187, HeartRateZones.estimateMaxBpm(ageYears = 30))
    }

    @Test
    fun aRacingHeartDoesNotUndoARest() {
        assertEquals(Intensity.REST, intensityOf(SegmentKind.REST, SpeedZone.WALK, HeartRateZone.Z5))
    }

    @Test
    fun fastRunningIsHighIntensityBeforeTheHeartCatchesUp() {
        assertEquals(Intensity.HIGH, intensityOf(SegmentKind.WORK, SpeedZone.SPRINT, HeartRateZone.Z2))
    }

    @Test
    fun heartRateGradesTheWorkBelowHighSpeed() {
        assertEquals(Intensity.LIGHT, intensityOf(SegmentKind.WORK, SpeedZone.JOG, HeartRateZone.Z2))
        assertEquals(Intensity.MODERATE, intensityOf(SegmentKind.WORK, SpeedZone.JOG, HeartRateZone.Z3))
        assertEquals(Intensity.THRESHOLD, intensityOf(SegmentKind.WORK, SpeedZone.JOG, HeartRateZone.Z4))
        assertEquals(Intensity.HIGH, intensityOf(SegmentKind.WORK, SpeedZone.WALK, HeartRateZone.Z5))
    }

    @Test
    fun withoutHeartRateTheThresholdIsNeverGuessed() {
        val levels = SpeedZone.entries.map { intensityOf(SegmentKind.WORK, it, heartRateZone = null) }

        assertEquals(
            listOf(Intensity.LIGHT, Intensity.MODERATE, Intensity.MODERATE, Intensity.HIGH, Intensity.HIGH),
            levels,
        )
    }
}
