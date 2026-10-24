package com.hananel.fast.mobile.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hananel.fast.core.data.SessionSummary
import com.hananel.fast.mobile.R
import com.hananel.fast.mobile.data.Library
import com.hananel.fast.mobile.home.SessionCard
import com.hananel.fast.mobile.ui.SectionTitle
import com.hananel.fast.mobile.ui.TopBar
import com.hananel.fast.mobile.ui.localTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** Every session the phone holds — runs, football and speed tests — newest first, grouped by week. */
@Composable
fun HistoryScreen(library: Library?, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val summaries = library?.summaries.orEmpty()
    val weeks = remember(summaries) { groupByWeek(summaries) }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        TopBar(
            title = stringResource(R.string.history_title),
            subtitle = pluralStringResource(R.plurals.history_count, summaries.size, summaries.size),
            onBack = onBack,
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (week in weeks) {
                item(key = "week-${week.start}") { SectionTitle(week.title(), Modifier.padding(top = 6.dp)) }
                items(week.sessions, key = { it.id }) { summary ->
                    SessionCard(summary, onClick = { onOpen(summary.id) }, modifier = Modifier.animateItem())
                }
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
        .map { (start, sessions) -> Week(start, sessions.sortedByDescending { it.startedUtcMs }) }
        .sortedByDescending { it.start }

/** The first day of the week as the phone's region counts it — Sunday in Israel, Monday in most of Europe. */
private fun weekStart(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))

private val DAY_MONTH = DateTimeFormatter.ofPattern("d.M", Locale.US)
