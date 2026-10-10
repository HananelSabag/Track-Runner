package com.hananel.fast.wear.sprint

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.hananel.fast.core.tracking.sprint.SprintTestOutcome
import com.hananel.fast.wear.R
import com.hananel.fast.wear.theme.FastColors
import com.hananel.fast.wear.theme.FastTheme
import com.hananel.fast.wear.ui.edgeMorphing
import com.hananel.fast.wear.ui.withSideMargins
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SprintHistoryRoute() {
    val context = LocalContext.current.applicationContext
    // Null while the file is being read.
    val sprints by produceState<List<SavedSprint>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { SprintLog(context).readResults() }
    }
    sprints?.let { SprintHistoryScreen(it) }
}

@Composable
fun SprintHistoryScreen(sprints: List<SavedSprint>) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding.withSideMargins()) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(R.string.home_sprint_history)) }
            }
            if (sprints.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = edgeMorphing(spec),
                    )
                }
            }
            items(sprints, key = { it.startedUtcMs }) { sprint ->
                SavedSprintRow(sprint, edgeMorphing(spec))
            }
        }
    }
}

@Composable
private fun SavedSprintRow(sprint: SavedSprint, modifier: Modifier = Modifier) {
    val speed = sprint.topSpeedKmh
    val confidence = sprint.confidence
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = formatWhen(sprint.startedUtcMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (confidence != null) stringResource(R.string.result_confidence, confidence) else stringResource(R.string.history_not_measured),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    confidence == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    confidence >= SprintTestOutcome.HIGH_CONFIDENCE -> FastColors.Mint
                    confidence >= SprintTestOutcome.MEDIUM_CONFIDENCE -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                },
            )
        }
        Text(
            text = speed?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun formatWhen(utcMs: Long): String =
    WHEN_FORMAT.format(Instant.ofEpochMilli(utcMs).atZone(ZoneId.systemDefault()))

private val WHEN_FORMAT = DateTimeFormatter.ofPattern("d.M · HH:mm", Locale.US)

@WearPreviewDevices
@Composable
private fun SprintHistoryPreview() {
    FastTheme {
        SprintHistoryScreen(
            listOf(
                SavedSprint(1_791_600_000_000, 27.3, 91),
                SavedSprint(1_791_590_000_000, 25.8, 66),
                SavedSprint(1_791_580_000_000, null, null),
            ),
        )
    }
}
