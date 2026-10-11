package com.hananel.fast.core.tracking.sprint

import com.hananel.fast.core.model.Speed
import com.hananel.fast.core.model.SpeedSample
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class SprintTestConfig(
    /** Below this top speed nobody sprinted; there is nothing to report. */
    val minTopSpeed: Speed = Speed.ofKmh(12.0),
    /** Faster than any human has run. A reading above it did not come from running. */
    val maxHumanSpeed: Speed = Speed.ofKmh(45.0),
    /** The top speed is the best average held for this long, not the single fastest sample. */
    val holdWindowMs: Long = 2_000,
    /** The fast phase of the sprint: everything at or above this share of the top speed. */
    val fastPhaseFraction: Double = 0.7,
    val expectedSampleIntervalMs: Long = 1_000,
)

enum class ConfidenceLevel { LOW, MEDIUM, HIGH }

/** Why a measurement deserves less trust. Shown to the runner so the retry can fix it. */
enum class SprintIssue {
    /** Too few readings while running fast, or readings went missing. */
    FEW_SAMPLES,

    /** The receiver itself reported a poor fix. */
    WEAK_SIGNAL,

    /** The top speed was touched, not held: the readings inside the window disagree. */
    NOT_HELD,

    /** One reading jumped further than a runner can accelerate. It was left out of the top speed. */
    SPIKE,

    /** The distance actually covered does not match the speed that was reported. */
    DISTANCE_MISMATCH,
}

enum class SprintRejection {
    /** Not enough readings to say anything. */
    NOT_ENOUGH_DATA,

    /** The runner never got fast enough for this to be a sprint. */
    NO_SPRINT,

    /** Faster than a person can run — measured in a vehicle, or a broken receiver. */
    TOO_FAST_FOR_A_RUNNER,
}

sealed interface SprintTestOutcome {

    data class Measured(
        /** The headline: the best average speed held for the hold window, with isolated jumps left out. */
        val topSpeed: Speed,
        /** The single fastest reading, glitches included. Shown for interest; never the headline. */
        val instantMax: Speed,
        /** One-sigma uncertainty of [topSpeed]. Null when the receiver gave no speed accuracy to base it on. */
        val uncertainty: Speed?,
        /** How long the runner stayed in the fast phase. */
        val fastPhaseMs: Long,
        val fastPhaseMeters: Double,
        /** 0–100. How much the readings support the headline number — not a promise of accuracy. */
        val confidence: Int,
        val issues: List<SprintIssue>,
    ) : SprintTestOutcome {
        val level: ConfidenceLevel
            get() = when {
                confidence >= HIGH_CONFIDENCE -> ConfidenceLevel.HIGH
                confidence >= MEDIUM_CONFIDENCE -> ConfidenceLevel.MEDIUM
                else -> ConfidenceLevel.LOW
            }
    }

    data class Rejected(val reason: SprintRejection) : SprintTestOutcome

    companion object {
        const val HIGH_CONFIDENCE = 80
        const val MEDIUM_CONFIDENCE = 60
    }
}

/**
 * Turns the readings of one sprint into a top speed and a confidence score.
 *
 * The top speed is the best two-second average of the readings after single
 * isolated jumps have been removed. A glitch therefore cannot become the
 * headline, and neither can a speed that was touched for one reading and lost.
 *
 * The score is built to be hard to earn. Independent checks each ask a
 * different question of the data, and the weakest of them counts as much as
 * all of them together, so a clean-looking number cannot hide one bad check
 * behind several good ones:
 *
 *  - coverage:   were there enough readings, without holes, while running fast?
 *  - signal:     did the receiver itself rate the fix as good?
 *  - steadiness: do the readings inside the top-speed window agree with each other?
 *  - glitches:   did any reading jump further than a runner can accelerate?
 *  - agreement:  does the ground actually covered match the reported speed?
 *
 * The last one is the strongest. Speed comes from the Doppler shift and position
 * from ranging, two separate measurements; on a straight sprint they must tell
 * the same story, and a fault in one rarely fools the other.
 *
 * When a check cannot be made at all, the score is capped rather than assumed
 * good: silence from the receiver is not evidence of quality.
 */
object SprintTestAnalyzer {

