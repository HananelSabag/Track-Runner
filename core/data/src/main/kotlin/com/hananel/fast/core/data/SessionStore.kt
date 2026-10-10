package com.hananel.fast.core.data

import com.hananel.fast.core.model.Speed
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Every recording on this device, one file each, in one directory.
 *
 * | file | holds |
 * |---|---|
 * | `<id>.fast.part` | a session being recorded right now |
 * | `<id>.fast` | a finished session: the raw records, never changed again |
 * | `<id>.summary` | its one-line summary — a cache, safe to delete |
 * | `<id>.synced` | proof the phone has confirmed it holds this session |
 *
 * The raw file is the only thing that matters. The same class runs on the
 * watch and on the phone, so a session means the same thing on both.
 */
class SessionStore(private val directory: File) {

    init {
        directory.mkdirs()
    }

    /** Finished sessions, in no particular order. */
    fun ids(): List<String> =
        directory.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(SESSION_SUFFIX) }
            .map { it.name.removeSuffix(SESSION_SUFFIX) }

    fun contains(id: String): Boolean = sessionFile(id).isFile

    fun read(id: String): RecordedSession? =
        try {
            sessionFile(id).takeIf { it.isFile }?.readText()?.let(SessionFormat::parse)
        } catch (e: IOException) {
            null
        }

    /** Starts a recording. Nothing is listed until [SessionWriter.finish], or until a crash is recovered. */
    fun newWriter(header: SessionHeader): SessionWriter =
        SessionWriter(partFile(header.id), sessionFile(header.id), header)

    /**
     * Adopts recordings whose process died before they were finished, except the
     * one being recorded now. They become ordinary sessions without an end marker.
     */
    fun recoverOrphans(activeId: String? = null): Int {
        var recovered = 0
        for (part in directory.listFiles().orEmpty().filter { it.name.endsWith(PART_SUFFIX) }) {
            val id = part.name.removeSuffix(PART_SUFFIX)
            if (id == activeId) continue
            // A recording that died before its first fix has nothing in it worth keeping.
            val hasSamples = try {
                SessionFormat.parse(part.readText())?.fixes?.isNotEmpty() == true
            } catch (e: IOException) {
                false
            }
            if (hasSamples && part.renameTo(sessionFile(id))) recovered++ else part.delete()
        }
        return recovered
    }

    fun delete(id: String) {
        sessionFile(id).delete()
        summaryFile(id).delete()
        syncedFile(id).delete()
    }

    // ---- Transfer ----

    /** The session compressed for sending. Null if it is not here. */
    fun pack(id: String): ByteArray? {
        val raw = try {
            sessionFile(id).takeIf { it.isFile }?.readBytes() ?: return null
        } catch (e: IOException) {
            return null
        }
        return ByteArrayOutputStream().also { bytes -> GZIPOutputStream(bytes).use { it.write(raw) } }.toByteArray()
    }

    /**
     * Stores a session that arrived from another device and returns its header, or
     * null if the bytes are not a session. A session already held is kept as it is:
     * receiving the same one twice must not create a second copy or disturb the first.
     *
     * The file appears under its final name only once it is completely written, so a
     * crash mid-import cannot leave half a session that looks whole.
     */
    fun import(packed: ByteArray): SessionHeader? {
        val raw = try {
            GZIPInputStream(ByteArrayInputStream(packed)).use { it.readBytes() }
        } catch (e: IOException) {
            return null
        }
        val header = SessionFormat.parseHeader(String(raw, Charsets.UTF_8).lineSequence()) ?: return null
        // The id becomes a file name, so it must not be able to point anywhere else.
        if (!header.id.matches(SAFE_ID)) return null
        if (contains(header.id)) return header

        val incoming = File(directory, header.id + IMPORT_SUFFIX)
        return try {
            incoming.writeBytes(raw)
            if (incoming.renameTo(sessionFile(header.id))) header else null
        } catch (e: IOException) {
            incoming.delete()
            null
        }
    }

    // ---- Sync state (meaningful on the watch) ----

    fun isSynced(id: String): Boolean = syncedFile(id).exists()

    fun markSynced(id: String) {
        if (contains(id)) syncedFile(id).createNewFile()
    }

    fun unsyncedIds(): List<String> = ids().filterNot(::isSynced)

    // ---- Summaries ----

    /** Summaries of every session, newest first. Cached ones are reused; the rest are worked out and cached. */
    fun summaries(context: AnalysisContext = AnalysisContext()): List<SessionSummary> =
        ids().mapNotNull { summary(it, context) }.sortedByDescending { it.startedUtcMs }

    fun summary(id: String, context: AnalysisContext = AnalysisContext()): SessionSummary? {
        readCachedSummary(id, context)?.let { return it }
        val session = read(id) ?: return null
        val summary = SessionInsights.summarize(SessionInsights.of(session, context))
        try {
            summaryFile(id).writeText(summary.toCache(context))
        } catch (e: IOException) {
            // A summary that could not be cached is simply worked out again next time.
        }
        return summary
    }

    private fun readCachedSummary(id: String, context: AnalysisContext): SessionSummary? {
        val fields = try {
            summaryFile(id).takeIf { it.isFile }?.readLines() ?: return null
        } catch (e: IOException) {
            return null
        }.mapNotNull { line -> line.indexOf('=').takeIf { it > 0 }?.let { line.substring(0, it) to line.substring(it + 1) } }
            .toMap()

        if (fields["algorithm"] != ALGORITHM_VERSION.toString() || fields["context"] != context.key) return null
        return try {
            SessionSummary(
                id = id,
                kind = ActivityKind.valueOf(fields.getValue("kind")),
                startedUtcMs = fields.getValue("startedUtcMs").toLong(),
                durationMs = fields.getValue("durationMs").toLong(),
                workMs = fields.getValue("workMs").toLong(),
                restMs = fields.getValue("restMs").toLong(),
                distanceMeters = fields.getValue("distanceMeters").toDouble(),
                maxSpeed = Speed(fields.getValue("maxSpeed").toDouble()),
                peak3s = Speed(fields.getValue("peak3s").toDouble()),
                workAverageSpeed = Speed(fields.getValue("workAverageSpeed").toDouble()),
                sprintCount = fields.getValue("sprintCount").toInt(),
                highSpeedMeters = fields.getValue("highSpeedMeters").toDouble(),
                workAverageBpm = fields["workAverageBpm"]?.toInt(),
                maxBpm = fields["maxBpm"]?.toInt(),
                trainingLoad = fields["trainingLoad"]?.toDouble(),
                confidence = fields["confidence"]?.toInt(),
                endedCleanly = fields.getValue("endedCleanly") == "1",
                demo = fields.getValue("demo") == "1",
            )
        } catch (e: RuntimeException) {
            // A cache written by something else, or cut short: ignore it and recompute.
            null
        }
    }

    private fun SessionSummary.toCache(context: AnalysisContext): String = buildString {
        appendLine("algorithm=$ALGORITHM_VERSION")
        appendLine("context=${context.key}")
        appendLine("kind=${kind.name}")
        appendLine("startedUtcMs=$startedUtcMs")
        appendLine("durationMs=$durationMs")
        appendLine("workMs=$workMs")
        appendLine("restMs=$restMs")
        appendLine("distanceMeters=$distanceMeters")
        appendLine("maxSpeed=${maxSpeed.metersPerSecond}")
        appendLine("peak3s=${peak3s.metersPerSecond}")
        appendLine("workAverageSpeed=${workAverageSpeed.metersPerSecond}")
        appendLine("sprintCount=$sprintCount")
        appendLine("highSpeedMeters=$highSpeedMeters")
        workAverageBpm?.let { appendLine("workAverageBpm=$it") }
        maxBpm?.let { appendLine("maxBpm=$it") }
        trainingLoad?.let { appendLine("trainingLoad=$it") }
        confidence?.let { appendLine("confidence=$it") }
        appendLine("endedCleanly=${if (endedCleanly) 1 else 0}")
        appendLine("demo=${if (demo) 1 else 0}")
    }

    private fun sessionFile(id: String) = File(directory, id + SESSION_SUFFIX)
    private fun partFile(id: String) = File(directory, id + PART_SUFFIX)
    private fun summaryFile(id: String) = File(directory, "$id.summary")
    private fun syncedFile(id: String) = File(directory, "$id.synced")

    private companion object {
        const val SESSION_SUFFIX = ".fast"
        const val PART_SUFFIX = ".fast.part"
        const val IMPORT_SUFFIX = ".incoming"
        val SAFE_ID = Regex("[A-Za-z0-9-]{1,64}")
    }
}

