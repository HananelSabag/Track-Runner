package com.hananel.fast.mobile.demo

import android.content.Context
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.core.data.FixRecord
import com.hananel.fast.core.data.HeartRateRecord
import com.hananel.fast.core.data.RawRecord
import com.hananel.fast.core.data.SessionHeader
import com.hananel.fast.core.model.GeoPoint
import com.hananel.fast.core.model.Speed
import com.hananel.fast.mobile.data.PhoneData
import java.util.UUID
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Made-up sessions, for looking at the app before the watch has recorded
 * anything real. Debug builds only; the release build has an empty stand-in.
 *
 * They are written as ordinary session files, marked as demo, and go through
 * exactly the same reading and analysis as real ones — which makes them a test
 * of the whole phone side, not a mock of it.
 */
object DemoSessions {

    const val AVAILABLE = true

    /** Writes a few weeks of training. Does file work: call it off the main thread. */
    fun add(context: Context) {
        val store = PhoneData.store(context)
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L
        val plan = listOf(
            Triple(ActivityKind.FOOTBALL, 1, 11L),
            Triple(ActivityKind.SPRINT_TEST, 2, 21L),
            Triple(ActivityKind.RUN, 3, 31L),
            Triple(ActivityKind.FOOTBALL, 5, 41L),
            Triple(ActivityKind.FOOTBALL, 8, 51L),
            Triple(ActivityKind.RUN, 10, 61L),
            Triple(ActivityKind.SPRINT_TEST, 12, 71L),
            Triple(ActivityKind.FOOTBALL, 15, 81L),
            Triple(ActivityKind.RUN, 19, 91L),
        )
        for ((kind, daysAgo, seed) in plan) {
            val header = SessionHeader(
                id = UUID.randomUUID().toString(),
                kind = kind,
                startedUtcMs = now - daysAgo * day - (seed / 10 % 5) * 37 * 60_000,
                startedElapsedMs = ORIGIN_MS,
                device = "OnePlus Watch 2",
                appVersion = "demo",
                maxBpm = MAX_BPM,
                demo = true,
            )
            val records = when (kind) {
                ActivityKind.FOOTBALL -> football(Random(seed))
                ActivityKind.RUN -> run(Random(seed))
                ActivityKind.SPRINT_TEST -> sprintTest(Random(seed), clean = seed < 50)
            }
            val writer = store.newWriter(header)
            records.forEach(writer::append)
            writer.finish(records.last().timeMs)
        }
        PhoneData.refresh(context)
    }

    fun remove(context: Context) {
        val store = PhoneData.store(context)
        store.summaries().filter { it.demo }.forEach { store.delete(it.id) }
        PhoneData.refresh(context)
    }

    // ---- A football training: drills with sprints, broken by rests on the touchline ----

    private fun football(random: Random): List<RawRecord> {
        val player = Player(random, x = 8.0, y = 20.0)
        // A few minutes of jogging to warm up.
        player.move(seconds = 200 + random.nextInt(60)) { 8.0 + random.nextDouble(2.5) }
        repeat(4 + random.nextInt(2)) {
            var left = 230 + random.nextInt(110)
            while (left > 0) {
                left -= when (random.nextInt(10)) {
                    in 0..3 -> player.move(14 + random.nextInt(22)) { 7.5 + random.nextDouble(3.5) }
                    in 4..5 -> player.move(5 + random.nextInt(8)) { 13.0 + random.nextDouble(4.0) }
                    6 -> player.sprint(peakKmh = 24.5 + random.nextDouble(4.5))
                    in 7..8 -> player.move(9 + random.nextInt(12)) { 4.0 + random.nextDouble(2.0) }
                    else -> player.stand(7 + random.nextInt(9))
                }
            }
            // To the touchline for a drink.
            player.goTo(x = 4.0 + random.nextDouble(6.0), y = 1.0, kmh = 5.5)
            player.stand(75 + random.nextInt(90))
        }
        player.move(60 + random.nextInt(40)) { 5.0 + random.nextDouble(1.5) }
        return player.records
    }

    // ---- A run: laps of a park loop, one stop, a fast finish ----

    private fun run(random: Random): List<RawRecord> {
        val player = Player(random, x = 0.0, y = 0.0)
        val base = 10.8 + random.nextDouble(1.6)
        player.lap(seconds = 150) { 9.2 + it * 1.5 }
        player.lap(seconds = 700 + random.nextInt(300)) { base + sin(it * 14) * 0.7 + random.nextDouble(0.5) }
        player.stand(22 + random.nextInt(12))
        player.lap(seconds = 420 + random.nextInt(200)) { base + 0.3 + sin(it * 9) * 0.6 + random.nextDouble(0.5) }
        player.lap(seconds = 50) { 14.5 + it * 2.5 }
        player.lap(seconds = 70) { 6.0 }
        return player.records
    }

    // ---- A sprint test: one clean, one the scorer should doubt ----

    private fun sprintTest(random: Random, clean: Boolean): List<RawRecord> {
        val player = Player(random, x = 0.0, y = 0.0, speedNoise = if (clean) 0.12 else 0.45, accuracy = if (clean) 0.35 else 1.3)
        val top = 27.2 + random.nextDouble(1.4)
        val profile = listOf(0.0, 0.0, 5.5, 12.5, 18.5, 23.0, top - 1.2, top, top, top - 0.2, top - 0.4, 21.0, 12.0, 5.0, 1.0, 0.0)
        for ((i, kmh) in profile.withIndex()) {
            // The doubtful one has a reading that jumps, as a receiver losing lock does.
            player.straight(if (!clean && i == 6) kmh + 7.5 else kmh)
        }
        return player.records
    }

