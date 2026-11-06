package com.hananel.fast.wear

import android.app.Application
import com.hananel.fast.wear.data.WearData
import com.hananel.fast.wear.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs once for every start of the app's process — the screen opened, the phone's
 * answer waking the listener, a service started.
 *
 * A recording lives in its process, so a new process cannot have one in progress:
 * any unfinished file is one a killed process left behind. It is adopted and offered
 * to the phone here, without waiting for someone to open the app (W1).
 */
class FastWearApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            WearData.store(this@FastWearApplication).recoverOrphans()
            SyncEngine.syncPending(this@FastWearApplication)
        }
    }
}
