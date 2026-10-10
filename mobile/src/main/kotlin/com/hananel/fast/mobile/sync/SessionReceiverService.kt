package com.hananel.fast.mobile.sync

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

/**
 * Woken by Play services whenever the watch offers a session, whether or not
 * the app is open. That is what makes a training recorded without the phone
 * show up by itself once the two are near each other again.
 */
class SessionReceiverService : WearableListenerService() {

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