    /** Someone moving around a pitch, as a 1 Hz GPS receiver and a wrist heart-rate sensor would see them. */
    private class Player(
        private val random: Random,
        private var x: Double,
        private var y: Double,
        private val speedNoise: Double = 0.2,
        private val accuracy: Double = 0.4,
    ) {
        val records = mutableListOf<RawRecord>()
        private var timeMs = ORIGIN_MS
        private var heading = 0.0
        private var heartRate = 98.0
        private var targetX = PITCH_LENGTH / 2
        private var targetY = PITCH_WIDTH / 2
        private var lapAngle = 0.0
        private var driftX = 0.0
        private var driftY = 0.0

        /** Wanders the pitch for [seconds], at whatever speed [kmh] gives each second. Returns the seconds used. */
        fun move(seconds: Int, kmh: () -> Double): Int {
            repeat(seconds) {
                if (hypot(targetX - x, targetY - y) < 4.0 || random.nextInt(25) == 0) newTarget(minDistance = 10.0)
                stepTowardsTarget(kmh())
            }
            return seconds
        }

        fun goTo(x: Double, y: Double, kmh: Double) {
            targetX = x
            targetY = y
            var guard = 0
            while (hypot(targetX - this.x, targetY - this.y) > 3.0 && guard++ < 90) stepTowardsTarget(kmh)
        }

        /** A sprint down the pitch: build up, hold, ease off. Returns the seconds used. */
        fun sprint(peakKmh: Double): Int {
            newTarget(minDistance = 34.0)
            val hold = 2 + random.nextInt(3)
            val profile = listOf(0.55, 0.8, 0.94) + List(hold) { 1.0 } + listOf(0.82, 0.55)
            for (share in profile) stepTowardsTarget(peakKmh * share)
            return profile.size
        }

        fun stand(seconds: Int): Int {
            repeat(seconds) { emit(kmh = 0.0) }
            return seconds
        }

        /** Runs round a park loop; [kmh] is given how far through this stretch the runner is, 0 to 1. */
        fun lap(seconds: Int, kmh: (Double) -> Double) {
            repeat(seconds) { second ->
                val speed = kmh(second.toDouble() / seconds)
                // Advance along an ellipse by the distance covered this second.
                val dx = -LOOP_A * sin(lapAngle)
                val dy = LOOP_B * cos(lapAngle)
                lapAngle += speed / 3.6 / sqrt(dx * dx + dy * dy)
                x = LOOP_A * cos(lapAngle)
                y = LOOP_B * sin(lapAngle)
                heading = atan2(dx, dy)
                emit(speed)
            }
        }

        /** One second in a straight line, for a sprint test. */
        fun straight(kmh: Double) {
            x += kmh / 3.6
            emit(kmh)
        }

        private fun newTarget(minDistance: Double) {
            repeat(12) {
                targetX = 3.0 + random.nextDouble(PITCH_LENGTH - 6.0)
                targetY = 3.0 + random.nextDouble(PITCH_WIDTH - 6.0)
                if (hypot(targetX - x, targetY - y) >= minDistance) return
            }
        }

        private fun stepTowardsTarget(kmh: Double) {
            val distance = hypot(targetX - x, targetY - y)
            val step = kmh / 3.6
            if (distance > 0.5) {
                heading = atan2(targetX - x, targetY - y)
                x += (targetX - x) / distance * minOf(step, distance)
                y += (targetY - y) / distance * minOf(step, distance)
            }
            emit(kmh)
        }

        private fun emit(kmh: Double) {
            timeMs += 1000
            // The heart follows the effort with a lag: quick to rise, slow to settle.
            val effort = (kmh / 28.0 * 1.1 + if (kmh > 3) 0.28 else 0.0).coerceIn(0.0, 1.0)
            val wanted = 96 + (MAX_BPM - 4 - 96) * effort
            heartRate += (wanted - heartRate) / if (wanted > heartRate) 14.0 else 42.0
            records += HeartRateRecord(timeMs, (heartRate + random.nextDouble(-1.0, 1.0)).toInt())

            // Now and then the receiver misses a second.
            if (random.nextInt(140) == 0) return

            // Position error wanders rather than jumping, as real GPS error does.
            driftX = driftX * 0.9 + random.nextDouble(-0.5, 0.5)
            driftY = driftY * 0.9 + random.nextDouble(-0.5, 0.5)
            val measured = (kmh / 3.6 + random.nextDouble(-speedNoise, speedNoise)).coerceAtLeast(0.0)
            records += FixRecord(
                timeMs = timeMs,
                utcMs = 0,
                hasSpeed = true,
                speed = Speed(measured),
                speedAccuracy = Speed(accuracy + random.nextDouble(0.1)),
                position = GeoPoint(
                    latitude = BASE_LATITUDE + (y + driftY) / METERS_PER_DEGREE,
                    longitude = BASE_LONGITUDE + (x + driftX) / (METERS_PER_DEGREE * cos(BASE_LATITUDE * PI / 180)),
                ),
                horizontalAccuracyMeters = 3.0 + random.nextDouble(2.5),
                bearingDegrees = ((heading * 180 / PI + 360) % 360).toFloat(),
            )
        }
    }

    private const val ORIGIN_MS = 1_000_000L
    private const val MAX_BPM = 187

    // Nowhere in particular: a point in the sea, so the demo track cannot be mistaken for a real place.
    private const val BASE_LATITUDE = 32.5
    private const val BASE_LONGITUDE = 34.2
    private const val METERS_PER_DEGREE = 111_320.0
    private const val PITCH_LENGTH = 64.0
    private const val PITCH_WIDTH = 40.0
    private const val LOOP_A = 95.0
    private const val LOOP_B = 48.0
}
