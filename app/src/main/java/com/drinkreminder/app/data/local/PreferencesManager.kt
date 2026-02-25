package com.drinkreminder.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.drinkreminder.app.ui.theme.DarkModeOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager @Inject constructor(
    private val context: Context
) {
    companion object {
        val DAILY_GOAL_BOTTLES = intPreferencesKey("daily_goal_bottles")
        val BOTTLE_SIZE_ML = intPreferencesKey("bottle_size_ml")
        val ACTIVE_HOURS_START = intPreferencesKey("active_hours_start")
        val ACTIVE_HOURS_END = intPreferencesKey("active_hours_end")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val BOTTLE_DEADLINES = stringPreferencesKey("bottle_deadlines")

        const val DEFAULT_GOAL_BOTTLES = 3
        const val DEFAULT_BOTTLE_SIZE_ML = 800
        const val DEFAULT_ACTIVE_HOURS_START = 7
        const val DEFAULT_ACTIVE_HOURS_END = 20

        fun computeDefaultDeadlines(goalBottles: Int, startHour: Int, endHour: Int): List<LocalTime> {
            if (goalBottles <= 0) return emptyList()
            val totalMinutes = (endHour - startHour) * 60
            val interval = totalMinutes.toDouble() / goalBottles
            return (1..goalBottles).map { i ->
                val rawMinutes = startHour * 60 + (interval * i).toInt()
                // Round to nearest 15 minutes
                val rounded = ((rawMinutes + 7) / 15) * 15
                val h = (rounded / 60).coerceAtMost(23)
                val m = rounded % 60
                LocalTime.of(h, m)
            }
        }
    }

    val dailyGoalBottles: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[DAILY_GOAL_BOTTLES] ?: DEFAULT_GOAL_BOTTLES
    }

    val bottleSizeMl: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[BOTTLE_SIZE_ML] ?: DEFAULT_BOTTLE_SIZE_ML
    }

    val activeHoursStart: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[ACTIVE_HOURS_START] ?: DEFAULT_ACTIVE_HOURS_START
    }

    val activeHoursEnd: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[ACTIVE_HOURS_END] ?: DEFAULT_ACTIVE_HOURS_END
    }

    val darkModeOption: Flow<DarkModeOption> = context.dataStore.data.map { prefs ->
        when (prefs[DARK_MODE]) {
            "LIGHT" -> DarkModeOption.LIGHT
            "DARK" -> DarkModeOption.DARK
            else -> DarkModeOption.SYSTEM
        }
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[NOTIFICATIONS_ENABLED] ?: true
    }

    val bottleDeadlines: Flow<List<LocalTime>> = combine(
        context.dataStore.data.map { it[BOTTLE_DEADLINES] },
        dailyGoalBottles,
        activeHoursStart,
        activeHoursEnd
    ) { stored, goalBottles, startHour, endHour ->
        if (stored != null) {
            val parsed = stored.split(",").mapNotNull { s ->
                runCatching { LocalTime.parse(s.trim()) }.getOrNull()
            }
            if (parsed.size == goalBottles) parsed
            else computeDefaultDeadlines(goalBottles, startHour, endHour)
        } else {
            computeDefaultDeadlines(goalBottles, startHour, endHour)
        }
    }

    suspend fun setDailyGoalBottles(bottles: Int) {
        context.dataStore.edit { it[DAILY_GOAL_BOTTLES] = bottles }
    }

    suspend fun setBottleSizeMl(sizeMl: Int) {
        context.dataStore.edit { it[BOTTLE_SIZE_ML] = sizeMl }
    }

    suspend fun setActiveHoursStart(hour: Int) {
        context.dataStore.edit { it[ACTIVE_HOURS_START] = hour }
    }

    suspend fun setActiveHoursEnd(hour: Int) {
        context.dataStore.edit { it[ACTIVE_HOURS_END] = hour }
    }

    suspend fun setDarkMode(option: DarkModeOption) {
        context.dataStore.edit { it[DARK_MODE] = option.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setBottleDeadlines(deadlines: List<LocalTime>) {
        context.dataStore.edit {
            it[BOTTLE_DEADLINES] = deadlines.joinToString(",") { t -> t.toString() }
        }
    }

    suspend fun clearBottleDeadlines() {
        context.dataStore.edit { it.remove(BOTTLE_DEADLINES) }
    }
}