    /**
     * @param satellites what the receiver was using during the test, one view per report.
     *   Only consulted when the receiver did not rate its own fixes.
     */
    fun analyze(
        samples: List<SpeedSample>,
        config: SprintTestConfig = SprintTestConfig(),
        satellites: List<SatelliteView> = emptyList(),
    ): SprintTestOutcome {
        if (samples.size < MIN_SAMPLES) return SprintTestOutcome.Rejected(SprintRejection.NOT_ENOUGH_DATA)

        val steady = withoutIsolatedJumps(samples)
        val steadyMax = steady.max()
        if (steadyMax > config.maxHumanSpeed.metersPerSecond) {
            return SprintTestOutcome.Rejected(SprintRejection.TOO_FAST_FOR_A_RUNNER)
        }

        val window = bestHoldWindow(samples, steady, config.holdWindowMs)
            ?: return SprintTestOutcome.Rejected(
                // Fast readings exist but never close enough together to form a window: a data problem.
                // No fast readings at all: simply no sprint.
                if (steadyMax >= config.minTopSpeed.metersPerSecond) SprintRejection.NOT_ENOUGH_DATA else SprintRejection.NO_SPRINT,
            )
        val topSpeed = Speed(steady.slice(window).average())
        if (topSpeed < config.minTopSpeed) return SprintTestOutcome.Rejected(SprintRejection.NO_SPRINT)

        val windowSamples = samples.slice(window)
        val fastPhase = fastPhaseAround(steady, window, topSpeed.metersPerSecond * config.fastPhaseFraction)
        val phaseSamples = samples.slice(fastPhase)
        val phaseMeters = dopplerDistance(phaseSamples)

        val coverage = coverageScore(phaseSamples, config.expectedSampleIntervalMs)
        val signal = signalScore(windowSamples, satellites)
        val steadiness = steadinessScore(windowSamples, topSpeed)
        val glitches = if (hasImpossibleJump(samples, fastPhase)) GLITCH_SCORE else 1.0
        val agreement = agreementScore(phaseSamples, phaseMeters)

        val checks = listOfNotNull(coverage, signal.score, steadiness, glitches, agreement)
        // Half the weight on the weakest check, half on the overall picture. With a plain
        // average, one check at 50% would still leave the score above 80%.
        var confidence = sqrt(checks.min() * geometricMean(checks))

        // A check that could not be made caps the score instead of counting as passed.
        if (!signal.fromSpeedAccuracy) confidence = minOf(confidence, CAP_WITHOUT_SPEED_ACCURACY)
        if (agreement == null) confidence = minOf(confidence, CAP_WITHOUT_AGREEMENT)

        return SprintTestOutcome.Measured(
            topSpeed = topSpeed,
            instantMax = samples.maxOf { it.speed },
            uncertainty = uncertaintyOf(windowSamples),
            fastPhaseMs = phaseSamples.last().timeMs - phaseSamples.first().timeMs,
            fastPhaseMeters = phaseMeters,
            confidence = (confidence * 100).roundToInt(),
            issues = buildList {
                if (coverage < ISSUE_THRESHOLD) add(SprintIssue.FEW_SAMPLES)
                if (signal.score < ISSUE_THRESHOLD) add(SprintIssue.WEAK_SIGNAL)
                if (steadiness < ISSUE_THRESHOLD) add(SprintIssue.NOT_HELD)
                if (glitches < 1.0) add(SprintIssue.SPIKE)
                if (agreement != null && agreement < ISSUE_THRESHOLD) add(SprintIssue.DISTANCE_MISMATCH)
            },
        )
    }

    /**
     * Speeds in m/s with every reading replaced by the median of itself and its
     * two neighbours. A value that stands alone — one glitch, or a top speed
     * touched for a single reading — disappears; anything held for two readings
     * or more is untouched.
     */
    internal fun withoutIsolatedJumps(samples: List<SpeedSample>): List<Double> =
        samples.indices.map { i ->
            val here = samples[i].speed.metersPerSecond
            if (i == 0 || i == samples.lastIndex) {
                here
            } else {
                listOf(samples[i - 1].speed.metersPerSecond, here, samples[i + 1].speed.metersPerSecond).sorted()[1]
            }
        }

