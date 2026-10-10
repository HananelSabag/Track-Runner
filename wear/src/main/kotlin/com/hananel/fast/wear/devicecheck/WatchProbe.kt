package com.hananel.fast.wear.devicecheck

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.getCapabilities
import com.google.android.gms.wearable.Wearable
import com.hananel.fast.core.diagnostics.ReportRow
import com.hananel.fast.core.diagnostics.ReportSection
import com.hananel.fast.core.diagnostics.RowStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** The watch-only parts of the device report. */
object WatchProbe {

    /** What the vendor's exercise pipeline offers for a running session on this watch. */
    suspend fun healthServices(context: Context): ReportSection {
        val rows = try {
            val capabilities = HealthServices.getClient(context).exerciseClient.getCapabilities()
            if (ExerciseType.RUNNING !in capabilities.supportedExerciseTypes) {
                listOf(ReportRow("Running exercise", "not supported", RowStatus.WARN))
            } else {
                val running = capabilities.getExerciseTypeCapabilities(ExerciseType.RUNNING)
                val wanted = listOf(
                    "Speed" to DataType.SPEED,
                    "Speed stats (max/avg)" to DataType.SPEED_STATS,
                    "Pace" to DataType.PACE,
                    "Distance" to DataType.DISTANCE_TOTAL,
                    "Location" to DataType.LOCATION,
                    "Cadence" to DataType.STEPS_PER_MINUTE,
                    "Heart rate" to DataType.HEART_RATE_BPM,
                )
                buildList {
                    add(ReportRow("Running exercise", "supported", RowStatus.GOOD))
                    for ((label, type) in wanted) {
                        val supported = type in running.supportedDataTypes
                        add(ReportRow(label, if (supported) "yes" else "no", if (supported) RowStatus.GOOD else RowStatus.WARN))
                    }
                    add(ReportRow("Auto-pause", if (running.supportsAutoPauseAndResume) "yes" else "no"))
                    add(ReportRow("Data types in total", running.supportedDataTypes.size.toString()))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            listOf(ReportRow("Status", "unavailable (${e.javaClass.simpleName})", RowStatus.WARN))
        }
        return ReportSection("Health Services", rows)
    }

    /** Whether a phone is reachable over the Data Layer right now. */
    suspend fun pairedDevices(context: Context): ReportSection {
        val rows = try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) {
                listOf(ReportRow("Phone", "not connected", RowStatus.WARN))
            } else {
                nodes.map { node ->
                    ReportRow(node.displayName, if (node.isNearby) "connected, nearby" else "connected via cloud", RowStatus.GOOD)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            listOf(ReportRow("Status", "unavailable (${e.javaClass.simpleName})", RowStatus.WARN))
        }
        return ReportSection("Paired devices", rows)
    }
}
