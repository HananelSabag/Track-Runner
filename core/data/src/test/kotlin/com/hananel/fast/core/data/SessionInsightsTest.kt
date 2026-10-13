package com.hananel.fast.core.data

import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.tracking.SegmentKind
import com.hananel.fast.core.tracking.sprint.SatelliteView
import com.hananel.fast.core.tracking.sprint.SprintTestOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionInsightsTest {

    @Test
    fun `a football session is read as work, rest and sprints`() {
        val recorded = SessionBuilder(ActivityKind.FOOTBALL).apply {
            maxBpm = 190
            heartRate(160)
            run(11.0, 120)
            run(27.0, 4)
            run(11.0, 30)
            heartRate(120)
            stand(90)
            heartRate(160)
            run(11.0, 60)
        }.build()

        val insight = SessionInsights.of(recorded)
        val summary = SessionInsights.summarize(insight)

        assertEquals(listOf(SegmentKind.WORK, SegmentKind.REST, SegmentKind.WORK), insight.analysis!!.segments.map { it.kind })
        assertEquals(1, summary.sprintCount)
        assertEquals(27.0, summary.maxSpeed.kmh, 0.01)
        assertEquals(90_000.0, summary.restMs.toDouble(), 3_000.0)
        assertNotNull(summary.workAverageBpm)
        assertNull(summary.confidence)
        assertEquals(recorded.durationMs, summary.durationMs)
    }

    @Test
    fun `without a maximum heart rate there is no heart-rate reading, and nothing is guessed`() {
        val recorded = session {
            heartRate(150)
            run(11.0, 60)
        }

        val summary = SessionInsights.summarize(SessionInsights.of(recorded))

        assertNull(summary.workAverageBpm)
        assertNull(summary.trainingLoad)
    }

    @Test
    fun `the phone's own setting fills in when the session carries no maximum`() {
        val recorded = session {
            heartRate(150)
            run(11.0, 60)
        }

        val insight = SessionInsights.of(recorded, AnalysisContext(fallbackMaxBpm = 185))

        assertEquals(185, insight.maxBpmUsed)
        assertEquals(150, insight.analysis!!.heartRate!!.workAverageBpm)
    }

    @Test
    fun `a sprint test is scored, not analysed as training`() {
        val recorded = SessionBuilder(ActivityKind.SPRINT_TEST).apply {
            second(6.0); second(12.0); second(18.0); second(23.0); second(26.0)
            second(27.5); second(27.5); second(27.5); second(27.0)
            second(20.0); second(10.0); second(4.0)
        }.build()

        val insight = SessionInsights.of(recorded)
        val summary = SessionInsights.summarize(insight)

        assertNull(insight.analysis)
        val measured = insight.sprintOutcome as SprintTestOutcome.Measured
        assertEquals(measured.topSpeed, summary.peak3s)
        assertEquals(measured.confidence, summary.confidence)
        assertEquals(27.5, summary.peak3s.kmh, 0.3)
    }

    @Test
    fun `a sprint test on the real watch is scored by the satellites it was using`() {
        // The watch claims 25 m/s and 125 m on every fix, so its claims say nothing.
        fun testUnder(view: SatelliteView) = SessionBuilder(ActivityKind.SPRINT_TEST).apply {
            speedAccuracy = 25.0
            positionAccuracy = 125.0
            sky = view
            second(6.0); second(12.0); second(18.0); second(23.0); second(26.0)
            second(27.5); second(27.5); second(27.5); second(27.0)
            second(20.0); second(10.0); second(4.0)
        }.build()

        val openSky = SessionInsights.summarize(SessionInsights.of(testUnder(SatelliteView(visible = 35, strong = 14, usedInFix = 14))))
        val balcony = SessionInsights.summarize(SessionInsights.of(testUnder(SatelliteView(visible = 8, strong = 1, usedInFix = 5))))

        assertTrue("open sky scored ${openSky.confidence}", openSky.confidence!! >= SprintTestOutcome.HIGH_CONFIDENCE)
        assertTrue("balcony scored ${balcony.confidence}", balcony.confidence!! < SprintTestOutcome.MEDIUM_CONFIDENCE)
        // Same speeds either way: the sky changes the trust, not the number.
        assertEquals(openSky.peak3s, balcony.peak3s)
    }

    @Test
    fun `standing still is an empty session, a walk is not`() {
        // What the real watch reports while standing: a trickle of speed, never three seconds of it.
        val stood = SessionInsights.summarize(
            SessionInsights.of(session(ActivityKind.RUN) { repeat(20) { second(0.4); second(1.1); second(0.2) } }),
        )
        val walked = SessionInsights.summarize(SessionInsights.of(session(ActivityKind.RUN) { run(4.5, 60) }))
        val tooShort = SessionInsights.summarize(SessionInsights.of(session(ActivityKind.RUN) { run(10.0, 2) }))

        assertTrue(!stood.showsMovement)
        assertTrue(walked.showsMovement)
        // Two seconds are not a session, whatever the speed.
        assertTrue(!tooShort.showsMovement)
    }

    @Test
    fun `a jog is not a top speed`() {
        val jog = SessionInsights.summarize(SessionInsights.of(session { run(11.0, 300) }))

        assertNull(SessionInsights.personalTopSpeed(listOf(jog)))
    }

    @Test
    fun `the top speed is the best three seconds on record`() {
        val slow = summaryOf("a") { run(11.0, 60); run(24.0, 5); run(11.0, 30) }
        val fast = summaryOf("b") { run(11.0, 60); run(28.0, 5); run(11.0, 30) }

        assertEquals(28.0, SessionInsights.personalTopSpeed(listOf(slow, fast))!!.kmh, 0.01)
    }

    @Test
    fun `a sprint test the app does not trust cannot move the bands`() {
        val training = summaryOf("a") { run(11.0, 60); run(24.0, 5); run(11.0, 30) }
        val doubtful = training.copy(id = "t", kind = ActivityKind.SPRINT_TEST, peak3s = Speed.ofKmh(36.0), confidence = 35)
        val unmeasured = doubtful.copy(id = "u", confidence = null)
        val trusted = doubtful.copy(id = "v", peak3s = Speed.ofKmh(29.0), confidence = 84)

        assertEquals(24.0, SessionInsights.personalTopSpeed(listOf(training, doubtful, unmeasured))!!.kmh, 0.01)
        assertEquals(29.0, SessionInsights.personalTopSpeed(listOf(training, doubtful, trusted))!!.kmh, 0.01)
    }

    @Test
    fun `demo sessions count only until there is something real`() {
        val demo = summaryOf("d") { run(11.0, 60); run(31.0, 5); run(11.0, 30) }.copy(demo = true)
        val real = summaryOf("r") { run(11.0, 60); run(25.0, 5); run(11.0, 30) }

        assertEquals(31.0, SessionInsights.personalTopSpeed(listOf(demo))!!.kmh, 0.01)
        assertEquals(25.0, SessionInsights.personalTopSpeed(listOf(demo, real))!!.kmh, 0.01)
    }

    @Test
    fun `a slow personal best does not turn a jog into a sprint`() {
        val profile = SessionInsights.profileFor(ActivityKind.FOOTBALL, Speed.ofKmh(13.0))

        // 85% of 13 km/h would be 11 km/h. The standard line, 25.2, still applies.
        assertTrue(profile.zones.lowerBound(com.hananel.fast.core.tracking.SpeedZone.SPRINT).kmh > 25.0)
    }

    private fun summaryOf(id: String, build: SessionBuilder.() -> Unit): SessionSummary =
        SessionInsights.summarize(SessionInsights.of(SessionBuilder(ActivityKind.FOOTBALL).apply(build).build(id)))
}
