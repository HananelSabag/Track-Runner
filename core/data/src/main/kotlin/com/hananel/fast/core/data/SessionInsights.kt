package com.hananel.fast.core.data

import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.model.SpeedSample
import com.hananel.fast.core.model.TrackPoint
import com.hananel.fast.core.tracking.ActivityProfile
import com.hananel.fast.core.tracking.HeartRateZones
import com.hananel.fast.core.tracking.SessionAnalysis
import com.hananel.fast.core.tracking.SessionAnalyzer
import com.hananel.fast.core.tracking.sprint.SprintTestAnalyzer
import com.hananel.fast.core.tracking.sprint.SprintTestOutcome
import kotlin.math.roundToInt

/**
 * Bumped whenever the way a session is read changes, so every stored summary
 * made by an older reading is thrown away and worked out again from the raw file.
 */
const val ALGORITHM_VERSION = 1

/** What the analysis needs to know about the player, beyond the session itself. */
data class AnalysisContext(
    /** The player's own top speed, which sets the football speed bands. Null until one is known. */
    val topSpeed: Speed? = null,
    /** Used when the session itself does not say which maximum heart rate applied. */
    val fallbackMaxBpm: Int? = null,
) {
    /** Two contexts with the same key read a session identically. */
    val key: String get() = "${topSpeed?.let { (it.kmh * 10).roundToInt() } ?: "-"}/${fallbackMaxBpm ?: "-"}"
}

/** A session together with everything derived from it. */
class SessionInsight(
    val session: RecordedSession,
    val points: List<TrackPoint>,
    /** Null for a sprint test, which is scored rather than analysed. */
    val analysis: SessionAnalysis?,
    /** Only for a sprint test. */
    val sprintOutcome: SprintTestOutcome?,
    val maxBpmUsed: Int?,
    /** The speed bands and rest rules the session was read with, for drawing it in the same colours. */
    val profile: ActivityProfile,
)

/** The one-line view of a session, for lists and personal bests. Always recomputable. */
data class SessionSummary(
    val id: String,
    val kind: ActivityKind,
    val startedUtcMs: Long,
    val durationMs: Long,
    val workMs: Long,
    val restMs: Long,
    /** Distance covered while working. */
    val distanceMeters: Double,
    val maxSpeed: Speed,
    /** The fastest speed held for three seconds; for a sprint test, the scored top speed. */
    val peak3s: Speed,
    val workAverageSpeed: Speed,
    val sprintCount: Int,
    val highSpeedMeters: Double,
    val workAverageBpm: Int?,
    val maxBpm: Int?,
    val trainingLoad: Double?,
    /** Sprint tests only: 0–100, or null when the test produced no measurement. */
    val confidence: Int?,
    val endedCleanly: Boolean,
    val demo: Boolean,
)

object SessionInsights {

    /**
     * Below this, a "top speed" is somebody who has only walked or jogged so far,
     * and bands scaled to it would call a brisk jog a sprint. The standard bands
     * apply until a real top speed is on record.
     */
    val MIN_PERSONAL_TOP_SPEED: Speed = Speed.ofKmh(18.0)

    fun profileFor(kind: ActivityKind, topSpeed: Speed?): ActivityProfile = when (kind) {
        ActivityKind.FOOTBALL -> ActivityProfile.football(topSpeed?.takeIf { it >= MIN_PERSONAL_TOP_SPEED })
        ActivityKind.RUN, ActivityKind.SPRINT_TEST -> ActivityProfile.RUN
    }

    fun of(session: RecordedSession, context: AnalysisContext = AnalysisContext()): SessionInsight {
        val points = TrackAssembler.assemble(session)
        val maxBpm = session.header.maxBpm ?: context.fallbackMaxBpm
        val profile = profileFor(session.header.kind, context.topSpeed)

        if (session.header.kind == ActivityKind.SPRINT_TEST) {
            // The scorer reads the receiver's own readings, not the cleaned track: judging
            // how far to trust them is its whole job.
            val samples = session.fixes.filter { it.hasSpeed }.map {
                SpeedSample(it.timeMs, it.speed, it.speedAccuracy, it.position, it.horizontalAccuracyMeters)
            }
            return SessionInsight(session, points, null, SprintTestAnalyzer.analyze(samples), maxBpm, profile)
        }

        val analysis = SessionAnalyzer.analyze(points, profile, heartRateZones = maxBpm?.let(::HeartRateZones))
        return SessionInsight(session, points, analysis, null, maxBpm, profile)
    }

    fun summarize(insight: SessionInsight): SessionSummary {
        val header = insight.session.header
        val analysis = insight.analysis
        val measured = insight.sprintOutcome as? SprintTestOutcome.Measured
        return SessionSummary(
            id = header.id,
            kind = header.kind,
            startedUtcMs = header.startedUtcMs,
            durationMs = insight.session.durationMs,
            workMs = analysis?.workMs ?: measured?.fastPhaseMs ?: 0,
            restMs = analysis?.restMs ?: 0,
            distanceMeters = analysis?.workDistanceMeters ?: measured?.fastPhaseMeters ?: 0.0,
            maxSpeed = analysis?.maxSpeed ?: measured?.instantMax ?: Speed.ZERO,
            peak3s = analysis?.peak3s ?: measured?.topSpeed ?: Speed.ZERO,
            workAverageSpeed = analysis?.workAverageSpeed ?: Speed.ZERO,
            sprintCount = analysis?.sprints?.size ?: 0,
            highSpeedMeters = analysis?.highSpeedDistanceMeters ?: 0.0,
            workAverageBpm = analysis?.heartRate?.workAverageBpm,
            maxBpm = analysis?.heartRate?.maxBpm,
            trainingLoad = analysis?.heartRate?.trainingLoad,
            confidence = measured?.confidence,
            endedCleanly = insight.session.endedCleanly,
            demo = header.demo,
        )
    }

    /**
     * The player's top speed as far as the stored sessions show: the best speed
     * held for three seconds in training, or the best sprint test that scored at
     * least medium confidence. A test the app itself does not trust is not allowed
     * to move every speed band.
     *
     * Demo sessions count only while there is nothing real to go on.
     */
    fun personalTopSpeed(summaries: List<SessionSummary>): Speed? = personalBest(summaries)?.peak3s

    /** The session that set [personalTopSpeed], or null when there is no top speed yet. */
    fun personalBest(summaries: List<SessionSummary>): SessionSummary? {
        val real = summaries.filterNot { it.demo }
        return real.ifEmpty { summaries }
            .filter { it.kind != ActivityKind.SPRINT_TEST || (it.confidence ?: 0) >= SprintTestOutcome.MEDIUM_CONFIDENCE }
            .maxByOrNull { it.peak3s }
            ?.takeIf { it.peak3s >= MIN_PERSONAL_TOP_SPEED }
    }
}
