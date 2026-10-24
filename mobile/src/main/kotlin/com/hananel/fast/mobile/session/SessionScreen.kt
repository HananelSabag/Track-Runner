package com.hananel.fast.mobile.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hananel.fast.core.data.ALGORITHM_VERSION
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.core.data.DeviceComparison
import com.hananel.fast.core.data.SessionInsight
import com.hananel.fast.core.model.TrackPoint
import com.hananel.fast.core.tracking.Effort
import com.hananel.fast.core.tracking.EffortKind
import com.hananel.fast.core.tracking.HeartRateSummary
import com.hananel.fast.core.tracking.HeartRateZone
import com.hananel.fast.core.tracking.SessionAnalysis
import com.hananel.fast.core.tracking.SpeedZone
import com.hananel.fast.core.tracking.sprint.ConfidenceLevel
import com.hananel.fast.core.tracking.sprint.SprintIssue
import com.hananel.fast.core.tracking.sprint.SprintRejection
import com.hananel.fast.core.tracking.sprint.SprintTestOutcome
import com.hananel.fast.mobile.R
import com.hananel.fast.mobile.data.PhoneData
import com.hananel.fast.mobile.reference.ComparisonCard
import com.hananel.fast.mobile.reference.ReferenceRecorder
import com.hananel.fast.mobile.theme.FastColors
import com.hananel.fast.mobile.ui.Card
import com.hananel.fast.mobile.ui.Dot
import com.hananel.fast.mobile.ui.HeroNumber
import com.hananel.fast.mobile.ui.LeftToRight
import com.hananel.fast.mobile.ui.SpeedChart
import com.hananel.fast.mobile.ui.StatTile
import com.hananel.fast.mobile.ui.TopBar
import com.hananel.fast.mobile.ui.color
import com.hananel.fast.mobile.ui.countedUp
import com.hananel.fast.mobile.ui.formatClock
import com.hananel.fast.mobile.ui.formatDateTime
import com.hananel.fast.mobile.ui.formatKmh
import com.hananel.fast.mobile.ui.formatMeters
import com.hananel.fast.mobile.ui.label
import com.hananel.fast.mobile.ui.rememberEntrance
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SessionRoute(id: String, onBack: () -> Unit, onReplay: () -> Unit) {
    val context = LocalContext.current.applicationContext
    // Null while the file is read and analysed; "missing" when it is gone.
    val loaded by produceState<Loaded>(initialValue = Loaded.Reading, id) {
        value = PhoneData.insight(context, id)?.let(Loaded::Ready) ?: Loaded.Missing
    }
    when (val current = loaded) {
        Loaded.Reading -> Unit
        Loaded.Missing -> LaunchedEffect(Unit) { onBack() }
        is Loaded.Ready -> SessionScreen(
            insight = current.insight,
            onBack = onBack,
            onReplay = onReplay,
            onDelete = {
                PhoneData.delete(context, id)
                onBack()
            },
        )
    }
}

private sealed interface Loaded {
    data object Reading : Loaded
    data object Missing : Loaded
    data class Ready(val insight: SessionInsight) : Loaded
}

