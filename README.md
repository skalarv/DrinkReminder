# DrinkReminder

A smart Android water-tracking app that helps you stay hydrated with **per-bottle deadline reminders**. Instead of generic "drink water" alerts, DrinkReminder assigns a deadline to each bottle and escalates notifications as deadlines approach or pass.

## Features

### Alarm-Clock Deadline Alerts
- When a bottle deadline is missed, a **full alarm fires** with looping sound and vibration
- Ongoing notification with **Snooze (10 min)** and **Stop** action buttons
- Alarm auto-stops after 5 minutes
- Opening the app silences any active alarm
- Configurable sound and vibration toggles in Settings
- **Test Alarm** button in Settings to verify the alarm system works

### Deadline-Based Reminders
- Each bottle gets its own deadline spread across your active hours (e.g., Bottle 1 by 11:15, Bottle 2 by 15:30, Bottle 3 by 20:00)
- **On schedule**: reminders at halfway point, 30 minutes before, and 10 minutes before each deadline
- **Behind schedule**: immediate urgent notification + repeat every 30 minutes until caught up
- Deadlines auto-compute when you change your bottle count or active hours
- Fully customizable per-bottle deadlines via time pickers in Settings

### Water Tracking
- One-tap "Add Bottle" button for quick logging
- Custom amount entry for partial fills
- Circular progress ring showing bottles completed vs. daily goal
- "Next: Bottle N by HH:MM" indicator on the home screen (turns red when behind)
- Today's drink log with swipe-to-delete

### History
- 7-day bar chart showing daily intake trends
- Daily totals with goal comparison

### Home Screen Widget
- Glance-based widget showing current progress
- Quick-add bottle action directly from the home screen

### Settings
- **Daily goal**: 3-10 bottles (configurable)
- **Bottle size**: 800ml default
- **Active hours**: customizable start/end (default 07:00-20:00)
- **Bottle deadlines**: per-bottle time pickers with "Reset to defaults"
- **Deadline alerts**: sound on/off, vibrate on/off, test alarm button
- **Appearance**: System / Light / Dark theme
- **Notifications**: enable/disable toggle
- **Version**: displayed at bottom of settings page

## Architecture

```
app/src/main/java/com/drinkreminder/app/
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt          # Room database
│   │   ├── DrinkLog.kt             # Entity: drink log entry
│   │   ├── DrinkLogDao.kt          # DAO: queries for drink logs
│   │   └── PreferencesManager.kt   # DataStore: all user preferences
│   └── repository/
│       └── DrinkRepository.kt      # Single source of truth for data
├── di/
│   └── AppModule.kt                # Hilt dependency injection module
├── notification/
│   ├── AlarmActionReceiver.kt      # Handles Snooze/Stop notification actions
│   ├── AlarmReceiver.kt            # Receives alarm broadcasts, starts AlarmService
│   ├── AlarmService.kt             # Foreground service: alarm sound + vibration
│   ├── BootReceiver.kt             # Reschedules alarms after device reboot
│   ├── NotificationHelper.kt       # Builds & shows notifications (3 channels)
│   ├── ReminderScheduler.kt        # Deadline-aware AlarmManager scheduling
│   └── ReminderWorker.kt           # Periodic WorkManager fallback
├── ui/
│   ├── components/
│   │   ├── BarChart.kt             # History bar chart
│   │   ├── CircularProgress.kt     # Home screen progress ring
│   │   └── DrinkLogItem.kt         # Drink log list item
│   ├── home/
│   │   ├── HomeScreen.kt           # Main screen with progress & actions
│   │   └── HomeViewModel.kt        # Home state management
│   ├── history/
│   │   ├── HistoryScreen.kt        # Weekly history view
│   │   └── HistoryViewModel.kt     # History state management
│   ├── settings/
│   │   ├── SettingsScreen.kt       # Settings with deadline time pickers
│   │   └── SettingsViewModel.kt    # Settings state management
│   ├── navigation/
│   │   └── AppNavigation.kt        # Bottom nav: Home / History / Settings
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
├── widget/
│   ├── DrinkWidget.kt              # Glance widget UI
│   └── DrinkWidgetReceiver.kt      # Widget broadcast receiver
├── DrinkReminderApp.kt             # Application class (Hilt entry point)
└── MainActivity.kt                 # Single activity host
```

### Alarm System Architecture

```
setAlarmClock() at deadline + 90s
  → AlarmReceiver.onReceive() [synchronous]
    → startForegroundService(AlarmService)
      → startForeground() with silent checking notification [immediate]
        → DB check in coroutine
          → Behind? Upgrade to alarm notification + MediaPlayer + Vibrator
          → On track? stopSelf() silently

Snooze → AlarmActionReceiver → setAlarmClock() +10min → same chain
Stop → AlarmActionReceiver → stops AlarmService + cancels notification
Reboot → BootReceiver → rescheduleReminders()
App open → stopService + cancel notification
Auto-timeout → 5 minutes
```

### Pattern: MVVM + Repository

- **View**: Jetpack Compose screens observe `StateFlow<UiState>` from ViewModels
- **ViewModel**: Combines data flows, exposes actions, triggers reminder rescheduling
- **Repository**: Mediates between Room DAO (drink logs) and DataStore (preferences)
- **AlarmManager**: Exact deadline alarms via `setAlarmClock()` (guaranteed delivery + FGS exemption)
- **WorkManager**: Periodic fallback worker every hour for edge cases

### Reminder Algorithm

1. Compute how many bottles are completed today
2. Find the next deadline from the deadlines list
3. If **on schedule**: schedule reminders at halfway, 30 min before, and 10 min before the deadline (minimum 10-min spacing, deduplicated)
4. If **behind schedule** (past the deadline): fire an immediate urgent reminder, then repeat every 30 min until the next future deadline or end of active hours
5. At each deadline + 90 seconds: fire a deadline-check alarm that triggers the full alarm-clock experience if behind
6. A periodic fallback worker runs every hour to catch edge cases

## Tech Stack

| Component | Library | Version |
|-----------|---------|---------|
| Language | Kotlin | 2.0.21 |
| UI | Jetpack Compose + Material 3 | BOM 2024.11 |
| Database | Room | 2.6.1 |
| Dependency Injection | Hilt | 2.52 |
| Background Work | WorkManager | 2.10.0 |
| Preferences | DataStore | 1.1.1 |
| Widget | Glance | 1.1.1 |
| Navigation | Navigation Compose | 2.8.4 |
| Build | AGP 8.7.3, Gradle 8.9, KSP 2.0.21 | |

## Requirements

- Android 12+ (API 31)
- JDK 17

## Build

```bash
# Clone the repository
git clone https://github.com/skalarv/DrinkReminder.git
cd DrinkReminder

# Build debug APK
./gradlew assembleDebug

# APK output
# app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio and click Run.

## Permissions

| Permission | Purpose |
|------------|---------|
| `POST_NOTIFICATIONS` | Show drink reminders and alarm notifications (runtime request on Android 13+) |
| `SCHEDULE_EXACT_ALARM` | Schedule precise reminder times (fallback for passive reminders) |
| `RECEIVE_BOOT_COMPLETED` | Restore reminders after device reboot |
| `VIBRATE` | Alarm vibration when deadline is missed |
| `FOREGROUND_SERVICE` | Alarm service with looping sound and vibration |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Required for alarm-style foreground service |
| `USE_FULL_SCREEN_INTENT` | Full-screen alarm notification on lock screen |

## License

This project is provided as-is for personal use.
