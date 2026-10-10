package com.hananel.fast.wear.devicecheck

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseTypeCapabilities
import androidx.health.services.client.getCapabilities
import com.google.android.gms.wearable.Wearable
import com.hananel.fast.core.diagnostics.DeviceProbe
import com.hananel.fast.core.diagnostics.ReportRow
import com.hananel.fast.core.diagnostics.ReportSection
import com.hananel.fast.core.diagnostics.RowStatus
import com.hananel.fast.wear.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Builds the watch's device report: first a plain-language verdict on whether
 * this watch can do what FAST needs, then the technical detail behind it.
 */
object WatchProbe {

    suspend fun report(context: Context): List<ReportSection> {
        val running = runningCapabilities(context)
        val phones = connectedPhones(context)
        return listOf(
            compatibility(context, running, phones),
            DeviceProbe.device(context),
            DeviceProbe.gnss(context),
            healthServices(running),
            DeviceProbe.motionSensors(context),
            pairedDevices(phones),
            DeviceProbe.allSensors(context),
        )
    }

    /** What the vendor's exercise pipeline offers for running, or why that could not be read. */
    private sealed interface Running {
        data class Supported(val capabilities: ExerciseTypeCapabilities) : Running
        data object NotSupported : Running
        data class Unavailable(val error: String) : Running
    }

    private suspend fun runningCapabilities(context: Context): Running = try {
        val capabilities = HealthServices.getClient(context).exerciseClient.getCapabilities()
        if (ExerciseType.RUNNING in capabilities.supportedExerciseTypes) {
            Running.Supported(capabilities.getExerciseTypeCapabilities(ExerciseType.RUNNING))
        } else {
            Running.NotSupported
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Running.Unavailable(e.javaClass.simpleName)
    }

    /** Names of the phones reachable over the Data Layer right now. Null when that could not be asked. */
    private suspend fun connectedPhones(context: Context): List<String>? = try {
        Wearable.getNodeClient(context).connectedNodes.await().map { it.displayName }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /**
     * The verdict, in the user's language: each thing FAST relies on, whether it
     * is there, and what to do when it is not.
     */
    private fun compatibility(context: Context, running: Running, phones: List<String>?): ReportSection {
        fun text(id: Int, vararg args: Any) = context.getString(id, *args)
        fun row(label: Int, ok: Boolean, good: String, bad: String, badStatus: RowStatus = RowStatus.WARN) =
            ReportRow(text(label), if (ok) good else bad, if (ok) RowStatus.GOOD else badStatus)

        val hasGnss = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
        val locationOn = context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true
        val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val dataTypes = (running as? Running.Supported)?.capabilities?.supportedDataTypes.orEmpty()
        val hasSpeedData = DataType.SPEED in dataTypes && DataType.DISTANCE_TOTAL in dataTypes
        val hasHeartRate = DataType.HEART_RATE_BPM in dataTypes
        val fastMotion = DeviceProbe.motionSensorsReach(context, hz = 100)
        val phone = phones?.firstOrNull()

        // The sprint test needs only the watch's own GPS; everything else adds to it.
        val compatible = hasGnss

        val rows = listOf(
            ReportRow(
                text(R.string.compat_verdict),
                text(if (compatible) R.string.compat_verdict_yes else R.string.compat_verdict_no),
                if (compatible) RowStatus.GOOD else RowStatus.WARN,
            ),
            row(R.string.compat_gps, hasGnss, text(R.string.compat_present), text(R.string.compat_absent)),
            row(R.string.compat_location, locationOn, text(R.string.compat_on), text(R.string.compat_location_off)),
            // Not yet granted is the normal state before the first test, not a fault.
            row(R.string.compat_permission, locationGranted, text(R.string.compat_granted), text(R.string.compat_permission_pending), RowStatus.INFO),
            row(R.string.compat_speed_data, hasSpeedData, text(R.string.compat_available), text(R.string.compat_unavailable)),
            row(R.string.compat_heart_rate, hasHeartRate, text(R.string.compat_present), text(R.string.compat_absent)),
            row(R.string.compat_motion, fastMotion, text(R.string.compat_motion_fast), text(R.string.compat_motion_slow), RowStatus.INFO),
            row(R.string.compat_phone, phone != null, text(R.string.compat_phone_connected, phone.orEmpty()), text(R.string.compat_phone_missing), RowStatus.INFO),
        )
        return ReportSection(text(R.string.compat_title), rows, technical = false)
    }

    private fun healthServices(running: Running): ReportSection {
        val rows = when (running) {
            is Running.Unavailable -> listOf(ReportRow("Status", "unavailable (${running.error})", RowStatus.WARN))
            Running.NotSupported -> listOf(ReportRow("Running exercise", "not supported", RowStatus.WARN))
            is Running.Supported -> {
                val wanted = listOf(
                    "Speed" to DataType.SPEED,
                    "Speed stats (max/avg)" to DataType.SPEED_STATS,
                    "Pace" to DataType.PACE,
                    "Distance" to DataType.DISTANCE_TOTAL,
                    "Location" to DataType.LOCATION,
                    "Cadence" to DataType.STEPS_PER_MINUTE,
                    "Heart rate" to DataType.HEART_RATE_BPM,
                )
                val supported = running.capabilities.supportedDataTypes
                buildList {
                    add(ReportRow("Running exercise", "supported", RowStatus.GOOD))
                    for ((label, type) in wanted) {
                        val has = type in supported
                        add(ReportRow(label, if (has) "yes" else "no", if (has) RowStatus.GOOD else RowStatus.WARN))
                    }
                    add(ReportRow("Auto-pause", if (running.capabilities.supportsAutoPauseAndResume) "yes" else "no"))
                    add(ReportRow("Data types in total", supported.size.toString()))
                }
            }
        }
        return ReportSection("Health Services", rows)
    }

    private fun pairedDevices(phones: List<String>?): ReportSection {
        val rows = when {
            phones == null -> listOf(ReportRow("Status", "unavailable", RowStatus.WARN))
            phones.isEmpty() -> listOf(ReportRow("Phone", "not connected", RowStatus.WARN))
            else -> phones.map { ReportRow(it, "connected", RowStatus.GOOD) }
        }
        return ReportSection("Paired devices", rows)
    }
}