@Composable
fun SessionScreen(insight: SessionInsight, onBack: () -> Unit, onReplay: () -> Unit, onDelete: () -> Unit) {
    val header = insight.session.header
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current.applicationContext
    val phoneComparison by produceState<DeviceComparison?>(initialValue = null, insight) {
        value = withContext(Dispatchers.IO) { ReferenceRecorder.comparisonFor(context, insight.session) }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        TopBar(
            title = header.kind.label(),
            subtitle = formatDateTime(header.startedUtcMs),
            onBack = onBack,
            actions = {
                // The film of the session is one tap away, not at the bottom of a long page.
                if (insight.points.count { it.position != null } >= MIN_POINTS_FOR_REPLAY) {
                    FilledTonalButton(onClick = onReplay, contentPadding = PaddingValues(start = 12.dp, end = 16.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.replay_open))
                    }
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!insight.session.endedCleanly) Note(stringResource(R.string.note_recovered))
            if (insight.session.trimAfterMs != null) Note(stringResource(R.string.note_auto_ended))
            if (header.demo) Note(stringResource(R.string.note_demo))

            val analysis = insight.analysis
            val outcome = insight.sprintOutcome
            when {
                outcome != null -> SprintTestBody(outcome, insight.points)
                analysis != null && header.kind == ActivityKind.FOOTBALL -> FootballBody(analysis, insight.points)
                analysis != null -> RunBody(analysis, insight.points)
            }

            // When the phone measured alongside ("Measure from the phone"), the two side by side.
            phoneComparison?.let { ComparisonCard(it) }

            // Where the numbers came from, so they can be trusted or questioned.
            Text(
                text = stringResource(R.string.session_source, header.device, header.appVersion, ALGORITHM_VERSION),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

// ---- Football ----

@Composable
private fun FootballBody(analysis: SessionAnalysis, points: List<TrackPoint>) {
    TopSpeedCard(analysis)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(stringResource(R.string.stat_work), formatClock(analysis.workMs), valueColor = FastColors.Mint)
        StatTile(stringResource(R.string.stat_rest), formatClock(analysis.restMs))
        StatTile(stringResource(R.string.stat_sprints), analysis.sprints.size.toString(), valueColor = FastColors.Rose)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(stringResource(R.string.stat_work_average), analysis.workAverageSpeed.formatKmh(), stringResource(R.string.unit_kmh))
        DistanceTile(stringResource(R.string.stat_distance), analysis.workDistanceMeters)
        DistanceTile(stringResource(R.string.stat_fast_running), analysis.highSpeedDistanceMeters)
    }
    Card {
        CardTitle(stringResource(R.string.section_timeline))
        SpeedChart(points, segments = analysis.segments, efforts = analysis.efforts, peakAtMs = analysis.maxSpeedAtMs)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(FastColors.Rose, stringResource(R.string.zone_sprint))
            Legend(FastColors.Zones[4], stringResource(R.string.zone_high_speed))
            Legend(MaterialTheme.colorScheme.surfaceContainerHigh, stringResource(R.string.stat_rest))
        }
    }
    Card {
        CardTitle(stringResource(R.string.section_zones))
        ZoneBars(analysis)
    }
    if (analysis.sprints.isNotEmpty()) {
        Card {
            CardTitle(stringResource(R.string.stat_sprints))
            SprintList(analysis.sprints)
        }
    }
    analysis.heartRate?.let { HeartRateCard(it) }
}

// ---- Run ----

@Composable
private fun RunBody(analysis: SessionAnalysis, points: List<TrackPoint>) {
    TopSpeedCard(analysis)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DistanceTile(stringResource(R.string.stat_distance), analysis.workDistanceMeters)
        StatTile(stringResource(R.string.stat_running), formatClock(analysis.workMs))
        StatTile(stringResource(R.string.stat_average), analysis.workAverageSpeed.formatKmh(), stringResource(R.string.unit_kmh))
    }
    // Shown only when there is something to say: most runs have no stop and no stretch near 20 km/h.
    val stopped = analysis.restMs > 0
    val ranFast = analysis.highSpeedDistanceMeters >= 1
    if (stopped || ranFast) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (stopped) StatTile(stringResource(R.string.stat_stopped), formatClock(analysis.restMs))
            if (ranFast) DistanceTile(stringResource(R.string.stat_fast_running), analysis.highSpeedDistanceMeters)
        }
    }
    Card {
        CardTitle(stringResource(R.string.section_speed))
        SpeedChart(points, segments = analysis.segments, efforts = analysis.efforts, peakAtMs = analysis.maxSpeedAtMs)
    }
    Card {
        CardTitle(stringResource(R.string.section_minutes))
        MinuteTable(points)
    }
    analysis.heartRate?.let { HeartRateCard(it) }
}

