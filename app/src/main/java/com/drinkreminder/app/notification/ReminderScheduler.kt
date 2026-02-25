package com.drinkreminder.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.drinkreminder.app.data.local.PreferencesManager
import com.drinkreminder.app.data.repository.DrinkRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DrinkRepository,
    private val preferencesManager: PreferencesManager
) {
    companion object {
        const val PERIODIC_WORK_NAME = "drink_reminder_periodic"
        private const val ALARM_BASE_REQUEST_CODE = 2000
        private const val DEADLINE_ALARM_BASE_REQUEST_CODE = 3000
        private const val MAX_ALARMS = 10
        private const val MAX_DEADLINE_ALARMS = 10
    }

    suspend fun rescheduleReminders() {
        val notificationsEnabled = preferencesManager.notificationsEnabled.first()
        if (!notificationsEnabled) {
            cancelReminders()
            return
        }

        // Cancel existing alarms before scheduling new ones
        cancelAlarms()
        cancelDeadlineAlarms()

        val goalBottles = preferencesManager.dailyGoalBottles.first()
        val bottleSize = preferencesManager.bottleSizeMl.first()
        val goalMl = goalBottles * bottleSize
        val todayTotal = repository.getTodayTotalOnce()

        if (todayTotal >= goalMl) {
            return
        }

        val startHour = preferencesManager.activeHoursStart.first()
        val endHour = preferencesManager.activeHoursEnd.first()
        val deadlines = preferencesManager.bottleDeadlines.first()

        val now = LocalDateTime.now()
        val activeEnd = LocalTime.of(endHour, 0)
        val currentTime = now.toLocalTime()

        if (currentTime.isAfter(activeEnd)) {
            return
        }

        val completedBottles = todayTotal / bottleSize
        val remainingBottles = goalBottles - completedBottles

        if (remainingBottles <= 0) return

        val nextDeadlineIdx = completedBottles.coerceIn(0, deadlines.size - 1)

        // Collect all reminder times to schedule
        val reminderTimes = mutableListOf<ScheduledReminder>()

        if (deadlines.isNotEmpty()) {
            val nextDeadline = deadlines[nextDeadlineIdx]
            val targetBottle = nextDeadlineIdx + 1
            val isBehind = currentTime.isAfter(nextDeadline)

            if (isBehind) {
                // Behind schedule: immediate urgent reminder + every 30 min
                val futureDeadline = deadlines.drop(nextDeadlineIdx + 1)
                    .firstOrNull { currentTime.isBefore(it) }
                val urgentEnd = futureDeadline ?: activeEnd

                // Immediate reminder (1 minute from now)
                reminderTimes.add(
                    ScheduledReminder(
                        time = currentTime.plusMinutes(1),
                        deadline = nextDeadline,
                        isBehind = true,
                        targetBottle = targetBottle
                    )
                )

                // Every 30 min after that
                var next = currentTime.plusMinutes(30)
                while (next.isBefore(urgentEnd) && next.isAfter(currentTime)) {
                    reminderTimes.add(
                        ScheduledReminder(
                            time = next,
                            deadline = nextDeadline,
                            isBehind = true,
                            targetBottle = targetBottle
                        )
                    )
                    next = next.plusMinutes(30)
                }
            } else {
                // On schedule: halfway, 30 min before, and 10 min before deadline
                val minutesUntilDeadline = Duration.between(currentTime, nextDeadline).toMinutes()

                val halfwayTime = currentTime.plusMinutes(minutesUntilDeadline / 2)
                val thirtyBefore = nextDeadline.minusMinutes(30)
                val tenBefore = nextDeadline.minusMinutes(10)

                val candidates = listOf(halfwayTime, thirtyBefore, tenBefore)
                    .filter { it.isAfter(currentTime) && !it.isAfter(nextDeadline) }
                    .distinct()
                    .sorted()

                // Dedup: enforce minimum 10-min spacing
                val deduped = mutableListOf<LocalTime>()
                for (t in candidates) {
                    if (deduped.isEmpty() ||
                        Duration.between(deduped.last(), t).toMinutes() >= 10
                    ) {
                        deduped.add(t)
                    }
                }

                for (t in deduped) {
                    reminderTimes.add(
                        ScheduledReminder(
                            time = t,
                            deadline = nextDeadline,
                            isBehind = false,
                            targetBottle = targetBottle
                        )
                    )
                }
            }
        }

        // Schedule exact alarms for each reminder
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        reminderTimes.take(MAX_ALARMS).forEachIndexed { index, reminder ->
            val delayMinutes = Duration.between(currentTime, reminder.time).toMinutes()
            if (delayMinutes > 0) {
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra(AlarmReceiver.EXTRA_NEXT_DEADLINE, reminder.deadline.toString())
                    putExtra(AlarmReceiver.EXTRA_IS_BEHIND, reminder.isBehind)
                    putExtra(AlarmReceiver.EXTRA_TARGET_BOTTLE, reminder.targetBottle)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    ALARM_BASE_REQUEST_CODE + index,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val triggerTimeMs = System.currentTimeMillis() + delayMinutes * 60 * 1000

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent
                    )
                }
            }
        }

        // Schedule deadline-check alarms at exact deadline times
        val deadlineAlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        deadlines.forEachIndexed { index, deadline ->
            if (index < MAX_DEADLINE_ALARMS && deadline.isAfter(currentTime)) {
                val deadlineIntent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra(AlarmReceiver.EXTRA_IS_DEADLINE_CHECK, true)
                }
                val deadlinePendingIntent = PendingIntent.getBroadcast(
                    context,
                    DEADLINE_ALARM_BASE_REQUEST_CODE + index,
                    deadlineIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val deadlineDelayMinutes = Duration.between(currentTime, deadline).toMinutes()
                val deadlineTriggerTimeMs = System.currentTimeMillis() + deadlineDelayMinutes * 60 * 1000

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (deadlineAlarmManager.canScheduleExactAlarms()) {
                        deadlineAlarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, deadlineTriggerTimeMs, deadlinePendingIntent
                        )
                    } else {
                        deadlineAlarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, deadlineTriggerTimeMs, deadlinePendingIntent
                        )
                    }
                } else {
                    deadlineAlarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, deadlineTriggerTimeMs, deadlinePendingIntent
                    )
                }
            }
        }

        // Periodic WorkManager fallback every 1 hour
        val periodicWork = PeriodicWorkRequestBuilder<ReminderWorker>(
            1, TimeUnit.HOURS,
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork
        )
    }

    fun cancelReminders() {
        cancelAlarms()
        cancelDeadlineAlarms()
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    private fun cancelAlarms() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until MAX_ALARMS) {
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_BASE_REQUEST_CODE + i,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { alarmManager.cancel(it) }
        }
    }

    private fun cancelDeadlineAlarms() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until MAX_DEADLINE_ALARMS) {
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                DEADLINE_ALARM_BASE_REQUEST_CODE + i,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { alarmManager.cancel(it) }
        }
    }

    private data class ScheduledReminder(
        val time: LocalTime,
        val deadline: LocalTime,
        val isBehind: Boolean,
        val targetBottle: Int
    )
}
