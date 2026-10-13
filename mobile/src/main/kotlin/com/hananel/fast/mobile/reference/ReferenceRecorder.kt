package com.hananel.fast.mobile.reference

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.core.data.RawRecord
import com.hananel.fast.core.data.SatelliteRecord
import com.hananel.fast.core.data.SessionHeader
import com.hananel.fast.core.data.SessionStore
import com.hananel.fast.core.data.SessionWriter
import com.hananel.fast.core.gnss.GnssFix
import com.hananel.fast.core.gnss.gnssFixes
import com.hananel.fast.core.gnss.satelliteViews
import com.hananel.fast.core.gnss.toRecord
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.tracking.sprint.SatelliteView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

sealed interface ReferenceState {
    /** [savedCount] is how many measurements are stored on the phone so far. */
    data class Idle(val savedCount: Int, val last: Finished? = null) : ReferenceState

    data class Recording(
        val elapsedMs: Long,
        val fixes: Int,
        /** Fixes per second over the last few seconds: what the receiver really delivers. */
        val fixesPerSecond: Double,
        val speed: Speed?,
        /** The receiver's own estimate of its speed error, when it gives one. */
        val speedAccuracy: Speed?,
        val positionAccuracyMeters: Double?,
        val satellites: SatelliteView?,
    ) : ReferenceState

    data class Finished(val durationMs: Long, val fixes: Int, val fixesPerSecond: Double)
}

/**
 * Records the phone's own GPS, so that one run can be measured by the watch and
 * the phone at once and the two compared.
 *
 * It is a measuring instrument, not a feature: its recordings are kept apart
 * from the history, are never synced, and are read on the computer. It asks the
 * receiver for fixes far more often than once a second, because how fast this
 * receiver can actually deliver them is one of the things being measured.
 *
 * Everything runs on the main thread except the file, which has its own.
 */
object ReferenceRecorder {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val disk = Dispatchers.IO.limitedParallelism(1)

    private val _state = MutableStateFlow<ReferenceState>(ReferenceState.Idle(savedCount = 0))
    val state: StateFlow<ReferenceState> = _state.asStateFlow()

    /** Called when a recording has been saved, so whoever keeps it alive can let go. */
    var onStopped: (() -> Unit)? = null

    private var listening: Job? = null
    private var writing: Job? = null
    private var writer: SessionWriter? = null
    private var records: Channel<RawRecord>? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var startedElapsedMs = 0L
    private var fixCount = 0
    private val recentFixTimes = ArrayDeque<Long>()
    private var latest: GnssFix? = null
    private var satellites: SatelliteView? = null

    val isRecording: Boolean get() = listening != null

    fun store(context: Context): SessionStore = SessionStore(File(context.applicationContext.filesDir, DIRECTORY))

    /** Shows how many measurements are already saved. Reads the folder: call it off the main thread. */
    fun refresh(context: Context) {
        if (isRecording) return
        val saved = store(context).ids().size
        _state.value = ReferenceState.Idle(saved, (_state.value as? ReferenceState.Idle)?.last)
    }

    fun start(context: Context) {
        if (isRecording) return
        val appContext = context.applicationContext
        startedElapsedMs = SystemClock.elapsedRealtime()
        val header = SessionHeader(
            id = UUID.randomUUID().toString(),
            kind = ActivityKind.RUN,
            startedUtcMs = System.currentTimeMillis(),
            startedElapsedMs = startedElapsedMs,
            device = Build.MODEL.orEmpty(),
            appVersion = appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty(),
        )
        val newWriter = try {
            store(appContext).newWriter(header)
        } catch (e: IOException) {
            Log.w(TAG, "Could not start a reference recording", e)
            return
        }
        val channel = Channel<RawRecord>(Channel.UNLIMITED)
        writer = newWriter
        records = channel
        writing = scope.launch(disk) {
            try {
                for (record in channel) newWriter.append(record)
            } catch (e: IOException) {
                Log.w(TAG, "Reference recording stopped reaching the disk", e)
            }
        }
        fixCount = 0
        recentFixTimes.clear()
        latest = null
        satellites = null
        // The phone will be in a pocket with its screen off; the fixes must keep their rhythm.
        wakeLock = appContext.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fast:reference").apply { acquire(WAKE_LOCK_LIMIT_MS) }
        publish()

        listening = scope.launch {
            launch {
                // The clock on screen has to move even between fixes.
                while (true) {
                    delay(1_000)
                    publish()
                }
            }
            launch {
                satelliteViews(appContext).collect {
                    satellites = it
                    channel.trySend(SatelliteRecord(SystemClock.elapsedRealtime(), it))
                }
            }
            gnssFixes(appContext, intervalMs = FASTEST_INTERVAL_MS).collect { fix ->
                fixCount++
                latest = fix
                recentFixTimes.addLast(fix.sample.timeMs)
                while (recentFixTimes.size > RATE_WINDOW_FIXES) recentFixTimes.removeFirst()
                channel.trySend(fix.toRecord())
                publish()
            }
        }
    }

    fun stop(context: Context) {
        if (!isRecording) return
        val appContext = context.applicationContext
        val endMs = SystemClock.elapsedRealtime()
        val count = fixCount
        listening?.cancel()
        listening = null
        val finishing = writer
        scope.launch {
            records?.close()
            writing?.join()
            val saved = withContext(disk) {
                try {
                    // A recording without a single fix has nothing to compare; it is not kept.
                    if (count > 0) finishing?.finish(endMs) else finishing?.discard()
                } catch (e: IOException) {
                    Log.w(TAG, "Could not finish the reference recording", e)
                }
                store(appContext).ids().size
            }
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
            val durationMs = endMs - startedElapsedMs
            val finished = ReferenceState.Finished(durationMs, count, if (durationMs > 0) count / (durationMs / 1000.0) else 0.0)
            _state.value = ReferenceState.Idle(saved, finished.takeIf { count > 0 })
            onStopped?.invoke()
        }
    }

    private fun publish() {
        val fix = latest
        _state.value = ReferenceState.Recording(
            elapsedMs = SystemClock.elapsedRealtime() - startedElapsedMs,
            fixes = fixCount,
            fixesPerSecond = recentRate(),
            speed = fix?.sample?.speed?.takeIf { fix.hasSpeed },
            speedAccuracy = fix?.sample?.speedAccuracy,
            positionAccuracyMeters = fix?.sample?.horizontalAccuracyMeters,
            satellites = satellites,
        )
    }

    /** How fast fixes have been arriving lately, from the spacing of the last few. */
    private fun recentRate(): Double {
        if (recentFixTimes.size < 2) return 0.0
        val spanMs = recentFixTimes.last() - recentFixTimes.first()
        return if (spanMs > 0) (recentFixTimes.size - 1) / (spanMs / 1000.0) else 0.0
    }

    private const val TAG = "FastReference"
    private const val DIRECTORY = "reference"

    /** Far faster than any phone receiver delivers; it answers with whatever its own best is. */
    private const val FASTEST_INTERVAL_MS = 100L
    private const val RATE_WINDOW_FIXES = 12
    private const val WAKE_LOCK_LIMIT_MS = 3 * 60 * 60_000L
}
