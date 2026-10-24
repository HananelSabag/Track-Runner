package com.hananel.fast.mobile.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.core.data.SessionInsights
import com.hananel.fast.core.data.SessionSummary
import com.hananel.fast.mobile.R
import com.hananel.fast.mobile.data.Library
import com.hananel.fast.mobile.reference.PhoneMeasureCard
import com.hananel.fast.mobile.sync.WatchStatus
import com.hananel.fast.mobile.theme.FastColors
import com.hananel.fast.mobile.ui.BrandGradient
import com.hananel.fast.mobile.ui.Card
import com.hananel.fast.mobile.ui.CardShape
import com.hananel.fast.mobile.ui.Dot
import com.hananel.fast.mobile.ui.HeroNumber
import com.hananel.fast.mobile.ui.LeftToRight
import com.hananel.fast.mobile.ui.SectionTitle
import com.hananel.fast.mobile.ui.StatTile
import com.hananel.fast.mobile.ui.color
import com.hananel.fast.mobile.ui.countedUp
import com.hananel.fast.mobile.ui.formatKmh
import com.hananel.fast.mobile.ui.label
import com.hananel.fast.mobile.ui.localTime
import com.hananel.fast.mobile.ui.rememberEntrance
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where each part of the home screen leads. */
class HomeActions(
    val onOpen: (String) -> Unit,
    val onHistory: () -> Unit,
    val onSettings: () -> Unit,
    val onMeasurements: () -> Unit,
)

/**
 * The first screen: how fast you are, the phone ready to measure, and the last
 * few sessions. The full history is one tap away, not the whole screen.
 */
@Composable
fun HomeScreen(library: Library?, watch: WatchStatus, actions: HomeActions) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val summaries = remember(library) { library?.summaries.orEmpty().sortedByDescending { it.startedUtcMs } }
    // Above the list, so the number runs up once and not every time its card scrolls back into view.
    val entrance = rememberEntrance()

    LazyColumn(
        // The top inset is outside the list, so rows scroll away under its edge rather than
        // behind the status bar's clock; the bottom one is inside, so the last row clears the gesture bar.
        modifier = Modifier.fillMaxSize().padding(top = insets.calculateTopPadding()),
        contentPadding = PaddingValues(
            start = 16.dp + insets.calculateLeftPadding(direction),
            end = 16.dp + insets.calculateRightPadding(direction),
            top = 8.dp,
            bottom = 24.dp + insets.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { Header(actions.onSettings) }
        if (library == null) return@LazyColumn

        item(key = "speed") { TopSpeedHero(library, summaries, entrance, actions.onOpen) }
        item(key = "watch") { WatchLine(watch, summaries.firstOrNull()) }
        item(key = "measure") { PhoneMeasureCard(onOpenMeasurements = actions.onMeasurements) }

        if (summaries.isEmpty()) {
            item(key = "empty") { EmptyState() }
            return@LazyColumn
        }

        item(key = "recent-title") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                SectionTitle(stringResource(R.string.home_recent), Modifier.weight(1f))
                TextButton(onClick = actions.onHistory) {
                    Text(pluralStringResource(R.plurals.home_all_history, summaries.size, summaries.size))
                }
            }
        }
        items(summaries.take(RECENT_COUNT), key = { it.id }) { summary ->
            SessionCard(summary, onClick = { actions.onOpen(summary.id) }, modifier = Modifier.animateItem())
        }
        item(key = "month") { LastThirtyDays(summaries) }
    }
}

