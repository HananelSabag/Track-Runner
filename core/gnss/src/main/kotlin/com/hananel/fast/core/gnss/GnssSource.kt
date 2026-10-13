package com.hananel.fast.core.gnss

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import com.hananel.fast.core.data.FixRecord
import com.hananel.fast.core.model.GeoPoint
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.model.SpeedSample
import com.hananel.fast.core.tracking.sprint.GnssAcquisitionCheck
import com.hananel.fast.core.tracking.sprint.SatelliteView
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
 * Fixes straight from this device's own GNSS receiver.
 *
 * The same code reads the watch and the phone, so that when the two are compared
 * any difference is the receivers' and not ours.
 *
 * The GPS provider is asked for by name on purpose. The fused provider on a
 * watch may quietly hand back the paired phone's location to save power, and a
 * sprint timed on the wrist must not be measured by a phone left in a bag.
 *
 * The caller must hold the fine-location permission and collect only while the
 * app is in the foreground, or from a foreground service.
 *
 * @param intervalMs how often a fix is asked for. A receiver delivers no faster than
 *   it is able to, so asking for less than a second is how to find out what it can do.
 */
@SuppressLint("MissingPermission")
fun gnssFixes(context: Context, intervalMs: Long = ONE_SECOND_MS): Flow<GnssFix> = callbackFlow {
    val manager = context.getSystemService(LocationManager::class.java)
    val listener = LocationListener { location -> trySend(location.toFix()) }
    val request = LocationRequest.Builder(intervalMs)
        .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
        // Take every fix the receiver produces, however close together.
        .setMinUpdateIntervalMillis(0)
        .build()

    manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, request, context.mainExecutor, listener)
    awaitClose { manager.removeUpdates(listener) }
}

/**
 * What the receiver can hear, roughly once a second, from the moment it is
 * switched on. This is the only feedback there is before the first fix: it
 * tells "still indoors, nothing heard" apart from "satellites found, locking".
 *
 * It reports only while something else keeps the receiver on, so collect it
 * alongside [gnssFixes]. Some receivers never send it; the flow then stays empty.
 */
@SuppressLint("MissingPermission")
fun satelliteViews(context: Context): Flow<SatelliteView> = callbackFlow {
    val manager = context.getSystemService(LocationManager::class.java)
    val callback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var visible = 0
            var strong = 0
            var used = 0
            for (i in 0 until status.satelliteCount) {
                val signal = status.getCn0DbHz(i)
                if (signal > 0f) visible++
                if (signal >= GnssAcquisitionCheck.STRONG_SIGNAL_DBHZ) strong++
                if (status.usedInFix(i)) used++
            }
            trySend(SatelliteView(visible, strong, used))
        }
    }

    manager.registerGnssStatusCallback(context.mainExecutor, callback)
    awaitClose { manager.unregisterGnssStatusCallback(callback) }
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

/** The fix as a line of a recording: every field as the receiver gave it. */
fun GnssFix.toRecord() = FixRecord(
    timeMs = sample.timeMs,
    utcMs = utcMs,
    hasSpeed = hasSpeed,
    speed = sample.speed,
    speedAccuracy = sample.speedAccuracy,
    position = sample.position,
    horizontalAccuracyMeters = sample.horizontalAccuracyMeters,
    bearingDegrees = bearingDegrees,
)

private const val ONE_SECOND_MS = 1_000L
