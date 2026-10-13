package com.hananel.fast.mobile.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.hananel.fast.core.model.Speed

/**
 * Runs from 0 to 1 once, when the caller first appears. Bars grow with it and
 * charts draw themselves with it, so a screen arrives instead of popping in.
 *
 * Inside a lazy list it would run again every time the row scrolls back into
 * view; hoist it above the list there.
 */
@Composable
fun rememberEntrance(durationMs: Int = ENTRANCE_MS, delayMs: Int = 0): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMs, delayMs, FastOutSlowInEasing)) }
    return progress.value
}

/** A result that runs up to its value: [entrance] of the way there. Only for the one number a screen is about. */
fun Speed.countedUp(entrance: Float): Speed = Speed(metersPerSecond * entrance)

/**
 * How one screen gives way to another. Going deeper, the new screen arrives from
 * the far edge; going back, it leaves the same way. A short slide and a fade:
 * enough to say which way one is going, not enough to wait for.
 *
 * @param forward whether the move goes deeper into the app.
 * @param rtl whether the layout reads right to left, which mirrors the slide.
 */
fun screenTransition(forward: Boolean, rtl: Boolean): ContentTransform {
    // In a left-to-right layout "deeper" lies to the right.
    val towardsDeeper = if (rtl) -1 else 1
    val enterFrom = if (forward) towardsDeeper else -towardsDeeper
    return (slideInHorizontally(tween(SCREEN_IN_MS)) { it / SLIDE_FRACTION * enterFrom } + fadeIn(tween(SCREEN_IN_MS, delayMillis = SCREEN_OUT_MS / 2)))
        .togetherWith(slideOutHorizontally(tween(SCREEN_OUT_MS)) { -it / SLIDE_FRACTION * enterFrom } + fadeOut(tween(SCREEN_OUT_MS)))
}

private const val ENTRANCE_MS = 750
private const val SCREEN_IN_MS = 260
private const val SCREEN_OUT_MS = 140
private const val SLIDE_FRACTION = 10
