package com.hananel.fast.core.tracking

import com.hananel.fast.core.model.GeoPoint
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.model.TrackPoint
import kotlin.math.cos

/**
 * Builds a synthetic session one second at a time, so a test reads like the
 * training it describes: `stand(90); run(12.0, 120); stand(150)`.
 */
class TrackBuilder(private val withPositions: Boolean = true) {

    private val points = mutableListOf<TrackPoint>()
    private var timeMs = 0L
    private var eastMeters = 0.0
    private var heartRate: Int? = null

    fun heartRate(bpm: Int?) = apply { heartRate = bpm }

    /** Stays put for [seconds]. */
    fun stand(seconds: Int) = move(kmh = 0.0, seconds = seconds)

    /** Moves east at a steady [kmh] for [seconds]. */
    fun run(kmh: Double, seconds: Int) = move(kmh, seconds)

    /** Runs back and forth over [lengthMeters], turning at each end, for [seconds]. */
    fun shuttle(kmh: Double, lengthMeters: Double, seconds: Int) = apply {
        val origin = eastMeters
        var direction = 1
        repeat(seconds) {
            val step = Speed.ofKmh(kmh).metersPerSecond
            if (eastMeters + direction * step > origin + lengthMeters || eastMeters + direction * step < origin) direction = -direction
            eastMeters += direction * step
            add(Speed.ofKmh(kmh))
        }
    }

    /** One second at an exact speed, for hand-built profiles such as a sprint. */
    fun second(kmh: Double) = move(kmh, seconds = 1)

    /** Skips ahead without samples, as when the signal is lost. */
    fun gap(seconds: Int) = apply { timeMs += seconds * 1000L }

    fun build(): List<TrackPoint> = points.toList()

    private fun move(kmh: Double, seconds: Int) = apply {
        repeat(seconds) {
            eastMeters += Speed.ofKmh(kmh).metersPerSecond
            add(Speed.ofKmh(kmh))
        }
    }

    private fun add(speed: Speed) {
        // The very first sample sits at time zero; every later one a second after the last.
        if (points.isNotEmpty()) timeMs += 1000
        val position = if (withPositions) GeoPoint(BASE_LATITUDE, BASE_LONGITUDE + eastMeters / metersPerDegreeLongitude) else null
        points += TrackPoint(timeMs, speed, position, heartRate)
    }

    private companion object {
        const val BASE_LATITUDE = 32.08
        const val BASE_LONGITUDE = 34.78
        val metersPerDegreeLongitude = 111_320.0 * cos(Math.toRadians(BASE_LATITUDE))
    }
}

fun track(withPositions: Boolean = true, build: TrackBuilder.() -> Unit): List<TrackPoint> =
    TrackBuilder(withPositions).apply(build).build()
