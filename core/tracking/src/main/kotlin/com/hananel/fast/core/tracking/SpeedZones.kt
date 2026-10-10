package com.hananel.fast.core.tracking

import com.hananel.fast.core.model.Speed

/** Movement intensity bands, slowest to fastest. */
enum class SpeedZone { WALK, JOG, RUN, HIGH_SPEED, SPRINT }

/**
 * The speeds at which one [SpeedZone] turns into the next.
 *
 * There is no universal standard: every tracking system picks its own cut-offs,
 * so numbers are only comparable between sessions analysed with the same bands.
 */
class SpeedZones private constructor(private val lowerBounds: Map<SpeedZone, Speed>) {

    fun zoneOf(speed: Speed): SpeedZone =
        SpeedZone.entries.last { speed >= lowerBound(it) }

    fun lowerBound(zone: SpeedZone): Speed = lowerBounds.getValue(zone)

    companion object {
        /**
         * The fixed bands most football GPS systems report, tuned on professional
         * players: 7.2 / 14.4 / 19.8 / 25.2 km/h.
         */
        val STANDARD: SpeedZones = ofKmh(jog = 7.2, run = 14.4, highSpeed = 19.8, sprint = 25.2)

        /**
         * Bands scaled to one player's own top speed. A fixed 25.2 km/h sprint line
         * is 71–91% of top speed depending on the player, so an amateur may never
         * cross it while working flat out. Relative bands fix that: sprinting starts
         * at 85% of the player's maximum and high-speed running at 66%, both inside
         * the ranges the research uses.
         */
        fun relativeTo(topSpeed: Speed): SpeedZones = SpeedZones(
            mapOf(
                SpeedZone.WALK to Speed.ZERO,
                SpeedZone.JOG to topSpeed * 0.24,
                SpeedZone.RUN to topSpeed * 0.48,
                SpeedZone.HIGH_SPEED to topSpeed * 0.66,
                SpeedZone.SPRINT to topSpeed * 0.85,
            ),
        )

        fun ofKmh(jog: Double, run: Double, highSpeed: Double, sprint: Double): SpeedZones {
            require(0 < jog && jog < run && run < highSpeed && highSpeed < sprint) {
                "Zone bounds must be positive and increasing"
            }
            return SpeedZones(
                mapOf(
                    SpeedZone.WALK to Speed.ZERO,
                    SpeedZone.JOG to Speed.ofKmh(jog),
                    SpeedZone.RUN to Speed.ofKmh(run),
                    SpeedZone.HIGH_SPEED to Speed.ofKmh(highSpeed),
                    SpeedZone.SPRINT to Speed.ofKmh(sprint),
                ),
            )
        }
    }
}

private operator fun Speed.times(factor: Double) = Speed(metersPerSecond * factor)
