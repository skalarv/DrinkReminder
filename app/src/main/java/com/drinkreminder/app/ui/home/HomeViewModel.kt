package com.drinkreminder.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drinkreminder.app.data.local.DrinkLog
import com.drinkreminder.app.data.repository.DrinkRepository
import com.drinkreminder.app.notification.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class HomeUiState(
    val todayTotalMl: Int = 0,
    val goalMl: Int = 2400,
    val bottleSizeMl: Int = 800,
    val goalBottles: Int = 3,
    val todayLogs: List<DrinkLog> = emptyList(),
    val showCustomDialog: Boolean = false,
    val nextDeadline: LocalTime? = null,
    val nextBottleNumber: Int = 0,
    val isBehindSchedule: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DrinkRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val _showCustomDialog = MutableStateFlow(false)
    val showCustomDialog: StateFlow<Boolean> = _showCustomDialog.asStateFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        repository.getTodayTotal(),
        repository.getTodayLogs(),
        repository.dailyGoalBottles,
        combine(
            repository.bottleSizeMl,
            repository.bottleDeadlines
        ) { bottleSize, deadlines -> bottleSize to deadlines }
    ) { total, logs, goalBottles, (bottleSize, deadlines) ->
        val completedBottles = total / bottleSize
        val nextIdx = completedBottles.coerceIn(0, deadlines.size - 1)
        val now = LocalTime.now()

        val nextDeadline: LocalTime?
        val nextBottleNumber: Int
        val isBehind: Boolean

        if (deadlines.isNotEmpty() && completedBottles < goalBottles) {
            nextDeadline = deadlines[nextIdx]
            nextBottleNumber = nextIdx + 1
            isBehind = now.isAfter(nextDeadline)
        } else {
            nextDeadline = null
            nextBottleNumber = 0
            isBehind = false
        }

        HomeUiState(
            todayTotalMl = total,
            goalMl = goalBottles * bottleSize,
            bottleSizeMl = bottleSize,
            goalBottles = goalBottles,
            todayLogs = logs,
            showCustomDialog = _showCustomDialog.value,
            nextDeadline = nextDeadline,
            nextBottleNumber = nextBottleNumber,
            isBehindSchedule = isBehind
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun addBottle() {
        viewModelScope.launch {
            val bottleSize = uiState.value.bottleSizeMl
            repository.addDrink(bottleSize)
            reminderScheduler.rescheduleReminders()
        }
    }

    fun addCustomAmount(amountMl: Int) {
        if (amountMl > 0) {
            viewModelScope.launch {
                repository.addDrink(amountMl)
                reminderScheduler.rescheduleReminders()
                _showCustomDialog.value = false
            }
        }
    }

    fun deleteDrink(drinkLog: DrinkLog) {
        viewModelScope.launch {
            repository.deleteDrink(drinkLog)
            reminderScheduler.rescheduleReminders()
        }
    }

    fun showCustomDialog() {
        _showCustomDialog.value = true
    }

    fun dismissCustomDialog() {
        _showCustomDialog.value = false
    }
}
