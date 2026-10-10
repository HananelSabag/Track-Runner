package com.hananel.fast.wear.sprint

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.tracking.sprint.ConfidenceLevel
import com.hananel.fast.core.tracking.sprint.AcquisitionStage
import com.hananel.fast.core.tracking.sprint.GnssAcquisition
import com.hananel.fast.core.tracking.sprint.SprintIssue
import com.hananel.fast.core.tracking.sprint.SprintRejection
import com.hananel.fast.core.tracking.sprint.SprintTestOutcome
import com.hananel.fast.wear.R
import com.hananel.fast.wear.theme.FastColors
import com.hananel.fast.wear.theme.FastTheme
import com.hananel.fast.wear.ui.KeepScreenOn
import com.hananel.fast.wear.ui.edgeMorphing
import com.hananel.fast.wear.ui.formatKmh
import com.hananel.fast.wear.ui.withSideMargins
import java.util.Locale

@Composable
fun SprintTestRoute(viewModel: SprintTestViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) viewModel.onPermissionResult(true)
    }
    // The receiver is on only while this screen is actually showing.
    LifecycleStartEffect(viewModel) {
        viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }
    // A screen that dims mid-sprint would hide the result of the one glance the runner takes.
    KeepScreenOn()

    when (val current = state) {
        is SprintUiState.NeedsPermission ->
            PermissionScreen(onAllow = { permissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION) })
        is SprintUiState.Locating -> ReadyScreen(current.acquisition, current.searchingForMs, onStart = viewModel::start)
        is SprintUiState.Countdown -> CountdownScreen(current.secondsLeft)
        is SprintUiState.Running -> RunningScreen(current, onStop = viewModel::stop)
        is SprintUiState.Result -> ResultScreen(current.outcome, onRetry = viewModel::retry)
    }
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding.withSideMargins()) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(R.string.home_sprint_title)) }
            }
            item {
                Text(
                    text = stringResource(R.string.sprint_permission_text),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = edgeMorphing(spec).padding(bottom = 8.dp),
                )
            }
            item {
                Button(
                    onClick = onAllow,
                    label = { Text(stringResource(R.string.sprint_permission_button)) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
        }
    }
}

