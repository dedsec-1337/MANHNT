package com.manhunt.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Manages proximity haptic alerts for outdoor gameplay.
 * Enables Hiders to sense nearby Seekers without staring at the phone screen.
 */
class HapticFeedbackManager(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var lastVibrateTimestamp = 0L

    /**
     * Proximity Alert:
     * - Under 10m: Urgent, intense double-pulse (throttled to 1.5s)
     * - Under 30m: Muted, distinct warning pulse (throttled to 3.5s)
     */
    fun evaluateProximityHaptics(distanceMeters: Double) {
        if (vibrator == null || !vibrator.hasVibrator()) return
        val now = System.currentTimeMillis()

        when {
            distanceMeters in 0.0..10.0 -> {
                if (now - lastVibrateTimestamp >= 1500L) {
                    lastVibrateTimestamp = now
                    triggerCriticalAlert()
                }
            }
            distanceMeters in 10.01..30.0 -> {
                if (now - lastVibrateTimestamp >= 3500L) {
                    lastVibrateTimestamp = now
                    triggerNearAlert()
                }
            }
        }
    }

    /**
     * Short, distinct pulse when a Seeker is within 30 meters.
     */
    fun triggerNearAlert() {
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(180L, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(180L)
        }
    }

    /**
     * Rapid, urgent double-pulse when a Seeker is within 10 meters (<10m catch zone).
     */
    fun triggerCriticalAlert() {
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Pattern: wait 0ms, vibrate 120ms, pause 70ms, vibrate 120ms
            val timings = longArrayOf(0, 120, 70, 120)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 120, 70, 120), -1)
        }
    }

    /**
     * Haptic notification when Head Start ends and Seekers are unfrozen.
     */
    fun triggerHeadStartOver() {
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 200, 100, 200, 100, 300)
            val amplitudes = intArrayOf(0, 200, 0, 200, 0, 255)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 200, 100, 200, 100, 300), -1)
        }
    }

    /**
     * Subtle confirmation when tag or catch occurs.
     */
    fun triggerCatchConfirmation() {
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(250L, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(250L)
        }
    }
}
