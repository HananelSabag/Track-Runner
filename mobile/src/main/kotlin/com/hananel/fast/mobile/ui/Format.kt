package com.hananel.fast.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.tracking.SpeedZone
import com.hananel.fast.mobile.R
import com.hananel.fast.mobile.theme.FastColors
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Numbers are always written with Latin digits and a decimal point, whatever the
// phone's language: a speed has to look the same on the watch and on the phone.

/** "27.3" */
fun Speed.formatKmh(): String = String.format(Locale.US, "%.1f", kmh)

/** "4:07", or "1:04:07" past the hour. */
fun formatClock(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    val hours = seconds / 3600
    val minutes = seconds / 60 % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds % 60)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds % 60)
    }
}

/** "2.31 km" from a kilometre up, "840 m" below it. */
@Composable
fun formatMeters(meters: Double): String =
    if (meters >= 1000) {
        stringResource(R.string.value_km, String.format(Locale.US, "%.2f", meters / 1000))
    } else {
        stringResource(R.string.value_meters, meters.toInt())
    }

fun localTime(utcMs: Long): ZonedDateTime = Instant.ofEpochMilli(utcMs).atZone(ZoneId.systemDefault())

/** "12.10.2026 · 18:30" */
fun formatDateTime(utcMs: Long): String = DATE_TIME.format(localTime(utcMs))

/** "12.10 · 18:30" */
fun formatDayTime(utcMs: Long): String = DAY_TIME.format(localTime(utcMs))

private val DATE_TIME = DateTimeFormatter.ofPattern("d.M.yyyy · HH:mm", Locale.US)
private val DAY_TIME = DateTimeFormatter.ofPattern("d.M · HH:mm", Locale.US)

@Composable
fun ActivityKind.label(): String = stringResource(
    when (this) {
        ActivityKind.RUN -> R.string.mode_run
        ActivityKind.FOOTBALL -> R.string.mode_football
        ActivityKind.SPRINT_TEST -> R.string.mode_sprint_test
    },
)

fun ActivityKind.color(): Color = when (this) {
    ActivityKind.RUN -> FastColors.Sky
    ActivityKind.FOOTBALL -> FastColors.Lilac
    ActivityKind.SPRINT_TEST -> FastColors.Rose
}

fun SpeedZone.color(): Color = FastColors.Zones[ordinal + 1]

@Composable
fun SpeedZone.label(): String = stringResource(
    when (this) {
        SpeedZone.WALK -> R.string.zone_walk
        SpeedZone.JOG -> R.string.zone_jog
        SpeedZone.RUN -> R.string.zone_run
        SpeedZone.HIGH_SPEED -> R.string.zone_high_speed
        SpeedZone.SPRINT -> R.string.zone_sprint
    },
)
