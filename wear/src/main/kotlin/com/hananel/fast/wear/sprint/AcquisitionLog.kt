package com.hananel.fast.wear.sprint

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * A plain-text record of how each GPS search went: when it began, what the
 * receiver heard, how long the first fix took and what that fix contained.
 *
 * It exists because a search that never finishes looks the same on the wrist
 * whether the watch is indoors or the receiver is not delivering — and the
 * watch is usually not connected to a computer when it happens. The file can
 * be pulled afterwards and read.
 *
 * It never contains positions.
 */
class AcquisitionLog(context: Context) {

    private val file = File(File(context.filesDir, "sprints").apply { mkdirs() }, "gnss-log.txt")

    fun record(event: String) {
        Log.i(TAG, event)
        try {
            if (file.length() > MAX_BYTES) file.writeText("")
            file.appendText("${Instant.now()} $event\n")
        } catch (e: IOException) {
            Log.w(TAG, "Could not write the acquisition log", e)
        }
    }

    private companion object {
        const val TAG = "FastGnss"

        /** Enough for hundreds of searches; beyond that the oldest are of no use. */
        const val MAX_BYTES = 200_000L
    }
}
