package com.google.ai.edge.gallery.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.R

/**
 * Helper class for providing haptic feedback across the app.
 * Supports different types of feedback for various user interactions.
 */
class HapticFeedbackHelper(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ContextCompat.getSystemService(context, Vibrator::class.java)
        }
    }

    private val hasVibrator: Boolean
        get() = vibrator?.hasVibrator() == true

    /**
     * Different types of haptic feedback that can be used.
     */
    enum class FeedbackType(val id: Int) {
        // Standard haptic feedback constants
        CLICK(R.integer.haptic_feedback_click),
        LONG_PRESS(R.integer.haptic_feedback_long_press),
        VIRTUAL_KEY(R.integer.haptic_feedback_virtual_key),
        KEYBOARD_TAP(R.integer.haptic_feedback_keyboard_tap),
        CLOCK_TICK(R.integer.haptic_feedback_clock_tick),
        CONFIRM(R.integer.haptic_feedback_confirm),
        REJECT(R.integer.haptic_feedback_reject),
        
        // Custom feedback types
        LIGHT_IMPACT(R.integer.haptic_feedback_light_impact),
        MEDIUM_IMPACT(R.integer.haptic_feedback_medium_impact),
        HEAVY_IMPACT(R.integer.haptic_feedback_heavy_impact),
        SUCCESS(R.integer.haptic_feedback_success),
        ERROR(R.integer.haptic_feedback_error),
        SELECTION_CHANGE(R.integer.haptic_feedback_selection_change),
        CAMERA_CLICK(R.integer.haptic_feedback_camera_click)
    }

    /**
     * Provide haptic feedback of the specified type.
     * 
     * @param type The type of haptic feedback to provide
     * @param fallback If true, will fall back to a default effect if the specific type is not available
     */
    fun performHapticFeedback(type: FeedbackType, fallback: Boolean = true) {
        if (!hasVibrator) return

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    // Use the most precise haptic feedback available
                    performHapticFeedbackApi29Plus(type, fallback)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    // Use vibration effects with duration and amplitude
                    performHapticFeedbackApi26Plus(type, fallback)
                }
                else -> {
                    // Fall back to basic vibration
                    performLegacyVibration(type, fallback)
                }
            }
        } catch (e: Exception) {
            // Ignore any errors to prevent crashes
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun performHapticFeedbackApi29Plus(type: FeedbackType, fallback: Boolean) {
        val effectId = getEffectId(type, fallback) ?: return
        val effect = VibrationEffect.createPredefined(effectId)
        vibrator?.vibrate(effect)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun performHapticFeedbackApi26Plus(type: FeedbackType, fallback: Boolean) {
        val (duration, amplitude) = getVibrationParams(type, fallback) ?: return
        val effect = VibrationEffect.createOneShot(duration, amplitude)
        vibrator?.vibrate(effect)
    }

    private fun performLegacyVibration(type: FeedbackType, fallback: Boolean) {
        val (duration, _) = getVibrationParams(type, fallback) ?: return
        @Suppress("DEPRECATION")
        vibrator?.vibrate(duration)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getEffectId(type: FeedbackType, fallback: Boolean): Int? {
        return try {
            val effectId = context.resources.getInteger(type.id)
            if (effectId == -1 && fallback) {
                // Fall back to default click effect
                context.resources.getInteger(R.integer.haptic_feedback_click)
            } else {
                effectId
            }
        } catch (e: Exception) {
            if (fallback) {
                // Fall back to default click effect
                context.resources.getInteger(R.integer.haptic_feedback_click)
            } else {
                null
            }
        }
    }

    private fun getVibrationParams(type: FeedbackType, fallback: Boolean): Pair<Long, Int>? {
        return try {
            val duration = context.resources.getInteger(type.id)
            if (duration == -1 && fallback) {
                // Fall back to default click effect
                val defaultDuration = context.resources.getInteger(R.integer.haptic_feedback_click)
                defaultDuration to 255 // Medium amplitude
            } else if (duration != -1) {
                duration.toLong() to 255 // Default amplitude
            } else {
                null
            }
        } catch (e: Exception) {
            if (fallback) {
                val defaultDuration = context.resources.getInteger(R.integer.haptic_feedback_click)
                defaultDuration.toLong() to 255
            } else {
                null
            }
        }
    }

    companion object {
        @Volatile
        private var instance: HapticFeedbackHelper? = null

        /**
         * Get the singleton instance of HapticFeedbackHelper.
         */
        fun getInstance(context: Context): HapticFeedbackHelper {
            return instance ?: synchronized(this) {
                instance ?: HapticFeedbackHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}