    /**
     * The run of consecutive samples spanning the hold window with the highest
     * average steady speed, or null when no run spans it without a hole. Among
     * equally fast windows the one whose raw readings agree best wins, so a
     * clean stretch is preferred over one that contains a glitch.
     */
    private fun bestHoldWindow(samples: List<SpeedSample>, steady: List<Double>, holdWindowMs: Long): IntRange? {
        var best: IntRange? = null
        var bestAverage = -1.0
        var bestSpread = Double.MAX_VALUE
        var start = 0
        for (end in samples.indices) {
            while (samples[end].timeMs - samples[start].timeMs > holdWindowMs + TIMING_JITTER_MS) start++
            val span = samples[end].timeMs - samples[start].timeMs
            val hasHole = (start + 1..end).any { samples[it].timeMs - samples[it - 1].timeMs > MAX_GAP_IN_WINDOW_MS }
            if (span < holdWindowMs - TIMING_JITTER_MS || end - start + 1 < MIN_SAMPLES_IN_WINDOW || hasHole) continue

            val average = (start..end).sumOf { steady[it] } / (end - start + 1)
            val raw = (start..end).map { samples[it].speed.metersPerSecond }
            val spread = raw.max() - raw.min()
            val faster = average > bestAverage + SAME_SPEED_TOLERANCE
            val asFastButCleaner = abs(average - bestAverage) <= SAME_SPEED_TOLERANCE && spread < bestSpread
            if (faster || asFastButCleaner) {
                best = start..end
                bestAverage = average
                bestSpread = spread
            }
        }
        return best
    }

    /** Grows the window outwards for as long as the runner stayed at or above [threshold] m/s. */
    private fun fastPhaseAround(steady: List<Double>, window: IntRange, threshold: Double): IntRange {
        var first = window.first
        var last = window.last
        while (first > 0 && steady[first - 1] >= threshold) first--
        while (last < steady.lastIndex && steady[last + 1] >= threshold) last++
        return first..last
    }

    /** Distance according to the speed readings alone. */
    private fun dopplerDistance(samples: List<SpeedSample>): Double {
        var meters = 0.0
        for (i in 1 until samples.size) {
            val seconds = (samples[i].timeMs - samples[i - 1].timeMs) / 1000.0
            meters += (samples[i].speed.metersPerSecond + samples[i - 1].speed.metersPerSecond) / 2 * seconds
        }
        return meters
    }

    /** Enough readings while fast, and none missing. */
    private fun coverageScore(phase: List<SpeedSample>, expectedIntervalMs: Long): Double {
        // Three readings are the bare minimum that forms a window; five mean four clear seconds at speed.
        val enough = ramp(phase.size.toDouble(), zeroAt = 2.0, oneAt = 5.0)
        val largestGap = (1 until phase.size).maxOfOrNull { phase[it].timeMs - phase[it - 1].timeMs } ?: return 0.0
        val unbroken = ramp(largestGap.toDouble(), zeroAt = expectedIntervalMs * 3.0, oneAt = expectedIntervalMs * 1.3)
        return enough * unbroken
    }

    private data class SignalScore(val score: Double, val fromSpeedAccuracy: Boolean)

    /**
     * How good the fix was during the top-speed window: by the receiver's own rating
     * where it gives a believable one, otherwise by the satellites it was using.
     */
    private fun signalScore(window: List<SpeedSample>, satellites: List<SatelliteView>): SignalScore {
        val speedAccuracies = window.mapNotNull { it.credibleSpeedAccuracy() }
        if (speedAccuracies.size == window.size) {
            return SignalScore(ramp(speedAccuracies.average(), zeroAt = 1.5, oneAt = 0.3), fromSpeedAccuracy = true)
        }
        val positionAccuracies = window.mapNotNull { it.crediblePositionAccuracy() }
        if (positionAccuracies.size == window.size) {
            return SignalScore(ramp(positionAccuracies.average(), zeroAt = 25.0, oneAt = 5.0), fromSpeedAccuracy = false)
        }
        // The weakest moment counts, not the average: one stretch of poor reception is
        // enough to spoil the speed that was read during it.
        satellites.minOfOrNull { it.fixQuality() }?.let { return SignalScore(it, fromSpeedAccuracy = false) }

        // Nothing at all about quality. Neither good nor bad can be assumed.
        return SignalScore(UNKNOWN_SIGNAL_SCORE, fromSpeedAccuracy = false)
    }

    /** Do the raw readings inside the window agree? A held speed is flat; a touched one is not. */
    private fun steadinessScore(window: List<SpeedSample>, topSpeed: Speed): Double {
        val speeds = window.map { it.speed.metersPerSecond }
        val relativeSpread = (speeds.max() - speeds.min()) / topSpeed.metersPerSecond
        return ramp(relativeSpread, zeroAt = 0.30, oneAt = 0.06)
    }

