package com.hananel.fast.core.diagnostics

/** A GNSS signal the receiver can track: a constellation on a carrier frequency. */
data class GnssSignal(val constellation: String, val carrierHz: Double) {
    val band: String get() = bandName(carrierHz)
}

/**
 * Lower L-band carriers (L5 / E5a / B2a and neighbours). A receiver that tracks
 * one of these alongside L1 is dual-frequency, which is what resists multipath
 * between buildings and under trees.
 */
private val LOWER_L_BAND_MHZ = 1164.0..1300.0

fun GnssSignal.isLowerBand(): Boolean = carrierHz / 1e6 in LOWER_L_BAND_MHZ

fun bandName(carrierHz: Double): String {
    val mhz = carrierHz / 1e6
    return when {
        mhz in 1574.0..1577.0 -> "L1"
        mhz in 1175.0..1178.0 -> "L5"
        mhz in 1226.0..1229.0 -> "L2"
        mhz in 1559.0..1563.0 -> "B1"
        mhz in 1597.0..1607.0 -> "G1"
        mhz in 1206.0..1209.0 -> "E5b"
        mhz in 1267.0..1280.0 -> "E6"
        else -> "${Math.round(mhz)}MHz"
    }
}

/** "GPS L1+L5, Galileo L1+L5, GLONASS G1" — one entry per constellation, bands sorted. */
fun summarizeSignals(signals: List<GnssSignal>): String =
    signals
        .groupBy { it.constellation }
        .map { (constellation, list) ->
            val bands = list.sortedByDescending { it.carrierHz }.map { it.band }.distinct()
            "$constellation ${bands.joinToString("+")}"
        }
        .joinToString(", ")

fun isDualFrequency(signals: List<GnssSignal>): Boolean =
    signals.any { it.isLowerBand() } && signals.any { !it.isLowerBand() }
