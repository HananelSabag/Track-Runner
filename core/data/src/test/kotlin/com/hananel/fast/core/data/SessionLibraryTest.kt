package com.hananel.fast.core.data

import com.hananel.fast.core.model.Speed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionLibraryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val store by lazy { SessionStore(folder.root) }

    private fun record(id: String, startedUtcMs: Long, build: SessionBuilder.() -> Unit) {
        val built = SessionBuilder(ActivityKind.FOOTBALL).apply(build).build(id)
        val recorded = built.copy(header = built.header.copy(startedUtcMs = startedUtcMs))
        val writer = store.newWriter(recorded.header)
        recorded.fixes.forEach(writer::append)
        writer.finish(recorded.fixes.last().timeMs)
    }

    @Test
    fun `an empty library has no sessions and no top speed`() {
        val snapshot = SessionLibrary(store).load()

        assertEquals(emptyList<SessionSummary>(), snapshot.summaries)
        assertNull(snapshot.topSpeed)
    }

    @Test
    fun `every session is read with bands from the fastest one`() {
        // 22 km/h is under the standard sprint line of 25.2…
        record("early", 1_000) { run(10.0, 60); run(22.0, 5); run(10.0, 60) }
        // …but this player tops out at 24, so for them it was a sprint.
        record("later", 2_000) { run(10.0, 60); run(24.0, 5); run(10.0, 60) }

        val snapshot = SessionLibrary(store).load()

        assertEquals(24.0, snapshot.topSpeed!!.kmh, 0.01)
        assertEquals(listOf("later", "early"), snapshot.summaries.map { it.id })
        assertEquals(listOf(1, 1), snapshot.summaries.map { it.sprintCount })
    }

    @Test
    fun `a stale hint does not change the answer`() {
        record("only", 1_000) { run(10.0, 60); run(24.0, 5); run(10.0, 60) }

        val fresh = SessionLibrary(store).load()
        val stale = SessionLibrary(store).load(lastKnownTopSpeed = Speed.ofKmh(33.0))

        assertEquals(fresh.topSpeed, stale.topSpeed)
        assertEquals(fresh.summaries, stale.summaries)
        assertEquals(fresh.context.key, stale.context.key)
    }

    @Test
    fun `deleting the fastest session lowers the bands again`() {
        record("slow", 1_000) { run(10.0, 60); run(22.0, 5); run(10.0, 60) }
        record("fast", 2_000) { run(10.0, 60); run(31.0, 5); run(10.0, 60) }
        val before = SessionLibrary(store).load()
        assertEquals(0, before.summaries.first { it.id == "slow" }.sprintCount)

        store.delete("fast")
        val after = SessionLibrary(store).load(lastKnownTopSpeed = before.topSpeed)

        assertEquals(22.0, after.topSpeed!!.kmh, 0.01)
        assertEquals(1, after.summaries.single().sprintCount)
    }
}
