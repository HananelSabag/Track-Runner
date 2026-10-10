package com.hananel.fast.core.tracking

/** What the player is doing right now, in the words shown on the watch. */
enum class Intensity { REST, LIGHT, MODERATE, THRESHOLD, HIGH }

/**
 * Reads one moment of a session.
 *
 * Movement and heart rate answer different questions. Movement says what the
 * body is doing this second; heart rate says how hard it has been working, and
 * trails behind by tens of seconds. So:
 *  - rest comes from movement alone — a racing heart does not undo a rest;
 *  - fast running is high intensity at once, before the heart has caught up;
 *  - otherwise heart rate grades the work, because it sees load that speed
 *    misses: tight turns, duels, running with the ball.
 */
fun intensityOf(segment: SegmentKind, speedZone: SpeedZone, heartRateZone: HeartRateZone?): Intensity {
    if (segment == SegmentKind.REST) return Intensity.REST
    if (speedZone >= SpeedZone.HIGH_SPEED) return Intensity.HIGH

    return when (heartRateZone) {
        HeartRateZone.Z5 -> Intensity.HIGH
        HeartRateZone.Z4 -> Intensity.THRESHOLD
        HeartRateZone.Z3 -> Intensity.MODERATE
        HeartRateZone.BELOW, HeartRateZone.Z1, HeartRateZone.Z2 -> Intensity.LIGHT
        // Without a heart-rate reading speed has to do. It cannot find the threshold,
        // so that level is never reported rather than guessed.
        null -> if (speedZone == SpeedZone.WALK) Intensity.LIGHT else Intensity.MODERATE
    }
}
