package com.hananel.fast.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackAssemblerTest {

    @Test
    fun `time is measured from the start of the session`() {
        val points = TrackAssembler.assemble(session { run(10.0, 5) })

        assertEquals(listOf(1000L, 2000L, 3000L, 4000L, 5000L), points.map { it.timeMs })
    }

    @Test
    fun `one reading that jumps does not become the top speed`() {
        val points = TrackAssembler.assemble(
            session {
                run(15.0, 10)
                second(38.0) // a glitch: no runner gains 23 km/h in a second and loses it in the next
                run(15.0, 10)
            },
        )

        assertEquals(15.0, points.maxOf { it.speed.kmh }, 0.01)
    }

    @Test
    fun `a speed held for two readings is kept`() {
        val points = TrackAssembler.assemble(
            session {
                run(15.0, 5)
                second(24.0)
                second(27.0)
                second(27.0)
                second(24.0)
                run(15.0, 5)
            },
        )

        assertEquals(27.0, points.maxOf { it.speed.kmh }, 0.01)
    }

    @Test
    fun `the newest reading waits for a second one to confirm it`() {
        // A live session: the last fix has no successor yet.
        val live = TrackAssembler.assemble(
            session {
                run(15.0, 5)
                second(30.0)
            },
        )
        assertEquals(15.0, live.last().speed.kmh, 0.01)

        val confirmed = TrackAssembler.assemble(
            session {
                run(15.0, 5)
                second(30.0)
                second(30.0)
            },
        )
        assertEquals(30.0, confirmed[confirmed.lastIndex - 1].speed.kmh, 0.01)
    }

    @Test
    fun `a reading on the far side of a signal gap is not a neighbour`() {
        val points = TrackAssembler.assemble(
            session {
                run(10.0, 5)
                gap(30)
                second(20.0)
                second(20.0)
                second(20.0)
            },
        )

        // The first fix after the gap is judged against the one after it, not the one 30 s before.
        assertEquals(20.0, points[5].speed.kmh, 0.01)
    }

    @Test
    fun `a speed no runner reaches is left out`() {
        val points = TrackAssembler.assemble(
            session {
                run(12.0, 10)
                run(70.0, 20) // drove off with the session still running
            },
        )

        assertEquals(10, points.size)
        assertTrue(points.all { it.speed.kmh < 45.0 })
    }

    @Test
    fun `a fix without a speed is not a point`() {
        val points = TrackAssembler.assemble(
            session {
                run(10.0, 3)
                second(0.0, hasSpeed = false)
                run(10.0, 3)
            },
        )

        assertEquals(6, points.size)
    }

    @Test
    fun `each point carries the nearest heart rate`() {
        val recorded = session {
            heartRate(120)
            run(10.0, 3)
            heartRate(150)
            run(10.0, 3)
        }

        val points = TrackAssembler.assemble(recorded)

        assertEquals(listOf(120, 120, 120, 150, 150, 150), points.map { it.heartRateBpm })
    }

    @Test
    fun `a heart rate that stopped arriving is not carried forward`() {
        val recorded = session {
            heartRate(140)
            run(10.0, 3)
            heartRate(null) // the strap lost contact
            run(10.0, 10)
        }

        val points = TrackAssembler.assemble(recorded)

        assertEquals(140, points[2].heartRateBpm)
        assertNull(points.last().heartRateBpm)
    }

    @Test
    fun `nothing after an automatic end is part of the training`() {
        val recorded = SessionBuilder(ActivityKind.FOOTBALL).apply {
            run(10.0, 60)
            trimAfterMs = nowMs
            stand(600)
        }.build()

        val points = TrackAssembler.assemble(recorded)

        assertEquals(60, points.size)
    }

    @Test
    fun `fixes out of order or repeated are straightened out`() {
        val base = session { run(10.0, 5) }
        val shuffled = base.copy(fixes = base.fixes.reversed() + base.fixes.first())

        val points = TrackAssembler.assemble(shuffled)

        assertEquals(listOf(1000L, 2000L, 3000L, 4000L, 5000L), points.map { it.timeMs })
    }
}
