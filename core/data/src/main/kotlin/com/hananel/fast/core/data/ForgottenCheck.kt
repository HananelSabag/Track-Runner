package com.hananel.fast.core.data

import com.hananel.fast.core.model.Speed

/**
 * Whether a recording has really been left running — the player gone home — or
 * only looks like it to the rest detection.
 *
 * Ending a session is the one decision the watch makes that cannot be undone:
 * once it stops, nothing more is recorded. On 2026-10-24 it ended a football
 * session after "twenty minutes of rest" in which the player had moved 700 m
 * with his heart at 140: on a small pitch, at walking pace, he never went far
 * enough from one spot for the rest detection to see him leave. So a long rest
 * alone is not enough. The stretch also has to look like nobody is there:
 * hardly any movement, and a heart rate that has come down.
 *
 * Heart rate is used here and nowhere in deciding work and rest. There it lags;
 * here the question is twenty minutes long, and a heart still at 140 after
 * twenty minutes is a player, not a forgotten watch.
 */
object ForgottenCheck {

    /** True when everything recorded between [fromMs] and [toMs] (monotonic clock) is consistent with nobody training. */
    fun looksForgotten(fixes: List<FixRecord>, heartRates: List<HeartRateRecord>, fromMs: Long, toMs: Long): Boolean =
        !movesTooMuch(fixes, fromMs, toMs) && !heartSaysActive(heartRates, toMs)

    /** True when the heart rate of the last few minutes says the player is still at it. */
    fun heartSaysActive(heartRates: List<HeartRateRecord>, nowMs: Long): Boolean {
        val recent = heartRates.filter { it.timeMs in (nowMs - HEART_WINDOW_MS)..nowMs }.map { it.bpm }.sorted()
        return recent.size >= MIN_HEART_READINGS && recent[recent.size / 2] >= ACTIVE_BPM
    }

    private fun movesTooMuch(fixes: List<FixRecord>, fromMs: Long, toMs: Long): Boolean {
        val inside = fixes.filter { it.hasSpeed && it.timeMs in fromMs..toMs }
        if (inside.isEmpty()) return false
        return inside.count { it.speed >= MOVING_SPEED } > inside.size * MAX_MOVING_SHARE
    }

    /** Walking pace. A standing watch reads well under it: 0 to 1 km/h, with the odd spike. */
    private val MOVING_SPEED = Speed.ofKmh(3.5)

    /** More than one second in ten on the move is somebody moving. */
    private const val MAX_MOVING_SHARE = 0.10

    private const val HEART_WINDOW_MS = 5 * 60_000L
    private const val MIN_HEART_READINGS = 30

    /** Well above anybody's heart at rest, well below anybody's while playing. */
    private const val ACTIVE_BPM = 110
}
