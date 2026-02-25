package com.drinkreminder.app.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
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
        const val ONE_TIME_WORK_TAG = "drink_reminder_onetime"
    }

    suspend fun rescheduleReminders() {
        val notificationsEnabled = preferencesManager.notificationsEnabled.first()
        if (!notificationsEnabled) {
            cancelReminders()
            return
        }

        val workManager = WorkManager.getInstance(context)

        // Cancel existing one-time reminders
        workManager.cancelAllWorkByTag(ONE_TIME_WORK_TAG)

        val goalBottles = preferencesManager.dailyGoalBottles.first()
        val bottleSize = preferencesManager.bottleSizeMl.first()
        val goalMl = goalBottles * bottleSize
        val todayTotal = repository.getTodayTotalOnce()

        if (todayTotal >= goalMl) {
            // Goal already met, no reminders needed
            return
        }

        val startHour = preferencesManager.activeHoursStart.first()
        val endHour = preferencesManager.activeHoursEnd.first()
        val deadlines = preferencesManager.bottleDeadlines.first()

        val now = LocalDateTime.now()
        val activeEnd = LocalTime.of(endHour, 0)
        val currentTime = now.toLocalTime()

        if (currentTime.isAfter(activeEnd)) {
            // Past active hours
            return
        }

        val completedBottles = todayTotal / bottleSize
        val remainingBottles = goalBottles - completedBottles

        if (remainingBottles <= 0) return

        // Find the next deadline (first deadline for a bottle not yet completed)
        val nextDeadlineIdx = completedBottles.coerceIn(0, deadlines.size - 1)

        // Collect all reminder times to schedule
        val reminderTimes = mutableListOf<ScheduledReminder>()

        if (deadlines.isNotEmpty()) {
            val nextDeadline = deadlines[nextDeadlineIdx]
            val targetBottle = nextDeadlineIdx + 1
            val isBehind = currentTime.isAfter(nextDeadline)

            if (isBehind) {
                // Behind schedule: immediate urgent reminder + every 30 min
                // until the next future deadline or end of active hours
                val futureDeadline = deadlines.drop(nextDeadlineIdx + 1)
                    .firstOrNull { currentTime.isBefore(it) }
                val urgentEnd = futureDeadline ?: activeEnd

                // Immediate reminder
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
                // On schedule: schedule at halfway, 30 min before, and 10 min before deadline
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

        // Schedule the one-time work requests
        for (reminder in reminderTimes) {
            val delayMinutes = Duration.between(currentTime, reminder.time).toMinutes()
            if (delayMinutes > 0) {
                val data = workDataOf(
                    ReminderWorker.KEY_NEXT_DEADLINE to reminder.deadline.toString(),
                    ReminderWorker.KEY_IS_BEHIND to reminder.isBehind,
                    ReminderWorker.KEY_TARGET_BOTTLE to reminder.targetBottle
                )

                val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                    .setInputData(data)
                    .addTag(ONE_TIME_WORK_TAG)
                    .build()

                workManager.enqueue(workRequest)
            }
        }

        // Periodic fallback check every 1 hour
        val periodicWork = PeriodicWorkRequestBuilder<ReminderWorker>(
            1, TimeUnit.HOURS,
            15, TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork
        )
    }

    fun cancelReminders() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(ONE_TIME_WORK_TAG)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    private data class ScheduledReminder(
        val time: LocalTime,
        val deadline: LocalTime,
        val isBehind: Boolean,
        val targetBottle: Int
    )
}
