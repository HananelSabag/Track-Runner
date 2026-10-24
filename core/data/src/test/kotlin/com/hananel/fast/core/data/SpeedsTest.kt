package com.hananel.fast.core.data

import com.hananel.fast.core.model.GeoPoint
import com.hananel.fast.core.model.Speed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedsTest {

    /** A 20 km/h run as a receiver that smooths its speed reports it: at half the speed it covers. */
    private fun smoothedRun(claimedAccuracy: Double): RecordedSession {
        val run = SessionBuilder(ActivityKind.RUN).apply {
            speedAccuracy = claimedAccuracy
            stand(5)
            run(20.0, 20)
        }.build()
        return run.copy(fixes = run.fixes.map { it.copy(speed = Speed(it.speed.metersPerSecond / 2)) })
    }

    @Test
    fun aReceiverThatDoesNotRateItsSpeedIsReadByItsPositions() {
        val watch = smoothedRun(claimedAccuracy = 25.0)

        assertEquals(SpeedSource.POSITIONS, Speeds.sourceFor(watch.fixes))
        val top = TrackAssembler.assemble(watch).maxOf { it.speed.kmh }
        assertEquals(20.0, top, 0.1)
    }

    @Test
    fun aReceiverThatRatesItsSpeedKeepsItsOwn() {
        val phone = smoothedRun(claimedAccuracy = 0.3)

        assertEquals(SpeedSource.RECEIVER, Speeds.sourceFor(phone.fixes))
        val top = TrackAssembler.assemble(phone).maxOf { it.speed.kmh }
        assertEquals(10.0, top, 0.1)
    }

    @Test
    fun aPositionThatJumpsAwayAndBackIsNotARecord() {
        val standing = SessionBuilder(ActivityKind.FOOTBALL).apply {
            speedAccuracy = 25.0
            stand(60)
        }.build()
        // One fix lands 30 m east and the next is back where it was.
        val glitched = standing.fixes.mapIndexed { i, fix ->
            if (i != 30) fix else fix.copy(position = GeoPoint(fix.position!!.latitude, fix.position!!.longitude + 30 / SessionBuilder.METERS_PER_DEGREE_LONGITUDE))
        }

        val top = TrackAssembler.assemble(standing.copy(fixes = glitched)).maxOf { it.speed.kmh }
        assertTrue("top was $top", top < 1.0)
    }

    @Test
    fun aFixWithoutAPositionFallsBackOnTheReceiversSpeed() {
        val fix = FixRecord(timeMs = 1_000, utcMs = 1_000, hasSpeed = true, speed = Speed.ofKmh(12.0), speedAccuracy = Speed(25.0))

        assertEquals(12.0, Speeds.of(listOf(fix), SpeedSource.POSITIONS).single()!!.kmh, 0.001)
    }
}
