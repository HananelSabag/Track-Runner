package com.hananel.fast.wear.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.core.data.SessionSummary
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.tracking.sprint.SprintTestOutcome
import com.hananel.fast.wear.R
import com.hananel.fast.wear.theme.FastColors
import com.hananel.fast.wear.theme.FastTheme
import com.hananel.fast.wear.ui.DetailRow
import com.hananel.fast.wear.ui.HeroNumber
import com.hananel.fast.wear.ui.color
import com.hananel.fast.wear.ui.confidenceColor
import com.hananel.fast.wear.ui.edgeMorphing
import com.hananel.fast.wear.ui.formatClock
import com.hananel.fast.wear.ui.formatKmh
import com.hananel.fast.wear.ui.formatMeters
import com.hananel.fast.wear.ui.icon
import com.hananel.fast.wear.ui.rememberCountUp
import com.hananel.fast.wear.ui.titleRes
import com.hananel.fast.wear.ui.withSideMargins
import java.util.Locale

/**
 * One session in numbers: straight after it ends, or later from the history.
 *
 * @param synced whether the phone has confirmed it holds this session.
 * @param onDone when given, a closing button is shown; from the history, swiping back is enough.
 */
@Composable
fun SessionSummaryScreen(summary: SessionSummary, synced: Boolean, autoEnded: Boolean = false, onDone: (() -> Unit)? = null) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val test = summary.kind == ActivityKind.SPRINT_TEST
    // A speed test is judged by its scored top speed; a test that measured nothing has none to show.
    val hero = if (test) summary.peak3s.takeIf { summary.confidence != null } else summary.maxSpeed

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding.withSideMargins()) {
            item {
                Column(modifier = edgeMorphing(spec), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Two short lines, not one long one: this is the narrowest part of a round screen.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(summary.kind.icon(), contentDescription = null, tint = summary.kind.color(), modifier = Modifier.size(13.dp))
                        Text(
                            text = stringResource(summary.kind.titleRes()),
                            style = MaterialTheme.typography.labelMedium,
                            color = summary.kind.color(),
                        )
                    }
                    Text(
                        text = formatWhen(summary.startedUtcMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (hero != null) {
                        HeroNumber(rememberCountUp(hero).formatKmh(), fontSize = 60, gradient = true)
                    } else {
                        HeroNumber("—", fontSize = 60, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = if (hero != null) {
                            stringResource(R.string.summary_peak) + " · " + stringResource(R.string.unit_kmh)
                        } else {
                            stringResource(R.string.history_not_measured)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (test) speedTestRows(summary, spec) else trainingRows(summary, autoEnded, spec)
            item {
                Text(
                    text = stringResource(if (synced) R.string.summary_synced else R.string.summary_pending),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (synced) FastColors.Mint else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = edgeMorphing(spec).padding(top = 4.dp, bottom = 4.dp),
                )
            }
            if (onDone != null) {
                item {
                    Button(
                        onClick = onDone,
                        label = { Text(stringResource(R.string.summary_done), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }
            }
        }
    }
}

private fun TransformingLazyColumnScope.speedTestRows(summary: SessionSummary, spec: TransformationSpec) {
    val confidence = summary.confidence ?: return
    item {
        val level = stringResource(
            when {
                confidence >= SprintTestOutcome.HIGH_CONFIDENCE -> R.string.level_high
                confidence >= SprintTestOutcome.MEDIUM_CONFIDENCE -> R.string.level_medium
                else -> R.string.level_low
            },
        )
        Text(
            text = stringResource(R.string.result_confidence, confidence) + " · " + level,
            style = MaterialTheme.typography.titleMedium,
            color = confidenceColor(confidence),
            textAlign = TextAlign.Center,
            modifier = edgeMorphing(spec).padding(top = 6.dp, bottom = 4.dp),
        )
    }
    item { DetailRow(stringResource(R.string.result_fastest_reading), summary.maxSpeed.formatKmh(), edgeMorphing(spec)) }
    item {
        DetailRow(
            label = stringResource(R.string.result_time_at_speed),
            value = stringResource(R.string.value_seconds, String.format(Locale.US, "%.1f", summary.workMs / 1000.0)),
            modifier = edgeMorphing(spec),
        )
    }
    item { DetailRow(stringResource(R.string.result_distance_at_speed), formatMeters(summary.distanceMeters), edgeMorphing(spec)) }
}

private fun TransformingLazyColumnScope.trainingRows(summary: SessionSummary, autoEnded: Boolean, spec: TransformationSpec) {
    val football = summary.kind == ActivityKind.FOOTBALL
    if (autoEnded || !summary.endedCleanly) {
        item(key = "note") {
            Text(
                text = stringResource(if (autoEnded) R.string.summary_auto_ended else R.string.summary_cut_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = edgeMorphing(spec).padding(top = 4.dp),
            )
        }
    }
    item { DetailRow(stringResource(R.string.summary_peak3s), summary.peak3s.formatKmh(), edgeMorphing(spec)) }
    item {
        DetailRow(
            label = stringResource(if (football) R.string.summary_work_average else R.string.summary_average),
            value = summary.workAverageSpeed.formatKmh(),
            modifier = edgeMorphing(spec),
        )
    }
    if (football) {
        item {
            DetailRow(
                label = stringResource(R.string.summary_sprints),
                value = summary.sprintCount.toString(),
                modifier = edgeMorphing(spec),
                valueColor = FastColors.Rose,
            )
        }
    }
    item {
        DetailRow(
            label = stringResource(if (football) R.string.summary_work_time else R.string.summary_run_time),
            value = formatClock(summary.workMs),
            modifier = edgeMorphing(spec),
        )
    }
    if (summary.restMs > 0) {
        item {
            DetailRow(
                label = stringResource(if (football) R.string.summary_rest_time else R.string.summary_stops_time),
                value = formatClock(summary.restMs),
                modifier = edgeMorphing(spec),
            )
        }
    }
    item { DetailRow(stringResource(R.string.summary_distance), formatMeters(summary.distanceMeters), edgeMorphing(spec)) }
    if (football && summary.highSpeedMeters >= 1) {
        item { DetailRow(stringResource(R.string.summary_high_speed), formatMeters(summary.highSpeedMeters), edgeMorphing(spec)) }
    }
    // One figure to a row: two in one row leave no room for the label on a round screen.
    summary.workAverageBpm?.let { average ->
        item { DetailRow(stringResource(R.string.summary_heart_rate_average), average.toString(), edgeMorphing(spec)) }
    }
    summary.maxBpm?.let { max ->
        item { DetailRow(stringResource(R.string.summary_heart_rate_max), max.toString(), edgeMorphing(spec)) }
    }
}

@WearPreviewDevices
@Composable
private fun SummaryPreview() {
    FastTheme {
        SessionSummaryScreen(
            summary = SessionSummary(
                id = "x", kind = ActivityKind.FOOTBALL, startedUtcMs = 1_791_600_000_000, durationMs = 3_900_000,
                workMs = 2_700_000, restMs = 1_200_000, distanceMeters = 5_230.0, maxSpeed = Speed.ofKmh(28.1),
                peak3s = Speed.ofKmh(27.2), workAverageSpeed = Speed.ofKmh(7.0), sprintCount = 9, highSpeedMeters = 410.0,
                workAverageBpm = 151, maxBpm = 183, trainingLoad = 142.0, confidence = null, endedCleanly = true, demo = false,
            ),
            synced = false,
            onDone = {},
        )
    }
}

@WearPreviewDevices
@Composable
private fun SpeedTestSummaryPreview() {
    FastTheme {
        SessionSummaryScreen(
            summary = SessionSummary(
                id = "y", kind = ActivityKind.SPRINT_TEST, startedUtcMs = 1_791_600_000_000, durationMs = 9_000,
                workMs = 6_000, restMs = 0, distanceMeters = 42.0, maxSpeed = Speed.ofKmh(27.9),
                peak3s = Speed.ofKmh(27.3), workAverageSpeed = Speed.ZERO, sprintCount = 0, highSpeedMeters = 0.0,
                workAverageBpm = null, maxBpm = null, trainingLoad = null, confidence = 91, endedCleanly = true, demo = false,
            ),
            synced = true,
        )
    }
}
