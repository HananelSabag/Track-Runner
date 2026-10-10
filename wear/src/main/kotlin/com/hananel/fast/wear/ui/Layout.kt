package com.hananel.fast.wear.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.hananel.fast.core.model.Speed
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

/** Keeps the display awake for as long as the caller is on screen. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** "27.3" — always with a decimal point and Latin digits, whatever the watch's language. */
fun Speed.formatKmh(): String = String.format(Locale.US, "%.1f", kmh)

private const val SIDE_MARGIN_FRACTION = 0.052f
