package com.hananel.fast.mobile.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.core.data.SessionInsights
import com.hananel.fast.core.data.SessionSummary
import com.hananel.fast.core.tracking.sprint.SprintTestOutcome
import com.hananel.fast.mobile.R
import com.hananel.fast.mobile.data.Library
import com.hananel.fast.mobile.sync.WatchStatus
import com.hananel.fast.mobile.theme.FastColors
import com.hananel.fast.mobile.ui.Card
import com.hananel.fast.mobile.ui.CardShape
import com.hananel.fast.mobile.ui.Dot
import com.hananel.fast.mobile.ui.HeroNumber
import com.hananel.fast.mobile.ui.SectionTitle
import com.hananel.fast.mobile.ui.StatTile
import com.hananel.fast.mobile.ui.color
import com.hananel.fast.mobile.ui.formatClock
import com.hananel.fast.mobile.ui.formatKmh
import com.hananel.fast.mobile.ui.formatMeters
import com.hananel.fast.mobile.ui.label
import com.hananel.fast.mobile.ui.localTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The history: personal bests on top, then every run, football session and
 * sprint test in one list, newest first, grouped by week.
 *
 * @param onLoadDemo given only in debug builds, to try the app before the first real session.
 */
@Composable
fun HomeScreen(
    library: Library?,
    watch: WatchStatus,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    onLoadDemo: (() -> Unit)?,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val summaries = library?.summaries.orEmpty()
    val weeks = remember(summaries) { groupByWeek(summaries) }

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
        item(key = "header") { Header(onSettings) }
        if (library == null) return@LazyColumn

        if (summaries.isEmpty()) {
            item(key = "empty") { EmptyState(watch, onLoadDemo) }
            return@LazyColumn
        }

        item(key = "best") { PersonalBest(library) }
        item(key = "month") { LastThirtyDays(summaries) }
        item(key = "watch") { WatchLine(watch) }
        for (week in weeks) {
            item(key = "week-${week.start}") { SectionTitle(week.title(), Modifier.padding(top = 6.dp)) }
            items(week.sessions, key = { it.id }) { summary -> SessionCard(summary, onClick = { onOpen(summary.id) }) }
        }
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

@Composable
private fun PersonalBest(library: Library) {
    val topSpeed = library.topSpeed
    // The session that set it, so the number has a date.
    val setBy = remember(library) { SessionInsights.personalBest(library.summaries) }
    Card {
        Text(
            text = stringResource(R.string.home_top_speed),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (topSpeed == null) {
            Text(
                text = stringResource(R.string.home_top_speed_none),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
            return@Card
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroNumber(topSpeed.formatKmh(), fontSize = 64)
            Text(
                text = stringResource(R.string.unit_kmh),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Text(
            text = listOfNotNull(
                // A sprint test scores the best two seconds, a training the best three.
                stringResource(if (setBy?.kind == ActivityKind.SPRINT_TEST) R.string.home_top_speed_held_test else R.string.home_top_speed_held),
                setBy?.let { it.kind.label() + " · " + DAY_MONTH.format(localTime(it.startedUtcMs)) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LastThirtyDays(summaries: List<SessionSummary>) {
    val recent = remember(summaries) {
        val since = System.currentTimeMillis() - THIRTY_DAYS_MS
        summaries.filter { it.startedUtcMs >= since && it.kind != ActivityKind.SPRINT_TEST }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
private fun WatchLine(watch: WatchStatus) {
    if (!watch.checked) return
    Row(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Dot(if (watch.connectedName != null) FastColors.Mint else FastColors.Slate)
        Text(
            text = watch.connectedName?.let { stringResource(R.string.watch_connected, it) } ?: stringResource(R.string.watch_not_connected),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionCard(summary: SessionSummary, onClick: () -> Unit) {
    val accent = summary.kind.color()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(start = 0.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(5.dp).height(84.dp).background(accent))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(summary.kind.label(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = accent)
                if (summary.demo) Badge(stringResource(R.string.badge_demo))
                if (!summary.endedCleanly) Badge(stringResource(R.string.badge_recovered))
            }
            Text(
                text = WEEKDAY_TIME.format(localTime(summary.startedUtcMs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // A sprint test the app itself doubts is shown as doubtful: its score in the
            // warning colour, and its speed dimmed so it does not pass for a result.
            val doubtful = summary.kind == ActivityKind.SPRINT_TEST &&
                (summary.confidence ?: 0) < SprintTestOutcome.MEDIUM_CONFIDENCE
            Text(
                text = summary.headline(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (doubtful) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val doubtful = summary.kind == ActivityKind.SPRINT_TEST &&
                (summary.confidence ?: 0) < SprintTestOutcome.MEDIUM_CONFIDENCE
            val measured = summary.kind != ActivityKind.SPRINT_TEST || summary.confidence != null
            // A sprint test's number is its scored top speed; a training's is the fastest it got.
            val speed = if (summary.kind == ActivityKind.SPRINT_TEST) summary.peak3s else summary.maxSpeed
            Text(
                text = if (measured) speed.formatKmh() else "—",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                color = if (doubtful) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
            Text(stringResource(R.string.unit_kmh), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The line that says what kind of session it was: the figures that matter for its kind. */
@Composable
private fun SessionSummary.headline(): String = when (kind) {
    ActivityKind.FOOTBALL -> listOf(
        stringResource(R.string.card_work, formatClock(workMs)),
        pluralStringResource(R.plurals.sprints, sprintCount, sprintCount),
        formatMeters(distanceMeters),
    ).joinToString(" · ")
    ActivityKind.RUN -> formatMeters(distanceMeters) + " · " + formatClock(workMs)
    ActivityKind.SPRINT_TEST -> confidence?.let { stringResource(R.string.confidence_value, it) } ?: stringResource(R.string.sprint_not_measured)
}

@Composable
private fun Badge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun EmptyState(watch: WatchStatus, onLoadDemo: (() -> Unit)?) {
    Card(Modifier.padding(top = 24.dp)) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.empty_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        WatchLine(watch)
        if (onLoadDemo != null) {
            FilledTonalButton(onClick = onLoadDemo, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(stringResource(R.string.demo_load), textAlign = TextAlign.Center)
            }
        }
    }
}

private class Week(val start: LocalDate, val sessions: List<SessionSummary>) {

    @Composable
    fun title(): String {
        val thisWeek = weekStart(LocalDate.now())
        return when (start) {
            thisWeek -> stringResource(R.string.week_this)
            thisWeek.minusWeeks(1) -> stringResource(R.string.week_last)
            // A date range reads low to high in every language.
            else -> "⁦" + DAY_MONTH.format(start) + " – " + DAY_MONTH.format(start.plusDays(6)) + "⁩"
        }
    }
}

private fun groupByWeek(summaries: List<SessionSummary>): List<Week> =
    summaries.groupBy { weekStart(localTime(it.startedUtcMs).toLocalDate()) }
        .map { (start, sessions) -> Week(start, sessions) }
        .sortedByDescending { it.start }

/** The first day of the week as the phone's region counts it — Sunday in Israel, Monday in most of Europe. */
private fun weekStart(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))

private val DAY_MONTH = DateTimeFormatter.ofPattern("d.M", Locale.US)

/** "Tuesday, 6.10 · 18:30", with the day's name in the phone's language. */
private val WEEKDAY_TIME: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("EEEE, d.M · HH:mm", Locale.getDefault())

private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
