package com.hananel.fast.wear.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.core.data.SessionSummary
import com.hananel.fast.core.model.Speed
import com.hananel.fast.wear.R
import com.hananel.fast.wear.data.WearData
import com.hananel.fast.wear.sprint.SprintLog
import com.hananel.fast.wear.theme.FastTheme
import com.hananel.fast.wear.ui.color
import com.hananel.fast.wear.ui.confidenceColor
import com.hananel.fast.wear.ui.edgeMorphing
import com.hananel.fast.wear.ui.formatClock
import com.hananel.fast.wear.ui.formatKmh
import com.hananel.fast.wear.ui.icon
import com.hananel.fast.wear.ui.titleRes
import com.hananel.fast.wear.ui.withSideMargins
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One line of the history: a speed test, a football training or a run.
 *
 * @param id the stored session behind it, or null for a speed test from before
 *   tests were stored as sessions. Such a line can be read but not opened.
 * @param topSpeed what the line is for: the fastest speed of a training, the
 *   scored top speed of a test. Null when a test measured nothing.
 * @param confidence speed tests only.
 * @param synced whether the phone has confirmed it holds this session.
 */
data class HistoryEntry(
    val id: String?,
    val kind: ActivityKind,
    val startedUtcMs: Long,
    val workMs: Long,
    val topSpeed: Speed?,
    val confidence: Int?,
    val synced: Boolean,
)

@Composable
fun HistoryRoute(onOpen: (String) -> Unit) {
    val context = LocalContext.current.applicationContext
    // Null while the files are being read.
    val entries by produceState<List<HistoryEntry>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val store = WearData.store(context)
            val stored = WearData.loadLibrary(context).summaries.map { it.toEntry(store.isSynced(it.id)) }
            // The first speed tests were kept only in the test log. They are still his, so they are still listed.
            val storedTests = stored.filter { it.kind == ActivityKind.SPRINT_TEST }.map { it.startedUtcMs }.toSet()
            val early = SprintLog(context).readResults()
                .filter { it.startedUtcMs !in storedTests }
                .map {
                    HistoryEntry(
                        id = null,
                        kind = ActivityKind.SPRINT_TEST,
                        startedUtcMs = it.startedUtcMs,
                        workMs = 0,
                        topSpeed = it.topSpeedKmh?.let(Speed::ofKmh),
                        confidence = it.confidence,
                        synced = true,
                    )
                }
            (stored + early).sortedByDescending { it.startedUtcMs }
        }
    }
    entries?.let { HistoryScreen(it, onOpen) }
}

private fun SessionSummary.toEntry(synced: Boolean): HistoryEntry {
    val test = kind == ActivityKind.SPRINT_TEST
    return HistoryEntry(
        id = id,
        kind = kind,
        startedUtcMs = startedUtcMs,
        workMs = workMs,
        topSpeed = if (test) peak3s.takeIf { confidence != null } else maxSpeed,
        confidence = confidence,
        synced = synced,
    )
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
            items(entries, key = { it.id ?: "test-${it.startedUtcMs}" }) { entry ->
                val shape = RoundedCornerShape(18.dp)
                val id = entry.id
                HistoryRow(
                    entry = entry,
                    modifier = edgeMorphing(spec).clip(shape).then(if (id != null) Modifier.clickable { onOpen(id) } else Modifier),
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, modifier: Modifier = Modifier) {
    val test = entry.kind == ActivityKind.SPRINT_TEST
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(entry.kind.icon(), contentDescription = null, tint = entry.kind.color(), modifier = Modifier.size(13.dp))
                Text(
                    text = stringResource(entry.kind.titleRes()),
                    style = MaterialTheme.typography.labelMedium,
                    color = entry.kind.color(),
                    maxLines = 1,
                )
            }
            Text(
                text = formatWhen(entry.startedUtcMs) + if (test) "" else " · " + formatClock(entry.workMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (test) {
                val confidence = entry.confidence
                Text(
                    text = if (confidence != null) stringResource(R.string.result_confidence, confidence) else stringResource(R.string.history_not_measured),
                    style = MaterialTheme.typography.labelSmall,
                    color = confidenceColor(confidence),
                    maxLines = 1,
                )
            }
            if (!entry.synced) {
                Text(
                    text = stringResource(R.string.summary_pending),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = entry.topSpeed?.formatKmh() ?: "—",
            style = MaterialTheme.typography.titleLarge,
            color = if (entry.topSpeed != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** One stored session, opened from the history. */
@Composable
fun SessionDetailRoute(id: String) {
    val context = LocalContext.current.applicationContext
    val entry by produceState<Pair<SessionSummary, Boolean>?>(initialValue = null, id) {
        value = withContext(Dispatchers.IO) {
            val store = WearData.store(context)
            WearData.loadLibrary(context).summaries.firstOrNull { it.id == id }?.let { it to store.isSynced(id) }
        }
    }
    entry?.let { (summary, synced) -> SessionSummaryScreen(summary, synced) }
}

/** "12.10 · 18:30" — Latin digits whatever the watch's language. */
internal fun formatWhen(utcMs: Long): String =
    WHEN_FORMAT.format(Instant.ofEpochMilli(utcMs).atZone(ZoneId.systemDefault()))

private val WHEN_FORMAT = DateTimeFormatter.ofPattern("d.M · HH:mm", Locale.US)

@WearPreviewDevices
@Composable
private fun HistoryPreview() {
    FastTheme {
        HistoryScreen(
            entries = listOf(
                HistoryEntry("a", ActivityKind.SPRINT_TEST, 1_791_600_000_000, 6_000, Speed.ofKmh(27.3), 91, synced = true),
                HistoryEntry("b", ActivityKind.FOOTBALL, 1_791_590_000_000, 2_700_000, Speed.ofKmh(26.1), null, synced = false),
                HistoryEntry("c", ActivityKind.RUN, 1_791_580_000_000, 660_000, Speed.ofKmh(23.9), null, synced = true),
                HistoryEntry(null, ActivityKind.SPRINT_TEST, 1_791_570_000_000, 0, null, null, synced = true),
            ),
            onOpen = {},
        )
    }
}
