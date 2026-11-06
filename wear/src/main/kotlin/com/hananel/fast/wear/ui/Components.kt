package com.hananel.fast.wear.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.tracking.sprint.AcquisitionStage
import com.hananel.fast.core.tracking.sprint.GnssAcquisition
import com.hananel.fast.wear.R
import com.hananel.fast.wear.theme.FastColors
import java.util.Locale

/** The one number a screen exists to show: heavy, slanted, and wide enough to read at a glance. */
@Composable
fun HeroNumber(text: String, fontSize: Int, gradient: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurface) {
    val style = MaterialTheme.typography.displayLarge.copy(
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.05f).sp,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
    )
    Text(
        text = text,
        style = if (gradient) {
            style.copy(brush = Brush.horizontalGradient(listOf(FastColors.Cyan, FastColors.Electric, FastColors.Violet)))
        } else {
            style
        },
        color = if (gradient) Color.Unspecified else color,
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * A result that runs up to its value when it appears. The number an effort was
 * made for deserves an entrance; a row in a list does not, so this is for the
 * hero of a result screen only.
 */
@Composable
fun rememberCountUp(target: Speed): Speed {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(target) { shown.animateTo(target.kmh.toFloat(), tween(COUNT_UP_MS, easing = FastOutSlowInEasing)) }
    return Speed.ofKmh(shown.value.toDouble())
}

@Composable
fun DetailRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The label gives way, never the value: a number broken across two lines is unreadable.
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, maxLines = 1, softWrap = false)
    }
}

/**
 * How the GPS search is going: a signal meter, the stage in words, what is
 * known so far, and a running clock while there is nothing usable yet.
 */
@Composable
fun GnssStatus(acquisition: GnssAcquisition, searchingForMs: Long, modifier: Modifier = Modifier) {
    val satellites = acquisition.satellites
    val status = when (acquisition.stage) {
        AcquisitionStage.NO_SATELLITES -> stringResource(R.string.sprint_gps_searching)
        AcquisitionStage.SATELLITES_FOUND -> stringResource(R.string.sprint_gps_satellites_found)
        AcquisitionStage.LOCKING -> stringResource(R.string.sprint_gps_locking)
        AcquisitionStage.FIX_WITHOUT_SPEED -> stringResource(R.string.sprint_gps_no_speed)
        AcquisitionStage.WEAK_FIX -> stringResource(R.string.sprint_gps_weak)
        AcquisitionStage.READY -> stringResource(R.string.sprint_gps_ready)
    }
    val color = gnssStatusColor(acquisition.stage)
    // What is known so far, most useful first: the fix's accuracy once there is one, the satellites until then.
    val detail = listOfNotNull(
        acquisition.positionAccuracyMeters?.let { stringResource(R.string.sprint_gps_accuracy, it.toInt()) },
        satellites?.takeIf { it.visible > 0 }?.let { stringResource(R.string.sprint_gps_satellites, it.visible, it.strong) },
    ).joinToString(" · ")

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SignalBars(bars = acquisition.bars, color = color, searching = !acquisition.canStart)
        Text(
            text = status,
            color = color,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        if (detail.isNotEmpty()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!acquisition.canStart) {
            // A running clock: proof the search is alive even when nothing else changes.
            Text(
                text = formatClock(searchingForMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun gnssStatusColor(stage: AcquisitionStage): Color = when (stage) {
    AcquisitionStage.READY -> StatusColors.Ready
    AcquisitionStage.WEAK_FIX, AcquisitionStage.FIX_WITHOUT_SPEED -> MaterialTheme.colorScheme.error
    // Still working on it, nothing for the player to do: grey, not the blue that means "ready".
    else -> StatusColors.Waiting
}

/**
 * Four bars, like a phone's signal meter. While the receiver is still working
 * towards a usable fix, the next bar pulses, so the screen is visibly alive
 * rather than a label that never changes.
 */
@Composable
fun SignalBars(bars: Int, color: Color, searching: Boolean, scale: Float = 1f) {
    val pulse = rememberInfiniteTransition(label = "signal")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 700), RepeatMode.Reverse),
        label = "next bar",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    // A signal meter reads left to right in every language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(horizontalArrangement = Arrangement.spacedBy((5 * scale).dp), verticalAlignment = Alignment.Bottom) {
            for (index in 0 until SIGNAL_BAR_COUNT) {
                val barColor = when {
                    index < bars -> color
                    searching && index == bars -> color.copy(alpha = pulseAlpha)
                    else -> track
                }
                Box(
                    Modifier
                        .size(width = (9 * scale).dp, height = ((10 + index * 7) * scale).dp)
                        .background(barColor, RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

/** "4:07", or "1:04:07" past the hour — Latin digits whatever the watch's language. */
fun formatClock(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    val hours = seconds / 3600
    val minutes = seconds / 60 % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds % 60)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds % 60)
    }
}

/** "2.31 km" from a kilometre up, "840 m" below it. */
@Composable
fun formatMeters(meters: Double): String =
    if (meters >= 1000) {
        stringResource(R.string.value_km, String.format(Locale.US, "%.2f", meters / 1000))
    } else {
        stringResource(R.string.value_meters, meters.toInt())
    }

private const val SIGNAL_BAR_COUNT = 4
private const val COUNT_UP_MS = 900
