package com.hananel.fast.core.tracking.sprint

import com.hananel.fast.core.model.GeoPoint
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.model.SpeedSample
import kotlin.math.cos

/**
 * Builds the readings of a synthetic sprint, one a second, so a test can state
 * exactly what the receiver "saw": the speeds, how good it claimed the fix was,
 * and whether the positions kept up with the speeds.
 */
class SprintBuilder {

    /** What the receiver reports as its speed error, in m/s. Null: it reports none. */
    var speedAccuracy: Double? = 0.2

    /** What the receiver reports as its position error, in metres. Null: it reports none. */
    var positionAccuracy: Double? = 3.0

    /** Seconds between readings. */
    var intervalSeconds: Int = 1

    /** Null: no positions at all. Otherwise the share of the true distance the positions cover. */
    var groundCovered: Double? = 1.0

    private val samples = mutableListOf<SpeedSample>()
    private var timeMs = 0L
    private var eastMeters = 0.0

    /** One reading per value, in km/h. */
    fun speeds(vararg kmh: Double) = apply { kmh.forEach { add(it) } }

    fun hold(kmh: Double, readings: Int) = apply { repeat(readings) { add(kmh) } }

    fun build(): List<SpeedSample> = samples.toList()

    private fun add(kmh: Double) {
        val speed = Speed.ofKmh(kmh)
        if (samples.isNotEmpty()) {
            timeMs += intervalSeconds * 1000L
            eastMeters += speed.metersPerSecond * intervalSeconds * (groundCovered ?: 0.0)
        }
        samples += SpeedSample(
            timeMs = timeMs,
            speed = speed,
            speedAccuracy = speedAccuracy?.let(::Speed),
            position = groundCovered?.let { GeoPoint(LATITUDE, LONGITUDE + eastMeters / metersPerDegree) },
            horizontalAccuracyMeters = positionAccuracy,
        )
    }

    private companion object {
        const val LATITUDE = 32.08
        const val LONGITUDE = 34.78
        val metersPerDegree = 111_320.0 * cos(Math.toRadians(LATITUDE))
    }
}

fun sprint(build: SprintBuilder.() -> Unit): List<SpeedSample> = SprintBuilder().apply(build).build()

/** A textbook sprint: build up over four seconds, hold 26 km/h for five, ease off. */
fun SprintBuilder.cleanSprint() = speeds(0.0, 8.0, 15.0, 21.0, 25.0).hold(26.0, 5).speeds(20.0, 12.0, 6.0)
