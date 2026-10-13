package com.hananel.fast.wear.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.core.data.SessionHeader
import com.hananel.fast.core.data.SessionLibrary
import com.hananel.fast.core.data.SessionStore
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.tracking.HeartRateZones
import java.io.File
import java.util.UUID

/** The watch's sessions and settings. One instance of each for the whole process. */
object WearData {

    @Volatile private var store: SessionStore? = null

    @Volatile private var settings: Settings? = null

    fun store(context: Context): SessionStore =
        store ?: synchronized(this) {
            store ?: SessionStore(File(context.applicationContext.filesDir, "sessions")).also { store = it }
        }

    fun settings(context: Context): Settings =
        settings ?: synchronized(this) {
            settings ?: Settings(context.applicationContext.getSharedPreferences("fast", Context.MODE_PRIVATE)).also { settings = it }
        }

    /** The header of a recording that starts now, or started at the given moment. */
    fun newHeader(context: Context, kind: ActivityKind, startedUtcMs: Long, startedElapsedMs: Long): SessionHeader =
        SessionHeader(
            id = UUID.randomUUID().toString(),
            kind = kind,
            startedUtcMs = startedUtcMs,
            startedElapsedMs = startedElapsedMs,
            device = Build.MODEL.orEmpty(),
            appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
            maxBpm = settings(context).maxBpm,
        )

    /**
     * Reads every stored session and remembers the top speed they show, which sets
     * the football speed bands of the next training. Reads files: call it off the
     * main thread.
     */
    fun loadLibrary(context: Context): SessionLibrary.Snapshot {
        val settings = settings(context)
        return SessionLibrary(store(context)).load(settings.maxBpm, settings.topSpeed).also { settings.topSpeed = it.topSpeed }
    }
}

/** The few things the watch has to remember between sessions. */
class Settings(private val preferences: SharedPreferences) {

    /** Asked once, to estimate the maximum heart rate. Null until answered. */
    var ageYears: Int?
        get() = preferences.getInt(KEY_AGE, 0).takeIf { it > 0 }
        set(value) = preferences.edit().apply { if (value != null) putInt(KEY_AGE, value) else remove(KEY_AGE) }.apply()

    /** True once the player has seen the age question, answered or not, so it is never asked twice. */
    var ageAsked: Boolean
        get() = preferences.getBoolean(KEY_AGE_ASKED, false)
        set(value) = preferences.edit().putBoolean(KEY_AGE_ASKED, value).apply()

    /** True once the heart-rate permission has been requested, so a refusal is not argued with on every session. */
    var sensorsAsked: Boolean
        get() = preferences.getBoolean(KEY_SENSORS_ASKED, false)
        set(value) = preferences.edit().putBoolean(KEY_SENSORS_ASKED, value).apply()

    /** The top speed the stored sessions show; see [WearData.loadLibrary]. */
    var topSpeed: Speed?
        get() = preferences.getFloat(KEY_TOP_SPEED, 0f).takeIf { it > 0f }?.let { Speed(it.toDouble()) }
        set(value) = preferences.edit().apply {
            if (value != null) putFloat(KEY_TOP_SPEED, value.metersPerSecond.toFloat()) else remove(KEY_TOP_SPEED)
        }.apply()

    val maxBpm: Int? get() = ageYears?.let(HeartRateZones::estimateMaxBpm)

    private companion object {
        const val KEY_AGE = "ageYears"
        const val KEY_AGE_ASKED = "ageAsked"
        const val KEY_SENSORS_ASKED = "sensorsAsked"
        const val KEY_TOP_SPEED = "topSpeedMps"
    }
}
