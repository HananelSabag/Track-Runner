package com.hananel.fast.mobile.data

import android.content.Context
import android.content.SharedPreferences
import com.hananel.fast.core.data.AnalysisContext
import com.hananel.fast.core.data.SessionInsight
import com.hananel.fast.core.data.SessionInsights
import com.hananel.fast.core.data.SessionLibrary
import com.hananel.fast.core.data.SessionStore
import com.hananel.fast.core.data.SessionSummary
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.tracking.HeartRateZones
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Everything the history shows, read in one go so the list and the personal bests always agree. */
class Library(
    /** Newest first. */
    val summaries: List<SessionSummary>,
    /** The top speed these sessions show, which sets the football speed bands. */
    val topSpeed: Speed?,
    val context: AnalysisContext,
)

/**
 * The phone's sessions and settings: the one place screens and the sync
 * service go through, so a session that arrives in the background appears in
 * an open history without anyone asking.
 */
object PhoneData {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var store: SessionStore? = null

    @Volatile private var settings: Settings? = null

    private val _library = MutableStateFlow<Library?>(null)

    /** Null until the first read finishes. */
    val library: StateFlow<Library?> = _library.asStateFlow()

    fun store(context: Context): SessionStore =
        store ?: synchronized(this) {
            store ?: SessionStore(File(context.applicationContext.filesDir, "sessions")).also { store = it }
        }

    fun settings(context: Context): Settings =
        settings ?: synchronized(this) {
            settings ?: Settings(context.applicationContext.getSharedPreferences("fast", Context.MODE_PRIVATE)).also { settings = it }
        }

    /** Rereads the sessions. Call after anything that adds, removes or re-reads them. */
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        scope.launch { _library.value = load(appContext) }
    }

    /** One session with its full analysis, read the same way as the list it was opened from. */
    suspend fun insight(context: Context, id: String): SessionInsight? = withContext(Dispatchers.IO) {
        val library = _library.value ?: load(context.applicationContext).also { _library.value = it }
        store(context).read(id)?.let { SessionInsights.of(it, library.context) }
    }

    fun delete(context: Context, id: String) {
        val appContext = context.applicationContext
        scope.launch {
            store(appContext).delete(id)
            _library.value = load(appContext)
        }
    }

    @Synchronized
    private fun load(context: Context): Library {
        val settings = settings(context)
        val snapshot = SessionLibrary(store(context)).load(settings.maxBpm, settings.lastTopSpeed)
        settings.lastTopSpeed = snapshot.topSpeed
        return Library(snapshot.summaries, snapshot.topSpeed, snapshot.context)
    }
}

class Settings(private val preferences: SharedPreferences) {

    /** Used for sessions that do not carry a maximum heart rate of their own. Null until set. */
    var ageYears: Int?
        get() = preferences.getInt(KEY_AGE, 0).takeIf { it > 0 }
        set(value) = preferences.edit().apply { if (value != null) putInt(KEY_AGE, value) else remove(KEY_AGE) }.apply()

    val maxBpm: Int? get() = ageYears?.let(HeartRateZones::estimateMaxBpm)

    /** Only saves work on the next read; see [SessionLibrary.load]. */
    var lastTopSpeed: Speed?
        get() = preferences.getFloat(KEY_TOP_SPEED, 0f).takeIf { it > 0f }?.let { Speed(it.toDouble()) }
        set(value) = preferences.edit().apply {
            if (value != null) putFloat(KEY_TOP_SPEED, value.metersPerSecond.toFloat()) else remove(KEY_TOP_SPEED)
        }.apply()

    /**
     * False on a fresh install. The first launch then asks the watch for everything
     * it has, which is what brings the history back after a reinstall.
     */
    var askedWatchForHistory: Boolean
        get() = preferences.getBoolean(KEY_ASKED_WATCH, false)
        set(value) = preferences.edit().putBoolean(KEY_ASKED_WATCH, value).apply()

    private companion object {
        const val KEY_AGE = "ageYears"
        const val KEY_TOP_SPEED = "topSpeedMps"
        const val KEY_ASKED_WATCH = "askedWatchForHistory"
    }
}
