package com.hananel.fast.mobile.devicecheck

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.hananel.fast.core.diagnostics.ReportRow
import com.hananel.fast.core.diagnostics.ReportSection
import com.hananel.fast.core.diagnostics.RowStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** The phone-only parts of the device report. */
object PhoneProbe {

    /** Whether a watch is reachable over the Data Layer right now. */
    suspend fun pairedDevices(context: Context): ReportSection {
        val rows = try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) {
                listOf(ReportRow("Watch", "not connected", RowStatus.WARN))
            } else {
                nodes.map { node ->
                    ReportRow(node.displayName, if (node.isNearby) "connected, nearby" else "connected via cloud", RowStatus.GOOD)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Thrown on phones without the Wear OS companion services.
            listOf(ReportRow("Status", "unavailable (${e.javaClass.simpleName})", RowStatus.WARN))
        }
        return ReportSection("Paired devices", rows)
    }
}
