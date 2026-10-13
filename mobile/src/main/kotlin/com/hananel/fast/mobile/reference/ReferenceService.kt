package com.hananel.fast.mobile.reference

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
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.hananel.fast.mobile.MainActivity
import com.hananel.fast.mobile.R

/**
 * Keeps a reference measurement going while the phone is in a pocket with its
 * screen off. Location for an app that is merely in the background is cut to a
 * trickle; a foreground service is what keeps every fix coming.
 */
class ReferenceService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        ReferenceRecorder.onStopped = {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        ReferenceRecorder.start(applicationContext)
        // If the system kills the process, the part of the recording already written is still on disk.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ReferenceRecorder.onStopped = null
        // Reached without a stop only if the system is tearing the service down: save what there is.
        ReferenceRecorder.stop(applicationContext)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.reference_title), NotificationManager.IMPORTANCE_LOW),
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.reference_notification))
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "reference"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ReferenceService::class.java))
        }
    }
}
