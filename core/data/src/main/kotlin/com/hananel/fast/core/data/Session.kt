package com.hananel.fast.core.data

import com.hananel.fast.core.model.GeoPoint
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.tracking.sprint.SatelliteView

/** What was recorded. The first two differ only in how they are read; a sprint test is one scored sprint. */
enum class ActivityKind { RUN, FOOTBALL, SPRINT_TEST }

/** The facts about a recording that are known the moment it starts. They never change afterwards. */
data class SessionHeader(
    /** A UUID, so a session synced twice is recognised as the same one. */
    val id: String,
    val kind: ActivityKind,
    /** Wall-clock time of the start, for showing when it happened. */
    val startedUtcMs: Long,
    /** The start on the device's monotonic clock: the origin every record's time is measured from. */
    val startedElapsedMs: Long,
    val device: String,
    val appVersion: String,
    /** The maximum heart rate the watch was using, from the player's age or from an earlier session. */
    val maxBpm: Int? = null,
    /** Made-up data for trying the app out. Never mixed into personal bests while real sessions exist. */
    val demo: Boolean = false,
)

/** One line of a recording, exactly as a sensor delivered it. */
sealed interface RawRecord {
    /** Milliseconds on the device's monotonic clock. */
    val timeMs: Long
}

/** One fix from the GNSS receiver. */
data class FixRecord(
    override val timeMs: Long,
    val utcMs: Long,
    /** False when the receiver sent a position without a speed. Kept, because it is still a position. */
    val hasSpeed: Boolean,
    val speed: Speed,
    val speedAccuracy: Speed? = null,
    val position: GeoPoint? = null,
    val horizontalAccuracyMeters: Double? = null,
    val bearingDegrees: Float? = null,
) : RawRecord

data class HeartRateRecord(override val timeMs: Long, val bpm: Int) : RawRecord

/**
 * What the receiver was using at one moment. On a watch whose receiver does not
 * rate its own fixes, this is the only evidence of how good they were.
 */
data class SatelliteRecord(override val timeMs: Long, val view: SatelliteView) : RawRecord

/** A whole recording read back from storage. */
data class RecordedSession(
    val header: SessionHeader,
    val fixes: List<FixRecord>,
    val heartRates: List<HeartRateRecord>,
    /** False when there is no end marker: the app or the watch died while recording. */
    val endedCleanly: Boolean,
    /** Empty for sessions recorded before satellites were logged, or on a receiver that reports none. */
    val satellites: List<SatelliteRecord> = emptyList(),
    /**
     * Set when the watch ended a forgotten session by itself. Everything after this
     * moment (on the monotonic clock) is the player having gone home, not training.
     */
    val trimAfterMs: Long? = null,
) {
    /** From the start to the last record that counts. */
    val durationMs: Long
        get() {
            val last = maxOf(fixes.lastOrNull()?.timeMs ?: 0L, heartRates.lastOrNull()?.timeMs ?: 0L)
            val end = trimAfterMs?.let { minOf(it, last) } ?: last
            return (end - header.startedElapsedMs).coerceAtLeast(0)
        }
}