@Composable
private fun Header(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HeroNumber(stringResource(R.string.app_name), fontSize = 34)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The number the app is named for, big, with where it was set — and under it the
 * top speed of each recent session, so the number is seen against the others.
 */
@Composable
private fun TopSpeedHero(library: Library, summaries: List<SessionSummary>, entrance: Float, onOpen: (String) -> Unit) {
    val topSpeed = library.topSpeed
    // The session that set it, so the number has a date and a place to go.
    val setBy = remember(library) { SessionInsights.personalBest(library.summaries) }
    Card(
        Modifier
            .border(BorderStroke(1.5.dp, BrandGradient), CardShape)
            .then(if (setBy != null) Modifier.clip(CardShape).clickable { onOpen(setBy.id) } else Modifier),
    ) {
        Text(stringResource(R.string.home_top_speed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (topSpeed == null) {
            Text(stringResource(R.string.home_top_speed_none), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            return@Card
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroNumber(topSpeed.countedUp(entrance).formatKmh(), fontSize = 76)
            Text(
                text = stringResource(R.string.unit_kmh),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
        Text(
            text = listOfNotNull(
                // A speed test scores the best two seconds, a training the best three.
                stringResource(if (setBy?.kind == ActivityKind.SPRINT_TEST) R.string.home_top_speed_held_test else R.string.home_top_speed_held),
                setBy?.let { it.kind.label() + " · " + DAY_MONTH.format(localTime(it.startedUtcMs)) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val recent = summaries.take(CHART_COUNT).reversed()
        if (recent.size >= 2) {
            TopSpeedBars(recent, best = setBy?.id, grow = entrance, modifier = Modifier.padding(top = 14.dp))
            Text(
                text = stringResource(R.string.home_chart_caption, recent.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Oldest on the left, newest on the right, in every language: one bar per session, its height its top speed. */
@Composable
private fun TopSpeedBars(sessions: List<SessionSummary>, best: String?, grow: Float, modifier: Modifier = Modifier) {
    val top = sessions.maxOf { it.topSpeed().kmh }.coerceAtLeast(1.0)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    LeftToRight {
        Canvas(modifier.fillMaxWidth().height(56.dp)) {
            val gap = 6.dp.toPx()
            val width = (size.width - gap * (sessions.size - 1)) / sessions.size
            sessions.forEachIndexed { i, session ->
                val x = i * (width + gap)
                val fraction = (session.topSpeed().kmh / top).toFloat().coerceIn(0.04f, 1f) * grow
                val height = size.height * fraction
                drawRoundRect(track, topLeft = Offset(x, 0f), size = Size(width, size.height), cornerRadius = CornerRadius(6.dp.toPx()))
                val topLeft = Offset(x, size.height - height)
                val barSize = Size(width, height)
                if (session.id == best) {
                    drawRoundRect(Brush.verticalGradient(listOf(FastColors.Cyan, FastColors.Violet)), topLeft, barSize, CornerRadius(6.dp.toPx()))
                } else {
                    drawRoundRect(session.kind.color().copy(alpha = 0.75f), topLeft, barSize, CornerRadius(6.dp.toPx()))
                }
            }
        }
    }
}

/** Whether the watch is within reach, and when the last session came in from it. */
@Composable
private fun WatchLine(watch: WatchStatus, latest: SessionSummary?) {
    if (!watch.checked) return
    Row(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Dot(if (watch.connectedName != null) FastColors.Mint else FastColors.Slate)
        Text(
            text = listOfNotNull(
                watch.connectedName?.let { stringResource(R.string.watch_connected, it) } ?: stringResource(R.string.watch_not_connected),
                latest?.let { stringResource(R.string.home_last_session, whenText(it.startedUtcMs)) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "today 20:40", "yesterday 20:40", or "24.10 20:40". */
@Composable
private fun whenText(utcMs: Long): String {
    val time = localTime(utcMs)
    val clock = CLOCK.format(time)
    return when (time.toLocalDate()) {
        LocalDate.now() -> stringResource(R.string.time_today, clock)
        LocalDate.now().minusDays(1) -> stringResource(R.string.time_yesterday, clock)
        else -> DAY_MONTH.format(time) + " " + clock
    }
}

@Composable
private fun LastThirtyDays(summaries: List<SessionSummary>) {
    val recent = remember(summaries) {
        val since = System.currentTimeMillis() - THIRTY_DAYS_MS
        summaries.filter { it.startedUtcMs >= since && it.kind != ActivityKind.SPRINT_TEST }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        SectionTitle(stringResource(R.string.home_last_30_days))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(R.string.home_sessions), recent.size.toString())
            StatTile(stringResource(R.string.stat_sprints), recent.sumOf { it.sprintCount }.toString(), valueColor = FastColors.Rose)
            StatTile(
                label = stringResource(R.string.stat_distance),
                value = String.format(Locale.US, "%.1f", recent.sumOf { it.distanceMeters } / 1000),
                unit = stringResource(R.string.unit_km),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Card {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.empty_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private const val RECENT_COUNT = 3
private const val CHART_COUNT = 12
private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
private val DAY_MONTH = DateTimeFormatter.ofPattern("d.M", Locale.US)
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
