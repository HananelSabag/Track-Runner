package com.hananel.fast.wear.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.roundToInt

fun hasHeartRatePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED

/**
 * Heart rate from the watch's own optical sensor.
 *
 * The sensor reports on change: a new value arrives when the reading moves, and
 * a steady heart produces no events at all. So a value stands until the next
 * one replaces it, and the flow emits null when the sensor says it has lost the
 * skin — a number measured off the wrist is not a heart rate.
 *
 * The flow stays empty on a watch without the sensor or without the permission.
 */
fun heartRates(context: Context): Flow<Int?> = callbackFlow {
    val manager = context.getSystemService(SensorManager::class.java)
    val sensor = manager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    if (sensor == null || !hasHeartRatePermission(context)) {
        close()
        awaitClose { }
        return@callbackFlow
    }

    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val bpm = event.values.firstOrNull()?.roundToInt()
            val onSkin = event.accuracy != SensorManager.SENSOR_STATUS_NO_CONTACT
            trySend(bpm?.takeIf { onSkin && it in PLAUSIBLE_BPM })
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    awaitClose { manager.unregisterListener(listener) }
}

/** Outside this range the sensor is reporting "no reading" or noise, not a heart. */
private val PLAUSIBLE_BPM = 30..230
