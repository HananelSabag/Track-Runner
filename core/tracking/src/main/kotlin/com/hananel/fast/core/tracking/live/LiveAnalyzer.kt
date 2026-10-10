package com.hananel.fast.core.tracking.live

import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.model.TrackPoint
import com.hananel.fast.core.tracking.ActivityProfile
import com.hananel.fast.core.tracking.Effort
import com.hananel.fast.core.tracking.EffortKind
import com.hananel.fast.core.tracking.HeartRateZones
import com.hananel.fast.core.tracking.Intensity
import com.hananel.fast.core.tracking.Segment
import com.hananel.fast.core.tracking.SegmentKind
import com.hananel.fast.core.tracking.SessionAnalysis
import com.hananel.fast.core.tracking.SessionAnalyzer
import com.hananel.fast.core.tracking.SpeedZone
import com.hananel.fast.core.tracking.intensityOf
import kotlin.math.abs

/** A finished stretch of work, shown while the player rests after it. */
data class BlockSummary(val durationMs: Long, val averageSpeed: Speed, val sprints: Int)

/** Everything the watch shows at one moment of a session. */
data class LiveState(
    /** Since the session started. */
    val elapsedMs: Long,
    val segment: SegmentKind,
    /** The stopwatch of the current block. A rest counts from the moment the player stopped. */
    val segmentElapsedMs: Long,
    val speed: Speed,
    val zone: SpeedZone,
    val intensity: Intensity,
    val heartRateBpm: Int?,
    /** True when no usable fix has arrived for a few seconds: the speed shown is not current. */
    val signalLost: Boolean,
    val workMs: Long,
    val restMs: Long,
    val workDistanceMeters: Double,
    val workAverageSpeed: Speed,
    val maxSpeed: Speed,
    val sprintCount: Int,
    /** A sprint is under way right now. */
    val sprinting: Boolean,
    /** The most recent sprint that has ended. */
    val lastSprint: Effort?,
    /** While resting: how far the heart rate has fallen since the rest began. */
    val restHeartRateDropBpm: Int?,
    /** While resting: the work that came before. */
    val lastWorkBlock: BlockSummary?,
) {
    companion object {
        /** A session that has started and has nothing to show yet. */
        val EMPTY = LiveState(
            elapsedMs = 0, segment = SegmentKind.WORK, segmentElapsedMs = 0, speed = Speed.ZERO, zone = SpeedZone.WALK,
            intensity = Intensity.LIGHT, heartRateBpm = null, signalLost = true, workMs = 0, restMs = 0,
            workDistanceMeters = 0.0, workAverageSpeed = Speed.ZERO, maxSpeed = Speed.ZERO, sprintCount = 0,
            sprinting = false, lastSprint = null, restHeartRateDropBpm = null, lastWorkBlock = null,
        )
    }
}

/**
 * Reads a session that is still going on.
 *
 * It runs the same analysis as a finished session over what has been recorded
 * so far, then picks out what matters this second. Nothing is tracked
 * incrementally, so what the watch shows can never drift from what the history
 * will say — and a rest can still begin "in the past", at the moment the player
 * actually stopped.
 */
object LiveAnalyzer {

    /**
     * @param nowMs time since the session started, on the same clock as the points.
     * @param latestHeartRate the newest reading, which may be fresher than the last point's.
     */
    fun snapshot(
        points: List<TrackPoint>,
        profile: ActivityProfile,
        heartRateZones: HeartRateZones?,
        nowMs: Long,
        latestHeartRate: Int? = null,
    ): LiveState {
        val analysis = SessionAnalyzer.analyze(points, profile, heartRateZones)
        val last = points.lastOrNull()
        val signalLost = last == null || nowMs - last.timeMs > SIGNAL_LOST_AFTER_MS

        val current = analysis.segments.lastOrNull()
        val segment = current?.kind ?: SegmentKind.WORK
        val speed = if (signalLost) Speed.ZERO else last!!.speed
        val zone = profile.zones.zoneOf(speed)
        val heartRate = latestHeartRate ?: last?.heartRateBpm
        val resting = segment == SegmentKind.REST

        val sprints = analysis.sprints
        // The newest sprint is still going if it reaches the newest point and that point is fresh.
        val ongoing = sprints.lastOrNull()?.takeIf { !signalLost && last != null && it.endMs >= last.timeMs }

        return LiveState(
            elapsedMs = nowMs,
            segment = segment,
            segmentElapsedMs = (nowMs - (current?.startMs ?: 0)).coerceAtLeast(0),
            speed = speed,
            zone = zone,
            intensity = intensityOf(segment, zone, heartRate?.let { bpm -> heartRateZones?.zoneOf(bpm) }),
            heartRateBpm = heartRate,
            signalLost = signalLost,
            workMs = analysis.workMs,
            restMs = analysis.restMs,
            workDistanceMeters = analysis.workDistanceMeters,
            workAverageSpeed = analysis.workAverageSpeed,
            maxSpeed = analysis.maxSpeed,
            sprintCount = sprints.size,
            sprinting = ongoing != null,
            lastSprint = sprints.lastOrNull { it !== ongoing },
            restHeartRateDropBpm = if (resting && current != null) heartRateDrop(points, analysis, current, heartRate) else null,
            lastWorkBlock = if (resting) previousWorkBlock(points, analysis) else null,
        )
    }

    /** The one-minute drop once the rest is that old, the drop so far until then. */
    private fun heartRateDrop(points: List<TrackPoint>, analysis: SessionAnalysis, rest: Segment, nowBpm: Int?): Int? {
        analysis.heartRate?.recoveries?.lastOrNull { it.restStartMs == rest.startMs }?.let { return it.dropBpm }
        val atStart = points
            .filter { it.heartRateBpm != null && abs(it.timeMs - rest.startMs) <= REST_START_TOLERANCE_MS }
            .minByOrNull { abs(it.timeMs - rest.startMs) }
            ?.heartRateBpm
        return if (atStart != null && nowBpm != null) atStart - nowBpm else null
    }

    private fun previousWorkBlock(points: List<TrackPoint>, analysis: SessionAnalysis): BlockSummary? {
        val work = analysis.segments.dropLast(1).lastOrNull { it.kind == SegmentKind.WORK } ?: return null
        var meters = 0.0
        var movingMs = 0L
        for (i in 1 until points.size) {
            val from = points[i - 1]
            val to = points[i]
            if (from.timeMs < work.startMs || from.timeMs >= work.endMs) continue
            val dtMs = to.timeMs - from.timeMs
            if (dtMs <= 0 || dtMs > MAX_GAP_MS) continue
            meters += (from.speed.metersPerSecond + to.speed.metersPerSecond) / 2 * dtMs / 1000.0
            movingMs += dtMs
        }
        return BlockSummary(
            durationMs = work.durationMs,
            averageSpeed = if (movingMs > 0) Speed(meters / (movingMs / 1000.0)) else Speed.ZERO,
            sprints = analysis.efforts.count { it.kind == EffortKind.SPRINT && it.startMs >= work.startMs && it.startMs < work.endMs },
        )
    }

    private const val SIGNAL_LOST_AFTER_MS = 5_000L
    private const val REST_START_TOLERANCE_MS = 5_000L
    private const val MAX_GAP_MS = 5_000L
}
