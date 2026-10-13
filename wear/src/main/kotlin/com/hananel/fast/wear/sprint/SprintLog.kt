package com.hananel.fast.wear.sprint

import android.content.Context
import android.util.Log
import com.hananel.fast.core.tracking.sprint.SprintTestOutcome
import com.hananel.fast.wear.gnss.GnssFix
import java.io.File
import java.io.IOException
import java.util.Locale

/** One line of the saved history. A test that produced no measurement has no speed and no confidence. */
data class SavedSprint(val startedUtcMs: Long, val topSpeedKmh: Double?, val confidence: Int?)

/**
 * Keeps every test's raw fixes and its verdict on the watch.
 *
 * The raw fixes are the point: the confidence score was tuned on synthetic
 * sprints, and only real ones can show whether its thresholds are right. With
 * the fixes kept, any test can be re-analysed after the scoring changes.
 *
 * Files stay in the app's private storage. They hold positions, so nothing
 * here writes them to the system log.
 */
class SprintLog(context: Context) {

    private val directory = File(context.filesDir, "sprints").apply { mkdirs() }

    fun save(startedUtcMs: Long, fixes: List<GnssFix>, outcome: SprintTestOutcome) {
        try {
            File(directory, "sprint-$startedUtcMs.csv").writeText(fixes.toCsv())
            File(directory, "results.csv").apply {
                if (!exists()) writeText(RESULT_HEADER + "\n")
                appendText(outcome.toCsvRow(startedUtcMs, fixes.size) + "\n")
            }
        } catch (e: IOException) {
            // Losing the log must not lose the result the runner is about to see.
            Log.w(TAG, "Could not save sprint log", e)
        }
        Log.i(TAG, outcome.toLogLine(fixes.size))
    }

    /**
     * The tests that produced a measurement, newest first. Attempts that did not —
     * a walk, a lost signal — stay in the log for diagnosis but are not tests anyone
     * wants to look back on. A row that cannot be read is skipped rather than failing the list.
     */
    fun readResults(): List<SavedSprint> {
        val file = File(directory, "results.csv")
        if (!file.exists()) return emptyList()
        return try {
            file.readLines().drop(1).mapNotNull(::parseRow).filter { it.topSpeedKmh != null }.sortedByDescending { it.startedUtcMs }
        } catch (e: IOException) {
            Log.w(TAG, "Could not read sprint log", e)
            emptyList()
        }
    }

    private fun parseRow(line: String): SavedSprint? {
        val cells = line.split(",")
        val startedUtcMs = cells.getOrNull(0)?.toLongOrNull() ?: return null
        val measured = cells.getOrNull(2) == "measured"
        return SavedSprint(
            startedUtcMs = startedUtcMs,
            topSpeedKmh = if (measured) cells.getOrNull(3)?.toDoubleOrNull() else null,
            confidence = if (measured) cells.getOrNull(6)?.toIntOrNull() else null,
        )
    }

    private fun List<GnssFix>.toCsv(): String = buildString {
        appendLine("t_ms,utc_ms,has_speed,speed_mps,speed_acc_mps,lat,lon,h_acc_m,bearing_deg")
        for (fix in this@toCsv) {
            val s = fix.sample
            appendLine(
                listOf(
                    s.timeMs,
                    fix.utcMs,
                    if (fix.hasSpeed) 1 else 0,
                    s.speed.metersPerSecond.fmt(3),
                    s.speedAccuracy?.metersPerSecond?.fmt(3).orEmpty(),
                    s.position?.latitude?.fmt(7).orEmpty(),
                    s.position?.longitude?.fmt(7).orEmpty(),
                    s.horizontalAccuracyMeters?.fmt(1).orEmpty(),
                    fix.bearingDegrees?.toDouble()?.fmt(1).orEmpty(),
                ).joinToString(","),
            )
        }
    }

    private fun SprintTestOutcome.toCsvRow(startedUtcMs: Long, fixCount: Int): String = when (this) {
        is SprintTestOutcome.Measured -> listOf(
            startedUtcMs, fixCount, "measured", topSpeed.kmh.fmt(2), instantMax.kmh.fmt(2),
            uncertainty?.kmh?.fmt(2).orEmpty(), confidence, fastPhaseMs, fastPhaseMeters.fmt(1), issues.joinToString("|"),
        )
        is SprintTestOutcome.Rejected -> listOf(startedUtcMs, fixCount, "rejected:$reason", "", "", "", "", "", "", "")
    }.joinToString(",")

    /** No positions here: this line goes to the system log. */
    private fun SprintTestOutcome.toLogLine(fixCount: Int): String = when (this) {
        is SprintTestOutcome.Measured ->
            "measured top=${topSpeed.kmh.fmt(1)}km/h max=${instantMax.kmh.fmt(1)} confidence=$confidence " +
                "issues=$issues fast=${fastPhaseMs}ms/${fastPhaseMeters.fmt(0)}m fixes=$fixCount"
        is SprintTestOutcome.Rejected -> "rejected reason=$reason fixes=$fixCount"
    }

    private fun Double.fmt(decimals: Int): String = String.format(Locale.US, "%.${decimals}f", this)

    private companion object {
        const val TAG = "FastSprint"
        const val RESULT_HEADER =
            "started_utc_ms,fixes,outcome,top_kmh,instant_max_kmh,uncertainty_kmh,confidence,fast_ms,fast_m,issues"
    }
}
