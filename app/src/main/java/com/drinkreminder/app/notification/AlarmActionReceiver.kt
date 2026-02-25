package com.drinkreminder.app.notification

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE = "com.drinkreminder.app.ACTION_SNOOZE"
        const val ACTION_STOP = "com.drinkreminder.app.ACTION_STOP"
        private const val SNOOZE_REQUEST_CODE = 4000
        private const val SNOOZE_DELAY_MS = 10L * 60 * 1000 // 10 minutes
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Stop the alarm service via ACTION_STOP_ALARM so it goes through
        // the proper stopAlarm() path: stopForeground + stopSelf + cleanup.
        try {
            val stopIntent = Intent(context, AlarmService::class.java).apply {
                action = AlarmService.ACTION_STOP_ALARM
            }
            context.startService(stopIntent)
        } catch (_: Exception) {
            // If startService fails, force-stop and clean up manually
            try { context.stopService(Intent(context, AlarmService::class.java)) } catch (_: Exception) {}
        }

        // Also dismiss the notification directly as a safety net
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NotificationHelper.ALARM_NOTIFICATION_ID)

        when (intent.action) {
            ACTION_SNOOZE -> {
                // Schedule a new deadline-check alarm in 10 minutes
                val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra(AlarmReceiver.EXTRA_IS_DEADLINE_CHECK, true)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    SNOOZE_REQUEST_CODE,
                    alarmIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val triggerTime = System.currentTimeMillis() + SNOOZE_DELAY_MS

                val showIntent = Intent(context, com.drinkreminder.app.MainActivity::class.java)
                val showPendingIntent = PendingIntent.getActivity(
                    context, 0, showIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                try {
                    alarmManager.setAlarmClock(AlarmClockInfo(triggerTime, showPendingIntent), pendingIntent)
                } catch (_: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    }
                }
            }
            ACTION_STOP -> {
                // Just stop — already handled above
            }
        }
    }
}
