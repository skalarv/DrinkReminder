package com.drinkreminder.app.notification

import android.app.AlarmManager
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
        // Stop the alarm service
        val stopIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP_ALARM
        }
        context.startService(stopIntent)

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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                    )
                }
            }
            ACTION_STOP -> {
                // Just stop — already handled above
            }
        }
    }
}
