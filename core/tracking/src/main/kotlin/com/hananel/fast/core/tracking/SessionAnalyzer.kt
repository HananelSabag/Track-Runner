package com.hananel.fast.core.tracking

import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.model.TrackPoint
import kotlin.math.abs
import kotlin.math.roundToInt

/** How a kind of activity should be read: its speed bands and what counts as a rest. */
data class ActivityProfile(
    val zones: SpeedZones,
    val rest: RestDetectionConfig,
    val efforts: EffortConfig = EffortConfig(),
) {
    companion object {
        val RUN = ActivityProfile(SpeedZones.STANDARD, RestDetectionConfig.RUN)

        /**
         * Football training, with bands scaled to the player's own top speed when it
         * is known. Until then the standard bands apply, which were set on professionals
         * and will under-count an amateur's sprints.
         */
        fun football(topSpeed: Speed? = null) = ActivityProfile(
            zones = topSpeed?.let(SpeedZones::relativeTo) ?: SpeedZones.STANDARD,
            rest = RestDetectionConfig.FOOTBALL,
        )
    }
}

/** How far the heart rate fell in the first minute of a rest — a direct, repeatable sign of fitness. */
data class RestRecovery(
    val restStartMs: Long,
    val startBpm: Int,
    val afterOneMinuteBpm: Int,
) {
    val dropBpm: Int get() = startBpm - afterOneMinuteBpm
}

data class HeartRateSummary(
    val workAverageBpm: Int,
    val maxBpm: Int,
    /** Over the whole session, rests included: the heart is still loaded while it recovers. */
    val zoneTimeMs: Map<HeartRateZone, Long>,
    /** Edwards' training load: minutes in each zone times the zone's weight. */
    val trainingLoad: Double,
    val recoveries: List<RestRecovery>,
)

/** Everything derived from one session. Recomputable at any time from its track points. */
data class SessionAnalysis(
    val segments: List<Segment>,
    val workMs: Long,
    val restMs: Long,
    val workDistanceMeters: Double,
    /** Distance over working time only — standing around does not drag it down. */
    val workAverageSpeed: Speed,
    val maxSpeed: Speed,
    val maxSpeedAtMs: Long?,
    /** The fastest speed held for three seconds: a top speed that one noisy sample cannot fake. */
    val peak3s: Speed,
    /** Working time only. */
    val zoneTimeMs: Map<SpeedZone, Long>,
    val zoneDistanceMeters: Map<SpeedZone, Double>,
    val efforts: List<Effort>,
    val heartRate: HeartRateSummary?,
) {
    val sprints: List<Effort> get() = efforts.filter { it.kind == EffortKind.SPRINT }

    val highSpeedDistanceMeters: Double
        get() = (zoneDistanceMeters[SpeedZone.HIGH_SPEED] ?: 0.0) + (zoneDistanceMeters[SpeedZone.SPRINT] ?: 0.0)
}

object SessionAnalyzer {

    fun analyze(
        points: List<TrackPoint>,
        profile: ActivityProfile,
        heartRateZones: HeartRateZones? = null,
    ): SessionAnalysis {
        val segments = WorkRestSegmenter.segment(points, profile.rest)
        val maxGapMs = profile.efforts.maxSampleGapMs

        var workMs = 0L
        var workMeters = 0.0
        val zoneTime = SpeedZone.entries.associateWith { 0L }.toMutableMap()
        val zoneMeters = SpeedZone.entries.associateWith { 0.0 }.toMutableMap()

        // Walk the session one sample interval at a time. Each interval belongs to
        // the segment it starts in and to the zone of its average speed.
        var segmentIndex = 0
        for (i in 1 until points.size) {
            val from = points[i - 1]
            val to = points[i]
            val dtMs = to.timeMs - from.timeMs
            if (dtMs <= 0 || dtMs > maxGapMs) continue

            while (segmentIndex < segments.lastIndex && from.timeMs >= segments[segmentIndex].endMs) segmentIndex++
            if (segments.getOrNull(segmentIndex)?.kind != SegmentKind.WORK) continue

            val speed = Speed((from.speed.metersPerSecond + to.speed.metersPerSecond) / 2)
            val meters = speed.metersPerSecond * dtMs / 1000.0
            val zone = profile.zones.zoneOf(speed)
            workMs += dtMs
            workMeters += meters
            zoneTime[zone] = zoneTime.getValue(zone) + dtMs
            zoneMeters[zone] = zoneMeters.getValue(zone) + meters
        }

        val restMs = segments.filter { it.kind == SegmentKind.REST }.sumOf { it.durationMs }
        val fastest = points.maxByOrNull { it.speed }

        return SessionAnalysis(
            segments = segments,
            workMs = workMs,
            restMs = restMs,
            workDistanceMeters = workMeters,
            workAverageSpeed = if (workMs > 0) Speed(workMeters / (workMs / 1000.0)) else Speed.ZERO,
            maxSpeed = fastest?.speed ?: Speed.ZERO,
            maxSpeedAtMs = fastest?.timeMs,
            peak3s = bestWindowAverage(points, windowMs = 3_000),
            zoneTimeMs = zoneTime,
            zoneDistanceMeters = zoneMeters,
            efforts = EffortDetector.detect(points, profile.zones, profile.efforts),
            heartRate = heartRateZones?.let { summarizeHeartRate(points, segments, it, maxGapMs) },
        )
    }

