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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmService : Service() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var preferencesManager: PreferencesManager

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var autoDismissJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        const val ACTION_STOP_ALARM = "com.drinkreminder.app.ACTION_STOP_ALARM"
        const val EXTRA_TARGET_BOTTLE = "target_bottle"
        const val EXTRA_DEADLINE_TIME = "deadline_time"
        const val EXTRA_TODAY_TOTAL_ML = "today_total_ml"
        const val EXTRA_TARGET_ML = "target_ml"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarm()
            return START_NOT_STICKY
        }

        // Stop any previous alarm sound/vibration before restarting
        stopSoundAndVibration()
        autoDismissJob?.cancel()

        val targetBottle = intent?.getIntExtra(EXTRA_TARGET_BOTTLE, 0) ?: 0
        val deadlineTime = intent?.getStringExtra(EXTRA_DEADLINE_TIME) ?: ""
        val todayTotalMl = intent?.getIntExtra(EXTRA_TODAY_TOTAL_ML, 0) ?: 0
        val targetMl = intent?.getIntExtra(EXTRA_TARGET_ML, 0) ?: 0

        val notification = notificationHelper.buildAlarmNotification(
            targetBottle = targetBottle,
            deadlineTime = deadlineTime,
            todayTotalMl = todayTotalMl,
            targetMl = targetMl
        )

        startForeground(NotificationHelper.ALARM_NOTIFICATION_ID, notification)

        serviceScope.launch {
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
                autoDismissJob = launch {
                    delay(30_000L)
                    stopAlarm()
                }
            }
        }

        return START_NOT_STICKY
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
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun stopAlarm() {
        stopSoundAndVibration()
        autoDismissJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSoundAndVibration()
        autoDismissJob?.cancel()
        super.onDestroy()
    }
}
