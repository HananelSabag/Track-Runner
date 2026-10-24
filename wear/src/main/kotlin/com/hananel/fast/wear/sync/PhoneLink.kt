package com.hananel.fast.wear.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.hananel.fast.core.data.sync.SyncProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Tells the phone when the watch starts and stops measuring, so a phone set to
 * "measure together with the watch" can do the same (D43).
 *
 * Messages, not data items: they matter only now, and only to a phone within
 * reach. One that is out of reach is not alongside the player either, and has
 * nothing to measure. The watch does not wait for an answer and does not care
 * whether the phone acts on it.
 */
object PhoneLink {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The GPS search has begun — a training or a speed test is being prepared. */
    fun measuringStarted(context: Context) = send(context, SyncProtocol.LINK_START_PATH)

    /** The watch has stopped measuring: the session ended, or the screen was left. */
    fun measuringStopped(context: Context) = send(context, SyncProtocol.LINK_STOP_PATH)

    private fun send(context: Context, path: String) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val phones = Wearable.getNodeClient(appContext).connectedNodes.await().filter { it.isNearby }
                for (phone in phones) Wearable.getMessageClient(appContext).sendMessage(phone.id, path, ByteArray(0)).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("FastLink", "Could not tell the phone: ${e.message}")
            }
        }
    }
}
