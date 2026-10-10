package com.hananel.fast.mobile.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.hananel.fast.core.data.sync.SyncProtocol
import com.hananel.fast.mobile.data.PhoneData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** What is known about the watch right now. */
data class WatchStatus(
    /** The name of a watch within reach, or null when none is. */
    val connectedName: String?,
    /** False until the first check has finished, so the screen can say nothing rather than "not connected". */
    val checked: Boolean,
)

/**
 * The phone's half of the sync: take each session the watch offers, store it,
 * and confirm — in that order. The confirmation is what lets the watch stop
 * holding the session as unsent, so it is written only after the file is safely
 * on disk, and written again whenever the same session is offered again.
 *
 * Every call here blocks on Play services and must run off the main thread.
 */
object PhoneSync {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _watch = MutableStateFlow(WatchStatus(connectedName = null, checked = false))
    val watch: StateFlow<WatchStatus> = _watch.asStateFlow()

    /**
     * Run when the app opens: store whatever the watch offered while nobody was
     * listening, ask for the whole history on a fresh install, and see whether a
     * watch is within reach.
     */
    fun onAppOpened(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val client = Wearable.getDataClient(appContext)
                val offered = Tasks.await(
                    client.getDataItems(wearUri(SyncProtocol.SESSION_PREFIX), DataClient.FILTER_PREFIX),
                    TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                )
                try {
                    offered.forEach { receive(appContext, it) }
                } finally {
                    offered.release()
                }

                val settings = PhoneData.settings(appContext)
                if (!settings.askedWatchForHistory) {
                    requestEverything(appContext)
                    settings.askedWatchForHistory = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not check for sessions: ${e.message}")
            }
            refreshWatchStatus(appContext)
        }
    }

    /** Asks the watch to offer every session again, whether or not it was confirmed before. */
    fun requestEverythingAgain(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                requestEverything(appContext)
            } catch (e: Exception) {
                Log.w(TAG, "Could not ask the watch for its sessions: ${e.message}")
            }
        }
    }

    /**
     * Stores one offered session and confirms it. Safe to call for a session
     * already held: nothing is stored twice, and the confirmation is repeated.
     */
    fun receive(context: Context, item: DataItem) {
        val id = SyncProtocol.idFrom(item.uri.path.orEmpty(), SyncProtocol.SESSION_PREFIX) ?: return
        val store = PhoneData.store(context)
        val client = Wearable.getDataClient(context)

        if (!store.contains(id)) {
            val asset = DataMapItem.fromDataItem(item).dataMap.getAsset(SyncProtocol.KEY_PAYLOAD) ?: return
            val packed = Tasks.await(client.getFdForAsset(asset), TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .inputStream.use { it.readBytes() }
            // Not confirmed unless it is really on disk under the id the watch knows it by.
            if (store.import(packed)?.id != id) {
                Log.w(TAG, "An offered session could not be stored")
                return
            }
            PhoneData.refresh(context)
        }

        val ack = PutDataMapRequest.create(SyncProtocol.ackPath(id)).apply {
            dataMap.putLong(SyncProtocol.KEY_WRITTEN_AT, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Tasks.await(client.putDataItem(ack), TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    fun refreshWatchStatus(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val name = try {
                Tasks.await(Wearable.getNodeClient(appContext).connectedNodes, TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .firstOrNull { it.isNearby }?.displayName
            } catch (e: Exception) {
                null
            }
            _watch.value = WatchStatus(connectedName = name, checked = true)
        }
    }

    private fun requestEverything(context: Context) {
        val request = PutDataMapRequest.create(SyncProtocol.RESYNC_PATH).apply {
            dataMap.putLong(SyncProtocol.KEY_WRITTEN_AT, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Tasks.await(Wearable.getDataClient(context).putDataItem(request), TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    /** A Data Layer address with no host: it matches the item on whichever device wrote it. */
    private fun wearUri(path: String): Uri = Uri.Builder().scheme("wear").path(path).build()

    private const val TAG = "FastSync"
    private const val TIMEOUT_SECONDS = 30L
}
