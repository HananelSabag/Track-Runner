package com.hananel.fast.core.data

import com.hananel.fast.core.model.SpeedSample
import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.tracking.sprint.credibleSpeedAccuracy
import com.hananel.fast.core.tracking.sprint.crediblePositionAccuracy

/**
 * One stretch of movement measured by two receivers at once — the watch on the
 * wrist and the phone carried alongside — set side by side.
 *
 * Only the time both were recording counts. The two are lined up by the
 * satellites' own clock, which each receiver stamps on every fix, so no manual
 * syncing is needed.
 */
data class DeviceComparison(
    /** How long both were recording at the same time. */
    val sharedMs: Long,
    val watch: ReceiverFigures,
    val phone: ReceiverFigures,
    /** How far apart the two placed the runner at the same second, on average. Null without positions on both. */
    val meanSeparationMeters: Double?,
)

/** What one receiver made of the shared stretch. */
data class ReceiverFigures(
    val fixes: Int,
    val fixesPerSecond: Double,
    /** The best three seconds by the speed the receiver reported. */
    val best3sReported: Speed,
    /** The best three seconds by how far its positions moved. Null without enough positions. */
    val best3sFromPositions: Speed?,
    /** Distance from adding up the reported speeds. */
    val distanceFromSpeedsMeters: Double,
    /** Distance from adding up the steps between positions. Null without positions. */
    val distanceFromPositionsMeters: Double?,
    /** The receiver's own claim about its speed error, when it makes a believable one. */
    val speedAccuracy: Speed?,
    /** Likewise for position, in metres. */
    val positionAccuracyMeters: Double?,
)

object DeviceComparisons {

    /** Whether the two recordings ran at the same time for long enough to compare. */
    fun overlap(a: RecordedSession, b: RecordedSession): Long {
        val (aStart, aEnd) = a.utcSpan() ?: return 0
        val (bStart, bEnd) = b.utcSpan() ?: return 0
        return (minOf(aEnd, bEnd) - maxOf(aStart, bStart)).coerceAtLeast(0)
    }

    /** Null when the two did not record at the same time for at least [MIN_SHARED_MS]. */
    fun of(watch: RecordedSession, phone: RecordedSession): DeviceComparison? {
        val (watchStart, watchEnd) = watch.utcSpan() ?: return null
        val (phoneStart, phoneEnd) = phone.utcSpan() ?: return null
        val from = maxOf(watchStart, phoneStart)
        val to = minOf(watchEnd, phoneEnd)
        if (to - from < MIN_SHARED_MS) return null
        val watchFixes = watch.fixes.filter { it.utcMs in from..to }
        val phoneFixes = phone.fixes.filter { it.utcMs in from..to }
        if (watchFixes.size < 2 || phoneFixes.size < 2) return null
        return DeviceComparison(
            sharedMs = to - from,
            watch = figures(watchFixes, from, to),
            phone = figures(phoneFixes, from, to),
            meanSeparationMeters = separation(watchFixes, phoneFixes, from, to),
        )
    }

    private fun figures(fixes: List<FixRecord>, from: Long, to: Long): ReceiverFigures {
        val seconds = ((to - from) / 1000).toInt() + 1
        val reported = arrayOfNulls<Double>(seconds)
        val fromPositions = arrayOfNulls<Double>(seconds)
        for (second in 0 until seconds) {
            val inside = fixes.filter { (it.utcMs - from) / 1000 == second.toLong() }
            val withSpeed = inside.filter { it.hasSpeed }
            if (withSpeed.isNotEmpty()) reported[second] = withSpeed.map { it.speed.metersPerSecond }.average()
            // How far the last position of this second lies from the last one before it.
            val here = inside.lastOrNull { it.position != null } ?: continue
            val before = fixes.lastOrNull { it.utcMs < from + second * 1000L && it.position != null } ?: continue
            val dt = (here.utcMs - before.utcMs) / 1000.0
            if (dt > 0 && dt <= MAX_STEP_S) fromPositions[second] = before.position!!.distanceTo(here.position!!) / dt
        }

        var speedDistance = 0.0
        var positionDistance = 0.0
        var positionSteps = 0
        for (i in 1 until fixes.size) {
            val dt = (fixes[i].utcMs - fixes[i - 1].utcMs) / 1000.0
            if (dt <= 0 || dt > MAX_STEP_S) continue
            speedDistance += fixes[i - 1].speed.metersPerSecond * dt
            val a = fixes[i - 1].position
            val b = fixes[i].position
            if (a != null && b != null) {
                positionDistance += a.distanceTo(b)
                positionSteps++
            }
        }
        val samples = fixes.map { SpeedSample(it.utcMs, it.speed, it.speedAccuracy, it.position, it.horizontalAccuracyMeters) }
        val spanS = (fixes.last().utcMs - fixes.first().utcMs) / 1000.0
        return ReceiverFigures(
            fixes = fixes.size,
            fixesPerSecond = if (spanS > 0) (fixes.size - 1) / spanS else 0.0,
            best3sReported = Speed(bestOfThree(reported) ?: 0.0),
            best3sFromPositions = bestOfThree(fromPositions)?.let(::Speed),
            distanceFromSpeedsMeters = speedDistance,
            distanceFromPositionsMeters = positionDistance.takeIf { positionSteps > 0 },
            speedAccuracy = samples.mapNotNull { it.credibleSpeedAccuracy() }.medianOrNull()?.let(::Speed),
            positionAccuracyMeters = samples.mapNotNull { it.crediblePositionAccuracy() }.medianOrNull(),
        )
    }

    /** The mean distance between the two receivers' positions, second by second. */
    private fun separation(watch: List<FixRecord>, phone: List<FixRecord>, from: Long, to: Long): Double? {
        val gaps = mutableListOf<Double>()
        for (second in 0..((to - from) / 1000)) {
            val a = watch.lastOrNull { (it.utcMs - from) / 1000 == second && it.position != null }?.position ?: continue
            val b = phone.lastOrNull { (it.utcMs - from) / 1000 == second && it.position != null }?.position ?: continue
            gaps += a.distanceTo(b)
        }
        return gaps.takeIf { it.isNotEmpty() }?.average()
    }

    /** The best mean over three consecutive whole seconds that all have a value. */
    private fun bestOfThree(perSecond: Array<Double?>): Double? {
        var best: Double? = null
        for (i in 0..perSecond.size - WINDOW_S) {
            val window = perSecond.copyOfRange(i, i + WINDOW_S)
            if (window.any { it == null }) continue
            val mean = window.sumOf { it!! } / WINDOW_S
            if (best == null || mean > best) best = mean
        }
        return best
    }

    private fun List<Double>.medianOrNull(): Double? = if (isEmpty()) null else sorted()[size / 2]

    private fun RecordedSession.utcSpan(): Pair<Long, Long>? =
        if (fixes.isEmpty()) null else fixes.minOf { it.utcMs } to fixes.maxOf { it.utcMs }

    /** Shorter than this, there is nothing to compare. */
    const val MIN_SHARED_MS = 5_000L
    private const val WINDOW_S = 3

    /** A longer silence is a gap in the signal, not a step to measure. */
    private const val MAX_STEP_S = 3.0
}
