package com.hananel.fast.wear.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

/** The app speaks through the wrist, so nobody has to look down mid-run. Each signal has its own shape. */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    /** One of the countdown beats. */
    fun tick() = play(VibrationEffect.createOneShot(TICK_MS, VibrationEffect.DEFAULT_AMPLITUDE))

    /** The GPS search has finished and it is possible to start: two quick taps, so there is no need to keep watching. */
    fun ready() = play(VibrationEffect.createWaveform(READY_PATTERN, NO_REPEAT))

    /** The start signal: unmistakably longer than a tick. */
    fun go() = play(VibrationEffect.createOneShot(GO_MS, VibrationEffect.DEFAULT_AMPLITUDE))

    /** It is over and a result is on screen. */
    fun done() = play(VibrationEffect.createWaveform(DONE_PATTERN, NO_REPEAT))

    /** It is over and there is no result. One long, flat buzz: nothing like the two taps of a result. */
    fun notCompleted() = play(VibrationEffect.createOneShot(NOT_COMPLETED_MS, SOFT_AMPLITUDE))

    /** The player is moving again after a rest: the watch is counting work. */
    fun workStarted() = play(VibrationEffect.createWaveform(WORK_PATTERN, NO_REPEAT))

    /** A minute of standing has been recognised as a rest. One soft tap: noticed, nothing to do. */
    fun restStarted() = play(VibrationEffect.createOneShot(TICK_MS, SOFT_AMPLITUDE))

    /** A sprint has begun. */
    fun sprint() = play(VibrationEffect.createOneShot(SPRINT_MS, VibrationEffect.DEFAULT_AMPLITUDE))

    /** That sprint was the fastest of the session. */
    fun record() = play(VibrationEffect.createWaveform(RECORD_PATTERN, NO_REPEAT))

    private fun play(effect: VibrationEffect) {
        vibrator?.vibrate(effect)
    }

    private companion object {
        const val TICK_MS = 70L
        const val GO_MS = 450L
        const val SPRINT_MS = 140L
        const val NOT_COMPLETED_MS = 600L
        const val SOFT_AMPLITUDE = 90
        val READY_PATTERN = longArrayOf(0, 60, 90, 60)
        val DONE_PATTERN = longArrayOf(0, 120, 110, 120)
        val WORK_PATTERN = longArrayOf(0, 90, 70, 180)
        val RECORD_PATTERN = longArrayOf(0, 60, 60, 60, 60, 220)
        const val NO_REPEAT = -1
    }
}
