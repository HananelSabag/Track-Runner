package com.hananel.fast.wear.devicecheck

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.hananel.fast.core.diagnostics.ReportRow
import com.hananel.fast.core.diagnostics.ReportSection
import com.hananel.fast.core.diagnostics.RowStatus
import com.hananel.fast.wear.R
import com.hananel.fast.wear.theme.FastColors
import com.hananel.fast.wear.theme.FastTheme

@Composable
fun DeviceCheckRoute(viewModel: DeviceCheckViewModel = viewModel()) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    DeviceCheckScreen(sections)
}

@Composable
fun DeviceCheckScreen(sections: List<ReportSection>?) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        if (sections == null) {
            Loading()
            return@ScreenScaffold
        }
        TransformingLazyColumn(state = listState, contentPadding = contentPadding.withSideMargins()) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text(stringResource(R.string.device_check_title))
                }
            }
            for (section in sections) {
                item(key = section.title) {
                    ListHeader(
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    ) {
                        Text(section.title, color = MaterialTheme.colorScheme.primary)
                    }
                }
                items(section.rows, key = { "${section.title}/${it.label}" }) { row ->
                    ReportRowItem(
                        row = row,
                        // Same shrink-and-fade the Material components get near the edge of the
                        // round screen; without it a row is simply cut off by the curve.
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .graphicsLayer {
                                with(transformationSpec) { applyContainerTransformation(scrollProgress) }
                            },
                    )
                }
            }
        }
    }
}

/**
 * Adds the side margins a round screen needs: 5.2% of the width, the Wear OS
 * layout guideline, so cards are not cut off by the curve of the display.
 */
@Composable
private fun PaddingValues.withSideMargins(): PaddingValues {
    val side = (LocalConfiguration.current.screenWidthDp * SIDE_MARGIN_FRACTION).dp
    return PaddingValues(
        start = side,
        end = side,
        top = calculateTopPadding(),
        bottom = calculateBottomPadding(),
    )
}

private const val SIDE_MARGIN_FRACTION = 0.052f

@Composable
private fun ReportRowItem(row: ReportRow, modifier: Modifier = Modifier) {
    // The report is English technical data. Laying it out RTL in a Hebrew UI
    // reorders values such as "16 (API 36)", so each row stays LTR.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = row.value,
                style = MaterialTheme.typography.bodyMedium,
                color = row.status.color(),
            )
        }
    }
}

@Composable
private fun RowStatus.color(): Color = when (this) {
    RowStatus.GOOD -> FastColors.Mint
    RowStatus.WARN -> MaterialTheme.colorScheme.error
    RowStatus.INFO -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun Loading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Box(Modifier.padding(top = 8.dp)) {
            Text(
                text = stringResource(R.string.device_check_loading),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@WearPreviewDevices
@Composable
private fun DeviceCheckScreenPreview() {
    FastTheme {
        DeviceCheckScreen(
            listOf(
                ReportSection(
                    "GNSS",
                    listOf(
                        ReportRow("On-board GNSS", "yes", RowStatus.GOOD),
                        ReportRow("Dual-frequency", "yes", RowStatus.GOOD),
                        ReportRow("Signals", "GPS L1+L5, Galileo L1+L5, GLONASS G1"),
                    ),
                ),
                ReportSection("Paired devices", listOf(ReportRow("Phone", "not connected", RowStatus.WARN))),
            ),
        )
    }
}
