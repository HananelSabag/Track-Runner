package com.hananel.fast.wear.gnss

import android.os.SystemClock
import com.hananel.fast.core.gnss.GnssFix
import com.hananel.fast.core.model.SpeedSample
import com.hananel.fast.core.tracking.sprint.GnssAcquisition
import com.hananel.fast.core.tracking.sprint.GnssAcquisitionCheck
import com.hananel.fast.core.tracking.sprint.SatelliteView

/**
 * Remembers what the receiver has delivered in the last few seconds, which is
 * all that is needed to say how a GPS search is going.
 *
 * Feed it every fix and satellite report; ask it for [current] whenever the
 * screen should be told. Not thread-safe: use it from the thread the location
 * callbacks arrive on.
 */
class AcquisitionTracker {

    private val recentSamples = ArrayDeque<SpeedSample>()
    private val recentFixTimes = ArrayDeque<Long>()
    private var satellites: SatelliteView? = null
    private var satellitesAtMs = 0L

    fun onFix(fix: GnssFix) {
        recentFixTimes.addLast(fix.sample.timeMs)
        while (recentFixTimes.size > CAPACITY) recentFixTimes.removeFirst()
        if (fix.hasSpeed) {
            recentSamples.addLast(fix.sample)
            while (recentSamples.size > CAPACITY) recentSamples.removeFirst()
        }
    }

    fun onSatellites(view: SatelliteView) {
        satellites = view
        satellitesAtMs = SystemClock.elapsedRealtime()
    }

    fun current(): GnssAcquisition {
        val now = SystemClock.elapsedRealtime()
        // A satellite report that has stopped being refreshed describes the past.
        val fresh = satellites?.takeIf { now - satellitesAtMs <= SATELLITE_REPORT_MAX_AGE_MS }
        return GnssAcquisitionCheck.evaluate(recentSamples.toList(), recentFixTimes.toList(), fresh, now)
    }

    fun reset() {
        recentSamples.clear()
        recentFixTimes.clear()
        satellites = null
    }

    companion object {
        /** What a search looks like before the receiver has said anything. */
        val NOTHING_YET: GnssAcquisition =
            GnssAcquisitionCheck.evaluate(emptyList(), emptyList(), satellites = null, nowMs = 0)

        private const val CAPACITY = 12
        private const val SATELLITE_REPORT_MAX_AGE_MS = 5_000L
    }
}
