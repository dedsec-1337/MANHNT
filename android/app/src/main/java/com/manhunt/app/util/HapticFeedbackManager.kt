package com.manhunt.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.manhunt.app.data.Constants

class HapticFeedbackManager(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var lastVibrateTimestamp = 0L

    fun evaluateProximityHaptics(distanceMeters: Double) {
        if (vibrator == null || !vibrator.hasVibrator()) return
        val now = System.currentTimeMillis()
        when {
            distanceMeters <= Constants.HEARTBEAT_CRITICAL_METERS -> {
                if (now - lastVibrateTimestamp >= 1200L) {
                    lastVibrateTimestamp = now
                    pulse(longArrayOf(0, 80, 50, 80, 50, 120), intArrayOf(0, 255, 0, 255, 0, 255))
                }
            }
            distanceMeters <= Constants.HEARTBEAT_NEAR_METERS -> {
                if (now - lastVibrateTimestamp >= 2500L) {
                    lastVibrateTimestamp = now
                    pulse(longArrayOf(0, 100, 60, 100), intArrayOf(0, 200, 0, 200))
                }
            }
            distanceMeters <= Constants.HEARTBEAT_MID_METERS -> {
                if (now - lastVibrateTimestamp >= 4000L) {
                    lastVibrateTimestamp = now
                    pulse(longArrayOf(0, 140), intArrayOf(0, 160))
                }
            }
            distanceMeters <= Constants.HEARTBEAT_FAR_METERS -> {
                if (now - lastVibrateTimestamp >= 6000L) {
                    lastVibrateTimestamp = now
                    pulse(longArrayOf(0, 90), intArrayOf(0, 100))
                }
            }
        }
    }

    fun triggerNearAlert() = pulse(longArrayOf(0, 180), intArrayOf(0, 180))
    fun triggerCriticalAlert() = pulse(longArrayOf(0, 120, 70, 120), intArrayOf(0, 255, 0, 255))
    fun triggerCatchConfirmation() = pulse(longArrayOf(0, 250), intArrayOf(0, 200))
    fun triggerJailbreakAlarm() = pulse(longArrayOf(0, 200, 100, 200, 100, 400), intArrayOf(0, 255, 0, 255, 0, 255))
    fun triggerPowerUp() = pulse(longArrayOf(0, 60, 40, 60), intArrayOf(0, 180, 0, 180))
    fun triggerHeadStartOver() = pulse(longArrayOf(0, 300, 100, 300), intArrayOf(0, 255, 0, 255))

    private fun pulse(timings: LongArray, amplitudes: IntArray) {
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }
}
