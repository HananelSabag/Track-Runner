package com.hananel.fast.wear.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.hananel.fast.core.data.sync.SyncProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean

/** What the phone said about the measurement the watch just started. */
enum class PhoneFollow {
    /** No answer: the phone is out of reach, or "measure together" is off there. Nothing is shown. */
    UNKNOWN,

    /** The phone is measuring alongside the watch. */
    FOLLOWING,

    /** "Measure together" is on, but the phone could not join: its app has to be opened. */
    NOT_FOLLOWING,
}

/**
 * Tells the phone when the watch starts and stops measuring, so a phone set to
 * "measure together with the watch" does the same (D43) — and hears back
 * whether it did, so the watch can say so instead of leaving it to luck.
 *
 * Messages, not data items: they matter only now, and only to a phone within
 * reach. They go out one at a time, in the order they were sent, so a stop
 * can never overtake the start before it.
 */
object PhoneLink {

    /** One message at a time, in order. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val _phone = MutableStateFlow(PhoneFollow.UNKNOWN)

    /** The phone's answer to the latest start; back to UNKNOWN when the watch stops measuring. */
    val phone: StateFlow<PhoneFollow> = _phone.asStateFlow()

    /** A start went out and its stop has not. */
    private val active = AtomicBoolean(false)

    /** Since that start, a training was started or a speed test counted down. */
    private val began = AtomicBoolean(false)

    /** True while the watch is preparing or doing a measurement the phone has been told about. */
    val isActive: Boolean get() = active.get()

    /**
     * The GPS search has begun — a training or a speed test is being prepared. Also sent again
     * for one already under way (its screen reopened, or the phone's invitation arrived during it):
     * the phone hears the start once more, and what the watch knows about this one is kept.
     */
    fun measuringStarted(context: Context): Job {
        if (!active.getAndSet(true)) {
            began.set(false)
            _phone.value = PhoneFollow.UNKNOWN
        }
        // A run opened from the menu instead of from the phone's invitation answers it all the same.
        JointRunInvite.dismiss(context)
        return send(context, SyncProtocol.LINK_START_PATH)
    }

    /** Start was pressed, or a speed test began its countdown: from here the watch is measuring. */
    fun measuringBegan() {
        began.set(true)
    }

    /**
     * The watch has stopped: the session ended, or the screen was left before Start. The phone
     * hears which, so a joint run started there keeps measuring after a search that was only
     * left (D47). Said once per start: the end of a session and its service both report it.
     */
    fun measuringStopped(context: Context): Job? {
        if (!active.getAndSet(false)) return null
        _phone.value = PhoneFollow.UNKNOWN
        val measured = if (began.getAndSet(false)) SyncProtocol.STOP_MEASURED else SyncProtocol.STOP_SEARCH_ONLY
        return send(context, SyncProtocol.LINK_STOP_PATH, byteArrayOf(measured))
    }

    /** The phone's answer arrived ([PhoneListenerService]). */
    fun onPhoneAnswered(path: String) {
        _phone.value = when (path) {
            SyncProtocol.LINK_FOLLOWING_PATH -> PhoneFollow.FOLLOWING
            SyncProtocol.LINK_NOT_FOLLOWING_PATH -> PhoneFollow.NOT_FOLLOWING
            else -> return
        }
    }

    private fun send(context: Context, path: String, payload: ByteArray = ByteArray(0)): Job {
        val appContext = context.applicationContext
        return scope.launch {
            try {
                val phones = Wearable.getNodeClient(appContext).connectedNodes.await().filter { it.isNearby }
                for (phone in phones) Wearable.getMessageClient(appContext).sendMessage(phone.id, path, payload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("FastLink", "Could not tell the phone: ${e.message}")
            }
        }
    }
}
