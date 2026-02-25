package com.drinkreminder.app.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.drinkreminder.app.data.local.PreferencesManager
import com.drinkreminder.app.data.repository.DrinkRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class AlarmService : Service() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var repository: DrinkRepository

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var autoDismissJob: Job? = null
    private var autoTimeoutJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        const val ACTION_STOP_ALARM = "com.drinkreminder.app.ACTION_STOP_ALARM"
        const val EXTRA_IS_DEADLINE_CHECK = "is_deadline_check"
        const val EXTRA_TARGET_BOTTLE = "target_bottle"
        const val EXTRA_DEADLINE_TIME = "deadline_time"
        const val EXTRA_TODAY_TOTAL_ML = "today_total_ml"
        const val EXTRA_TARGET_ML = "target_ml"
        private const val AUTO_TIMEOUT_MS = 5L * 60 * 1000 // 5 minutes
    }

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarm()
            return START_NOT_STICKY
        }

        // Stop any previous alarm sound/vibration before restarting
        stopSoundAndVibration()
        autoDismissJob?.cancel()
        autoTimeoutJob?.cancel()

        val isDeadlineCheck = intent?.getBooleanExtra(EXTRA_IS_DEADLINE_CHECK, false) ?: false

        if (isDeadlineCheck) {
            handleDeadlineCheck()
        } else {
            handleDirectAlarm(intent)
        }

        return START_NOT_STICKY
    }

    /**
     * Called when AlarmReceiver starts the service for a deadline check.
     * Uses a SILENT checking notification for the initial startForeground call
     * to avoid false alarm sounds when the user is actually on track.
     */
    private fun handleDeadlineCheck() {
        // Step 1: Call startForeground IMMEDIATELY with a silent checking notification.
        // This MUST happen within 5 seconds of startForegroundService or Android kills us.
        try {
            val checkingNotification = notificationHelper.buildCheckingNotification()
            startForeground(NotificationHelper.ALARM_NOTIFICATION_ID, checkingNotification)
        } catch (e: Exception) {
            // startForeground failed — service can't run in foreground.
            // Stop self to avoid ANR. The receiver's fallback will handle the notification.
            stopSelf()
            return
        }

        // Step 2: Check DB asynchronously (service is safely in foreground now).
        serviceScope.launch {
            try {
                val notificationsEnabled = preferencesManager.notificationsEnabled.first()
                if (!notificationsEnabled) {
                    stopAlarm()
                    return@launch
                }

                val goalBottles = preferencesManager.dailyGoalBottles.first()
                val bottleSize = preferencesManager.bottleSizeMl.first()
                val todayTotal = repository.getTodayTotalOnce()
                val deadlines = preferencesManager.bottleDeadlines.first()

                val now = LocalTime.now()
                val completedBottles = todayTotal / bottleSize
                val dueBottles = deadlines.indexOfLast { !it.isAfter(now) } + 1

                if (completedBottles >= dueBottles) {
                    // User is on track — dismiss silently
                    stopAlarm()
                    return@launch
                }

                // Step 3: User is behind — upgrade to full alarm notification.
                val targetBottle = completedBottles + 1
                val deadline = deadlines.getOrNull(completedBottles)
                val targetMl = targetBottle * bottleSize

                val alarmNotification = notificationHelper.buildAlarmNotification(
                    targetBottle = targetBottle,
                    deadlineTime = deadline?.format(timeFormatter) ?: "",
                    todayTotalMl = todayTotal,
                    targetMl = targetMl,
                    silent = true // Service MediaPlayer handles looping audio
                )

                // Replace the silent checking notification with the alarm notification.
                // Using startForeground again (not notify) to avoid POST_NOTIFICATIONS issues.
                startForeground(NotificationHelper.ALARM_NOTIFICATION_ID, alarmNotification)

                // Start sound and vibration effects
                startAlarmEffects()
            } catch (_: Exception) {
                stopAlarm()
            }
        }
    }

    /**
     * Direct alarm start with pre-computed data.
     */
    private fun handleDirectAlarm(intent: Intent?) {
        val targetBottle = intent?.getIntExtra(EXTRA_TARGET_BOTTLE, 0) ?: 0
        val deadlineTime = intent?.getStringExtra(EXTRA_DEADLINE_TIME) ?: ""
        val todayTotalMl = intent?.getIntExtra(EXTRA_TODAY_TOTAL_ML, 0) ?: 0
        val targetMl = intent?.getIntExtra(EXTRA_TARGET_ML, 0) ?: 0

        try {
            val notification = notificationHelper.buildAlarmNotification(
                targetBottle = targetBottle,
                deadlineTime = deadlineTime,
                todayTotalMl = todayTotalMl,
                targetMl = targetMl,
                silent = true // Service MediaPlayer handles looping audio
            )
            startForeground(NotificationHelper.ALARM_NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            stopSelf()
            return
        }

        serviceScope.launch {
            startAlarmEffects()
        }
    }

    private suspend fun startAlarmEffects() {
        val soundEnabled = preferencesManager.alarmSound.first()
        val vibrateEnabled = preferencesManager.alarmVibrate.first()

        if (soundEnabled) {
            startAlarmSound()
        }
        if (vibrateEnabled) {
            startVibration()
        }

        // If both sound and vibrate are off, auto-dismiss after 30 seconds
        if (!soundEnabled && !vibrateEnabled) {
            autoDismissJob = serviceScope.launch {
                delay(30_000L)
                stopAlarm()
            }
        }

        // Auto-stop after 5 minutes to prevent indefinite alarm
        autoTimeoutJob = serviceScope.launch {
            delay(AUTO_TIMEOUT_MS)
            stopAlarm()
        }
    }

    private fun startAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            // Fallback: no sound if alarm tone unavailable
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 500, 500)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun stopSoundAndVibration() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {
            try { mediaPlayer?.release() } catch (_: Exception) {}
        }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun stopAlarm() {
        stopSoundAndVibration()
        autoDismissJob?.cancel()
        autoTimeoutJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSoundAndVibration()
        autoDismissJob?.cancel()
        autoTimeoutJob?.cancel()
        // Safety net: ensure foreground notification is removed even if stopAlarm() wasn't called
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }
}
