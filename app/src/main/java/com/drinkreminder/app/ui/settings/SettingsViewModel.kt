package com.drinkreminder.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drinkreminder.app.data.repository.DrinkRepository
import com.drinkreminder.app.notification.ReminderScheduler
import com.drinkreminder.app.ui.theme.DarkModeOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class SettingsUiState(
    val dailyGoalBottles: Int = 3,
    val activeHoursStart: Int = 7,
    val activeHoursEnd: Int = 20,
    val darkModeOption: DarkModeOption = DarkModeOption.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val bottleDeadlines: List<LocalTime> = emptyList(),
    val alarmVibrate: Boolean = true,
    val alarmSound: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: DrinkRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.dailyGoalBottles,
        repository.activeHoursStart,
        repository.activeHoursEnd,
        repository.darkModeOption,
        combine(
            repository.notificationsEnabled,
            repository.bottleDeadlines,
            repository.alarmVibrate,
            repository.alarmSound
        ) { notifications, deadlines, vibrate, sound ->
            NotificationPrefs(notifications, deadlines, vibrate, sound)
        }
    ) { goalBottles, startHour, endHour, darkMode, notifPrefs ->
        SettingsUiState(
            dailyGoalBottles = goalBottles,
            activeHoursStart = startHour,
            activeHoursEnd = endHour,
            darkModeOption = darkMode,
            notificationsEnabled = notifPrefs.enabled,
            bottleDeadlines = notifPrefs.deadlines,
            alarmVibrate = notifPrefs.vibrate,
            alarmSound = notifPrefs.sound
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    private data class NotificationPrefs(
        val enabled: Boolean,
        val deadlines: List<LocalTime>,
        val vibrate: Boolean,
        val sound: Boolean
    )

    fun setDailyGoalBottles(bottles: Int) {
        viewModelScope.launch {
            repository.setDailyGoalBottles(bottles)
            repository.clearBottleDeadlines()
            reminderScheduler.rescheduleReminders()
        }
    }

    fun setActiveHoursStart(hour: Int) {
        viewModelScope.launch {
            repository.setActiveHoursStart(hour)
            repository.clearBottleDeadlines()
            reminderScheduler.rescheduleReminders()
        }
    }

    fun setActiveHoursEnd(hour: Int) {
        viewModelScope.launch {
            repository.setActiveHoursEnd(hour)
            repository.clearBottleDeadlines()
            reminderScheduler.rescheduleReminders()
        }
    }

    fun setDarkMode(option: DarkModeOption) {
        viewModelScope.launch {
            repository.setDarkMode(option)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setNotificationsEnabled(enabled)
            if (enabled) {
                reminderScheduler.rescheduleReminders()
            } else {
                reminderScheduler.cancelReminders()
            }
        }
    }

    fun setBottleDeadline(index: Int, time: LocalTime) {
        viewModelScope.launch {
            val current = uiState.value.bottleDeadlines.toMutableList()
            if (index !in current.indices) return@launch
            current[index] = time

            // Enforce chronological order: push later deadlines forward if needed
            for (i in index + 1 until current.size) {
                if (current[i].isBefore(current[i - 1]) || current[i] == current[i - 1]) {
                    current[i] = current[i - 1].plusMinutes(15)
                }
            }
            // Enforce earlier deadlines backward if needed
            for (i in index - 1 downTo 0) {
                if (current[i].isAfter(current[i + 1]) || current[i] == current[i + 1]) {
                    current[i] = current[i + 1].minusMinutes(15)
                }
            }

            repository.setBottleDeadlines(current)
            reminderScheduler.rescheduleReminders()
        }
    }

    fun resetDeadlinesToDefault() {
        viewModelScope.launch {
            repository.clearBottleDeadlines()
            reminderScheduler.rescheduleReminders()
        }
    }

    fun setAlarmVibrate(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAlarmVibrate(enabled)
        }
    }

    fun setAlarmSound(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAlarmSound(enabled)
        }
    }
}