/**
 * Appends one session to disk as it is recorded.
 *
 * Records reach the operating system within a few seconds, and the storage
 * itself a little later, so a crashed app loses seconds and a dead battery not
 * much more. Not thread-safe: call it from one thread.
 */
class SessionWriter internal constructor(
    private val partFile: File,
    private val finalFile: File,
    header: SessionHeader,
) : Closeable {

    private val stream = FileOutputStream(partFile)
    private val writer = stream.bufferedWriter(Charsets.UTF_8)
    private var lastFlushNs = System.nanoTime()
    private var lastSyncNs = lastFlushNs
    private var closed = false

    init {
        writer.write(SessionFormat.header(header))
        writer.flush()
    }

    fun append(record: RawRecord) {
        if (closed) return
        writer.write(SessionFormat.line(record))
        writer.newLine()

        val now = System.nanoTime()
        if (now - lastFlushNs >= FLUSH_INTERVAL_NS) {
            writer.flush()
            lastFlushNs = now
            if (now - lastSyncNs >= SYNC_INTERVAL_NS) {
                stream.fd.sync()
                lastSyncNs = now
            }
        }
    }

    /**
     * Ends the recording on purpose and makes it a listed session. With
     * [trimAfterMs], it was the watch that gave up on a forgotten session, and
     * everything after that moment is left out of the training.
     */
    fun finish(endMs: Long, trimAfterMs: Long? = null) {
        if (closed) return
        writer.write(if (trimAfterMs != null) SessionFormat.autoEndMarker(endMs, trimAfterMs) else SessionFormat.endMarker(endMs))
        writer.newLine()
        writer.flush()
        stream.fd.sync()
        close()
        if (!partFile.renameTo(finalFile)) throw IOException("Could not finish ${partFile.name}")
    }

    /** Throws the recording away: it was started and nothing came of it. */
    fun discard() {
        close()
        partFile.delete()
    }

    override fun close() {
        if (closed) return
        closed = true
        writer.close()
    }

    private companion object {
        const val FLUSH_INTERVAL_NS = 3_000_000_000L
        const val SYNC_INTERVAL_NS = 15_000_000_000L
    }
}