    /**
     * True when a reading in the fast phase stands above both its neighbours by
     * more than a sprinter can gain in the time between them. Such a reading is
     * already kept out of the top speed; its presence still says the receiver
     * was struggling.
     */
    private fun hasImpossibleJump(samples: List<SpeedSample>, phase: IntRange): Boolean =
        phase.any { i ->
            if (i == 0 || i == samples.lastIndex) return@any false
            val seconds = minOf(samples[i].timeMs - samples[i - 1].timeMs, samples[i + 1].timeMs - samples[i].timeMs) / 1000.0
            if (seconds <= 0 || seconds > MAX_GAP_IN_WINDOW_MS / 1000.0) return@any false
            val neighbours = maxOf(samples[i - 1].speed.metersPerSecond, samples[i + 1].speed.metersPerSecond)
            samples[i].speed.metersPerSecond - neighbours > MAX_SPRINT_ACCELERATION * seconds
        }

    /**
     * Compares the straight-line distance between the first and last position of
     * the fast phase with the distance the speed readings add up to. Null when
     * it cannot be judged: no positions, or too short a stretch for position
     * error not to dominate.
     */
    private fun agreementScore(phase: List<SpeedSample>, dopplerMeters: Double): Double? {
        val from = phase.first().position ?: return null
        val to = phase.last().position ?: return null
        if (dopplerMeters < MIN_METERS_FOR_AGREEMENT) return null

        val deviation = abs(from.distanceTo(to) / dopplerMeters - 1.0)
        // Position error is forgiven up to what the receiver admitted to; beyond that it counts.
        // A placeholder is not an admission: forgiving "125 m" would forgive any mismatch at all,
        // so a receiver that does not rate its positions is allowed an ordinary GPS error instead.
        val admittedError = (admittedPositionError(phase.first()) + admittedPositionError(phase.last())) / 2 / dopplerMeters
        return ramp(maxOf(0.0, deviation - admittedError), zeroAt = 0.40, oneAt = 0.10)
    }

    private fun admittedPositionError(sample: SpeedSample): Double =
        sample.crediblePositionAccuracy() ?: if (sample.horizontalAccuracyMeters == null) 0.0 else TYPICAL_POSITION_ERROR_METERS

    /**
     * One-sigma uncertainty of the window average: the receiver's own error and the scatter of the
     * readings, combined. Null when the receiver gave no believable error to build it on.
     */
    private fun uncertaintyOf(window: List<SpeedSample>): Speed? {
        val reported = window.mapNotNull { it.credibleSpeedAccuracy() }
        if (reported.size != window.size) return null
        val n = window.size
        val speeds = window.map { it.speed.metersPerSecond }
        val mean = speeds.average()
        val scatter = sqrt(speeds.sumOf { (it - mean) * (it - mean) } / (n - 1))
        val fromReceiver = reported.average() / sqrt(n.toDouble())
        val fromScatter = scatter / sqrt(n.toDouble())
        return Speed(sqrt(fromReceiver * fromReceiver + fromScatter * fromScatter))
    }

    /** Linear from 0 at [zeroAt] to 1 at [oneAt], clamped. Works in either direction. */
    private fun ramp(value: Double, zeroAt: Double, oneAt: Double): Double =
        ((value - zeroAt) / (oneAt - zeroAt)).coerceIn(0.0, 1.0)

    private fun geometricMean(values: List<Double>): Double =
        exp(values.sumOf { ln(maxOf(it, SCORE_FLOOR)) } / values.size)

    private const val MIN_SAMPLES = 4
    private const val MIN_SAMPLES_IN_WINDOW = 3
    private const val TIMING_JITTER_MS = 250L
    private const val MAX_GAP_IN_WINDOW_MS = 1_500L
    private const val SAME_SPEED_TOLERANCE = 0.01
    private const val MIN_METERS_FOR_AGREEMENT = 12.0

    /** What a watch-sized receiver is typically off by under open sky, for one that will not say. */
    private const val TYPICAL_POSITION_ERROR_METERS = 5.0

    /**
     * In m/s². More than any sprinter gains per second once already running fast, so a
     * reading that tops both its neighbours by more than this is not a burst of speed.
     */
    private const val MAX_SPRINT_ACCELERATION = 2.5

    /** A glitch outside the top-speed window does not spoil the headline, but it does cost trust. */
    private const val GLITCH_SCORE = 0.5
    private const val UNKNOWN_SIGNAL_SCORE = 0.5
    private const val CAP_WITHOUT_SPEED_ACCURACY = 0.85
    private const val CAP_WITHOUT_AGREEMENT = 0.80
    private const val ISSUE_THRESHOLD = 0.6

    /** Keeps the logarithm finite for a check that scored zero. */
    private const val SCORE_FLOOR = 0.01
}
