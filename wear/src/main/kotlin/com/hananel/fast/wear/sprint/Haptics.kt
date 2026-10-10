package com.hananel.fast.wear.sprint

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

/** The test speaks through the wrist, so the runner never has to look down. */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    /** One of the countdown beats. */
    fun tick() = play(VibrationEffect.createOneShot(TICK_MS, VibrationEffect.DEFAULT_AMPLITUDE))

    /** The GPS search has finished and the test can start: two quick taps, so there is no need to keep watching. */
    fun ready() = play(VibrationEffect.createWaveform(READY_PATTERN, NO_REPEAT))

    /** The start signal: unmistakably longer than a tick. */
    fun go() = play(VibrationEffect.createOneShot(GO_MS, VibrationEffect.DEFAULT_AMPLITUDE))

    /** The test is over and a result is on screen. */
    fun done() = play(VibrationEffect.createWaveform(DONE_PATTERN, NO_REPEAT))

    private fun play(effect: VibrationEffect) {
        vibrator?.vibrate(effect)
    }

    private companion object {
        const val TICK_MS = 70L
        const val GO_MS = 450L
        val READY_PATTERN = longArrayOf(0, 60, 90, 60)
        val DONE_PATTERN = longArrayOf(0, 120, 110, 120)
        const val NO_REPEAT = -1
    }
}
