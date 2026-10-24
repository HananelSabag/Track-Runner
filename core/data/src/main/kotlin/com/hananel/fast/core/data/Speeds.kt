package com.hananel.fast.core.data

import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.model.SpeedSample
import com.hananel.fast.core.tracking.sprint.credibleSpeedAccuracy

/** Where a recording's speeds come from. */
enum class SpeedSource {
    /** The speed the receiver reports with each fix, from the Doppler shift. */
    RECEIVER,

    /** The distance between consecutive positions, over the time between them. */
    POSITIONS,
}

/**
 * Which speed to believe, and how to get it from positions when it is not the
 * receiver's own.
 *
 * Q17, decided by Hananel on 2026-10-25. The OnePlus Watch 2's receiver smooths
 * the speed it reports: in a sprint measured by the watch and a phone together
 * it lagged two to three seconds and reached about half (best 3 s 13.8 km/h
 * against 23.3 on the phone), while the watch's own positions reached 21.0. A
 * football game of 97 minutes never passed 14.6 km/h by the reported speed. The
 * same receiver also fills in its accuracy fields with placeholders, so a
 * receiver that does not say how accurate its speed is gets its speed from its
 * positions. A receiver that does — the phone — keeps its own.
 */
object Speeds {

    /** The source for a recording, decided once from its fixes. */
    fun sourceFor(fixes: List<FixRecord>): SpeedSource {
        val withSpeed = fixes.filter { it.hasSpeed }
        if (withSpeed.isEmpty()) return SpeedSource.POSITIONS
        val credible = withSpeed.count { it.toSample().credibleSpeedAccuracy() != null }
        return if (credible * 2 >= withSpeed.size) SpeedSource.RECEIVER else SpeedSource.POSITIONS
    }

    /** The source for a single fix, for code that sees fixes one at a time as they arrive. */
    fun sourceFor(fix: FixRecord): SpeedSource =
        if (fix.hasSpeed && fix.toSample().credibleSpeedAccuracy() != null) SpeedSource.RECEIVER else SpeedSource.POSITIONS

    /**
     * How fast the player went from [previous] to [fix], by their positions. Null
     * when that cannot be told: a position missing on either side, or the two too
     * far apart in time to say anything about the moment.
     */
    fun fromPositions(previous: FixRecord?, fix: FixRecord): Speed? {
        val from = previous?.position ?: return null
        val to = fix.position ?: return null
        val dtMs = fix.timeMs - previous.timeMs
        if (dtMs <= 0 || dtMs > MAX_STEP_MS) return null
        return Speed(from.distanceTo(to) / (dtMs / 1000.0))
    }

    /**
     * The speed of every fix in a time-ordered list from [source], or null where it
     * has none. From positions, a fix that cannot be measured against the one
     * before it falls back on the receiver's own speed, if it has one.
     */
    fun of(fixes: List<FixRecord>, source: SpeedSource = sourceFor(fixes)): List<Speed?> =
        fixes.indices.map { i ->
            val fix = fixes[i]
            val receiver = fix.speed.takeIf { fix.hasSpeed }
            when (source) {
                SpeedSource.RECEIVER -> receiver
                SpeedSource.POSITIONS -> fromPositions(fixes.getOrNull(i - 1), fix) ?: receiver
            }
        }

    private fun FixRecord.toSample() = SpeedSample(timeMs, speed, speedAccuracy, position, horizontalAccuracyMeters)

    /** A longer silence is a gap in the signal, not a step to measure. */
    private const val MAX_STEP_MS = 3_000L
}
