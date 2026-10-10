package com.hananel.fast.core.data

import com.hananel.fast.core.model.GeoPoint
import com.hananel.fast.core.model.Speed
import java.util.Locale

/**
 * The on-disk and on-the-wire form of a recording: plain text, one record per
 * line, appended as the session goes.
 *
 * ```
 * FAST 1
 * id=…
 * kind=FOOTBALL
 * …
 * #
 * G,<t>,<utc>,<hasSpeed>,<speed m/s>,<speed accuracy>,<lat>,<lon>,<position accuracy m>,<bearing>
 * H,<t>,<bpm>
 * M,<t>,END
 * ```
 *
 * Append-only is what makes a crash survivable: whatever was written before the
 * process died is a valid file, short of its end marker. And because the watch
 * and the phone update at different times, a reader skips anything it does not
 * recognise — a header key, a record type, a half-written last line — rather
 * than refusing the file.
 */
object SessionFormat {

    const val SCHEMA_VERSION = 1

    private const val MAGIC = "FAST"
    private const val HEADER_END = "#"
    private const val FIX = "G"
    private const val HEART_RATE = "H"
    private const val MARKER = "M"
    private const val MARKER_END = "END"
    private const val MARKER_AUTO_END = "AUTO_END"

    fun header(header: SessionHeader): String = buildString {
        appendLine("$MAGIC $SCHEMA_VERSION")
        appendLine("id=${header.id}")
        appendLine("kind=${header.kind.name}")
        appendLine("startedUtcMs=${header.startedUtcMs}")
        appendLine("startedElapsedMs=${header.startedElapsedMs}")
        appendLine("device=${header.device.oneLine()}")
        appendLine("appVersion=${header.appVersion.oneLine()}")
        header.maxBpm?.let { appendLine("maxBpm=$it") }
        if (header.demo) appendLine("demo=1")
        appendLine(HEADER_END)
    }

    fun line(record: RawRecord): String = when (record) {
        is FixRecord -> listOf(
            FIX,
            record.timeMs,
            record.utcMs,
            if (record.hasSpeed) 1 else 0,
            record.speed.metersPerSecond.fmt(3),
            record.speedAccuracy?.metersPerSecond?.fmt(3).orEmpty(),
            record.position?.latitude?.fmt(7).orEmpty(),
            record.position?.longitude?.fmt(7).orEmpty(),
            record.horizontalAccuracyMeters?.fmt(1).orEmpty(),
            record.bearingDegrees?.toDouble()?.fmt(1).orEmpty(),
        ).joinToString(",")
        is HeartRateRecord -> "$HEART_RATE,${record.timeMs},${record.bpm}"
    }

    /** The last line of a session that was finished on purpose. */
    fun endMarker(timeMs: Long): String = "$MARKER,$timeMs,$MARKER_END"

    /** The last line of a session the watch ended by itself; [trimAfterMs] is when the training really stopped. */
    fun autoEndMarker(timeMs: Long, trimAfterMs: Long): String = "$MARKER,$timeMs,$MARKER_AUTO_END,$trimAfterMs"

    /** Reads only the header, for when the samples are not needed. Null if this is not a session file. */
    fun parseHeader(lines: Sequence<String>): SessionHeader? {
        val iterator = lines.iterator()
        if (!iterator.hasNext() || !iterator.next().startsWith("$MAGIC ")) return null
        val fields = mutableMapOf<String, String>()
        while (iterator.hasNext()) {
            val line = iterator.next()
            if (line == HEADER_END) break
            val split = line.indexOf('=')
            if (split > 0) fields[line.substring(0, split)] = line.substring(split + 1)
        }
        return SessionHeader(
            id = fields["id"]?.takeIf { it.isNotBlank() } ?: return null,
            kind = fields["kind"]?.let { name -> ActivityKind.entries.firstOrNull { it.name == name } } ?: return null,
            startedUtcMs = fields["startedUtcMs"]?.toLongOrNull() ?: return null,
            startedElapsedMs = fields["startedElapsedMs"]?.toLongOrNull() ?: return null,
            device = fields["device"].orEmpty(),
            appVersion = fields["appVersion"].orEmpty(),
            maxBpm = fields["maxBpm"]?.toIntOrNull(),
            demo = fields["demo"] == "1",
        )
    }

    /** Null if this is not a session file. Unreadable lines inside one are skipped. */
    fun parse(text: String): RecordedSession? {
        val header = parseHeader(text.lineSequence()) ?: return null
        val fixes = mutableListOf<FixRecord>()
        val heartRates = mutableListOf<HeartRateRecord>()
        var endedCleanly = false
        var trimAfterMs: Long? = null

        text.lineSequence().dropWhile { it != HEADER_END }.drop(1).forEach { line ->
            val cells = line.split(",")
            val timeMs = cells.getOrNull(1)?.toLongOrNull() ?: return@forEach
            when (cells[0]) {
                FIX -> parseFix(timeMs, cells)?.let(fixes::add)
                HEART_RATE -> cells.getOrNull(2)?.toIntOrNull()?.let { heartRates += HeartRateRecord(timeMs, it) }
                MARKER -> when (cells.getOrNull(2)) {
                    MARKER_END -> endedCleanly = true
                    MARKER_AUTO_END -> {
                        endedCleanly = true
                        trimAfterMs = cells.getOrNull(3)?.toLongOrNull()
                    }
                }
            }
        }
        return RecordedSession(header, fixes, heartRates, endedCleanly, trimAfterMs)
    }

    private fun parseFix(timeMs: Long, cells: List<String>): FixRecord? {
        // A fix line cut short by a crash has too few cells; it is dropped whole rather than half-read.
        if (cells.size < FIX_CELLS) return null
        val latitude = cells[6].toDoubleOrNull()
        val longitude = cells[7].toDoubleOrNull()
        return FixRecord(
            timeMs = timeMs,
            utcMs = cells[2].toLongOrNull() ?: return null,
            hasSpeed = cells[3] == "1",
            speed = Speed(cells[4].toDoubleOrNull() ?: return null),
            speedAccuracy = cells[5].toDoubleOrNull()?.let(::Speed),
            position = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null,
            horizontalAccuracyMeters = cells[8].toDoubleOrNull(),
            bearingDegrees = cells[9].toFloatOrNull(),
        )
    }

    private fun String.oneLine(): String = replace('\n', ' ').replace('\r', ' ')

    private fun Double.fmt(decimals: Int): String = String.format(Locale.US, "%.${decimals}f", this)

    private const val FIX_CELLS = 10
}
