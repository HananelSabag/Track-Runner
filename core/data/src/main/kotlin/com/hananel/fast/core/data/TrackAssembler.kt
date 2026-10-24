package com.hananel.fast.core.data

import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.model.TrackPoint
import kotlin.math.abs

/**
 * Turns the raw records of a session into the track every analysis reads:
 * one point per usable fix, with the speed cleaned of lone spikes and the
 * nearest heart-rate reading attached.
 *
 * Point times are measured from the start of the session, so the first second
 * of every session is second zero.
 *
 * The same function serves the live screen and the saved session, which is why
 * a number seen on the wrist during training is the number in the history.
 */
object TrackAssembler {

    /** Faster than any human has run. A reading above it was taken in a vehicle, or is a receiver fault. */
    val MAX_HUMAN_SPEED: Speed = Speed.ofKmh(45.0)

    fun assemble(session: RecordedSession): List<TrackPoint> {
        val origin = session.header.startedElapsedMs
        val trimAfter = session.effectiveTrimAfterMs

        // A fix without a speed says nothing about how fast the player moved, and a
        // speed no runner can reach is left out rather than reported as a record.
        val fixes = buildList {
            var lastTimeMs = Long.MIN_VALUE
            for (fix in session.fixes.sortedBy { it.timeMs }) {
                if (!fix.hasSpeed || fix.speed > MAX_HUMAN_SPEED) continue
                if (fix.timeMs < origin || (trimAfter != null && fix.timeMs > trimAfter)) continue
                if (fix.timeMs <= lastTimeMs) continue
                lastTimeMs = fix.timeMs
                add(fix)
            }
        }

        val heartRates = session.heartRates.sortedBy { it.timeMs }
        var heartIndex = 0

        return fixes.indices.map { i ->
            val fix = fixes[i]
            // Both lists are in time order, so the nearest reading only ever moves forward.
            while (heartIndex < heartRates.lastIndex &&
                abs(heartRates[heartIndex + 1].timeMs - fix.timeMs) <= abs(heartRates[heartIndex].timeMs - fix.timeMs)
            ) {
                heartIndex++
            }
            val heartRate = heartRates.getOrNull(heartIndex)
                ?.takeIf { abs(it.timeMs - fix.timeMs) <= HEART_RATE_MAX_AGE_MS }

            TrackPoint(
                timeMs = fix.timeMs - origin,
                speed = calmSpeed(fixes, i),
                position = fix.position,
                heartRateBpm = heartRate?.bpm,
            )
        }
    }

    /**
     * The median of a fix and its two neighbours, so one reading that jumps cannot
     * set a top speed or start a sprint.
     *
     * Where a neighbour is missing — the newest fix of a live session, or either
     * side of a signal gap — the lower of the two remaining readings is taken. A
     * speed has to be confirmed by a second reading before it counts; until then
     * the cautious value stands. On the live screen that delays a rising speed by
     * one second and never invents one.
     */
    private fun calmSpeed(fixes: List<FixRecord>, index: Int): Speed {
        val own = fixes[index]
        val before = fixes.getOrNull(index - 1)?.takeIf { own.timeMs - it.timeMs <= NEIGHBOUR_MAX_GAP_MS }
        val after = fixes.getOrNull(index + 1)?.takeIf { it.timeMs - own.timeMs <= NEIGHBOUR_MAX_GAP_MS }
        return when {
            before != null && after != null -> listOf(before.speed, own.speed, after.speed).sorted()[1]
            before != null -> minOf(before.speed, own.speed)
            after != null -> minOf(after.speed, own.speed)
            else -> own.speed
        }
    }

    /** Further apart than this, two fixes are either side of a signal gap and say nothing about each other. */
    private const val NEIGHBOUR_MAX_GAP_MS = 5_000L
    private const val HEART_RATE_MAX_AGE_MS = 3_000L
}
