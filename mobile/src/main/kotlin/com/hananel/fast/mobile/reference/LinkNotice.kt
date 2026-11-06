package com.hananel.fast.mobile.reference

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.hananel.fast.mobile.MainActivity
import com.hananel.fast.mobile.R

/**
 * "The watch started measuring, the phone did not join": posted when "measure together"
 * is on but the service waiting for the watch is gone — the system ended the app.
 * Android lets a background app neither start measuring location nor start the service
 * that would, so the user is asked; one tap opens the app, which joins (L1).
 */
object LinkNotice {

    fun offerToJoin(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.link_notice_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val join = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_JOIN_WATCH, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.link_notice_title))
                .setContentText(context.getString(R.string.link_notice_text))
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(join)
                .addAction(0, context.getString(R.string.link_notice_join), join)
                .build(),
        )
    }

    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private const val CHANNEL_ID = "link"
    private const val NOTIFICATION_ID = 2
}
