package com.hananel.fast.wear.record

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.VerticalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.VerticalPagerScaffold
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.tracking.Effort
import com.hananel.fast.core.tracking.EffortKind
import com.hananel.fast.core.tracking.Intensity
import com.hananel.fast.core.tracking.SegmentKind
import com.hananel.fast.core.tracking.SpeedZone
import com.hananel.fast.core.tracking.live.BlockSummary
import com.hananel.fast.core.tracking.live.LiveState
import com.hananel.fast.wear.R
import com.hananel.fast.wear.theme.FastColors
import com.hananel.fast.wear.theme.FastTheme
import com.hananel.fast.wear.ui.Ambient
import com.hananel.fast.wear.ui.AmbientReadout
import com.hananel.fast.wear.ui.HeroNumber
import com.hananel.fast.wear.ui.SpeedGauge
import com.hananel.fast.wear.ui.formatClock
import com.hananel.fast.wear.ui.formatKmh
import com.hananel.fast.wear.ui.formatMeters
import kotlinx.coroutines.launch

/**
 * A training in progress. The first page is for a glance mid-run; swiping up
 * reaches the totals and the way to finish, out of reach of a stray touch.
 */
@Composable
fun LiveScreen(kind: ActivityKind, live: LiveState, onFinish: () -> Unit) {
    val ambient by Ambient.active.collectAsStateWithLifecycle()
    if (ambient) {
        AmbientLive(live)
        return
    }
    val pagerState = rememberPagerState(pageCount = { 2 })
    VerticalPagerScaffold(pagerState = pagerState) {
        VerticalPager(state = pagerState) { page ->
            AnimatedPage(page, pagerState) {
                when {
                    page == 1 -> ControlsPage(kind, live, onFinish)
                    kind == ActivityKind.FOOTBALL -> FootballPage(live)
                    else -> RunPage(live)
                }
            }
        }
    }
}

/** The dimmed watch: the speed, the clock of whatever is going on, and the fastest so far. */
@Composable
private fun AmbientLive(live: LiveState) {
    val resting = live.segment == SegmentKind.REST
    AmbientReadout(
        number = if (live.signalLost) "—" else live.speed.formatKmh(),
        above = if (resting) stringResource(R.string.live_rest) + " · " + formatClock(live.segmentElapsedMs) else formatClock(live.elapsedMs),
        below = stringResource(R.string.sprint_peak, live.maxSpeed.formatKmh()),
    )
}

// ---- Run ----

