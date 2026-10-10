package com.hananel.fast.wear.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.wear.R
import com.hananel.fast.wear.theme.FastColors
import com.hananel.fast.wear.theme.FastTheme
import com.hananel.fast.wear.ui.edgeMorphing
import com.hananel.fast.wear.ui.withSideMargins

/** Where each button on the home screen leads. */
class HomeActions(
    val onRecord: (ActivityKind) -> Unit,
    val onResume: () -> Unit,
    val onSprintTest: () -> Unit,
    val onHistory: () -> Unit,
    val onSprintHistory: () -> Unit,
    val onSettings: () -> Unit,
    val onDeviceCheck: () -> Unit,
)

/** @param recording the training being recorded right now, if any. */
@Composable
fun HomeScreen(recording: ActivityKind?, actions: HomeActions) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding.withSideMargins()) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            brush = Brush.horizontalGradient(listOf(FastColors.Cyan, FastColors.Electric, FastColors.Violet)),
                        ),
                    )
                }
            }
            if (recording != null) {
                // While a training is being recorded, the only thing to do here is go back to it:
                // the GPS has one job at a time.
                item(key = "resume") {
                    Button(
                        onClick = actions.onResume,
                        label = { Text(stringResource(R.string.home_recording)) },
                        secondaryLabel = { Text(stringResource(R.string.home_recording_back)) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }
            } else {
                item(key = "football") {
                    Button(
                        onClick = { actions.onRecord(ActivityKind.FOOTBALL) },
                        label = { Text(stringResource(R.string.mode_football)) },
                        secondaryLabel = { Text(stringResource(R.string.home_football_subtitle)) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }
                item(key = "run") {
                    Button(
                        onClick = { actions.onRecord(ActivityKind.RUN) },
                        label = { Text(stringResource(R.string.mode_run)) },
                        secondaryLabel = { Text(stringResource(R.string.home_run_subtitle)) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }
                item(key = "sprint") {
                    FilledTonalButton(
                        onClick = actions.onSprintTest,
                        label = { Text(stringResource(R.string.home_sprint_title)) },
                        secondaryLabel = { Text(stringResource(R.string.home_sprint_subtitle)) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }
            }
            item { Link(R.string.home_history, actions.onHistory, spec) }
            item { Link(R.string.home_sprint_history, actions.onSprintHistory, spec) }
            item { Link(R.string.home_settings, actions.onSettings, spec) }
            item { Link(R.string.home_device_check, actions.onDeviceCheck, spec) }
            item {
                // Says which build is on the wrist, so "do I have the latest?" has an answer.
                Text(
                    text = stringResource(R.string.home_version, version),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = edgeMorphing(spec),
                )
            }
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.Link(label: Int, onClick: () -> Unit, spec: TransformationSpec) {
    FilledTonalButton(
        onClick = onClick,
        label = { Text(stringResource(label)) },
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
    )
}

@WearPreviewDevices
@Composable
private fun HomeScreenPreview() {
    FastTheme { HomeScreen(recording = null, actions = HomeActions({}, {}, {}, {}, {}, {}, {})) }
}
