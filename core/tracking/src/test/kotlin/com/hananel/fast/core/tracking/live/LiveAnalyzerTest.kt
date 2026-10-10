package com.hananel.fast.core.tracking.live

import com.hananel.fast.core.tracking.ActivityProfile
import com.hananel.fast.core.tracking.HeartRateZones
import com.hananel.fast.core.tracking.Intensity
import com.hananel.fast.core.tracking.SegmentKind
import com.hananel.fast.core.tracking.SpeedZone
import com.hananel.fast.core.tracking.TrackBuilder
import com.hananel.fast.core.tracking.track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveAnalyzerTest {

    private val football = ActivityProfile.football()
    private val zones = HeartRateZones(maxBpm = 190)

    private fun live(build: TrackBuilder.() -> Unit): LiveState {
        val points = track(build = build)
        return LiveAnalyzer.snapshot(points, football, zones, nowMs = points.last().timeMs)
    }

    @Test
    fun `a session with nothing recorded yet is work with no signal`() {
        val state = LiveAnalyzer.snapshot(emptyList(), football, zones, nowMs = 4_000)

        assertEquals(SegmentKind.WORK, state.segment)
        assertTrue(state.signalLost)
        assertEquals(4_000, state.elapsedMs)
        assertEquals(0, state.sprintCount)
    }

    @Test
    fun `while working the stopwatch runs from the start of the block`() {
        val state = live { run(11.0, 90) }

        assertEquals(SegmentKind.WORK, state.segment)
        assertEquals(89_000, state.segmentElapsedMs)
        assertEquals(SpeedZone.JOG, state.zone)
        assertFalse(state.signalLost)
    }

    @Test
    fun `a rest shows up after a minute, already counting from the stop`() {
        val before = live { run(11.0, 120); stand(50) }
        assertEquals(SegmentKind.WORK, before.segment)

        val after = live { run(11.0, 120); stand(70) }
        assertEquals(SegmentKind.REST, after.segment)
        assertEquals(Intensity.REST, after.intensity)
        // Seventy seconds standing, give or take the second in which the stop happened.
        assertEquals(70_000.0, after.segmentElapsedMs.toDouble(), 2_000.0)
    }

    @Test
    fun `a rest reports the work that came before it`() {
        val state = live {
            run(11.0, 120)
            run(27.0, 4)
            run(11.0, 30)
            stand(80)
        }

        val block = state.lastWorkBlock!!
        assertEquals(154_000.0, block.durationMs.toDouble(), 3_000.0)
        assertEquals(1, block.sprints)
        assertEquals(11.4, block.averageSpeed.kmh, 0.4)
    }

    @Test
    fun `moving again ends the rest and restarts the stopwatch`() {
        val state = live {
            run(11.0, 120)
            stand(90)
            run(11.0, 20)
        }

        assertEquals(SegmentKind.WORK, state.segment)
        assertEquals(20_000.0, state.segmentElapsedMs.toDouble(), 2_000.0)
        assertNull(state.lastWorkBlock)
        assertNull(state.restHeartRateDropBpm)
    }

    @Test
    fun `a sprint is live while it lasts and remembered when it ends`() {
        val during = live { run(11.0, 60); run(27.0, 3) }
        assertTrue(during.sprinting)
        assertEquals(1, during.sprintCount)
        assertNull(during.lastSprint)

        val after = live { run(11.0, 60); run(27.0, 3); run(11.0, 5) }
        assertFalse(after.sprinting)
        assertEquals(1, after.sprintCount)
        assertNotNull(after.lastSprint)
        assertEquals(27.0, after.lastSprint!!.peak.kmh, 0.01)
    }

    @Test
    fun `one fast reading is not a sprint`() {
        val state = live { run(11.0, 60); second(27.0) }

        assertFalse(state.sprinting)
        assertEquals(0, state.sprintCount)
    }

    @Test
    fun `heart rate recovery is reported while resting`() {
        val state = live {
            heartRate(170)
            run(11.0, 120)
            stand(10)
            heartRate(150)
            stand(30)
            heartRate(128)
            stand(50)
        }

        assertEquals(SegmentKind.REST, state.segment)
        // 170 when the rest began, 128 a minute later.
        assertEquals(42, state.restHeartRateDropBpm)
    }

    @Test
    fun `before a minute has passed the drop so far is shown, from the freshest reading`() {
        val points = track {
            heartRate(170)
            run(11.0, 120)
            stand(61)
        }
        // The rest has only just been recognised; the wrist reads 158 right now.
        val state = LiveAnalyzer.snapshot(points, football, zones, nowMs = points.last().timeMs, latestHeartRate = 158)

        assertEquals(SegmentKind.REST, state.segment)
        assertEquals(158, state.heartRateBpm)
    }

    @Test
    fun `a speed that stopped arriving is not shown as current`() {
        val points = track { run(14.0, 30) }

        val state = LiveAnalyzer.snapshot(points, football, zones, nowMs = points.last().timeMs + 9_000)

        assertTrue(state.signalLost)
        assertEquals(0.0, state.speed.kmh, 0.0)
        assertFalse(state.sprinting)
    }

    @Test
    fun `heart rate grades the work when speed alone cannot`() {
        val easy = live { heartRate(110); run(11.0, 60) }
        val threshold = live { heartRate(160); run(11.0, 60) }

        assertEquals(Intensity.LIGHT, easy.intensity)
        assertEquals(Intensity.THRESHOLD, threshold.intensity)
    }
}
