package com.drinkreminder.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.drinkreminder.app.data.local.PreferencesManager
import com.drinkreminder.app.notification.AlarmService
import com.drinkreminder.app.notification.NotificationHelper
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

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Whether granted or not, schedule reminders.
        // If not granted, the alarm sound/vibration from FGS will still work,
        // only the notification text won't be visible.
        scheduleReminders()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Stop any active alarm when user opens the app.
        // Use stopService (safe no-op if not running) + cancel notification directly.
        try { stopService(Intent(this, AlarmService::class.java)) } catch (_: Exception) {}
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NotificationHelper.ALARM_NOTIFICATION_ID)

        // Create notification channels early so the system knows about them
        notificationHelper.createNotificationChannel()
        notificationHelper.createAlarmNotificationChannel()
        notificationHelper.createAlarmCheckChannel()

        // Request POST_NOTIFICATIONS permission (required on Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                scheduleReminders()
            }
        } else {
            scheduleReminders()
        }

        setContent {
            val darkMode by preferencesManager.darkModeOption
                .collectAsState(initial = DarkModeOption.SYSTEM)

            DrinkReminderTheme(darkModeOption = darkMode) {
                AppNavigation()
            }
        }
    }

    private fun scheduleReminders() {
        lifecycleScope.launch {
            try {
                reminderScheduler.rescheduleReminders()
            } catch (_: Exception) { }
        }
    }
}
