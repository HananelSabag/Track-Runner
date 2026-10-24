package com.hananel.fast.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgottenCheckTest {

    @Test
    fun aWatchLeftOnATableIsForgotten() {
        val session = session {
            heartRate(78)
            stand(25 * 60)
        }

        assertTrue(ForgottenCheck.looksForgotten(session.fixes, session.heartRates, session.fixes.first().timeMs, session.fixes.last().timeMs))
    }

    @Test
    fun aPlayerStillMovingAboutIsNotForgotten() {
        // The real case: twenty minutes the rest detection called rest, moving most of it, heart at 140.
        val session = session {
            heartRate(140)
            repeat(20) {
                run(4.5, 30)
                stand(30)
            }
        }

        assertFalse(ForgottenCheck.looksForgotten(session.fixes, session.heartRates, session.fixes.first().timeMs, session.fixes.last().timeMs))
    }

    @Test
    fun aHighHeartRateAloneKeepsTheSessionGoing() {
        // An indoor court: the GPS sees nothing move, the heart says otherwise.
        val session = session {
            heartRate(150)
            stand(25 * 60)
        }

        assertFalse(ForgottenCheck.looksForgotten(session.fixes, session.heartRates, session.fixes.first().timeMs, session.fixes.last().timeMs))
    }

    @Test
    fun aTrimTheDataContradictsIsIgnoredWhenTheSessionIsRead() {
        val builder = SessionBuilder(ActivityKind.FOOTBALL).apply {
            heartRate(150)
            run(9.0, 600)
        }
        val trimAt = builder.nowMs
        builder.apply {
            repeat(20) {
                run(4.5, 30)
                stand(30)
            }
            trimAfterMs = trimAt
        }
        val played = builder.build()

        assertNull(played.effectiveTrimAfterMs)
        assertEquals(30 * 60_000L, played.durationMs)
    }

    @Test
    fun aTrimTheDataAgreesWithStands() {
        val builder = SessionBuilder(ActivityKind.FOOTBALL).apply {
            heartRate(150)
            run(9.0, 600)
            heartRate(80)
        }
        val trimAt = builder.nowMs
        builder.apply {
            stand(20 * 60)
            trimAfterMs = trimAt
        }
        val forgotten = builder.build()

        assertEquals(trimAt, forgotten.effectiveTrimAfterMs)
        assertEquals(10 * 60_000L, forgotten.durationMs)
    }
}