    /**
     * The highest average speed over any stretch of about [windowMs]. Zero if no
     * stretch is that long. Real samples do not land exactly a second apart, so a
     * window may be off by [WINDOW_JITTER_MS] either way.
     */
    private fun bestWindowAverage(points: List<TrackPoint>, windowMs: Long): Speed {
        var best = 0.0
        var start = 0
        for (end in points.indices) {
            while (points[end].timeMs - points[start].timeMs > windowMs + WINDOW_JITTER_MS) start++
            if (points[end].timeMs - points[start].timeMs >= windowMs - WINDOW_JITTER_MS) {
                val average = (start..end).sumOf { points[it].speed.metersPerSecond } / (end - start + 1)
                if (average > best) best = average
            }
        }
        return Speed(best)
    }

    private fun summarizeHeartRate(
        points: List<TrackPoint>,
        segments: List<Segment>,
        zones: HeartRateZones,
        maxGapMs: Long,
    ): HeartRateSummary? {
        val readings = points.filter { it.heartRateBpm != null }
        if (readings.isEmpty()) return null

        val zoneTime = HeartRateZone.entries.associateWith { 0L }.toMutableMap()
        for (i in 1 until readings.size) {
            val dtMs = readings[i].timeMs - readings[i - 1].timeMs
            if (dtMs <= 0 || dtMs > maxGapMs) continue
            val zone = zones.zoneOf(readings[i].heartRateBpm!!)
            zoneTime[zone] = zoneTime.getValue(zone) + dtMs
        }

        val workReadings = readings.filter { reading ->
            segments.any { it.kind == SegmentKind.WORK && reading.timeMs >= it.startMs && reading.timeMs < it.endMs }
        }

        return HeartRateSummary(
            workAverageBpm = workReadings.map { it.heartRateBpm!! }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0,
            maxBpm = readings.maxOf { it.heartRateBpm!! },
            zoneTimeMs = zoneTime,
            trainingLoad = zoneTime.entries.sumOf { (zone, ms) -> ms / 60_000.0 * zone.weight },
            recoveries = segments
                .filter { it.kind == SegmentKind.REST && it.durationMs >= ONE_MINUTE_MS }
                .mapNotNull { rest ->
                    val start = readings.nearestTo(rest.startMs) ?: return@mapNotNull null
                    val later = readings.nearestTo(rest.startMs + ONE_MINUTE_MS) ?: return@mapNotNull null
                    RestRecovery(rest.startMs, start.heartRateBpm!!, later.heartRateBpm!!)
                },
        )
    }

    /** The reading closest to [timeMs], or null when none is close enough to stand in for it. */
    private fun List<TrackPoint>.nearestTo(timeMs: Long): TrackPoint? =
        minByOrNull { abs(it.timeMs - timeMs) }
            ?.takeIf { abs(it.timeMs - timeMs) <= NEAREST_READING_TOLERANCE_MS }

    private const val ONE_MINUTE_MS = 60_000L
    private const val NEAREST_READING_TOLERANCE_MS = 5_000L
    private const val WINDOW_JITTER_MS = 250L
}
