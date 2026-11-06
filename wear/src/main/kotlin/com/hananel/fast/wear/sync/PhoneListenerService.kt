package com.hananel.fast.wear.sync

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.hananel.fast.core.data.sync.SyncProtocol
import kotlinx.coroutines.runBlocking

/**
 * Hears what the phone writes to the Data Layer, even when the watch app is
 * not running: a confirmation that a session is stored, or a request to send
 * everything again.
 */
class PhoneListenerService : WearableListenerService() {

    // Already on a background thread. The work is done here, before returning: once this
    // returns, Play services may let the process go, and a resync request is not delivered twice.
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val path = event.dataItem.uri.path.orEmpty()
            val ackedId = SyncProtocol.idFrom(path, SyncProtocol.ACK_PREFIX)
            runBlocking {
                when {
                    ackedId != null -> SyncEngine.onAck(applicationContext, ackedId).join()
                    path == SyncProtocol.RESYNC_PATH -> SyncEngine.onResyncRequested(applicationContext).join()
                }
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            // Already in a run or a test: the phone only needs to hear the start it missed.
            SyncProtocol.LINK_INVITE_PATH -> if (PhoneLink.isActive) {
                PhoneLink.measuringStarted(applicationContext)
            } else {
                JointRunInvite.show(applicationContext)
            }
            else -> PhoneLink.onPhoneAnswered(event.path)
        }
    }
}