@Composable
private fun ReadyScreen(acquisition: GnssAcquisition, searchingForMs: Long, onStart: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val ready = acquisition.stage == AcquisitionStage.READY
    val satellites = acquisition.satellites

    val status = when (acquisition.stage) {
        AcquisitionStage.NO_SATELLITES -> stringResource(R.string.sprint_gps_searching)
        AcquisitionStage.SATELLITES_FOUND -> stringResource(R.string.sprint_gps_satellites_found)
        AcquisitionStage.LOCKING -> stringResource(R.string.sprint_gps_locking)
        AcquisitionStage.FIX_WITHOUT_SPEED -> stringResource(R.string.sprint_gps_no_speed)
        AcquisitionStage.WEAK_FIX -> stringResource(R.string.sprint_gps_weak)
        AcquisitionStage.READY -> stringResource(R.string.sprint_gps_ready)
    }
    val statusColor = when (acquisition.stage) {
        AcquisitionStage.READY -> FastColors.Mint
        AcquisitionStage.WEAK_FIX, AcquisitionStage.FIX_WITHOUT_SPEED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    // What is known so far, most useful first: the fix's accuracy once there is one, the satellites until then.
    val detail = listOfNotNull(
        acquisition.positionAccuracyMeters?.let { stringResource(R.string.sprint_gps_accuracy, it.toInt()) },
        satellites?.takeIf { it.visible > 0 }?.let { stringResource(R.string.sprint_gps_satellites, it.visible, it.strong) },
    ).joinToString(" · ")
    val hint = when {
        acquisition.stage == AcquisitionStage.WEAK_FIX -> stringResource(R.string.sprint_hint_weak)
        acquisition.stage == AcquisitionStage.FIX_WITHOUT_SPEED -> stringResource(R.string.sprint_hint_no_speed)
        !ready && searchingForMs >= SLOW_SEARCH_MS -> stringResource(R.string.sprint_hint_slow)
        else -> null
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding.withSideMargins()) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(R.string.home_sprint_title)) }
            }
            item {
                Column(modifier = edgeMorphing(spec), horizontalAlignment = Alignment.CenterHorizontally) {
                    SignalBars(bars = acquisition.bars, color = statusColor, searching = !acquisition.canStart)
                    Text(
                        text = status,
                        color = statusColor,
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
                            text = formatElapsed(searchingForMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (hint != null) {
                item(key = "hint") {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = edgeMorphing(spec).padding(top = 4.dp),
                    )
                }
            }
            item {
                Button(
                    onClick = onStart,
                    enabled = acquisition.canStart,
                    label = {
                        Text(
                            text = stringResource(R.string.sprint_start),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
            item {
                Column(
                    modifier = edgeMorphing(spec).padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (line in listOf(
                        R.string.sprint_instruction_ground,
                        R.string.sprint_instruction_one,
                        R.string.sprint_instruction_hold,
                    )) {
                        Text(
                            text = stringResource(line),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Four bars, like a phone's signal meter. While the receiver is still working
 * towards a usable fix, the next bar pulses, so the screen is visibly alive
 * rather than a label that never changes.
 */
@Composable
private fun SignalBars(bars: Int, color: Color, searching: Boolean) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
            for (index in 0 until BAR_COUNT) {
                val barColor = when {
                    index < bars -> color
                    searching && index == bars -> color.copy(alpha = pulseAlpha)
                    else -> track
                }
                Box(
                    Modifier
                        .size(width = 9.dp, height = (10 + index * 7).dp)
                        .background(barColor, RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val seconds = ms / 1000
    return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}

private const val BAR_COUNT = 4

/** After this long without a fix, say why it is slow instead of leaving the runner to wonder. */
private const val SLOW_SEARCH_MS = 20_000L

@Composable
private fun CountdownScreen(secondsLeft: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = secondsLeft.toString(),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 110.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun RunningScreen(state: SprintUiState.Running, onStop: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.started) {
                HeroNumber(state.speed.formatKmh(), fontSize = 76)
                Text(stringResource(R.string.unit_kmh), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = stringResource(R.string.sprint_peak, state.peak.formatKmh()),
                    style = MaterialTheme.typography.titleMedium,
                    color = FastColors.Rose,
                )
            } else {
                Text(
                    text = stringResource(R.string.sprint_run),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        CompactButton(
            onClick = onStop,
            label = { Text(stringResource(R.string.sprint_stop)) },
            colors = ButtonDefaults.filledTonalButtonColors(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
        )
    }
}

@Composable
private fun ResultScreen(outcome: SprintTestOutcome, onRetry: () -> Unit) {
    when (outcome) {
        is SprintTestOutcome.Measured -> MeasuredScreen(outcome, onRetry)
        is SprintTestOutcome.Rejected -> RejectedScreen(outcome.reason, onRetry)
    }
}

@Composable
private fun MeasuredScreen(result: SprintTestOutcome.Measured, onRetry: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val levelColor = when (result.level) {
        ConfidenceLevel.HIGH -> FastColors.Mint
        ConfidenceLevel.MEDIUM -> MaterialTheme.colorScheme.primary
        ConfidenceLevel.LOW -> MaterialTheme.colorScheme.error
    }
    val levelName = stringResource(
        when (result.level) {
            ConfidenceLevel.HIGH -> R.string.level_high
            ConfidenceLevel.MEDIUM -> R.string.level_medium
            ConfidenceLevel.LOW -> R.string.level_low
        },
    )
    val isLow = result.level == ConfidenceLevel.LOW

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding.withSideMargins()) {
            item {
                Column(modifier = edgeMorphing(spec), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.result_top_speed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HeroNumber(result.topSpeed.formatKmh(), fontSize = 64, gradient = !isLow)
                    Text(
                        text = result.uncertainty
                            ?.let { stringResource(R.string.value_plus_minus_kmh, it.formatKmh()) }
                            ?: stringResource(R.string.unit_kmh),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Column(
                    modifier = edgeMorphing(spec).padding(top = 6.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.result_confidence, result.confidence) + " · " + levelName,
                        style = MaterialTheme.typography.titleMedium,
                        color = levelColor,
                        textAlign = TextAlign.Center,
                    )
                    if (isLow) {
                        Text(
                            text = stringResource(R.string.result_low_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            for (issue in result.issues) {
                item(key = issue.name) {
                    Text(
                        text = stringResource(issue.textRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = edgeMorphing(spec),
                    )
                }
            }
            item {
                DetailRow(stringResource(R.string.result_fastest_reading), result.instantMax.formatKmh(), edgeMorphing(spec))
            }
            item {
                DetailRow(
                    label = stringResource(R.string.result_time_at_speed),
                    value = stringResource(R.string.value_seconds, String.format(Locale.US, "%.1f", result.fastPhaseMs / 1000.0)),
                    modifier = edgeMorphing(spec),
                )
            }
            item {
                DetailRow(
                    label = stringResource(R.string.result_distance_at_speed),
                    value = stringResource(R.string.value_meters, result.fastPhaseMeters.toInt()),
                    modifier = edgeMorphing(spec),
                )
            }
            item { RetryButton(onRetry, prominent = isLow, modifier = Modifier.fillMaxWidth().transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) }
        }
    }
}

@Composable
private fun RejectedScreen(reason: SprintRejection, onRetry: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val (title, text) = when (reason) {
        SprintRejection.NO_SPRINT -> R.string.rejected_no_sprint_title to R.string.rejected_no_sprint_text
        SprintRejection.NOT_ENOUGH_DATA -> R.string.rejected_no_data_title to R.string.rejected_no_data_text
        SprintRejection.TOO_FAST_FOR_A_RUNNER -> R.string.rejected_too_fast_title to R.string.rejected_too_fast_text
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding.withSideMargins()) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(title), textAlign = TextAlign.Center) }
            }
            item {
                Text(
                    text = stringResource(text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = edgeMorphing(spec).padding(bottom = 8.dp),
                )
            }
            item { RetryButton(onRetry, prominent = true, modifier = Modifier.fillMaxWidth().transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) }
        }
    }
}

@Composable
private fun RetryButton(onRetry: () -> Unit, prominent: Boolean, modifier: Modifier, transformation: SurfaceTransformation) {
    val label: @Composable () -> Unit = {
        Text(stringResource(R.string.result_retry), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    // When the measurement is doubtful, trying again is the main thing to do; otherwise it is an option.
    if (prominent) {
        Button(onClick = onRetry, label = { label() }, modifier = modifier, transformation = transformation)
    } else {
        FilledTonalButton(onClick = onRetry, label = { label() }, modifier = modifier, transformation = transformation)
    }
}

/** The one number the screen exists to show: heavy, slanted, and wide enough to read at a glance. */
@Composable
private fun HeroNumber(text: String, fontSize: Int, gradient: Boolean = false) {
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
        color = if (gradient) Color.Unspecified else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
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
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
    }
}

private fun SprintIssue.textRes(): Int = when (this) {
    SprintIssue.FEW_SAMPLES -> R.string.issue_few_samples
    SprintIssue.WEAK_SIGNAL -> R.string.issue_weak_signal
    SprintIssue.NOT_HELD -> R.string.issue_not_held
    SprintIssue.SPIKE -> R.string.issue_spike
    SprintIssue.DISTANCE_MISMATCH -> R.string.issue_distance_mismatch
}

@WearPreviewDevices
@Composable
private fun MeasuredHighPreview() {
    FastTheme {
        MeasuredScreen(
            SprintTestOutcome.Measured(
                topSpeed = Speed.ofKmh(27.3),
                instantMax = Speed.ofKmh(27.9),
                uncertainty = Speed.ofKmh(0.5),
                fastPhaseMs = 6_000,
                fastPhaseMeters = 42.0,
                confidence = 91,
                issues = emptyList(),
            ),
            onRetry = {},
        )
    }
}

@WearPreviewDevices
@Composable
private fun MeasuredLowPreview() {
    FastTheme {
        MeasuredScreen(
            SprintTestOutcome.Measured(
                topSpeed = Speed.ofKmh(23.7),
                instantMax = Speed.ofKmh(31.0),
                uncertainty = null,
                fastPhaseMs = 3_000,
                fastPhaseMeters = 18.0,
                confidence = 12,
                issues = listOf(SprintIssue.SPIKE),
            ),
            onRetry = {},
        )
    }
}
