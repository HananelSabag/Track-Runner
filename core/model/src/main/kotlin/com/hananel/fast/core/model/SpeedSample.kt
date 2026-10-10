package com.hananel.fast.core.model

/**
 * One raw speed reading exactly as a sensor delivered it, before any filtering,
 * together with the sensor's own statement of how far to trust it.
 */
data class SpeedSample(
    /** Milliseconds on the device's monotonic clock, so samples order correctly even if the wall clock jumps. */
    val timeMs: Long,
    val speed: Speed,
    /** The sensor's one-sigma estimate of the speed error. Null when the receiver does not report one. */
    val speedAccuracy: Speed? = null,
    val position: GeoPoint? = null,
    /** The sensor's one-sigma estimate of the position error. */
    val horizontalAccuracyMeters: Double? = null,
)
