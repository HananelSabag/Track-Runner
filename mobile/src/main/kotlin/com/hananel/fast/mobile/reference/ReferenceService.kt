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
import com.hananel.fast.mobile.data.PhoneData

/**
 * Keeps a phone measurement going while the phone is in a pocket with its screen
 * off — location for an app merely in the background is cut to a trickle — and,
 * for "measure together with the watch", waits for the watch to start (D43).
 *
 * Waiting has to happen here. Android does not let an app in the background start
 * measuring location, so the service is started while the app is open, and the
 * watch's message only switches the receiver on inside it. Waiting costs nothing:
 * the receiver is off until then.
 */
class ReferenceService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        ReferenceRecorder.onRecordingChanged = ::refreshNotification
        ReferenceRecorder.onStopped = {
            // Still waiting for the watch: the service stays, quietly. Otherwise it is done.
            if (!ReferenceRecorder.armed.value) finish()
        }
        when (intent?.action) {
            ACTION_ARM -> {
                PhoneData.settings(applicationContext).measureWithWatch = true
                ReferenceRecorder.setArmed(true)
            }
            ACTION_DISARM -> {
                PhoneData.settings(applicationContext).measureWithWatch = false
                ReferenceRecorder.setArmed(false)
                if (!ReferenceRecorder.isRecording) finish()
            }
            ACTION_STOP -> if (ReferenceRecorder.isRecording) ReferenceRecorder.stop(applicationContext) else if (!ReferenceRecorder.armed.value) finish()
            ACTION_JOIN -> {
                PhoneData.settings(applicationContext).measureWithWatch = true
                ReferenceRecorder.setArmed(true)
                ReferenceRecorder.joinWatch(applicationContext)
            }
            else -> ReferenceRecorder.start(applicationContext)
        }
        refreshNotification()
        // If the system kills the process, what was written is still on disk, and the app re-arms when opened.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ReferenceRecorder.onStopped = null
        ReferenceRecorder.onRecordingChanged = null
        ReferenceRecorder.setArmed(false)
        // Reached without a stop only if the system is tearing the service down: save what there is.
        ReferenceRecorder.stop(applicationContext)
        super.onDestroy()
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
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
        val recording = ReferenceRecorder.isRecording
        val (text, actionLabel, action) = when {
            recording -> Triple(R.string.reference_notification, R.string.reference_stop, ACTION_STOP)
            else -> Triple(R.string.link_notification_waiting, R.string.link_turn_off, ACTION_DISARM)
        }
        val button = PendingIntent.getService(
            this,
            1,
            Intent(this, ReferenceService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(text))
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(0, getString(actionLabel), button)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "reference"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_ARM = "com.hananel.fast.reference.ARM"
        private const val ACTION_DISARM = "com.hananel.fast.reference.DISARM"
        private const val ACTION_STOP = "com.hananel.fast.reference.STOP"
        private const val ACTION_JOIN = "com.hananel.fast.reference.JOIN"

        /** Starts a measurement now, by hand. */
        fun start(context: Context) = send(context, null)

        fun stop(context: Context) = send(context, ACTION_STOP)

        /** "Measure together with the watch" on: wait for the watch. Only while the app is open. */
        fun arm(context: Context) = send(context, ACTION_ARM)

        fun disarm(context: Context) = send(context, ACTION_DISARM)

        /** Join a measurement the watch has already started. Only while the app is in front. */
        fun join(context: Context) = send(context, ACTION_JOIN)

        private fun send(context: Context, action: String?) {
            ContextCompat.startForegroundService(context, Intent(context, ReferenceService::class.java).setAction(action))
        }
    }
}
