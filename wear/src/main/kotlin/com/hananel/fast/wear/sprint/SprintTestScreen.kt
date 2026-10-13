package com.hananel.fast.wear.sprint

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import com.hananel.fast.wear.ui.DetailRow
import com.hananel.fast.wear.ui.GnssStatus
import com.hananel.fast.wear.ui.HeroNumber
import com.hananel.fast.wear.ui.KeepScreenOn
import com.hananel.fast.wear.ui.edgeMorphing
import com.hananel.fast.wear.ui.formatKmh
import com.hananel.fast.wear.ui.withSideMargins
import java.util.Locale

@Composable
fun SprintTestRoute(viewModel: SprintTestViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Location is what the test needs. The notification permission only decides whether the
    // "test in progress" notice is visible, so the test goes ahead with or without it.
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        viewModel.onPermissionResult(results[Manifest.permission.ACCESS_FINE_LOCATION] == true)
    }

    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) viewModel.onPermissionResult(true)
    }
    // Best effort only: the watch still turns the display off when the wrist drops, which
    // is why the test itself runs in SprintService and not in this screen.
    KeepScreenOn()

    when (val current = state) {
        is SprintUiState.NeedsPermission ->
            PermissionScreen(
                onAllow = {
                    permissionRequest.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS),
                    )
                },
            )
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
                GnssStatus(acquisition, searchingForMs, modifier = edgeMorphing(spec))
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
