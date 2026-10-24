package com.hananel.fast.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceComparisonTest {

    // The watch claims the same placeholder accuracy on every fix; the phone measures its own.
    private val watch = SessionBuilder(ActivityKind.RUN).apply {
        speedAccuracy = 25.0
        positionAccuracy = 125.0
        stand(5)
        run(20.0, 15)
    }.build()

    // Started two seconds later, then the same run on the same line.
    private val phone = SessionBuilder(ActivityKind.RUN).apply {
        gap(2)
        stand(3)
        run(20.0, 15)
    }.build("22222222-2222-3333-4444-555555555555")

    @Test
    fun onlyTheTimeBothWereRecordingCounts() {
        val comparison = DeviceComparisons.of(watch, phone)!!

        assertEquals(17_000L, comparison.sharedMs)
        assertEquals(18, comparison.phone.fixes)
        assertEquals(18, comparison.watch.fixes)
    }

    @Test
    fun theSameRunGivesTheSameFiguresOnBoth() {
        val comparison = DeviceComparisons.of(watch, phone)!!

        assertEquals(20.0, comparison.watch.best3sReported.kmh, 0.01)
        assertEquals(20.0, comparison.phone.best3sReported.kmh, 0.01)
        assertEquals(20.0, comparison.phone.best3sFromPositions!!.kmh, 0.1)
        assertEquals(comparison.watch.distanceFromPositionsMeters!!, comparison.phone.distanceFromPositionsMeters!!, 0.01)
        assertEquals(1.0, comparison.phone.fixesPerSecond, 0.01)
        assertEquals(0.0, comparison.meanSeparationMeters!!, 0.01)
    }

    @Test
    fun aPlaceholderAccuracyIsNotPresentedAsAClaim() {
        val comparison = DeviceComparisons.of(watch, phone)!!

        assertNull(comparison.watch.speedAccuracy)
        assertNull(comparison.watch.positionAccuracyMeters)
        assertEquals(0.3, comparison.phone.speedAccuracy!!.metersPerSecond, 0.001)
        assertEquals(4.0, comparison.phone.positionAccuracyMeters!!, 0.001)
    }

    @Test
    fun recordingsAtDifferentTimesAreNotCompared() {
        val later = SessionBuilder(ActivityKind.RUN).apply {
            gap(120)
            run(12.0, 30)
        }.build("33333333-2222-3333-4444-555555555555")

        assertNull(DeviceComparisons.of(watch, later))
        assertEquals(0L, DeviceComparisons.overlap(watch, later))
    }

    @Test
    fun aMomentOfOverlapIsNotEnough() {
        val brief = SessionBuilder(ActivityKind.RUN).apply {
            gap(17)
            run(12.0, 30)
        }.build("44444444-2222-3333-4444-555555555555")

        assertTrue(DeviceComparisons.overlap(watch, brief) in 1 until DeviceComparisons.MIN_SHARED_MS)
        assertNull(DeviceComparisons.of(watch, brief))
        assertNotNull(DeviceComparisons.of(watch, phone))
    }
}
