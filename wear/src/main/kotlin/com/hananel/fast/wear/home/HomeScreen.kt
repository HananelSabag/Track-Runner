package com.hananel.fast.wear.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
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
import com.hananel.fast.wear.ui.FastIcons
import com.hananel.fast.wear.ui.withSideMargins

/** Where each button on the home screen leads. */
class HomeActions(
    val onRecord: (ActivityKind) -> Unit,
    val onResume: () -> Unit,
    val onSpeedTest: () -> Unit,
    val onHistory: () -> Unit,
    val onSettings: () -> Unit,
)

/**
 * Three things to start, then the two places to look things up. Nothing else
 * lives here: every extra row is one more to scroll past with a sweaty finger.
 *
 * @param recording the training being recorded right now, if any.
 */
@Composable
fun HomeScreen(recording: ActivityKind?, actions: HomeActions) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

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
                            brush = Brush.horizontalGradient(BRAND_GRADIENT),
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
                item(key = "speed-test") {
                    // The one the app is named for, so it alone wears the brand's colours.
                    Start(
                        icon = FastIcons.Bolt,
                        tint = FastColors.Cyan,
                        title = R.string.home_sprint_title,
                        subtitle = R.string.home_sprint_subtitle,
                        onClick = actions.onSpeedTest,
                        spec = spec,
                        border = BorderStroke(1.5.dp, Brush.horizontalGradient(BRAND_GRADIENT)),
                    )
                }
                item(key = "football") {
                    Start(
                        icon = FastIcons.Ball,
                        tint = FastColors.Lilac,
                        title = R.string.mode_football,
                        subtitle = R.string.home_football_subtitle,
                        onClick = { actions.onRecord(ActivityKind.FOOTBALL) },
                        spec = spec,
                    )
                }
                item(key = "run") {
                    Start(
                        icon = FastIcons.Runner,
                        tint = FastColors.Sky,
                        title = R.string.mode_run,
                        subtitle = R.string.home_run_subtitle,
                        onClick = { actions.onRecord(ActivityKind.RUN) },
                        spec = spec,
                    )
                }
            }
            item { Link(FastIcons.Clock, R.string.home_history, actions.onHistory, spec) }
            item { Link(FastIcons.Sliders, R.string.home_settings, actions.onSettings, spec) }
        }
    }
}

/** Something to start: an icon in the activity's colour, its name, and what it measures. */
@Composable
private fun TransformingLazyColumnItemScope.Start(
    icon: ImageVector,
    tint: Color,
    title: Int,
    subtitle: Int,
    onClick: () -> Unit,
    spec: TransformationSpec,
    border: BorderStroke? = null,
) {
    Button(
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(ButtonDefaults.IconSize)) },
        label = { Text(stringResource(title)) },
        secondaryLabel = { Text(stringResource(subtitle)) },
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            secondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = border,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
    )
}

/** Somewhere to look things up: quieter than the three above it. */
@Composable
private fun TransformingLazyColumnItemScope.Link(icon: ImageVector, label: Int, onClick: () -> Unit, spec: TransformationSpec) {
    Button(
        onClick = onClick,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
        },
        label = { Text(stringResource(label)) },
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
    )
}

private val BRAND_GRADIENT = listOf(FastColors.Cyan, FastColors.Electric, FastColors.Violet)

@WearPreviewDevices
@Composable
private fun HomeScreenPreview() {
    FastTheme { HomeScreen(recording = null, actions = HomeActions({}, {}, {}, {}, {})) }
}
