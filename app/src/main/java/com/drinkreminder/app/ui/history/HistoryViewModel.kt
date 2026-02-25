package com.drinkreminder.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drinkreminder.app.data.repository.DrinkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HistoryUiState(
    val dailyTotals: Map<LocalDate, Int> = emptyMap(),
    val goalMl: Int = 2400,
    val bottleSizeMl: Int = 800,
    val showDays: Int = 7,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: DrinkRepository
) : ViewModel() {

    private val _showDays = MutableStateFlow(7)
    val showDays: StateFlow<Int> = _showDays.asStateFlow()

    private val _dailyTotals = MutableStateFlow<Map<LocalDate, Int>>(emptyMap())
    private val _streaks = MutableStateFlow(Pair(0, 0)) // current, best

    val uiState: StateFlow<HistoryUiState> = combine(
        _dailyTotals,
        repository.dailyGoalBottles,
        repository.bottleSizeMl,
        _showDays,
        _streaks
    ) { totals, goalBottles, bottleSize, days, streaks ->
        HistoryUiState(
            dailyTotals = totals,
            goalMl = goalBottles * bottleSize,
            bottleSizeMl = bottleSize,
            showDays = days,
            currentStreak = streaks.first,
            bestStreak = streaks.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState()
    )

    init {
        loadData()
    }

    fun toggleDays() {
        _showDays.value = if (_showDays.value == 7) 30 else 7
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val startDate = today.minusDays(29) // Always load 30 days for streaks
            val totals = repository.getDailyTotals(startDate, today)
            _dailyTotals.value = totals
            calculateStreaks(totals)
        }
    }

    private fun calculateStreaks(totals: Map<LocalDate, Int>) {
        viewModelScope.launch {
            val goalMl = uiState.value.goalMl.takeIf { it > 0 } ?: 2400
            val today = LocalDate.now()

            // Current streak (counting backwards from today)
            var currentStreak = 0
            var date = today
            while (true) {
                val total = totals[date] ?: 0
                if (total >= goalMl) {
                    currentStreak++
                    date = date.minusDays(1)
                } else {
                    break
                }
            }

            // Best streak in last 30 days
            var bestStreak = 0
            var streak = 0
            for (i in 29L downTo 0L) {
                val d = today.minusDays(i)
                val total = totals[d] ?: 0
                if (total >= goalMl) {
                    streak++
                    bestStreak = maxOf(bestStreak, streak)
                } else {
                    streak = 0
                }
            }

            _streaks.value = Pair(currentStreak, bestStreak)
        }
    }
}
