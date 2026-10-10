package com.hananel.fast.wear.sync

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.hananel.fast.core.data.sync.SyncProtocol

/**
 * Hears what the phone writes to the Data Layer, even when the watch app is
 * not running: a confirmation that a session is stored, or a request to send
 * everything again.
 */
class PhoneListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val path = event.dataItem.uri.path.orEmpty()
            val ackedId = SyncProtocol.idFrom(path, SyncProtocol.ACK_PREFIX)
            when {
                ackedId != null -> SyncEngine.onAck(applicationContext, ackedId)
                path == SyncProtocol.RESYNC_PATH -> SyncEngine.onResyncRequested(applicationContext)
            }
        }
    }
}
