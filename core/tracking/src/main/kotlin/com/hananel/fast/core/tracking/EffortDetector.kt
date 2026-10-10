package com.hananel.fast.core.tracking

import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.model.TrackPoint

enum class EffortKind { HIGH_SPEED, SPRINT }

/** One burst of fast running: when it happened, how fast it got, how far it went. */
data class Effort(
    val kind: EffortKind,
    val startMs: Long,
    val endMs: Long,
    val peak: Speed,
    val distanceMeters: Double,
) {
    val durationMs: Long get() = endMs - startMs
}

data class EffortConfig(
    /** The usual research definition: above the threshold for at least one second. */
    val minDurationMs: Long = 1_000,
    /** A dip this short between two fast stretches is the same effort, not two. */
    val mergeGapMs: Long = 2_000,
    /** Samples further apart than this are a signal gap, not continuous movement. */
    val maxSampleGapMs: Long = 5_000,
)

/**
 * Finds the fast efforts in a session: every stretch spent in the high-speed
 * zone or above. An effort whose peak reaches the sprint zone is a sprint.
 *
 * A single sample above the line is ignored: at one sample per second it is
 * indistinguishable from a measurement spike, and the duration rule excludes it.
 */
object EffortDetector {

    fun detect(points: List<TrackPoint>, zones: SpeedZones, config: EffortConfig = EffortConfig()): List<Effort> {
        val entry = zones.lowerBound(SpeedZone.HIGH_SPEED)
        val sprint = zones.lowerBound(SpeedZone.SPRINT)

        // Index ranges of consecutive samples at or above the entry speed.
        val runs = mutableListOf<IntRange>()
        var start = -1
        for (i in points.indices) {
            val fast = points[i].speed >= entry
            val brokenByGap = i > 0 && points[i].timeMs - points[i - 1].timeMs > config.maxSampleGapMs
            if (start >= 0 && (!fast || brokenByGap)) {
                runs += start until i
                start = -1
            }
            if (fast && start < 0) start = i
        }
        if (start >= 0) runs += start..points.lastIndex

        return merge(runs, points, config.mergeGapMs)
            .filter { points[it.last].timeMs - points[it.first].timeMs >= config.minDurationMs }
            .map { range ->
                val peak = range.maxOf { points[it].speed }
                Effort(
                    kind = if (peak >= sprint) EffortKind.SPRINT else EffortKind.HIGH_SPEED,
                    startMs = points[range.first].timeMs,
                    endMs = points[range.last].timeMs,
                    peak = peak,
                    distanceMeters = distance(points, range),
                )
            }
    }

    private fun merge(runs: List<IntRange>, points: List<TrackPoint>, mergeGapMs: Long): List<IntRange> {
        val merged = mutableListOf<IntRange>()
        for (run in runs) {
            val previous = merged.lastOrNull()
            if (previous != null && points[run.first].timeMs - points[previous.last].timeMs <= mergeGapMs) {
                merged[merged.lastIndex] = previous.first..run.last
            } else {
                merged += run
            }
        }
        return merged
    }

    private fun distance(points: List<TrackPoint>, range: IntRange): Double {
        var meters = 0.0
        for (i in range.first + 1..range.last) {
            val seconds = (points[i].timeMs - points[i - 1].timeMs) / 1000.0
            meters += (points[i].speed.metersPerSecond + points[i - 1].speed.metersPerSecond) / 2 * seconds
        }
        return meters
    }
}
