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

    companion object {
        const val KEY_NEXT_DEADLINE = "next_deadline"
        const val KEY_IS_BEHIND = "is_behind"
        const val KEY_TARGET_BOTTLE = "target_bottle"
    }

    override suspend fun doWork(): Result {
        val notificationsEnabled = preferencesManager.notificationsEnabled.first()
        if (!notificationsEnabled) return Result.success()

        val startHour = preferencesManager.activeHoursStart.first()
        val endHour = preferencesManager.activeHoursEnd.first()
        val now = LocalTime.now()

        // Only send notifications during active hours
        if (now.isBefore(LocalTime.of(startHour, 0)) || now.isAfter(LocalTime.of(endHour, 0))) {
            return Result.success()
        }

        val goalBottles = preferencesManager.dailyGoalBottles.first()
        val bottleSize = preferencesManager.bottleSizeMl.first()
        val goalMl = goalBottles * bottleSize
        val todayTotal = repository.getTodayTotalOnce()

        if (todayTotal >= goalMl) {
            // Goal met, no reminder needed
            return Result.success()
        }

        val remainingMl = goalMl - todayTotal
        val remainingBottles = (remainingMl + bottleSize - 1) / bottleSize

        // Read deadline context from inputData (set by ReminderScheduler)
        val deadlineStr = inputData.getString(KEY_NEXT_DEADLINE)
        val nextDeadline = deadlineStr?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val isBehind = inputData.getBoolean(KEY_IS_BEHIND, false)
        val targetBottle = inputData.getInt(KEY_TARGET_BOTTLE, 0)

        // If no inputData deadline, try to compute from preferences (periodic fallback)
        val effectiveDeadline: LocalTime?
        val effectiveIsBehind: Boolean
        val effectiveTarget: Int
        if (nextDeadline != null) {
            effectiveDeadline = nextDeadline
            effectiveIsBehind = isBehind
            effectiveTarget = targetBottle
        } else {
            val deadlines = preferencesManager.bottleDeadlines.first()
            val completedBottles = todayTotal / bottleSize
            val nextIdx = completedBottles.coerceAtMost(deadlines.size - 1)
            if (deadlines.isNotEmpty()) {
                effectiveDeadline = deadlines[nextIdx]
                effectiveIsBehind = now.isAfter(effectiveDeadline)
                effectiveTarget = nextIdx + 1
            } else {
                effectiveDeadline = null
                effectiveIsBehind = false
                effectiveTarget = 0
            }
        }

        notificationHelper.createNotificationChannel()
        notificationHelper.showReminderNotification(
            remainingBottles = remainingBottles,
            nextDeadline = effectiveDeadline,
            isBehind = effectiveIsBehind,
            targetBottle = effectiveTarget
        )

        return Result.success()
    }
}
