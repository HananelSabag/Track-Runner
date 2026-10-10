package com.hananel.fast.wear.sprint

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import com.hananel.fast.core.model.GeoPoint
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.model.SpeedSample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** One fix from the receiver, kept whole so it can be logged exactly as it arrived. */
data class GnssFix(
    val sample: SpeedSample,
    /** False when the receiver sent a position without a speed; such a fix is logged but not analysed. */
    val hasSpeed: Boolean,
    val utcMs: Long,
    val bearingDegrees: Float?,
)

/**
 * Speed straight from the watch's own GNSS receiver, once a second.
 *
 * The GPS provider is asked for by name on purpose. The fused provider on a
 * watch may quietly hand back the paired phone's location to save power, and a
 * sprint timed on the wrist must not be measured by a phone left in a bag.
 *
 * The caller must hold the fine-location permission and collect only while the
 * app is in the foreground.
 */
@SuppressLint("MissingPermission")
fun gnssFixes(context: Context): Flow<GnssFix> = callbackFlow {
    val manager = context.getSystemService(LocationManager::class.java)
    val listener = LocationListener { location -> trySend(location.toFix()) }
    val request = LocationRequest.Builder(INTERVAL_MS)
        .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
        // Take every fix the receiver produces, however close together.
        .setMinUpdateIntervalMillis(0)
        .build()

    manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, request, context.mainExecutor, listener)
    awaitClose { manager.removeUpdates(listener) }
}

private fun Location.toFix() = GnssFix(
    sample = SpeedSample(
        // The boot clock, not the wall clock: it never jumps, so intervals between fixes are exact.
        timeMs = elapsedRealtimeNanos / 1_000_000,
        speed = Speed(if (hasSpeed()) speed.toDouble() else 0.0),
        speedAccuracy = if (hasSpeedAccuracy()) Speed(speedAccuracyMetersPerSecond.toDouble()) else null,
        position = GeoPoint(latitude, longitude),
        horizontalAccuracyMeters = if (hasAccuracy()) accuracy.toDouble() else null,
    ),
    hasSpeed = hasSpeed(),
    utcMs = time,
    bearingDegrees = if (hasBearing()) bearing else null,
)

private const val INTERVAL_MS = 1_000L
