package com.hananel.fast.mobile.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.core.data.SessionSummary
import com.hananel.fast.core.tracking.sprint.SprintTestOutcome
import com.hananel.fast.mobile.R
import com.hananel.fast.mobile.ui.CardShape
import com.hananel.fast.mobile.ui.color
import com.hananel.fast.mobile.ui.formatClock
import com.hananel.fast.mobile.ui.formatKmh
import com.hananel.fast.mobile.ui.formatMeters
import com.hananel.fast.mobile.ui.icon
import com.hananel.fast.mobile.ui.label
import com.hananel.fast.mobile.ui.localTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One session in a list: its kind and when, the figures that matter for its kind, and its top speed. */
@Composable
fun SessionCard(summary: SessionSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = summary.kind.color()
    // A speed test the app itself doubts is shown as doubtful: its score in the warning
    // colour, and its speed dimmed so it does not pass for a result.
    val doubtful = summary.kind == ActivityKind.SPRINT_TEST && (summary.confidence ?: 0) < SprintTestOutcome.MEDIUM_CONFIDENCE
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(5.dp).height(84.dp).background(accent))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(summary.kind.icon(), contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Text(summary.kind.label(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = accent)
                if (summary.demo) Badge(stringResource(R.string.badge_demo))
                if (!summary.endedCleanly) Badge(stringResource(R.string.badge_recovered))
            }
            Text(
                text = WEEKDAY_TIME.format(localTime(summary.startedUtcMs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = summary.headline(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (doubtful) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val measured = summary.kind != ActivityKind.SPRINT_TEST || summary.confidence != null
            Text(
                text = if (measured) summary.topSpeed().formatKmh() else "—",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                color = if (doubtful) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
            Text(stringResource(R.string.unit_kmh), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A speed test's number is its scored top speed; a training's is the fastest it got. */
fun SessionSummary.topSpeed() = if (kind == ActivityKind.SPRINT_TEST) peak3s else maxSpeed

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

/** "Tuesday, 6.10 · 18:30", with the day's name in the phone's language. */
private val WEEKDAY_TIME: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("EEEE, d.M · HH:mm", Locale.getDefault())
