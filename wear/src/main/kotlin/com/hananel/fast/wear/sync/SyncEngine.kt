package com.hananel.fast.wear.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.hananel.fast.core.data.sync.SyncProtocol
import com.hananel.fast.wear.data.WearData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Gets every session from the watch to the phone, and knows which ones made it.
 *
 * A session is handed to the Data Layer, which delivers it whenever the phone
 * is reachable — now, tonight, after a reboot. It counts as synced only when
 * the phone answers that it has the session on disk. Until then the watch
 * keeps offering it, and never deletes it.
 */
object SyncEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _pendingCount = MutableStateFlow(0)

    /** How many sessions the phone has not confirmed yet. */
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private val _confirmed = MutableStateFlow<Set<String>>(emptySet())

    /** Sessions the phone confirmed while this process ran: a summary on screen turns to "on the phone" with it. */
    val confirmed: StateFlow<Set<String>> = _confirmed.asStateFlow()

    /** Offers every unconfirmed session again. Cheap when there is nothing to do, so call it freely. */
    fun syncPending(context: Context): Job {
        val appContext = context.applicationContext
        return scope.launch {
            mutex.withLock {
                val store = WearData.store(appContext)
                val client = Wearable.getDataClient(appContext)
                try {
                    collectAcks(client, appContext)
                    for (id in store.unsyncedIds()) {
                        val packed = store.pack(id) ?: continue
                        val request = PutDataMapRequest.create(SyncProtocol.sessionPath(id)).apply {
                            dataMap.putAsset(SyncProtocol.KEY_PAYLOAD, Asset.createFromBytes(packed))
                            dataMap.putLong(SyncProtocol.KEY_WRITTEN_AT, System.currentTimeMillis())
                        }.asPutDataRequest().setUrgent()
                        client.putDataItem(request).await()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // No Play services, or they are busy: the sessions stay on the watch and are offered again later.
                    Log.w(TAG, "Could not offer sessions: ${e.message}")
                }
                _pendingCount.value = store.unsyncedIds().size
            }
        }
    }

    /** The phone has confirmed it holds [id]: the heavy copy in the Data Layer can go. */
    fun onAck(context: Context, id: String): Job {
        val appContext = context.applicationContext
        return scope.launch {
            mutex.withLock {
                val store = WearData.store(appContext)
                store.markSynced(id)
                _confirmed.value = _confirmed.value + id
                _pendingCount.value = store.unsyncedIds().size
                val client = Wearable.getDataClient(appContext)
                try {
                    client.deleteDataItems(wearUri(SyncProtocol.sessionPath(id))).await()
                    client.deleteDataItems(wearUri(SyncProtocol.ackPath(id))).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not clear synced items: ${e.message}")
                }
            }
        }
    }

    /**
     * The phone has lost its copies (reinstalled, data cleared) and asks for
     * everything again. Sending a session it still has is harmless: it keeps one copy.
     */
    fun onResyncRequested(context: Context): Job {
        val appContext = context.applicationContext
        return scope.launch {
            mutex.withLock { WearData.store(appContext).forgetSynced() }
            syncPending(appContext).join()
        }
    }

    /** Picks up confirmations that arrived while the app was not running to hear them. */
    private suspend fun collectAcks(client: DataClient, context: Context) {
        val acks = client.getDataItems(wearUri(SyncProtocol.ACK_PREFIX), DataClient.FILTER_PREFIX).await()
        try {
            val ids = acks.mapNotNull { SyncProtocol.idFrom(it.uri.path.orEmpty(), SyncProtocol.ACK_PREFIX) }
            val store = WearData.store(context)
            for (id in ids) {
                store.markSynced(id)
                _confirmed.value = _confirmed.value + id
                client.deleteDataItems(wearUri(SyncProtocol.sessionPath(id))).await()
                client.deleteDataItems(wearUri(SyncProtocol.ackPath(id))).await()
            }
        } finally {
            acks.release()
        }
    }

    /** A Data Layer address with no host: it matches the item on whichever device wrote it. */
    private fun wearUri(path: String): Uri = Uri.Builder().scheme("wear").path(path).build()

    private const val TAG = "FastSync"
}
