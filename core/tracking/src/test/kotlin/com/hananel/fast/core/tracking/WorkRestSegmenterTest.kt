package com.hananel.fast.core.tracking

import com.hananel.fast.core.tracking.SegmentKind.REST
import com.hananel.fast.core.tracking.SegmentKind.WORK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkRestSegmenterTest {

    private val football = RestDetectionConfig.FOOTBALL

    @Test
    fun aLongStopIsARestThatStartsWhenThePlayerActuallyStopped() {
        val points = track {
            stand(90)
            run(12.0, 120)
            stand(150)
            run(12.0, 60)
        }

        val segments = WorkRestSegmenter.segment(points, football)

        assertEquals(
            listOf(
                Segment(REST, 0, 90_000),
                Segment(WORK, 90_000, 210_000),
                // Not 270 s, when the stop had lasted long enough to count: 210 s, when it began.
                Segment(REST, 210_000, 360_000),
                Segment(WORK, 360_000, 419_000),
            ),
            segments,
        )
    }

    @Test
    fun aShortStopStaysInsideTheWork() {
        val points = track {
            run(12.0, 60)
            stand(40)
            run(12.0, 60)
        }

        assertEquals(listOf(Segment(WORK, 0, 159_000)), WorkRestSegmenter.segment(points, football))
    }

    @Test
    fun shuttleRunsInASmallAreaAreWorkEvenThoughThePlayerGoesNowhere() {
        val points = track { shuttle(kmh = 12.0, lengthMeters = 8.0, seconds = 180) }

        assertEquals(listOf(Segment(WORK, 0, 179_000)), WorkRestSegmenter.segment(points, football))
    }

    @Test
    fun oneNoisySampleDoesNotDelayARest() {
        val points = track {
            stand(30)
            second(9.0)
            stand(60)
        }

        assertEquals(listOf(Segment(REST, 0, 90_000)), WorkRestSegmenter.segment(points, football))
    }

    @Test
    fun oneNoisySampleDoesNotEndARest() {
        val points = track {
            stand(90)
            second(9.0)
            stand(60)
        }

        assertEquals(listOf(Segment(REST, 0, 150_000)), WorkRestSegmenter.segment(points, football))
    }

    @Test
    fun walkingAwayEndsTheRestOnceThePlayerHasGoneFarEnough() {
        val points = track {
            stand(90)
            run(4.0, 60)
        }

        val segments = WorkRestSegmenter.segment(points, football)

        assertEquals(listOf(REST, WORK), segments.map { it.kind })
        // Walking pace takes him 15 m from where he was within about 15 s of setting off at 90 s.
        assertTrue("rest ended at ${segments[0].endMs}", segments[0].endMs in 90_000..110_000)
    }

    @Test
    fun playingAtWalkingPaceOnASmallPitchEndsARest() {
        // 2026-10-24: after a real break the player went on playing at walking pace, never more
        // than 30 m from where he had stopped. The rest never ended, and after twenty minutes
        // of it the watch decided the session was forgotten.
        val points = track {
            stand(90)
            shuttle(kmh = 5.0, lengthMeters = 25.0, seconds = 600)
        }

        val segments = WorkRestSegmenter.segment(points, football)

        assertEquals(listOf(REST, WORK), segments.map { it.kind })
        assertTrue("rest ended at ${segments[0].endMs}", segments[0].endMs <= 120_000)
    }

    @Test
    fun pacingOnTheSpotIsStillARest() {
        val points = track {
            stand(90)
            shuttle(kmh = 4.0, lengthMeters = 6.0, seconds = 300)
        }

        assertEquals(listOf(REST), WorkRestSegmenter.segment(points, football).map { it.kind })
    }

    @Test
    fun withoutAPositionFixSpeedAloneDecides() {
        val points = track(withPositions = false) {
            stand(90)
            run(12.0, 30)
        }

        assertEquals(
            listOf(Segment(REST, 0, 90_000), Segment(WORK, 90_000, 119_000)),
            WorkRestSegmenter.segment(points, football),
        )
    }

    @Test
    fun aRunCountsAShorterStopThanFootballDoes() {
        val points = track {
            run(12.0, 60)
            stand(30)
            run(12.0, 60)
        }

        val asRun = WorkRestSegmenter.segment(points, RestDetectionConfig.RUN)
        val asFootball = WorkRestSegmenter.segment(points, football)

        assertEquals(listOf(WORK, REST, WORK), asRun.map { it.kind })
        assertEquals(30_000, asRun[1].durationMs)
        assertEquals(listOf(WORK), asFootball.map { it.kind })
    }

    @Test
    fun tooFewPointsGiveNoSegments() {
        assertTrue(WorkRestSegmenter.segment(track { stand(1) }, football).isEmpty())
    }
}