@Composable
private fun TopSpeedCard(analysis: SessionAnalysis) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.stat_top_speed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroNumber(analysis.maxSpeed.countedUp(rememberEntrance()).formatKmh(), fontSize = 60)
                    Text(
                        text = stringResource(R.string.unit_kmh),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.stat_best_3s), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(analysis.peak3s.formatKmh(), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

/** Time and distance in each speed band, fastest first. Bars compare time. */
@Composable
private fun ZoneBars(analysis: SessionAnalysis) {
    val longest = analysis.zoneTimeMs.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (zone in SpeedZone.entries.reversed()) {
            val timeMs = analysis.zoneTimeMs[zone] ?: 0
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(zone.label(), style = MaterialTheme.typography.bodyMedium, color = zone.color(), modifier = Modifier.weight(1f))
                    Text(
                        text = formatClock(timeMs) + " · " + formatMeters(analysis.zoneDistanceMeters[zone] ?: 0.0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Bar(fraction = timeMs.toFloat() / longest, color = zone.color())
            }
        }
    }
}

@Composable
private fun SprintList(sprints: List<Effort>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            for ((text, weight) in listOf(
                stringResource(R.string.column_at) to 1f,
                stringResource(R.string.column_peak) to 1f,
                stringResource(R.string.stat_distance) to 1f,
                stringResource(R.string.column_duration) to 1f,
            )) {
                Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(weight))
            }
        }
        for (sprint in sprints) {
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatClock(sprint.startMs), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    text = sprint.peak.formatKmh(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (sprint.kind == EffortKind.SPRINT) FastColors.Rose else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.value_meters, sprint.distanceMeters.toInt()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    // One fix a second cannot time a sprint to the tenth, so it is not shown to the tenth.
                    text = stringResource(R.string.value_seconds, ((sprint.durationMs + 500) / 1000).toString()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeartRateCard(heartRate: HeartRateSummary) {
    Card {
        CardTitle(stringResource(R.string.section_heart_rate))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(R.string.stat_hr_work), heartRate.workAverageBpm.toString())
            StatTile(stringResource(R.string.stat_hr_max), heartRate.maxBpm.toString(), valueColor = FastColors.Rose)
            StatTile(stringResource(R.string.stat_load), heartRate.trainingLoad.toInt().toString())
        }
        val longest = heartRate.zoneTimeMs.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (zone in listOf(HeartRateZone.Z5, HeartRateZone.Z4, HeartRateZone.Z3, HeartRateZone.Z2, HeartRateZone.Z1)) {
                val timeMs = heartRate.zoneTimeMs[zone] ?: 0
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(zone.label(), style = MaterialTheme.typography.bodySmall, color = zone.color(), modifier = Modifier.width(96.dp))
                    Box(Modifier.weight(1f)) { Bar(fraction = timeMs.toFloat() / longest, color = zone.color()) }
                    Text(formatClock(timeMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (heartRate.recoveries.isNotEmpty()) {
            Text(
                text = stringResource(R.string.section_recovery),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            for (recovery in heartRate.recoveries) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.recovery_at, formatClock(recovery.restStartMs)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    // "172 → 131" is a sequence in time, so it reads left to right.
                    LeftToRight {
                        Text("${recovery.startBpm} → ${recovery.afterOneMinuteBpm}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        text = stringResource(R.string.recovery_drop, recovery.dropBpm),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = FastColors.Mint,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

/** Average and fastest speed of each minute: the plain answer to "how fast was I, minute by minute". */
@Composable
private fun MinuteTable(points: List<TrackPoint>) {
    val minutes = remember(points) {
        points.groupBy { it.timeMs / 60_000 }
            .toSortedMap()
            .map { (minute, inMinute) -> Triple(minute.toInt(), inMinute.map { it.speed.kmh }.average(), inMinute.maxOf { it.speed.kmh }) }
    }
    val top = minutes.maxOfOrNull { it.third }?.coerceAtLeast(1.0) ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.column_minute), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.column_average_max), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for ((minute, average, max) in minutes) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text((minute + 1).toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(34.dp))
                Box(Modifier.weight(1f)) { Bar(fraction = (average / top).toFloat(), color = FastColors.Sky) }
                LeftToRight {
                    Text(
                        text = String.format(Locale.US, "%.1f / %.1f", average, max),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(84.dp),
                    )
                }
            }
        }
    }
}

// ---- Sprint test ----

@Composable
private fun SprintTestBody(outcome: SprintTestOutcome, points: List<TrackPoint>) {
    when (outcome) {
        is SprintTestOutcome.Measured -> {
            val levelColor = when (outcome.level) {
                ConfidenceLevel.HIGH -> FastColors.Mint
                ConfidenceLevel.MEDIUM -> FastColors.Sky
                ConfidenceLevel.LOW -> MaterialTheme.colorScheme.error
            }
            Card {
                Text(stringResource(R.string.stat_top_speed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // A number the app itself doubts does not get the brand treatment.
                    HeroNumber(outcome.topSpeed.countedUp(rememberEntrance()).formatKmh(), fontSize = 64, gradient = outcome.level != ConfidenceLevel.LOW)
                    Text(
                        text = outcome.uncertainty?.let { stringResource(R.string.value_plus_minus_kmh, it.formatKmh()) }
                            ?: stringResource(R.string.unit_kmh),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.confidence_value, outcome.confidence) + " · " + stringResource(outcome.level.nameRes()),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = levelColor,
                    modifier = Modifier.padding(top = 4.dp),
                )
                for (issue in outcome.issues) {
                    Text(
                        text = "• " + stringResource(issue.textRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.stat_fastest_reading), outcome.instantMax.formatKmh())
                StatTile(stringResource(R.string.stat_time_at_speed), ((outcome.fastPhaseMs + 500) / 1000).toString(), stringResource(R.string.unit_seconds))
                DistanceTile(stringResource(R.string.stat_distance_at_speed), outcome.fastPhaseMeters)
            }
        }
        is SprintTestOutcome.Rejected -> Card {
            Text(stringResource(outcome.reason.titleRes()), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.sprint_not_measured_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    if (points.size >= 2) {
        Card {
            CardTitle(stringResource(R.string.section_speed))
            SpeedChart(points)
        }
    }
}

// ---- Small pieces ----

/** A distance in kilometres from one up, in metres below, with the unit small beside it. */
@Composable
private fun RowScope.DistanceTile(label: String, meters: Double) {
    if (meters >= 1000) {
        StatTile(label, String.format(Locale.US, "%.2f", meters / 1000), stringResource(R.string.unit_km))
    } else {
        StatTile(label, meters.toInt().toString(), stringResource(R.string.value_meters_text))
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Dot(color)
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A horizontal bar that fills from the left in every language, like the charts above it. */
@Composable
private fun Bar(fraction: Float, color: Color) {
    val grown = fraction * rememberEntrance(delayMs = 150)
    LeftToRight {
        Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp))) {
            if (fraction > 0f) {
                Box(Modifier.fillMaxWidth(grown.coerceIn(0.01f, 1f)).height(8.dp).background(color, RoundedCornerShape(4.dp)))
            }
        }
    }
}

@Composable
private fun HeartRateZone.label(): String = stringResource(
    when (this) {
        HeartRateZone.BELOW -> R.string.hr_zone_below
        HeartRateZone.Z1 -> R.string.hr_zone_1
        HeartRateZone.Z2 -> R.string.hr_zone_2
        HeartRateZone.Z3 -> R.string.hr_zone_3
        HeartRateZone.Z4 -> R.string.hr_zone_4
        HeartRateZone.Z5 -> R.string.hr_zone_5
    },
)

private fun HeartRateZone.color(): Color = when (this) {
    HeartRateZone.BELOW -> FastColors.Slate
    HeartRateZone.Z1 -> FastColors.Cyan
    HeartRateZone.Z2 -> FastColors.Electric
    HeartRateZone.Z3 -> FastColors.Violet
    HeartRateZone.Z4 -> FastColors.Zones[4]
    HeartRateZone.Z5 -> FastColors.Rose
}

private fun ConfidenceLevel.nameRes(): Int = when (this) {
    ConfidenceLevel.HIGH -> R.string.level_high
    ConfidenceLevel.MEDIUM -> R.string.level_medium
    ConfidenceLevel.LOW -> R.string.level_low
}

private fun SprintIssue.textRes(): Int = when (this) {
    SprintIssue.FEW_SAMPLES -> R.string.issue_few_samples
    SprintIssue.WEAK_SIGNAL -> R.string.issue_weak_signal
    SprintIssue.NOT_HELD -> R.string.issue_not_held
    SprintIssue.SPIKE -> R.string.issue_spike
    SprintIssue.DISTANCE_MISMATCH -> R.string.issue_distance_mismatch
}

private fun SprintRejection.titleRes(): Int = when (this) {
    SprintRejection.NO_SPRINT -> R.string.rejected_no_sprint
    SprintRejection.NOT_ENOUGH_DATA -> R.string.rejected_no_data
    SprintRejection.TOO_FAST_FOR_A_RUNNER -> R.string.rejected_too_fast
}

/** Fewer positions than this do not make a track worth watching. */
/** A few seconds of movement are enough for a film: a speed test is seldom longer, and plays in slow motion. */
private const val MIN_POINTS_FOR_REPLAY = 5
