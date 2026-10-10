package com.hananel.fast.core.tracking.sprint

import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.model.SpeedSample

/** Is the receiver locked on well enough to start a test? */
enum class GnssReadiness {
    /** No recent readings at all. */
    SEARCHING,

    /** Readings arrive, but too few or too poor to trust a sprint to. */
    WEAK,

    READY,
}

object GnssReadinessCheck {

    /**
     * Judges the last few seconds of readings. Starting a test before the fix has
     * settled is the commonest way to get a bad measurement, so the bar is the
     * same one the analysis will apply afterwards.
     */
    fun evaluate(samples: List<SpeedSample>, nowMs: Long): GnssReadiness {
        val recent = samples.filter { nowMs - it.timeMs <= LOOKBACK_MS }
        if (recent.isEmpty()) return GnssReadiness.SEARCHING
        if (recent.size < MIN_RECENT_SAMPLES) return GnssReadiness.WEAK

        val speedAccuracies = recent.mapNotNull { it.speedAccuracy?.metersPerSecond }
        if (speedAccuracies.isNotEmpty() && speedAccuracies.average() > MAX_SPEED_ACCURACY) return GnssReadiness.WEAK

        val positionAccuracies = recent.mapNotNull { it.horizontalAccuracyMeters }
        if (positionAccuracies.isNotEmpty() && positionAccuracies.average() > MAX_POSITION_ACCURACY_METERS) return GnssReadiness.WEAK

        return GnssReadiness.READY
    }

    private const val LOOKBACK_MS = 6_000L

    /** At one reading a second, four in six seconds allows for one or two dropped. */
    private const val MIN_RECENT_SAMPLES = 4
    private const val MAX_SPEED_ACCURACY = 1.0
    private const val MAX_POSITION_ACCURACY_METERS = 12.0
}

/** Where a running test stands, judged from the readings since "go". */
data class SprintProgress(
    val started: Boolean,
    val peak: Speed,
    /** The runner got up to speed and has clearly slowed down again: the test can end by itself. */
    val finished: Boolean,
) {
    companion object {

        fun of(samples: List<SpeedSample>, config: SprintTestConfig = SprintTestConfig()): SprintProgress {
            // Judged on the same glitch-free speeds as the final result. Otherwise one
            // wild reading would become the peak, and the honest readings after it
            // would look like slowing down and end the test early.
            val steady = SprintTestAnalyzer.withoutIsolatedJumps(samples)
            val peakIndex = steady.indices.maxByOrNull { steady[it] } ?: return SprintProgress(false, Speed.ZERO, false)
            val peak = Speed(steady[peakIndex])
            val started = peak >= STARTED_SPEED

            // Finished: a real sprint happened, and the last two readings are both
            // well under the peak — two, so that one dropped-out reading cannot end the test early.
            val afterPeak = steady.drop(peakIndex + 1)
            val slowedDown = afterPeak.size >= 2 &&
                afterPeak.takeLast(2).all { it < peak.metersPerSecond * SLOWED_FRACTION }
            return SprintProgress(started, peak, finished = peak >= config.minTopSpeed && slowedDown)
        }

        private val STARTED_SPEED = Speed.ofKmh(8.0)
        private const val SLOWED_FRACTION = 0.5
    }
}
