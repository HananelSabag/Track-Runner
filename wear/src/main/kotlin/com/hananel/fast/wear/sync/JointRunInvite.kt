package com.hananel.fast.wear.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.hananel.fast.wear.MainActivity
import com.hananel.fast.wear.R

/**
 * The phone started a joint run and is already measuring (D47). The watch cannot open a
 * screen by itself from the background, so it buzzes with a notice; one tap opens a run,
 * and from there the run starts as always: wait for the blue "GPS ready", press Start.
 */
object JointRunInvite {

    fun show(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.joint_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_RUN, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.joint_title))
                .setContentText(context.getString(R.string.joint_text))
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_WORKOUT)
                .setAutoCancel(true)
                .setContentIntent(open)
                .addAction(0, context.getString(R.string.joint_open), open)
                .build(),
        )
    }

    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private const val CHANNEL_ID = "joint"
    private const val NOTIFICATION_ID = 7
}
