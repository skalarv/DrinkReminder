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
            handleDeadlineCheck(context)
        } else {
            handlePassiveReminder(intent)
        }
    }

    private fun handleDeadlineCheck(context: Context) {
        // Try the foreground service (best experience: looping sound + vibration).
        // startForegroundService must be called SYNCHRONOUSLY from onReceive
        // to guarantee the FGS start exemption from the alarm broadcast.
        try {
            val serviceIntent = Intent(context, AlarmService::class.java).apply {
                putExtra(AlarmService.EXTRA_IS_DEADLINE_CHECK, true)
            }
            context.startForegroundService(serviceIntent)
        } catch (_: Exception) {
            // Foreground service failed (permission, OEM restriction, etc.)
            // Fall back to direct notification with channel-managed alarm sound.
            handleDeadlineCheckFallback()
        }
    }

    /**
     * Fallback when foreground service cannot start.
     * Posts a high-priority alarm notification directly from the receiver.
     * The alarm channel has sound + vibration enabled, so the user gets
     * at least one alarm tone and vibration burst.
     */
    private fun handleDeadlineCheckFallback() {
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
                val dueBottles = deadlines.indexOfLast { !it.isAfter(now) } + 1

                if (completedBottles >= dueBottles) return@launch

                val targetBottle = completedBottles + 1
                val deadline = deadlines.getOrNull(completedBottles) ?: return@launch
                val targetMl = targetBottle * bottleSize

                notificationHelper.showFallbackAlarmNotification(
                    targetBottle = targetBottle,
                    deadlineTime = deadline.format(timeFormatter),
                    todayTotalMl = todayTotal,
                    targetMl = targetMl
                )
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
