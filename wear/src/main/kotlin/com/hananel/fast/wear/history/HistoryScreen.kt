package com.hananel.fast.wear.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
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
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.core.data.SessionSummary
import com.hananel.fast.wear.R
import com.hananel.fast.wear.data.WearData
import com.hananel.fast.wear.theme.FastColors
import com.hananel.fast.wear.ui.edgeMorphing
import com.hananel.fast.wear.ui.formatClock
import com.hananel.fast.wear.ui.formatKmh
import com.hananel.fast.wear.ui.withSideMargins
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A stored training and whether the phone has it. */
data class HistoryEntry(val summary: SessionSummary, val synced: Boolean)

@Composable
fun HistoryRoute(onOpen: (String) -> Unit) {
    val context = LocalContext.current.applicationContext
    // Null while the files are being read.
    val entries by produceState<List<HistoryEntry>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val store = WearData.store(context)
            WearData.loadLibrary(context).summaries
                // Sprint tests have their own list, with their confidence scores.
                .filter { it.kind != ActivityKind.SPRINT_TEST }
                .map { HistoryEntry(it, store.isSynced(it.id)) }
        }
    }
    entries?.let { HistoryScreen(it, onOpen) }
}

@Composable
fun HistoryScreen(entries: List<HistoryEntry>, onOpen: (String) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding.withSideMargins()) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(R.string.home_history)) }
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.history_sessions_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = edgeMorphing(spec),
                    )
                }
            }
            items(entries, key = { it.summary.id }) { entry ->
                HistoryRow(entry, edgeMorphing(spec).clip(RoundedCornerShape(18.dp)).clickable { onOpen(entry.summary.id) })
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, modifier: Modifier = Modifier) {
    val summary = entry.summary
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(if (summary.kind == ActivityKind.FOOTBALL) R.string.mode_football else R.string.mode_run),
                style = MaterialTheme.typography.labelMedium,
                color = if (summary.kind == ActivityKind.FOOTBALL) FastColors.Lilac else MaterialTheme.colorScheme.primary,
            )
            Text(
                text = formatWhen(summary.startedUtcMs) + " · " + formatClock(summary.workMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (!entry.synced) {
                Text(
                    text = stringResource(R.string.summary_pending),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(text = summary.maxSpeed.formatKmh(), style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
    }
}

/** One stored training, opened from the history. */
@Composable
fun SessionDetailRoute(id: String) {
    val context = LocalContext.current.applicationContext
    val entry by produceState<HistoryEntry?>(initialValue = null, id) {
        value = withContext(Dispatchers.IO) {
            val store = WearData.store(context)
            WearData.loadLibrary(context).summaries.firstOrNull { it.id == id }?.let { HistoryEntry(it, store.isSynced(id)) }
        }
    }
    entry?.let { SessionSummaryScreen(it.summary, it.synced) }
}

/** "12.10 · 18:30" — Latin digits whatever the watch's language. */
internal fun formatWhen(utcMs: Long): String =
    WHEN_FORMAT.format(Instant.ofEpochMilli(utcMs).atZone(ZoneId.systemDefault()))

private val WHEN_FORMAT = DateTimeFormatter.ofPattern("d.M · HH:mm", Locale.US)
