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
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.hananel.fast.wear.R
import com.hananel.fast.wear.theme.FastColors
import com.hananel.fast.wear.theme.FastTheme
import com.hananel.fast.wear.ui.edgeMorphing
import com.hananel.fast.wear.ui.withSideMargins

@Composable
fun HomeScreen(onSprintTest: () -> Unit, onSprintHistory: () -> Unit, onDeviceCheck: () -> Unit) {
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
            item {
                Button(
                    onClick = onSprintTest,
                    label = { Text(stringResource(R.string.home_sprint_title)) },
                    secondaryLabel = { Text(stringResource(R.string.home_sprint_subtitle)) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
            item {
                FilledTonalButton(
                    onClick = onSprintHistory,
                    label = { Text(stringResource(R.string.home_sprint_history)) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
            item {
                FilledTonalButton(
                    onClick = onDeviceCheck,
                    label = { Text(stringResource(R.string.home_device_check)) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
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

@WearPreviewDevices
@Composable
private fun HomeScreenPreview() {
    FastTheme { HomeScreen(onSprintTest = {}, onSprintHistory = {}, onDeviceCheck = {}) }
}
