package com.hananel.fast.core.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.GnssCapabilities
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build

/**
 * Reads what this device's hardware can actually deliver for speed tracking.
 * Everything here works without a runtime permission, so the report can be
 * shown before the user has granted anything.
 */
object DeviceProbe {

    fun device(context: Context): ReportSection {
        val pm = context.packageManager
        val isWatch = pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
        val metrics = context.resources.displayMetrics
        val shape = if (context.resources.configuration.isScreenRound) "round" else "rectangular"

        val rows = buildList {
            add(ReportRow("Model", "${Build.MANUFACTURER} ${Build.MODEL}"))
            add(ReportRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"))
            if (isWatch) add(ReportRow("Wear OS", wearOsName(Build.VERSION.SDK_INT)))
            add(ReportRow("Display", "${metrics.widthPixels}x${metrics.heightPixels} px, $shape"))
        }
        return ReportSection("Device", rows)
    }

    fun gnss(context: Context): ReportSection {
        val pm = context.packageManager
        val lm = context.getSystemService(LocationManager::class.java)
        val hasChip = pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)

        val rows = buildList {
            add(ReportRow("On-board GNSS", yesNo(hasChip), if (hasChip) RowStatus.GOOD else RowStatus.WARN))
            if (lm == null) return@buildList

            val enabled = lm.isLocationEnabled
            add(ReportRow("Location setting", if (enabled) "on" else "off", if (enabled) RowStatus.GOOD else RowStatus.WARN))
            add(ReportRow("Chipset", lm.gnssHardwareModelName ?: "not reported"))
            add(ReportRow("Hardware year", lm.gnssYearOfHardware.takeIf { it > 0 }?.toString() ?: "not reported"))

            val caps = lm.gnssCapabilities
            val raw = caps.hasMeasurements()
            add(ReportRow("Raw measurements", yesNo(raw), if (raw) RowStatus.GOOD else RowStatus.INFO))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                addAll(signalRows(caps))
            }
        }
        return ReportSection("GNSS", rows)
    }

    fun motionSensors(context: Context): ReportSection {
        val sm = context.getSystemService(SensorManager::class.java)
        val wanted = listOf(
            "Accelerometer" to Sensor.TYPE_ACCELEROMETER,
            "Gyroscope" to Sensor.TYPE_GYROSCOPE,
            "Step detector" to Sensor.TYPE_STEP_DETECTOR,
            "Barometer" to Sensor.TYPE_PRESSURE,
            "Magnetometer" to Sensor.TYPE_MAGNETIC_FIELD,
        )
        val rows = wanted.map { (label, type) ->
            val sensor = sm?.getDefaultSensor(type)
            if (sensor == null) {
                ReportRow(label, "missing", RowStatus.WARN)
            } else {
                ReportRow(label, describe(sensor), RowStatus.GOOD)
            }
        }
        return ReportSection("Motion sensors", rows)
    }

    private fun signalRows(caps: GnssCapabilities): List<ReportRow> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return emptyList()

        val signals = caps.gnssSignalTypes.map {
            GnssSignal(constellationName(it.constellationType), it.carrierFrequencyHz)
        }
        if (signals.isEmpty()) {
            // Reporting signal types is optional for the vendor HAL, so absence proves nothing.
            return listOf(ReportRow("Signals", "not reported by the chipset"))
        }
        val dual = isDualFrequency(signals)
        return listOf(
            ReportRow("Dual-frequency", yesNo(dual), if (dual) RowStatus.GOOD else RowStatus.WARN),
            ReportRow("Signals", summarizeSignals(signals)),
        )
    }

    private fun describe(sensor: Sensor): String {
        // minDelay is the shortest period in microseconds; 0 means the sensor only reports on change.
        val rate = if (sensor.minDelay > 0) "up to ${1_000_000 / sensor.minDelay} Hz" else "on change"
        val fifo = if (sensor.fifoMaxEventCount > 0) ", batch ${sensor.fifoMaxEventCount}" else ""
        return rate + fifo
    }

    private fun constellationName(type: Int): String = when (type) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
        GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
        GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
        GnssStatus.CONSTELLATION_IRNSS -> "NavIC"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        else -> "Other"
    }

    private fun wearOsName(api: Int): String = when (api) {
        33 -> "4"
        34 -> "5"
        35 -> "5.1"
        36 -> "6"
        37 -> "7"
        else -> "unknown (API $api)"
    }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"
}
