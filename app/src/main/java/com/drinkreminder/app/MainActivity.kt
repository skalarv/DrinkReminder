package com.drinkreminder.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.drinkreminder.app.data.local.PreferencesManager
import com.drinkreminder.app.notification.AlarmService
import com.drinkreminder.app.notification.ReminderScheduler
import com.drinkreminder.app.ui.navigation.AppNavigation
import com.drinkreminder.app.ui.theme.DarkModeOption
import com.drinkreminder.app.ui.theme.DrinkReminderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Stop any active alarm when user opens the app
        try {
            val stopAlarmIntent = Intent(this, AlarmService::class.java).apply {
                action = AlarmService.ACTION_STOP_ALARM
            }
            startService(stopAlarmIntent)
        } catch (_: Exception) {
            // Service not running — ignore
        }

        // Schedule reminders on app startup
        lifecycleScope.launch {
            reminderScheduler.rescheduleReminders()
        }

        setContent {
            val darkMode by preferencesManager.darkModeOption
                .collectAsState(initial = DarkModeOption.SYSTEM)

            DrinkReminderTheme(darkModeOption = darkMode) {
                AppNavigation()
            }
        }
    }
}
