package com.hananel.fast.core.data

import com.hananel.fast.core.model.GeoPoint
import com.hananel.fast.core.model.Speed
import kotlin.math.cos

/**
 * Builds a synthetic recording one second at a time, so a test reads like the
 * training it describes: `stand(90); run(12.0, 120)`.
 */
class SessionBuilder(private val kind: ActivityKind, private val startedElapsedMs: Long = 50_000) {

    private val fixes = mutableListOf<FixRecord>()
    private val heartRates = mutableListOf<HeartRateRecord>()
    private var timeMs = startedElapsedMs
    private var eastMeters = 0.0
    private var heartRate: Int? = null
    var maxBpm: Int? = null
    var demo = false
    var endedCleanly = true
    var trimAfterMs: Long? = null

    fun heartRate(bpm: Int?) = apply { heartRate = bpm }

    fun stand(seconds: Int) = run(0.0, seconds)

    fun run(kmh: Double, seconds: Int) = apply { repeat(seconds) { second(kmh) } }

    /** One second at an exact speed. */
    fun second(kmh: Double, hasSpeed: Boolean = true) = apply {
        timeMs += 1000
        eastMeters += Speed.ofKmh(kmh).metersPerSecond
        fixes += FixRecord(
            timeMs = timeMs,
            utcMs = STARTED_UTC_MS + (timeMs - startedElapsedMs),
            hasSpeed = hasSpeed,
            speed = Speed.ofKmh(kmh),
            speedAccuracy = Speed(0.3),
            position = GeoPoint(BASE_LATITUDE, BASE_LONGITUDE + eastMeters / METERS_PER_DEGREE_LONGITUDE),
            horizontalAccuracyMeters = 4.0,
            bearingDegrees = 90f,
        )
        heartRate?.let { heartRates += HeartRateRecord(timeMs, it) }
    }

    /** Skips ahead without records, as when the signal is lost. */
    fun gap(seconds: Int) = apply { timeMs += seconds * 1000L }

    val nowMs: Long get() = timeMs

    fun header(id: String = "11111111-2222-3333-4444-555555555555") = SessionHeader(
        id = id,
        kind = kind,
        startedUtcMs = STARTED_UTC_MS,
        startedElapsedMs = startedElapsedMs,
        device = "TEST",
        appVersion = "0.0.0",
        maxBpm = maxBpm,
        demo = demo,
    )

    fun build(id: String = "11111111-2222-3333-4444-555555555555") =
        RecordedSession(header(id), fixes.toList(), heartRates.toList(), endedCleanly, trimAfterMs)

    companion object {
        const val STARTED_UTC_MS = 1_791_600_000_000
        const val BASE_LATITUDE = 32.08
        const val BASE_LONGITUDE = 34.78
        val METERS_PER_DEGREE_LONGITUDE = 111_320.0 * cos(Math.toRadians(BASE_LATITUDE))
    }
}

fun session(kind: ActivityKind = ActivityKind.FOOTBALL, build: SessionBuilder.() -> Unit): RecordedSession =
    SessionBuilder(kind).apply(build).build()

/** The session as the watch would have written it. */
fun RecordedSession.toText(): String = buildString {
    append(SessionFormat.header(header))
    (fixes + heartRates).sortedBy { it.timeMs }.forEach { appendLine(SessionFormat.line(it)) }
    if (endedCleanly) {
        val end = (fixes + heartRates).maxOfOrNull { it.timeMs } ?: header.startedElapsedMs
        appendLine(trimAfterMs?.let { SessionFormat.autoEndMarker(end, it) } ?: SessionFormat.endMarker(end))
    }
}
