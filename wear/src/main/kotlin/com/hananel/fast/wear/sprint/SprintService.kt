package com.hananel.fast.wear.sprint

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import com.hananel.fast.wear.MainActivity
import com.hananel.fast.wear.R

/**
 * Keeps a sprint session alive, and entitled to the GPS, while the display is
 * off or the app is not on screen.
 *
 * A foreground service is the only way to do that: location access for an app
 * that is merely in the background is cut to a trickle within seconds. It is
 * started from the sprint screen and stopped when the runner leaves it, or by
 * the session itself after a long idle spell.
 */
class SprintService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        SprintSession.onIdleTimeout = { stopSelf() }
        SprintSession.open(applicationContext)
        // If the system kills the process there is no test worth resuming.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        SprintSession.onIdleTimeout = null
        SprintSession.close()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setContentIntent(openApp)

        // Puts a tappable FAST indicator on the watch face, so the way back to a
        // running test is one tap after the display has gone to sleep.
        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_launcher_monochrome)
            .setTouchIntent(openApp)
            .build()
            .apply(applicationContext)

        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "sprint-test"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SprintService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SprintService::class.java))
        }
    }
}
