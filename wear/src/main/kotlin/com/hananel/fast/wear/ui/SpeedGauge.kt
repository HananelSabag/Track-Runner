package com.hananel.fast.wear.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import com.hananel.fast.core.model.Speed
import com.hananel.fast.wear.theme.FastColors

/**
 * A 270° dial around the edge of the screen: the fill is the current speed and
 * the rose mark is the fastest so far.
 *
 * @param full fills the whole dial whatever the speed; used while a sprint is on.
 */
@Composable
fun SpeedGauge(speed: Speed, peak: Speed, color: Color, full: Boolean = false) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val peakColor = FastColors.Rose
    // The dial grows with the player: it always has room above the session's peak.
    val scaleKmh = maxOf(GAUGE_MIN_SCALE_KMH, (peak.kmh / 5).toInt() * 5 + 5.0)
    // The receiver speaks once a second; the dial moves between its words instead of jumping from one to the next.
    val fraction by animateFloatAsState(
        targetValue = if (full) 1f else (speed.kmh / scaleKmh).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(GAUGE_GLIDE_MS, easing = FastOutSlowInEasing),
        label = "speed",
    )
    val fill by animateColorAsState(color, tween(GAUGE_COLOR_MS), label = "zone")

    Canvas(Modifier.fillMaxSize().padding(4.dp)) {
        val stroke = 6.dp.toPx()
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arcSize = Size(size.width - stroke, size.height - stroke)
        fun arc(arcColor: Color, start: Float, sweep: Float, width: Float = stroke) =
            drawArc(arcColor, start, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width, cap = StrokeCap.Round))

        arc(track, GAUGE_START, GAUGE_SWEEP)
        if (fraction > 0f) arc(fill, GAUGE_START, GAUGE_SWEEP * fraction)
        if (peak.kmh > 0) {
            val at = GAUGE_START + GAUGE_SWEEP * (peak.kmh / scaleKmh).toFloat().coerceIn(0f, 1f)
            arc(peakColor, at - 1f, 2f, width = stroke * 1.9f)
        }
    }
}

private const val GAUGE_START = 135f
private const val GAUGE_SWEEP = 270f
private const val GAUGE_MIN_SCALE_KMH = 30.0
private const val GAUGE_GLIDE_MS = 650
private const val GAUGE_COLOR_MS = 300
