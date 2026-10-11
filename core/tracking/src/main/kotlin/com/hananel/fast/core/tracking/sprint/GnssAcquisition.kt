package com.hananel.fast.core.tracking.sprint

import com.hananel.fast.core.model.SpeedSample

/** What the receiver can hear right now. Available before there is any fix, which is when it matters most. */
data class SatelliteView(
    /** Satellites with any signal at all. */
    val visible: Int,
    /** Satellites heard clearly enough to contribute to a fix. */
    val strong: Int,
    val usedInFix: Int,
)

/** How far along the receiver is, from hearing nothing to being ready for a sprint. */
enum class AcquisitionStage {
    /** No satellite heard yet. Indoors this is where it stays. */
    NO_SATELLITES,

    /** Some satellites heard, not enough of them clearly. */
    SATELLITES_FOUND,

    /** Enough satellites, or the first fixes are arriving: a position is moments away. */
    LOCKING,

    /** Positions arrive but carry no speed. The test cannot run on these. */
    FIX_WITHOUT_SPEED,

    /**
     * A steady stream of fixes, but a poor one: the receiver rates them badly, or
     * rates them not at all and is using too few satellites. A test may start; it
     * will score lower.
     */
    WEAK_FIX,

    READY,
}

data class GnssAcquisition(
    val stage: AcquisitionStage,
    /** 0–4, for a signal-strength display. */
    val bars: Int,
    val satellites: SatelliteView?,
    /** The receiver's own estimate of its position error, from the latest fix. Null when it gives none worth showing. */
    val positionAccuracyMeters: Double?,
    /** True once fixes with speed arrive steadily. A weak fix still allows a start: the score will say so. */
    val canStart: Boolean,
)

object GnssAcquisitionCheck {

    /**
     * @param speedSamples recent fixes that carried a speed
     * @param fixTimesMs arrival times of all recent fixes, with or without speed
     * @param satellites the latest satellite report, or null when the receiver sends none
     */
    fun evaluate(
        speedSamples: List<SpeedSample>,
        fixTimesMs: List<Long>,
        satellites: SatelliteView?,
        nowMs: Long,
    ): GnssAcquisition {
        val recentSamples = speedSamples.filter { nowMs - it.timeMs <= LOOKBACK_MS }
        val recentFixes = fixTimesMs.count { nowMs - it <= LOOKBACK_MS }
        // A placeholder such as "125 m" is not shown: it would look like a measurement.
        val accuracy = recentSamples.lastOrNull()?.crediblePositionAccuracy()

        val stage = when {
            recentSamples.size >= STEADY_FIXES ->
                if (GnssReadinessCheck.evaluate(recentSamples, nowMs, satellites) == GnssReadiness.READY) AcquisitionStage.READY else AcquisitionStage.WEAK_FIX
            // Plenty of fixes and none with a speed: waiting longer will not help.
            recentSamples.isEmpty() && recentFixes >= STEADY_FIXES -> AcquisitionStage.FIX_WITHOUT_SPEED
            recentFixes > 0 -> AcquisitionStage.LOCKING
            satellites == null || satellites.visible == 0 -> AcquisitionStage.NO_SATELLITES
            satellites.strong >= SATELLITES_FOR_A_FIX -> AcquisitionStage.LOCKING
            else -> AcquisitionStage.SATELLITES_FOUND
        }
        return GnssAcquisition(
            stage = stage,
            bars = when (stage) {
                AcquisitionStage.NO_SATELLITES -> 0
                AcquisitionStage.SATELLITES_FOUND -> 1
                AcquisitionStage.LOCKING, AcquisitionStage.FIX_WITHOUT_SPEED -> 2
                AcquisitionStage.WEAK_FIX -> 3
                AcquisitionStage.READY -> 4
            },
            satellites = satellites,
            positionAccuracyMeters = accuracy,
            canStart = stage == AcquisitionStage.READY || stage == AcquisitionStage.WEAK_FIX,
        )
    }

    /** A satellite at or above this carrier-to-noise density (dB-Hz) is heard clearly. Indoors they rarely reach it. */
    const val STRONG_SIGNAL_DBHZ = 30f

    /** Wide enough that a fix arriving a second late does not flip the stage back and forth. */
    private const val LOOKBACK_MS = 8_000L

    /** At one fix a second, four in eight seconds is a steady stream with room for dropped ones. */
    private const val STEADY_FIXES = 4

    /** A position needs four satellites. */
    private const val SATELLITES_FOR_A_FIX = 4
}
