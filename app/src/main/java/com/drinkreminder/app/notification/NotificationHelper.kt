package com.drinkreminder.app.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.drinkreminder.app.MainActivity
import com.drinkreminder.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "drink_reminders"
        const val ALARM_CHANNEL_ID = "deadline_alarms"
        const val NOTIFICATION_ID = 1001
        const val ALARM_NOTIFICATION_ID = 1002
    }

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    fun createAlarmNotificationChannel() {
        val channel = NotificationChannel(
            ALARM_CHANNEL_ID,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.alarm_channel_description)
            // Sound and vibration managed by AlarmService directly
            setSound(null, null)
            enableVibration(false)
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun showReminderNotification(
        remainingBottles: Int,
        nextDeadline: LocalTime? = null,
        isBehind: Boolean = false,
        targetBottle: Int = 0
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title: String
        val message: String
        val priority: Int

        if (remainingBottles <= 0) {
            title = "Great job!"
            message = "You've met your goal. Keep it up!"
            priority = NotificationCompat.PRIORITY_DEFAULT
        } else if (nextDeadline != null && isBehind) {
            title = "You're behind schedule!"
            message = "Bottle $targetBottle was due by ${nextDeadline.format(timeFormatter)}. " +
                "$remainingBottles bottle${if (remainingBottles > 1) "s" else ""} remaining today."
            priority = NotificationCompat.PRIORITY_HIGH
        } else if (nextDeadline != null && targetBottle > 0) {
            title = "Time to drink water!"
            message = "Bottle $targetBottle is due by ${nextDeadline.format(timeFormatter)}. " +
                "$remainingBottles bottle${if (remainingBottles > 1) "s" else ""} remaining today."
            priority = NotificationCompat.PRIORITY_DEFAULT
        } else {
            title = "Time to drink water!"
            message = "You still need $remainingBottles more bottle${if (remainingBottles > 1) "s" else ""} today. Stay hydrated!"
            priority = NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun buildAlarmNotification(
        targetBottle: Int,
        deadlineTime: String,
        todayTotalMl: Int,
        targetMl: Int
    ): Notification {
        createAlarmNotificationChannel()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 1, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val snoozeIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = AlarmActionReceiver.ACTION_SNOOZE
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, 2, snoozeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = AlarmActionReceiver.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Deadline missed!")
            .setContentText("Bottle $targetBottle was due by $deadlineTime. You've had ${todayTotalMl}ml of ${targetMl}ml so far.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Bottle $targetBottle was due by $deadlineTime. You've had ${todayTotalMl}ml of ${targetMl}ml so far."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_popup_reminder, "Snooze 10 min", snoozePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }
}
