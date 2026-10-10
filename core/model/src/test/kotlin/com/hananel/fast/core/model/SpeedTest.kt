package com.hananel.fast.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedTest {

    @Test
    fun convertsMetresPerSecondToDisplayUnits() {
        val speed = Speed(10.0)

        assertEquals(36.0, speed.kmh, 1e-9)
        assertEquals(22.369, speed.mph, 1e-3)
        assertEquals(36.0, speed.valueIn(SpeedUnit.KMH), 1e-9)
    }

    @Test
    fun roundTripsThroughEachUnit() {
        assertEquals(27.5, Speed.ofKmh(27.5).kmh, 1e-9)
        assertEquals(17.1, Speed.ofMph(17.1).mph, 1e-9)
    }

    @Test
    fun paceIsFourMinutesPerKmAtFifteenKmh() {
        assertEquals(240.0, Speed.ofKmh(15.0).secondsPerKm!!, 1e-9)
    }

    @Test
    fun paceIsUndefinedWhenStandingStill() {
        assertNull(Speed.ZERO.secondsPerKm)
    }

    @Test
    fun ordersBySpeed() {
        assertTrue(Speed.ofKmh(20.0) > Speed.ofKmh(19.9))
    }
}
