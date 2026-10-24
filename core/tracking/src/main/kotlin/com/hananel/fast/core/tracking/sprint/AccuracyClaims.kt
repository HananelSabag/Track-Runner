package com.hananel.fast.core.tracking.sprint

import com.hananel.fast.core.model.SpeedSample

/*
 * Some receivers fill in the accuracy fields without measuring anything.
 *
 * The OnePlus Watch 2 reports a speed accuracy of 25 m/s and a position accuracy
 * of 125 m on every single fix: the same under open sky with fourteen satellites
 * in use as indoors with five. Such a figure is a placeholder. Reading it as "the
 * fix is terrible" is as wrong as reading it as "the fix is fine" — it says
 * nothing, and the first version, which believed it, called every fix weak and
 * would have scored every sprint zero.
 *
 * So an accuracy beyond anything a working fix would admit to is treated as not
 * reported, and the judgement falls to the evidence that does exist: how many
 * satellites the receiver is actually using, and how clearly it hears them.
 */

/**
 * One sigma of 10 m/s is 36 km/h either way. No receiver that can produce a fix
 * is that unsure of a runner's speed; a figure this large was not measured.
 */
private const val MAX_CREDIBLE_SPEED_ACCURACY = 10.0

/** Likewise for position: 100 m is what a cell tower gives, not a satellite fix. */
private const val MAX_CREDIBLE_POSITION_ACCURACY_METERS = 100.0

/** The receiver's speed error in m/s, or null when it reported none or reported a placeholder. */
fun SpeedSample.credibleSpeedAccuracy(): Double? =
    speedAccuracy?.metersPerSecond?.takeIf { it < MAX_CREDIBLE_SPEED_ACCURACY }

/** The receiver's position error in metres, or null when it reported none or reported a placeholder. */
fun SpeedSample.crediblePositionAccuracy(): Double? =
    horizontalAccuracyMeters?.takeIf { it < MAX_CREDIBLE_POSITION_ACCURACY_METERS }

/**
 * How good a fix these satellites can give, 0 to 1, when the receiver will not
 * say so itself. Both numbers have to be good: many satellites heard faintly, or
 * a few heard clearly, are each a poor fix.
 *
 * The scale comes from the first real searches on the watch: indoors it used five
 * or six satellites with none or one heard clearly; outdoors eleven to fourteen
 * with three to fourteen clear. It is a first setting, to be tuned once sprints
 * recorded with their satellite counts exist.
 */
fun SatelliteView.fixQuality(): Double {
    // Four satellites are the bare minimum for a position; ten leave room to drop the worst.
    val enough = ((usedInFix - 4) / 6.0).coerceIn(0.0, 1.0)
    val clear = ((strong - 1) / 5.0).coerceIn(0.0, 1.0)
    return minOf(enough, clear)
}

/** At or above this [fixQuality], a receiver that reports no accuracy is taken to be ready. */
internal const val READY_FIX_QUALITY = 0.4
