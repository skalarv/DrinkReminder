package com.drinkreminder.app.data.repository

import com.drinkreminder.app.data.local.DrinkLog
import com.drinkreminder.app.data.local.DrinkLogDao
import com.drinkreminder.app.data.local.PreferencesManager
import com.drinkreminder.app.ui.theme.DarkModeOption
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrinkRepository @Inject constructor(
    private val drinkLogDao: DrinkLogDao,
    private val preferencesManager: PreferencesManager
) {
    // Preferences
    val dailyGoalBottles: Flow<Int> = preferencesManager.dailyGoalBottles
    val bottleSizeMl: Flow<Int> = preferencesManager.bottleSizeMl
    val activeHoursStart: Flow<Int> = preferencesManager.activeHoursStart
    val activeHoursEnd: Flow<Int> = preferencesManager.activeHoursEnd
    val darkModeOption: Flow<DarkModeOption> = preferencesManager.darkModeOption
    val notificationsEnabled: Flow<Boolean> = preferencesManager.notificationsEnabled
    val bottleDeadlines: Flow<List<LocalTime>> = preferencesManager.bottleDeadlines
    val alarmVibrate: Flow<Boolean> = preferencesManager.alarmVibrate
    val alarmSound: Flow<Boolean> = preferencesManager.alarmSound

    suspend fun setDailyGoalBottles(bottles: Int) = preferencesManager.setDailyGoalBottles(bottles)
    suspend fun setActiveHoursStart(hour: Int) = preferencesManager.setActiveHoursStart(hour)
    suspend fun setActiveHoursEnd(hour: Int) = preferencesManager.setActiveHoursEnd(hour)
    suspend fun setDarkMode(option: DarkModeOption) = preferencesManager.setDarkMode(option)
    suspend fun setNotificationsEnabled(enabled: Boolean) = preferencesManager.setNotificationsEnabled(enabled)
    suspend fun setBottleDeadlines(deadlines: List<LocalTime>) = preferencesManager.setBottleDeadlines(deadlines)
    suspend fun clearBottleDeadlines() = preferencesManager.clearBottleDeadlines()
    suspend fun setAlarmVibrate(enabled: Boolean) = preferencesManager.setAlarmVibrate(enabled)
    suspend fun setAlarmSound(enabled: Boolean) = preferencesManager.setAlarmSound(enabled)

    // Drink logs
    suspend fun addDrink(amountMl: Int): Long {
        return drinkLogDao.insert(DrinkLog(amountMl = amountMl))
    }

    suspend fun deleteDrink(drinkLog: DrinkLog) {
        drinkLogDao.delete(drinkLog)
    }

    fun getTodayLogs(): Flow<List<DrinkLog>> {
        val (start, end) = getDayBounds(LocalDate.now())
        return drinkLogDao.getLogsForDate(start, end)
    }

    fun getTodayTotal(): Flow<Int> {
        val (start, end) = getDayBounds(LocalDate.now())
        return drinkLogDao.getTotalForDate(start, end)
    }

    suspend fun getTodayTotalOnce(): Int {
        val (start, end) = getDayBounds(LocalDate.now())
        return drinkLogDao.getTotalForDateOnce(start, end)
    }

    suspend fun getLogsBetweenDates(startDate: LocalDate, endDate: LocalDate): List<DrinkLog> {
        val start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return drinkLogDao.getLogsBetweenDates(start, end)
    }

    suspend fun getDailyTotals(startDate: LocalDate, endDate: LocalDate): Map<LocalDate, Int> {
        val logs = getLogsBetweenDates(startDate, endDate)
        return logs.groupBy { log ->
            java.time.Instant.ofEpochMilli(log.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }.mapValues { (_, logs) -> logs.sumOf { it.amountMl } }
    }

    private fun getDayBounds(date: LocalDate): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }
}
