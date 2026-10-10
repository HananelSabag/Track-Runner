package com.hananel.fast.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssBandsTest {

    private val l1 = 1575.42e6
    private val l5 = 1176.45e6
    private val g1 = 1602.0e6

    @Test
    fun namesTheCommonCarriers() {
        assertEquals("L1", bandName(l1))
        assertEquals("L5", bandName(l5))
        assertEquals("G1", bandName(g1))
        assertEquals("B1", bandName(1561.098e6))
    }

    @Test
    fun fallsBackToTheFrequencyForUnknownCarriers() {
        assertEquals("2492MHz", bandName(2492.028e6))
    }

    @Test
    fun groupsBandsPerConstellation() {
        val signals = listOf(
            GnssSignal("GPS", l5),
            GnssSignal("GPS", l1),
            GnssSignal("GLONASS", g1),
        )

        assertEquals("GPS L1+L5, GLONASS G1", summarizeSignals(signals))
    }

    @Test
    fun dualFrequencyNeedsBothAnUpperAndALowerBand() {
        assertTrue(isDualFrequency(listOf(GnssSignal("GPS", l1), GnssSignal("GPS", l5))))
        assertFalse(isDualFrequency(listOf(GnssSignal("GPS", l1), GnssSignal("GLONASS", g1))))
        assertFalse(isDualFrequency(emptyList()))
    }
}
