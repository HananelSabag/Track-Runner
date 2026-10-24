package com.hananel.fast.wear.record

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
import androidx.wear.ongoing.OngoingActivity
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.wear.MainActivity
import com.hananel.fast.wear.R
import com.hananel.fast.wear.sensors.hasHeartRatePermission
import com.hananel.fast.wear.sync.PhoneLink

/**
 * Keeps a training alive, and entitled to the GPS and the heart-rate sensor,
 * while the display is off — which during a training is nearly always.
 *
 * Started when the recording screen opens, so the GPS warms up before "start";
 * it stops itself once the session has been saved. Leaving the screen does not
 * stop a recording: only finishing it does.
 */
class RecordingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val kind = intent?.getStringExtra(EXTRA_KIND)?.let { name -> ActivityKind.entries.firstOrNull { it.name == name } }
        if (kind == null) {
            // Restarted by the system with nothing to resume: a recording does not survive its process.
            stopSelf()
            return START_NOT_STICKY
        }

        // The heart-rate type may only be claimed once the permission is held.
        val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
            if (hasHeartRatePermission(this)) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(kind), types)

        RecordingSession.onFinished = { stop() }
        RecordingSession.onIdleTimeout = { stop() }
        RecordingSession.open(applicationContext, kind)
        PhoneLink.measuringStarted(applicationContext)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        RecordingSession.onFinished = null
        RecordingSession.onIdleTimeout = null
        RecordingSession.close()
        PhoneLink.measuringStopped(applicationContext)
        super.onDestroy()
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(kind: ActivityKind): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.recording_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = getString(if (kind == ActivityKind.FOOTBALL) R.string.mode_football else R.string.mode_run)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(title)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setContentIntent(openApp)

        // Puts a tappable FAST indicator on the watch face: the way back to the training
        // after the display has gone to sleep is one tap.
        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_launcher_monochrome)
            .setTouchIntent(openApp)
            .build()
            .apply(applicationContext)

        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 2
        private const val EXTRA_KIND = "kind"

        fun start(context: Context, kind: ActivityKind) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RecordingService::class.java).putExtra(EXTRA_KIND, kind.name),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecordingService::class.java))
        }
    }
}
