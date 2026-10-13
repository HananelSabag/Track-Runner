package com.hananel.fast.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store(name: String = "sessions") = SessionStore(File(folder.root, name))

    /** Records [recorded] through a writer, the way the watch does. */
    private fun SessionStore.record(recorded: RecordedSession, finish: Boolean = true): SessionWriter {
        val writer = newWriter(recorded.header)
        (recorded.fixes + recorded.heartRates).sortedBy { it.timeMs }.forEach(writer::append)
        if (finish) writer.finish(recorded.fixes.last().timeMs) else writer.close()
        return writer
    }

    @Test
    fun `a session is not listed until it is finished`() {
        val store = store()
        val recorded = session { run(10.0, 30) }
        val writer = store.newWriter(recorded.header)
        recorded.fixes.forEach(writer::append)

        assertTrue(store.ids().isEmpty())

        writer.finish(recorded.fixes.last().timeMs)

        assertEquals(listOf(recorded.header.id), store.ids())
        val read = store.read(recorded.header.id)!!
        assertEquals(30, read.fixes.size)
        assertTrue(read.endedCleanly)
    }

    @Test
    fun `a recording whose process died is recovered, marked as cut off`() {
        val store = store()
        val recorded = session { run(10.0, 30) }
        store.record(recorded, finish = false)

        // The app starts again.
        val recovered = SessionStore(File(folder.root, "sessions")).recoverOrphans()

        assertEquals(1, recovered)
        val read = store.read(recorded.header.id)!!
        assertEquals(30, read.fixes.size)
        assertFalse(read.endedCleanly)
    }

    @Test
    fun `the recording in progress is not mistaken for an orphan`() {
        val store = store()
        val recorded = session { run(10.0, 30) }
        store.record(recorded, finish = false)

        assertEquals(0, store.recoverOrphans(activeId = recorded.header.id))
        assertTrue(store.ids().isEmpty())
    }

    @Test
    fun `a recording that died before its first fix is thrown away`() {
        val store = store()
        store.newWriter(SessionBuilder(ActivityKind.RUN).header()).close()

        assertEquals(0, store.recoverOrphans())
        assertTrue(store.ids().isEmpty())
        assertTrue(File(folder.root, "sessions").listFiles()!!.isEmpty())
    }

    @Test
    fun `a session travels packed from one store to another unchanged`() {
        val watch = store("watch")
        val phone = store("phone")
        val recorded = session {
            heartRate(150)
            run(12.0, 120)
        }
        watch.record(recorded)

        val header = phone.import(watch.pack(recorded.header.id)!!)

        assertEquals(recorded.header, header)
        assertEquals(watch.read(recorded.header.id), phone.read(recorded.header.id))
    }

    @Test
    fun `receiving the same session twice keeps one copy`() {
        val watch = store("watch")
        val phone = store("phone")
        val recorded = session { run(12.0, 60) }
        watch.record(recorded)
        val packed = watch.pack(recorded.header.id)!!

        assertNotNull(phone.import(packed))
        assertNotNull(phone.import(packed))

        assertEquals(1, phone.ids().size)
    }

    @Test
    fun `bytes that are not a session are refused`() {
        val phone = store()

        assertNull(phone.import(byteArrayOf(1, 2, 3)))
        assertNull(phone.import(gzip("hello")))
        assertTrue(phone.ids().isEmpty())
    }

    @Test
    fun `an id that is really a path is refused`() {
        val phone = store()
        val hostile = SessionFormat.header(SessionBuilder(ActivityKind.RUN).header(id = "../../escaped"))

        assertNull(phone.import(gzip(hostile)))
        assertFalse(File(folder.root, "escaped.fast").exists())
    }

    @Test
    fun `only a confirmed session counts as synced`() {
        val store = store()
        val first = SessionBuilder(ActivityKind.RUN).apply { run(10.0, 30) }.build("aaaa")
        val second = SessionBuilder(ActivityKind.RUN).apply { run(10.0, 30) }.build("bbbb")
        store.record(first)
        store.record(second)

        store.markSynced("aaaa")
        store.markSynced("never-recorded")

        assertTrue(store.isSynced("aaaa"))
        assertFalse(store.isSynced("never-recorded"))
        assertEquals(listOf("bbbb"), store.unsyncedIds())
    }

    @Test
    fun `a phone that lost everything can be sent everything again`() {
        val store = store()
        val first = SessionBuilder(ActivityKind.RUN).apply { run(10.0, 30) }.build("aaaa")
        val second = SessionBuilder(ActivityKind.RUN).apply { run(10.0, 30) }.build("bbbb")
        store.record(first)
        store.record(second)
        store.markSynced("aaaa")
        store.markSynced("bbbb")

        store.forgetSynced()

        assertEquals(setOf("aaaa", "bbbb"), store.unsyncedIds().toSet())
        // The sessions themselves are untouched.
        assertEquals(30, store.read("aaaa")!!.fixes.size)
    }

    @Test
    fun `a session set aside leaves the history but is not destroyed`() {
        val store = store()
        val empty = SessionBuilder(ActivityKind.RUN).apply { stand(40) }.build("empty")
        val real = SessionBuilder(ActivityKind.RUN).apply { run(10.0, 40) }.build("real")
        store.record(empty)
        store.record(real)
        store.summary("empty")
        store.markSynced("empty")

        store.setAside("empty")

        assertEquals(listOf("real"), store.ids())
        assertEquals(listOf("real"), store.unsyncedIds())
        assertNull(store.read("empty"))
        // Still there for working out why nothing was recorded.
        assertTrue(File(folder.root, "sessions/set-aside/empty.fast").isFile)
        assertFalse(File(folder.root, "sessions/empty.summary").exists())
    }

    @Test
    fun `only the newest few set-aside sessions are kept`() {
        val store = store()
        repeat(13) { i ->
            store.record(SessionBuilder(ActivityKind.RUN).apply { stand(5) }.build("empty-$i"))
            store.setAside("empty-$i")
        }

        assertEquals(10, File(folder.root, "sessions/set-aside").listFiles()!!.size)
        assertTrue(store.ids().isEmpty())
    }

    @Test
    fun `deleting a session removes everything about it`() {
        val store = store()
        val recorded = session { run(10.0, 30) }
        store.record(recorded)
        store.summary(recorded.header.id)
        store.markSynced(recorded.header.id)

        store.delete(recorded.header.id)

        assertTrue(File(folder.root, "sessions").listFiles()!!.isEmpty())
    }

    @Test
    fun `a summary read from the cache equals the one worked out`() {
        val store = store()
        val recorded = SessionBuilder(ActivityKind.FOOTBALL).apply {
            maxBpm = 190
            heartRate(150)
            run(12.0, 120)
            run(27.0, 5)
            run(12.0, 60)
        }.build()
        store.record(recorded)

        val computed = store.summary(recorded.header.id)!!
        val cached = store.summary(recorded.header.id)!!

        assertEquals(computed, cached)
        assertEquals(27.0, cached.maxSpeed.kmh, 0.01)
    }

    @Test
    fun `a summary made for another top speed is not reused`() {
        val store = store()
        val recorded = SessionBuilder(ActivityKind.FOOTBALL).apply {
            run(10.0, 60)
            run(22.0, 5)
            run(10.0, 60)
        }.build()
        store.record(recorded)

        // With the standard bands 22 km/h is a fast run, not a sprint…
        val standard = store.summary(recorded.header.id, AnalysisContext())!!
        // …but for a player whose top speed is 24 km/h it is one.
        val personal = store.summary(recorded.header.id, AnalysisContext(topSpeed = com.hananel.fast.core.model.Speed.ofKmh(24.0)))!!

        assertEquals(0, standard.sprintCount)
        assertEquals(1, personal.sprintCount)
    }

    @Test
    fun `a damaged cache is ignored`() {
        val store = store()
        val recorded = session { run(10.0, 60) }
        store.record(recorded)
        val good = store.summary(recorded.header.id)!!
        File(folder.root, "sessions/${recorded.header.id}.summary").writeText("algorithm=$ALGORITHM_VERSION\ncontext=-/-\nkind=RUN\n")

        assertEquals(good, store.summary(recorded.header.id))
    }

    @Test
    fun `summaries come newest first`() {
        val store = store()
        val older = SessionBuilder(ActivityKind.RUN).apply { run(10.0, 30) }.build("older")
        val newer = SessionBuilder(ActivityKind.RUN).apply { run(10.0, 30) }.build("newer")
        store.record(older.copy(header = older.header.copy(startedUtcMs = 1_000)))
        store.record(newer.copy(header = newer.header.copy(startedUtcMs = 2_000)))

        assertEquals(listOf("newer", "older"), store.summaries().map { it.id })
    }

    private fun gzip(text: String): ByteArray =
        java.io.ByteArrayOutputStream().also { bytes ->
            java.util.zip.GZIPOutputStream(bytes).use { it.write(text.toByteArray()) }
        }.toByteArray()
}
