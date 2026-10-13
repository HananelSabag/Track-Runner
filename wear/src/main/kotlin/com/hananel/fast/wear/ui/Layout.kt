package com.hananel.fast.wear.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.hananel.fast.core.model.Speed
import com.hananel.fast.wear.data.WearData
import java.util.Locale

/**
 * Adds the side margins a round screen needs: 5.2% of the width, the Wear OS
 * layout guideline, so content is not cut off by the curve of the display.
 */
@Composable
fun PaddingValues.withSideMargins(): PaddingValues {
    val side = (LocalConfiguration.current.screenWidthDp * SIDE_MARGIN_FRACTION).dp
    return PaddingValues(
        start = side,
        end = side,
        top = calculateTopPadding(),
        bottom = calculateBottomPadding(),
    )
}

/**
 * The modifier every hand-built row in a [androidx.wear.compose.foundation.lazy.TransformingLazyColumn]
 * needs: full width, plus the shrink-and-fade the Material components get near
 * the edge of the round screen. Without it a row is simply cut off by the curve.
 */
fun TransformingLazyColumnItemScope.edgeMorphing(spec: TransformationSpec): Modifier =
    Modifier
        .fillMaxWidth()
        .transformedHeight(this, spec)
        .graphicsLayer { with(spec) { applyContainerTransformation(scrollProgress) } }

/**
 * Keeps the display lit for as long as the caller is on screen and [enabled] is true.
 *
 * It only stops the watch from darkening by itself. Covering the screen with a
 * palm still turns it off, so nothing that measures may depend on it.
 */
@Composable
fun KeepScreenOn(enabled: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/** Whether the player has left "screen on while measuring" switched on. Read once per screen. */
@Composable
fun rememberKeepScreenOnSetting(): Boolean {
    val context = LocalContext.current.applicationContext
    return remember { WearData.settings(context).keepScreenOn }
}

/**
 * How one stage of a flow gives way to the next: the new one fades up from
 * slightly small while the old one is already gone. Short, because the player
 * is waiting for what comes next.
 */
fun stageTransition(): ContentTransform =
    (fadeIn(tween(STAGE_IN_MS, delayMillis = STAGE_OUT_MS)) + scaleIn(tween(STAGE_IN_MS, delayMillis = STAGE_OUT_MS), initialScale = 0.94f))
        .togetherWith(fadeOut(tween(STAGE_OUT_MS)))

/** "27.3" — always with a decimal point and Latin digits, whatever the watch's language. */
fun Speed.formatKmh(): String = String.format(Locale.US, "%.1f", kmh)

private const val SIDE_MARGIN_FRACTION = 0.052f
private const val STAGE_OUT_MS = 90
private const val STAGE_IN_MS = 220
