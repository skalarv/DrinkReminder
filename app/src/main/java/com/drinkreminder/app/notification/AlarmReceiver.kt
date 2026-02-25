package com.drinkreminder.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.drinkreminder.app.data.local.PreferencesManager
import com.drinkreminder.app.data.repository.DrinkRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var repository: DrinkRepository

    companion object {
        const val EXTRA_NEXT_DEADLINE = "next_deadline"
        const val EXTRA_IS_BEHIND = "is_behind"
        const val EXTRA_TARGET_BOTTLE = "target_bottle"
        const val EXTRA_IS_DEADLINE_CHECK = "is_deadline_check"
    }

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun onReceive(context: Context, intent: Intent) {
        val isDeadlineCheck = intent.getBooleanExtra(EXTRA_IS_DEADLINE_CHECK, false)

        if (isDeadlineCheck) {
            handleDeadlineCheck(context, intent)
        } else {
            handlePassiveReminder(intent)
        }
    }

    private fun handleDeadlineCheck(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val notificationsEnabled = preferencesManager.notificationsEnabled.first()
                if (!notificationsEnabled) return@launch

                val goalBottles = preferencesManager.dailyGoalBottles.first()
                val bottleSize = preferencesManager.bottleSizeMl.first()
                val todayTotal = repository.getTodayTotalOnce()
                val deadlines = preferencesManager.bottleDeadlines.first()

                val now = LocalTime.now()
                val completedBottles = todayTotal / bottleSize

                // Find which bottle should have been completed by now
                val dueBottles = deadlines.indexOfLast { !it.isAfter(now) } + 1

                if (completedBottles >= dueBottles) {
                    // User is on track — no alarm needed
                    return@launch
                }

                // User is behind — determine the missed bottle
                val targetBottle = completedBottles + 1
                val deadline = deadlines.getOrNull(completedBottles) ?: return@launch
                val targetMl = targetBottle * bottleSize

                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    putExtra(AlarmService.EXTRA_TARGET_BOTTLE, targetBottle)
                    putExtra(AlarmService.EXTRA_DEADLINE_TIME, deadline.format(timeFormatter))
                    putExtra(AlarmService.EXTRA_TODAY_TOTAL_ML, todayTotal)
                    putExtra(AlarmService.EXTRA_TARGET_ML, targetMl)
                }
                context.startForegroundService(serviceIntent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handlePassiveReminder(intent: Intent) {
        val pendingResult = goAsync()

        val deadlineStr = intent.getStringExtra(EXTRA_NEXT_DEADLINE)
        val nextDeadline = deadlineStr?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val isBehind = intent.getBooleanExtra(EXTRA_IS_BEHIND, false)
        val targetBottle = intent.getIntExtra(EXTRA_TARGET_BOTTLE, 0)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val notificationsEnabled = preferencesManager.notificationsEnabled.first()
                if (!notificationsEnabled) return@launch

                val startHour = preferencesManager.activeHoursStart.first()
                val endHour = preferencesManager.activeHoursEnd.first()
                val now = LocalTime.now()
                if (now.isBefore(LocalTime.of(startHour, 0)) || now.isAfter(LocalTime.of(endHour, 0))) {
                    return@launch
                }

                val goalBottles = preferencesManager.dailyGoalBottles.first()
                val bottleSize = preferencesManager.bottleSizeMl.first()
                val goalMl = goalBottles * bottleSize
                val todayTotal = repository.getTodayTotalOnce()

                if (todayTotal >= goalMl) return@launch

                val remainingMl = goalMl - todayTotal
                val remainingBottles = (remainingMl + bottleSize - 1) / bottleSize

                notificationHelper.createNotificationChannel()
                notificationHelper.showReminderNotification(
                    remainingBottles = remainingBottles,
                    nextDeadline = nextDeadline,
                    isBehind = isBehind,
                    targetBottle = targetBottle
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
