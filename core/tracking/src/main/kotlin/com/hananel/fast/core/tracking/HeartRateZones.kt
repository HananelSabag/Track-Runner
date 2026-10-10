package com.hananel.fast.core.tracking

import kotlin.math.roundToInt

/**
 * Edwards' five heart-rate zones, the model most football conditioning work
 * uses. [weight] is the zone's multiplier in the training-load sum.
 */
enum class HeartRateZone(val weight: Int) {
    /** Under half of maximum: not training. */
    BELOW(0),
    Z1(1),
    Z2(2),
    Z3(3),

    /** 80–90% of maximum — the threshold zone. */
    Z4(4),

    /** 90% and above — maximal work. */
    Z5(5),
}

class HeartRateZones(val maxBpm: Int) {

    init {
        require(maxBpm > 0) { "Maximum heart rate must be positive" }
    }

    fun zoneOf(bpm: Int): HeartRateZone {
        val fraction = bpm.toDouble() / maxBpm
        return when {
            fraction >= 0.9 -> HeartRateZone.Z5
            fraction >= 0.8 -> HeartRateZone.Z4
            fraction >= 0.7 -> HeartRateZone.Z3
            fraction >= 0.6 -> HeartRateZone.Z2
            fraction >= 0.5 -> HeartRateZone.Z1
            else -> HeartRateZone.BELOW
        }
    }

    companion object {
        /**
         * Tanaka's age formula. An estimate only: it puts roughly one person in
         * seven in the wrong zone, so a measured maximum should replace it as
         * soon as a session provides one.
         */
        fun estimateMaxBpm(ageYears: Int): Int = (208 - 0.7 * ageYears).roundToInt()
    }
}
