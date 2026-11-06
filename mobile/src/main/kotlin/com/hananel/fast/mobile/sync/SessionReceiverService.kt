package com.hananel.fast.mobile.sync

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.hananel.fast.core.data.sync.SyncProtocol
import com.hananel.fast.mobile.reference.ReferenceRecorder

/**
 * Woken by Play services whenever the watch offers a session, whether or not
 * the app is open. That is what makes a training recorded without the phone
 * show up by itself once the two are near each other again.
 */
class SessionReceiverService : WearableListenerService() {

    /** The watch has started or stopped measuring; a phone set to measure with it follows. */
    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            SyncProtocol.LINK_START_PATH -> ReferenceRecorder.onWatchStarted(applicationContext, event.sourceNodeId)
            SyncProtocol.LINK_STOP_PATH -> ReferenceRecorder.onWatchStopped(
                applicationContext,
                measured = event.data.firstOrNull() != SyncProtocol.STOP_SEARCH_ONLY,
            )
        }
    }

    // Called on a background thread, and the events are only valid until it returns,
    // so each session is stored and confirmed before moving on.
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            try {
                PhoneSync.receive(applicationContext, event.dataItem)
            } catch (e: Exception) {
                // Left unconfirmed, so the watch offers it again.
                Log.w("FastSync", "Could not receive a session: ${e.message}")
            }
        }
    }
}
