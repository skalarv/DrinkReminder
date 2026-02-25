package com.drinkreminder.app.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.drinkreminder.app.data.local.PreferencesManager
import com.drinkreminder.app.data.repository.DrinkRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalTime

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: DrinkRepository,
    private val notificationHelper: NotificationHelper,
    private val preferencesManager: PreferencesManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val notificationsEnabled = preferencesManager.notificationsEnabled.first()
        if (!notificationsEnabled) return Result.success()

        val startHour = preferencesManager.activeHoursStart.first()
        val endHour = preferencesManager.activeHoursEnd.first()
        val now = LocalTime.now()

        if (now.isBefore(LocalTime.of(startHour, 0)) || now.isAfter(LocalTime.of(endHour, 0))) {
            return Result.success()
        }

        val goalBottles = preferencesManager.dailyGoalBottles.first()
        val bottleSize = preferencesManager.bottleSizeMl.first()
        val goalMl = goalBottles * bottleSize
        val todayTotal = repository.getTodayTotalOnce()

        if (todayTotal >= goalMl) return Result.success()

        val remainingMl = goalMl - todayTotal
        val remainingBottles = (remainingMl + bottleSize - 1) / bottleSize

        // Compute deadline context from preferences
        val deadlines = preferencesManager.bottleDeadlines.first()
        val completedBottles = todayTotal / bottleSize
        val nextIdx = completedBottles.coerceAtMost(deadlines.size - 1)

        val nextDeadline: LocalTime?
        val isBehind: Boolean
        val targetBottle: Int

        if (deadlines.isNotEmpty()) {
            nextDeadline = deadlines[nextIdx]
            isBehind = now.isAfter(nextDeadline)
            targetBottle = nextIdx + 1
        } else {
            nextDeadline = null
            isBehind = false
            targetBottle = 0
        }

        notificationHelper.createNotificationChannel()
        notificationHelper.showReminderNotification(
            remainingBottles = remainingBottles,
            nextDeadline = nextDeadline,
            isBehind = isBehind,
            targetBottle = targetBottle
        )

        return Result.success()
    }
}
