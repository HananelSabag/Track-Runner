package com.hananel.fast.core.model

/**
 * A speed, stored in metres per second — the unit every sensor reports in.
 * Conversion to a display unit happens only at the edge, so two sources can
 * never be compared or fused in mismatched units.
 */
@JvmInline
value class Speed(val metersPerSecond: Double) : Comparable<Speed> {

    val kmh: Double get() = metersPerSecond * KMH_PER_MPS

    val mph: Double get() = metersPerSecond * MPH_PER_MPS

    /** Running pace. Null when standing still, where pace has no meaning. */
    val secondsPerKm: Double?
        get() = if (metersPerSecond > 0.0) METERS_PER_KM / metersPerSecond else null

    fun valueIn(unit: SpeedUnit): Double = when (unit) {
        SpeedUnit.KMH -> kmh
        SpeedUnit.MPH -> mph
    }

    override fun compareTo(other: Speed): Int = metersPerSecond.compareTo(other.metersPerSecond)

    companion object {
        private const val KMH_PER_MPS = 3.6
        private const val MPH_PER_MPS = 2.2369362920544025
        private const val METERS_PER_KM = 1000.0

        val ZERO = Speed(0.0)

        fun ofKmh(kmh: Double) = Speed(kmh / KMH_PER_MPS)

        fun ofMph(mph: Double) = Speed(mph / MPH_PER_MPS)
    }
}

enum class SpeedUnit { KMH, MPH }
