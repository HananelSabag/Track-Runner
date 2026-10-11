package com.hananel.fast.core.data

import com.hananel.fast.core.tracking.sprint.SatelliteView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionFormatTest {

    @Test
    fun `a session reads back as it was written`() {
        val original = SessionBuilder(ActivityKind.FOOTBALL).apply {
            maxBpm = 187
            heartRate(142)
            run(11.5, 20)
            second(27.3)
        }.build()

        val read = SessionFormat.parse(original.toText())!!

        assertEquals(original.header, read.header)
        assertEquals(original.fixes.size, read.fixes.size)
        assertEquals(original.heartRates, read.heartRates)
        assertTrue(read.endedCleanly)
        assertNull(read.trimAfterMs)

        val written = original.fixes.last()
        val got = read.fixes.last()
        assertEquals(written.timeMs, got.timeMs)
        assertEquals(written.utcMs, got.utcMs)
        assertEquals(written.speed.metersPerSecond, got.speed.metersPerSecond, 0.001)
        assertEquals(written.speedAccuracy!!.metersPerSecond, got.speedAccuracy!!.metersPerSecond, 0.001)
        // Seven decimals of a degree is about a centimetre.
        assertEquals(written.position!!.latitude, got.position!!.latitude, 1e-7)
        assertEquals(written.position!!.longitude, got.position!!.longitude, 1e-7)
        assertEquals(4.0, got.horizontalAccuracyMeters!!, 0.05)
        assertEquals(90f, got.bearingDegrees!!, 0.05f)
    }

    @Test
    fun `the satellites the receiver was using are kept with the session`() {
        val original = SessionBuilder(ActivityKind.SPRINT_TEST).apply {
            sky = SatelliteView(visible = 29, strong = 6, usedInFix = 12)
            run(20.0, 3)
            sky = SatelliteView(visible = 8, strong = 1, usedInFix = 5)
            run(20.0, 2)
        }.build()

        val read = SessionFormat.parse(original.toText())!!

        assertEquals(original.satellites, read.satellites)
        assertEquals(5, read.satellites.size)
        assertEquals(SatelliteView(8, 1, 5), read.satellites.last().view)
    }

    @Test
    fun `values the receiver did not report stay absent`() {
        val bare = FixRecord(timeMs = 60_000, utcMs = 5, hasSpeed = false, speed = com.hananel.fast.core.model.Speed.ZERO)
        val text = SessionFormat.header(SessionBuilder(ActivityKind.RUN).header()) + SessionFormat.line(bare) + "\n"

        val read = SessionFormat.parse(text)!!.fixes.single()

        assertFalse(read.hasSpeed)
        assertNull(read.speedAccuracy)
        assertNull(read.position)
        assertNull(read.horizontalAccuracyMeters)
        assertNull(read.bearingDegrees)
    }

    @Test
    fun `a session with no end marker was cut off`() {
        val crashed = SessionBuilder(ActivityKind.RUN).apply {
            endedCleanly = false
            run(10.0, 30)
        }.build()

        val read = SessionFormat.parse(crashed.toText())!!

        assertFalse(read.endedCleanly)
        assertEquals(30, read.fixes.size)
    }

    @Test
    fun `a last line cut mid-write is dropped and the rest survives`() {
        val whole = SessionBuilder(ActivityKind.RUN).apply {
            endedCleanly = false
            run(10.0, 10)
        }.build().toText()
        // The process died halfway through writing the eleventh fix.
        val cut = whole + "G,61000,1791600011000,1,2.7"

        val read = SessionFormat.parse(cut)!!

        assertEquals(10, read.fixes.size)
    }

    @Test
    fun `lines from a newer version are skipped, not fatal`() {
        val text = buildString {
            appendLine("FAST 2")
            appendLine("id=abc")
            appendLine("kind=RUN")
            appendLine("startedUtcMs=1000")
            appendLine("startedElapsedMs=50000")
            appendLine("device=FUTURE")
            appendLine("appVersion=9.0.0")
            appendLine("somethingNew=whatever")
            appendLine("#")
            appendLine("X,51000,an,unknown,record,type")
            appendLine("H,51000,150")
            appendLine("M,52000,SOMETHING_NEW")
            appendLine("M,52000,END")
        }

        val read = SessionFormat.parse(text)!!

        assertEquals("abc", read.header.id)
        assertEquals(listOf(HeartRateRecord(51_000, 150)), read.heartRates)
        assertTrue(read.endedCleanly)
    }

    @Test
    fun `an automatic end carries the moment training really stopped`() {
        val forgotten = SessionBuilder(ActivityKind.FOOTBALL).apply {
            run(10.0, 60)
            trimAfterMs = nowMs
            stand(120)
        }.build()

        val read = SessionFormat.parse(forgotten.toText())!!

        assertTrue(read.endedCleanly)
        assertEquals(forgotten.trimAfterMs, read.trimAfterMs)
        assertEquals(60_000, read.durationMs)
    }

    @Test
    fun `something that is not a session is refused`() {
        assertNull(SessionFormat.parse(""))
        assertNull(SessionFormat.parse("t_ms,utc_ms,has_speed\n1,2,1\n"))
        // The magic line alone is not enough: without an id there is nothing to store it under.
        assertNull(SessionFormat.parse("FAST 1\nkind=RUN\n#\n"))
    }

    @Test
    fun `the header can be read without the samples`() {
        val text = SessionBuilder(ActivityKind.SPRINT_TEST).apply { run(20.0, 5) }.build().toText()

        val header = SessionFormat.parseHeader(text.lineSequence())

        assertNotNull(header)
        assertEquals(ActivityKind.SPRINT_TEST, header!!.kind)
    }

    @Test
    fun `a device name with a line break cannot break the header`() {
        val header = SessionBuilder(ActivityKind.RUN).header().copy(device = "odd\nname")

        val read = SessionFormat.parseHeader(SessionFormat.header(header).lineSequence())!!

        assertEquals("odd name", read.device)
        assertEquals(header.startedElapsedMs, read.startedElapsedMs)
    }
}
