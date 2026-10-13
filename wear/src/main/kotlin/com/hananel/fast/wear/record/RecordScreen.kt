package com.hananel.fast.wear.record

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.core.tracking.sprint.GnssAcquisition
import com.hananel.fast.wear.R
import com.hananel.fast.wear.gnss.AcquisitionTracker
import com.hananel.fast.wear.history.SessionSummaryScreen
import com.hananel.fast.wear.theme.FastTheme
import com.hananel.fast.wear.ui.GnssStatus
import com.hananel.fast.wear.ui.HeroNumber
import com.hananel.fast.wear.ui.edgeMorphing
import com.hananel.fast.wear.ui.withSideMargins

/** @param onDone called when the player leaves the summary, to go back where they came from. */
@Composable
fun RecordRoute(kind: ActivityKind, onDone: () -> Unit, viewModel: RecordViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Location is what a recording needs. Heart rate adds intensity, and the notification only makes
    // the "recording" notice visible; a session goes ahead without either.
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        viewModel.onPermissionResult(results[Manifest.permission.ACCESS_FINE_LOCATION] == true)
    }
    LaunchedEffect(kind) { viewModel.enter(kind) }

    when (val current = state) {
        is RecordScreenState.NeedsPermission ->
            PermissionScreen(
                kind = kind,
                denied = current.denied,
                onAllow = {
                    permissionRequest.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.BODY_SENSORS,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ),
                    )
                },
            )
        RecordScreenState.AskAge -> AgeScreen(initialAge = DEFAULT_AGE, onConfirm = viewModel::onAge, onSkip = { viewModel.onAge(null) })
        is RecordScreenState.Session -> when (val session = current.state) {
            is RecordingUiState.Locating -> ReadyScreen(session.kind, session.acquisition, session.searchingForMs, onStart = viewModel::start)
            is RecordingUiState.Recording -> LiveScreen(session.kind, session.live, onFinish = viewModel::finish)
            is RecordingUiState.Finished ->
                FinishedScreen(session, onDone = {
                    viewModel.dismiss()
                    onDone()
                })
            RecordingUiState.Idle -> Unit
        }
    }
}

@Composable
private fun PermissionScreen(kind: ActivityKind, denied: Boolean, onAllow: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding.withSideMargins()) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(kind.titleRes())) }
            }
            item {
                Text(
                    text = stringResource(if (denied) R.string.record_permission_denied else R.string.record_permission_text),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = edgeMorphing(spec).padding(bottom = 8.dp),
                )
            }
            item {
                Button(
                    onClick = onAllow,
                    label = { Text(stringResource(R.string.record_permission_button), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
        }
    }
}

/**
 * The one-time age question. Age only feeds the estimate of maximum heart rate,
 * so skipping it is allowed: the session then runs without heart-rate zones.
 */
@Composable
fun AgeScreen(initialAge: Int, onConfirm: (Int) -> Unit, onSkip: (() -> Unit)?) {
    var age by rememberSaveable { mutableIntStateOf(initialAge) }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.age_title), style = MaterialTheme.typography.titleMedium)
        // Less on the left, more on the right, in every language: it is a number line.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactButton(onClick = { if (age > MIN_AGE) age-- }, label = { Text("−") })
                HeroNumber(age.toString(), fontSize = 46)
                CompactButton(onClick = { if (age < MAX_AGE) age++ }, label = { Text("+") })
            }
        }
        Text(
            text = stringResource(R.string.age_explain),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (onSkip != null) {
                CompactButton(
                    onClick = onSkip,
                    label = { Text(stringResource(R.string.age_skip)) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                )
            }
            CompactButton(onClick = { onConfirm(age) }, label = { Text(stringResource(R.string.age_confirm)) })
        }
    }
}

@Composable
private fun ReadyScreen(kind: ActivityKind, acquisition: GnssAcquisition, searchingForMs: Long, onStart: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding.withSideMargins()) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(kind.titleRes())) }
            }
            item { GnssStatus(acquisition, searchingForMs, modifier = edgeMorphing(spec)) }
            item {
                // A training may start before the GPS is ready — on the way out of the changing
                // room, say. The track then begins with the first fix, and the button says so.
                val label: @Composable () -> Unit = {
                    Text(
                        text = stringResource(if (acquisition.canStart) R.string.sprint_start else R.string.record_start_anyway),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val modifier = Modifier.fillMaxWidth().padding(top = 6.dp).transformedHeight(this, spec)
                if (acquisition.canStart) {
                    Button(onClick = onStart, label = { label() }, modifier = modifier, transformation = SurfaceTransformation(spec))
                } else {
                    FilledTonalButton(onClick = onStart, label = { label() }, modifier = modifier, transformation = SurfaceTransformation(spec))
                }
            }
            item {
                Text(
                    text = stringResource(
                        when {
                            !acquisition.canStart -> R.string.record_hint_no_gps
                            kind == ActivityKind.FOOTBALL -> R.string.record_hint_football
                            else -> R.string.record_hint_run
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = edgeMorphing(spec).padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FinishedScreen(finished: RecordingUiState.Finished, onDone: () -> Unit) {
    val summary = finished.summary
    if (summary != null) {
        // It was saved a moment ago, so the phone cannot have confirmed it yet.
        SessionSummaryScreen(summary, synced = false, autoEnded = finished.autoEnded, onDone = onDone)
        return
    }
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding.withSideMargins()) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(R.string.summary_nothing_title), textAlign = TextAlign.Center) }
            }
            item {
                Text(
                    text = stringResource(R.string.summary_nothing_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = edgeMorphing(spec).padding(bottom = 8.dp),
                )
            }
            item {
                Button(
                    onClick = onDone,
                    label = { Text(stringResource(R.string.summary_done), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
        }
    }
}

internal fun ActivityKind.titleRes(): Int = when (this) {
    ActivityKind.RUN -> R.string.mode_run
    ActivityKind.FOOTBALL -> R.string.mode_football
    ActivityKind.SPRINT_TEST -> R.string.home_sprint_title
}

const val DEFAULT_AGE = 30
private const val MIN_AGE = 10
private const val MAX_AGE = 90

@WearPreviewDevices
@Composable
private fun ReadyPreview() {
    FastTheme { ReadyScreen(ActivityKind.FOOTBALL, AcquisitionTracker.NOTHING_YET, 12_000, onStart = {}) }
}

@WearPreviewDevices
@Composable
private fun AgePreview() {
    FastTheme { AgeScreen(initialAge = 30, onConfirm = {}, onSkip = {}) }
}
