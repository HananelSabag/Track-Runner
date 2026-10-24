package com.hananel.fast.core.tracking

import com.hananel.fast.core.model.GeoPoint
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.model.TrackPoint

enum class SegmentKind { WORK, REST }

/** A stretch of the session that was all work or all rest. */
data class Segment(val kind: SegmentKind, val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * When does standing around count as a rest?
 *
 * Movement decides where a rest begins and ends: after a hard effort the heart
 * stays high for a minute or more while the player is already standing, so heart
 * rate alone would report "still working" long after the work has stopped.
 *
 * But the heart has the last word on a long rest (D42). Past its first
 * [heartGraceMs], a rest whose heart has not come down was play at a pace the
 * movement cannot tell from standing — keeping goal, holding a position on a
 * small pitch. On 2026-10-24 that was most of what was called rest.
 */
data class RestDetectionConfig(
    /** Staying inside a circle of this radius counts as not going anywhere. GPS drifts 3–5 m while standing, so it cannot be much smaller. */
    val stillRadiusMeters: Double = 12.0,
    /** Slow pacing still counts as standing around. */
    val stillMaxSpeed: Speed = Speed.ofKmh(5.0),
    /** How long the stillness must last before it is a rest. Shorter stops are recovery inside the work. */
    val minRestMs: Long = 60_000,
    /** A rest ends when the player is at least jogging… */
    val resumeSpeed: Speed = Speed.ofKmh(7.2),
    /** …for this long, so one noisy sample cannot end it. */
    val resumeHoldMs: Long = 3_000,
    /** A rest also ends when the player has walked this far from where it began. */
    val leaveRadiusMeters: Double = 30.0,
    /**
     * And once the player is this far from where he was [relocateWindowMs] earlier.
     * On a small pitch a player can play for twenty minutes at walking pace without
     * ever going [leaveRadiusMeters] from one spot; standing drift does not cover
     * this distance in this time, and pacing on the spot does not either.
     */
    val relocateMeters: Double = 15.0,
    val relocateWindowMs: Long = 15_000,
    /** The heart is given this long to come down before it is asked whether this is a rest at all. */
    val heartGraceMs: Long = 120_000,
    /** Fewer heart readings than this after the grace, and the movement's verdict stands. */
    val minHeartReadings: Int = 30,
) {
    companion object {
        /** Football training: breaks of a minute or more are rests; shorter pauses are part of the drill. */
        val FOOTBALL = RestDetectionConfig()

        /** Running: a 20-second stop (a crossing, a shoelace) is already time not spent running. */
        val RUN = RestDetectionConfig(minRestMs = 20_000)
    }
}

/**
 * Splits a session into alternating work and rest segments, so nobody has to
 * remember to press pause — or to press resume afterwards.
 *
 * A rest starts at the moment the player actually stopped, not at the moment
 * the stop became long enough to count; the segmenter looks back.
 */
object WorkRestSegmenter {

    fun segment(points: List<TrackPoint>, config: RestDetectionConfig): List<Segment> {
        if (points.size < 2) return emptyList()

        val calmSpeeds = medianFilter(points)
        val segments = mutableListOf<Segment>()
        var kind = SegmentKind.WORK
        var segmentStartMs = points.first().timeMs

        // While working: where and when the current spell of stillness began, if there is one.
        var stillSinceIndex = -1
        var anchor: GeoPoint? = null
        // While resting: when the player started moving fast enough to end the rest.
        var resumeSinceIndex = -1
        // While resting: the first sample of the rest, and the latest one at least relocateWindowMs old.
        var restStartIndex = -1
        var lagIndex = -1

        fun close(endMs: Long, next: SegmentKind) {
            if (endMs > segmentStartMs) segments += Segment(kind, segmentStartMs, endMs)
            segmentStartMs = endMs
            kind = next
        }

        for (i in points.indices) {
            val point = points[i]
            val slow = calmSpeeds[i] <= config.stillMaxSpeed

            when (kind) {
                SegmentKind.WORK -> {
                    val insideCircle = isWithin(point.position, anchor, config.stillRadiusMeters)
                    if (slow && stillSinceIndex >= 0 && insideCircle) {
                        if (anchor == null) anchor = point.position
                        if (point.timeMs - points[stillSinceIndex].timeMs >= config.minRestMs) {
                            close(points[stillSinceIndex].timeMs, SegmentKind.REST)
                            resumeSinceIndex = -1
                            restStartIndex = stillSinceIndex
                            lagIndex = stillSinceIndex
                        }
                    } else if (slow) {
                        // Slow, but either the first slow sample or one that wandered out of the
                        // circle: the stillness starts over from here.
                        stillSinceIndex = i
                        anchor = point.position
                    } else {
                        stillSinceIndex = -1
                        anchor = null
                    }
                }

                SegmentKind.REST -> {
                    if (anchor == null) anchor = point.position

                    if (point.speed >= config.resumeSpeed) {
                        if (resumeSinceIndex < 0) resumeSinceIndex = i
                    } else {
                        resumeSinceIndex = -1
                    }

                    val movingAgain = resumeSinceIndex >= 0 &&
                        point.timeMs - points[resumeSinceIndex].timeMs >= config.resumeHoldMs
                    val walkedAway = !isWithin(point.position, anchor, config.leaveRadiusMeters)

                    while (lagIndex + 1 < i && points[lagIndex + 1].timeMs <= point.timeMs - config.relocateWindowMs) lagIndex++
                    val relocated = lagIndex >= restStartIndex &&
                        point.timeMs - points[lagIndex].timeMs >= config.relocateWindowMs &&
                        !isWithin(point.position, points[lagIndex].position, config.relocateMeters)

                    if (relocated && !movingAgain) {
                        // He set off somewhere in the last few seconds: the work starts at the first sample
                        // that had clearly left the spot, not at the start of the window.
                        val setOff = (lagIndex + 1..i).firstOrNull {
                            !isWithin(points[it].position, points[lagIndex].position, config.relocateMeters / SET_OFF_FRACTION)
                        } ?: i
                        close(points[setOff].timeMs, SegmentKind.WORK)
                        stillSinceIndex = if (slow) i else -1
                        anchor = if (slow) point.position else null
                        continue
                    }

                    if (movingAgain) {
                        close(points[resumeSinceIndex].timeMs, SegmentKind.WORK)
                        stillSinceIndex = -1
                        anchor = null
                    } else if (walkedAway) {
                        close(point.timeMs, SegmentKind.WORK)
                        // Still slow, so this may be the start of the next rest somewhere else.
                        stillSinceIndex = if (slow) i else -1
                        anchor = if (slow) point.position else null
                    }
                }
            }
        }

        close(points.last().timeMs, kind)
        return segments
    }

    /**
     * Turns back into work every rest longer than [RestDetectionConfig.heartGraceMs] in which,
     * after that grace, the heart stayed at or above [activeBpm]. Neighbouring work is joined up.
     */
    fun confirmByHeart(segments: List<Segment>, points: List<TrackPoint>, config: RestDetectionConfig, activeBpm: Int): List<Segment> {
        val judged = segments.map { segment ->
            if (segment.kind != SegmentKind.REST || segment.durationMs <= config.heartGraceMs) return@map segment
            val after = points
                .filter { it.timeMs >= segment.startMs + config.heartGraceMs && it.timeMs < segment.endMs }
                .mapNotNull { it.heartRateBpm }
                .sorted()
            val heartStayedUp = after.size >= config.minHeartReadings && after[after.size / 2] >= activeBpm
            if (heartStayedUp) segment.copy(kind = SegmentKind.WORK) else segment
        }
        return judged.fold(mutableListOf()) { merged, segment ->
            val last = merged.lastOrNull()
            if (last != null && last.kind == segment.kind && last.endMs == segment.startMs) {
                merged[merged.lastIndex] = last.copy(endMs = segment.endMs)
            } else {
                merged += segment
            }
            merged
        }
    }

    /** With no position on either side there is nothing to contradict "has not moved". */
    private fun isWithin(position: GeoPoint?, anchor: GeoPoint?, radiusMeters: Double): Boolean {
        if (position == null || anchor == null) return true
        return position.distanceTo(anchor) <= radiusMeters
    }

    /**
     * Speed with single-sample spikes removed. A standing player's wrist still
     * moves — a drink, a gesture — and one such spike must not break up a rest.
     */
    private fun medianFilter(points: List<TrackPoint>): List<Speed> =
        points.indices.map { i ->
            val from = (i - MEDIAN_HALF_WINDOW).coerceAtLeast(0)
            val to = (i + MEDIAN_HALF_WINDOW).coerceAtMost(points.lastIndex)
            val window = (from..to).map { points[it].speed }.sorted()
            window[window.size / 2]
        }

    private const val MEDIAN_HALF_WINDOW = 2

    /** A third of the way there is past standing drift, and close enough to when he really set off. */
    private const val SET_OFF_FRACTION = 3.0
}
