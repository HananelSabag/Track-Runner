package com.hananel.fast.core.model

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * One fused sample of a session, roughly once per second: the output of the
 * speed filter, and the input of every analysis that follows.
 */
data class TrackPoint(
    val timeMs: Long,
    val speed: Speed,
    /** Null while there is no position fix; speed can still be valid. */
    val position: GeoPoint? = null,
    val heartRateBpm: Int? = null,
)

data class GeoPoint(val latitude: Double, val longitude: Double) {

    /**
     * Distance in metres. Uses a flat-earth approximation, which is accurate to
     * well under a centimetre over the size of a football pitch and far cheaper
     * than a great-circle formula for the thousands of calls a session makes.
     */
    fun distanceTo(other: GeoPoint): Double {
        val meanLatitude = Math.toRadians((latitude + other.latitude) / 2)
        val dx = Math.toRadians(other.longitude - longitude) * cos(meanLatitude)
        val dy = Math.toRadians(other.latitude - latitude)
        return EARTH_RADIUS_METERS * sqrt(dx * dx + dy * dy)
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