@Composable
private fun RunPage(live: LiveState) {
    val stopped = live.segment == SegmentKind.REST
    Box(Modifier.fillMaxSize()) {
        SpeedGauge(speed = live.speed, peak = live.maxSpeed, color = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (stopped) {
                    stringResource(R.string.live_stopped) + " · " + formatClock(live.segmentElapsedMs)
                } else {
                    formatClock(live.elapsedMs)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (stopped) FastColors.Cyan else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CurrentSpeed(live, fontSize = 62)
            Text(
                text = stringResource(R.string.sprint_peak, live.maxSpeed.formatKmh()) + " · " +
                    stringResource(R.string.live_average, live.workAverageSpeed.formatKmh()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formatMeters(live.workDistanceMeters),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Football ----

/** One page that changes by itself between work, sprint and rest, following what the player is doing. */
@Composable
private fun FootballPage(live: LiveState) {
    when {
        live.segment == SegmentKind.REST -> RestView(live)
        else -> WorkView(live)
    }
}

@Composable
private fun WorkView(live: LiveState) {
    val sprintColor = FastColors.Rose
    val zoneColor = live.zone.color()
    // For a few seconds after a sprint its numbers stay up: that is when the player looks.
    val justFinished = live.lastSprint?.takeIf { !live.sprinting && live.elapsedMs - it.endMs <= SPRINT_RECAP_MS }

    Box(Modifier.fillMaxSize()) {
        SpeedGauge(speed = live.speed, peak = live.maxSpeed, color = if (live.sprinting) sprintColor else zoneColor, full = live.sprinting)
        Column(modifier = Modifier.align(Alignment.Center).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when {
                    live.sprinting -> stringResource(R.string.live_sprint)
                    justFinished != null -> sprintRecap(justFinished)
                    else -> stringResource(R.string.live_work) + " · " + formatClock(live.segmentElapsedMs)
                },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (live.sprinting || justFinished != null) sprintColor else FastColors.Mint,
                maxLines = 1,
            )
            CurrentSpeed(live, fontSize = 58, color = if (live.sprinting) sprintColor else MaterialTheme.colorScheme.onSurface)
            if (!live.signalLost) {
                // During a sprint the heading already says so; the band is not named twice.
                if (!live.sprinting) {
                    Text(text = stringResource(live.zone.nameRes()), style = MaterialTheme.typography.labelMedium, color = zoneColor)
                }
                ZoneBar(live.zone, Modifier.padding(top = 3.dp, bottom = 5.dp))
            }
            Text(
                text = pluralStringResource(R.plurals.live_sprints, live.sprintCount, live.sprintCount) + " · " +
                    stringResource(R.string.sprint_peak, live.maxSpeed.formatKmh()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            live.heartRateBpm?.let { bpm -> HeartRateLine(bpm.toString() + " · " + stringResource(live.intensity.nameRes()), live.intensity.color()) }
        }
    }
}

@Composable
private fun RestView(live: LiveState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.live_rest),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = FastColors.Cyan,
        )
        HeroNumber(formatClock(live.segmentElapsedMs), fontSize = 54)
        live.heartRateBpm?.let { bpm ->
            val drop = live.restHeartRateDropBpm?.takeIf { it > 0 }
            HeartRateLine(
                text = bpm.toString() + (drop?.let { " · " + stringResource(R.string.live_heart_rate_drop, it) } ?: ""),
                color = if (drop != null) FastColors.Mint else MaterialTheme.colorScheme.onSurface,
            )
        }
        live.lastWorkBlock?.let { block ->
            Text(
                text = stringResource(R.string.live_last_block, formatClock(block.durationMs), block.averageSpeed.formatKmh()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (block.sprints > 0) {
                Text(
                    text = pluralStringResource(R.plurals.live_sprints, block.sprints, block.sprints),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun sprintRecap(sprint: Effort): String =
    sprint.peak.formatKmh() + " · " + stringResource(R.string.value_meters, sprint.distanceMeters.toInt()) + " · " +
        // One fix a second cannot time a sprint to the tenth, so it is not shown to the tenth.
        stringResource(R.string.value_seconds, ((sprint.durationMs + 500) / 1000).toString())

// ---- Totals and finish ----

@Composable
private fun ControlsPage(kind: ActivityKind, live: LiveState, onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val football = kind == ActivityKind.FOOTBALL
        Text(
            text = stringResource(if (football) R.string.summary_work_time else R.string.summary_run_time) + " " +
                formatClock(live.workMs) + " · " + formatMeters(live.workDistanceMeters),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (live.restMs > 0) {
            Text(
                text = stringResource(if (football) R.string.summary_rest_time else R.string.summary_stops_time) + " " +
                    formatClock(live.restMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HoldToFinishButton(onFinish, Modifier.padding(top = 8.dp, bottom = 6.dp))
        Text(
            text = stringResource(R.string.controls_hold),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Finishing takes a deliberate hold, with a ring filling up while it is held.
 * A sweaty tap or a sleeve brushing the screen must not end a training.
 */
@Composable
private fun HoldToFinishButton(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnFinish by rememberUpdatedState(onFinish)
    val label = stringResource(R.string.controls_finish)
    val ring = FastColors.Rose
    val fill = FastColors.RoseDeep.copy(alpha = 0.28f)

    Box(
        modifier = modifier
            .size(84.dp)
            .semantics {
                role = Role.Button
                onLongClick(label) { currentOnFinish(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val holding = scope.launch {
                            progress.animateTo(1f, tween(HOLD_TO_FINISH_MS, easing = LinearEasing))
                            currentOnFinish()
                        }
                        tryAwaitRelease()
                        if (progress.value < 1f) {
                            holding.cancel()
                            progress.snapTo(0f)
                        }
                    },
                )
            }
            .drawBehind {
                val stroke = 5.dp.toPx()
                drawCircle(fill, radius = size.minDimension / 2 - stroke)
                drawArc(
                    color = ring,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.value,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = ring)
    }
}

// ---- Shared pieces ----

/** The current speed, or a dash when the GPS has stopped saying. A stale number is worse than none. */
@Composable
private fun CurrentSpeed(live: LiveState, fontSize: Int, color: Color = MaterialTheme.colorScheme.onSurface) {
    if (live.signalLost) {
        HeroNumber("—", fontSize = fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.live_no_gps), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
    } else {
        HeroNumber(live.speed.formatKmh(), fontSize = fontSize, color = color)
    }
}

/**
 * A heart and what it says. The bottom of a round screen is its narrowest part,
 * so the word "heart rate" is a drawing, not text.
 */
@Composable
private fun HeartRateLine(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(11.dp)) {
            val w = size.width
            val h = size.height
            val heart = Path().apply {
                moveTo(w / 2, h * 0.95f)
                cubicTo(-w * 0.35f, h * 0.45f, w * 0.15f, -h * 0.25f, w / 2, h * 0.25f)
                cubicTo(w * 0.85f, -h * 0.25f, w * 1.35f, h * 0.45f, w / 2, h * 0.95f)
                close()
            }
            drawPath(heart, color)
        }
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

/** Five segments, slow to fast, the current one lit. Speed reads left to right in every language. */
@Composable
private fun ZoneBar(current: SpeedZone, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (zone in SpeedZone.entries) {
                val lit = zone == current
                val width by animateDpAsState(if (lit) 26.dp else 18.dp, tween(ZONE_SHIFT_MS), label = "zone width")
                val alpha by animateFloatAsState(if (lit) 1f else 0.3f, tween(ZONE_SHIFT_MS), label = "zone light")
                Box(Modifier.width(width).height(5.dp).background(zone.color().copy(alpha = alpha), RoundedCornerShape(3.dp)))
            }
        }
    }
}

internal fun SpeedZone.color(): Color = FastColors.Zones[ordinal + 1]

internal fun SpeedZone.nameRes(): Int = when (this) {
    SpeedZone.WALK -> R.string.zone_walk
    SpeedZone.JOG -> R.string.zone_jog
    SpeedZone.RUN -> R.string.zone_run
    SpeedZone.HIGH_SPEED -> R.string.zone_high_speed
    SpeedZone.SPRINT -> R.string.zone_sprint
}

private fun Intensity.nameRes(): Int = when (this) {
    Intensity.REST -> R.string.live_rest
    Intensity.LIGHT -> R.string.intensity_light
    Intensity.MODERATE -> R.string.intensity_moderate
    Intensity.THRESHOLD -> R.string.intensity_threshold
    Intensity.HIGH -> R.string.intensity_high
}

private fun Intensity.color(): Color = when (this) {
    Intensity.REST -> FastColors.Mist
    Intensity.LIGHT -> FastColors.Cyan
    Intensity.MODERATE -> FastColors.Sky
    Intensity.THRESHOLD -> FastColors.Lilac
    Intensity.HIGH -> FastColors.Rose
}

private const val ZONE_SHIFT_MS = 250
private const val SPRINT_RECAP_MS = 7_000L
private const val HOLD_TO_FINISH_MS = 1_200

// ---- Previews ----

private val previewWork = LiveState.EMPTY.copy(
    elapsedMs = 1_512_000, segmentElapsedMs = 252_000, speed = Speed.ofKmh(13.4), zone = SpeedZone.JOG,
    intensity = Intensity.THRESHOLD, heartRateBpm = 162, signalLost = false, workMs = 1_100_000, restMs = 320_000,
    workDistanceMeters = 2_840.0, workAverageSpeed = Speed.ofKmh(9.3), maxSpeed = Speed.ofKmh(27.3), sprintCount = 4,
)

@WearPreviewDevices
@Composable
private fun FootballWorkPreview() {
    FastTheme { LiveScreen(ActivityKind.FOOTBALL, previewWork, onFinish = {}) }
}

@WearPreviewDevices
@Composable
private fun FootballSprintPreview() {
    FastTheme {
        LiveScreen(
            ActivityKind.FOOTBALL,
            previewWork.copy(speed = Speed.ofKmh(26.8), zone = SpeedZone.SPRINT, intensity = Intensity.HIGH, sprinting = true),
            onFinish = {},
        )
    }
}

@WearPreviewDevices
@Composable
private fun FootballRestPreview() {
    FastTheme {
        LiveScreen(
            ActivityKind.FOOTBALL,
            previewWork.copy(
                segment = SegmentKind.REST, segmentElapsedMs = 83_000, speed = Speed.ZERO, zone = SpeedZone.WALK,
                intensity = Intensity.REST, heartRateBpm = 128, restHeartRateDropBpm = 34,
                lastWorkBlock = BlockSummary(252_000, Speed.ofKmh(11.2), sprints = 2),
                lastSprint = Effort(EffortKind.SPRINT, 1_400_000, 1_403_400, Speed.ofKmh(27.3), 24.0),
            ),
            onFinish = {},
        )
    }
}

@WearPreviewDevices
@Composable
private fun RunPreview() {
    FastTheme { LiveScreen(ActivityKind.RUN, previewWork, onFinish = {}) }
}
