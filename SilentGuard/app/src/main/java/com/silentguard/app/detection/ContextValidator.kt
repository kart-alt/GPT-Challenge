package com.silentguard.app.detection

import android.content.Context
import android.util.Log
import com.silentguard.app.model.*
import java.util.Calendar

/**
 * Context-aware validation to prevent false alarms
 * 
 * Analyzes environmental and situational factors:
 * - Ambient noise levels
 * - Motion patterns
 * - Time of day
 * - User-set modes
 */
class ContextValidator(
    private val context: Context,
    private val audioClassifier: AudioClassifier,
    private val motionAnalyzer: MotionAnalyzer
) {

    private var currentUserMode = UserMode.NORMAL
    private val motionDurationTracker = mutableMapOf<String, Long>()

    companion object {
        private const val TAG = "ContextValidator"
        
        // Noise level thresholds
        private const val CONCERT_NOISE_THRESHOLD = 85f  // dB
        private const val HIGH_AMBIENT_THRESHOLD = 75f   // dB
        
        // Motion duration for exercise detection
        private const val EXERCISE_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    }

    /**
     * Get current context state
     */
    fun getCurrentContext(): ContextState {
        val ambientNoise = audioClassifier.getAmbientNoiseLevel()
        val isRhythmic = motionAnalyzer.isRhythmicMotion()
        val phonePosition = motionAnalyzer.detectPhonePosition()
        val timeOfDay = getTimeOfDay()
        
        return ContextState(
            ambientNoiseLevel = ambientNoise,
            isRhythmicMotion = isRhythmic,
            phonePosition = phonePosition,
            timeOfDay = timeOfDay,
            userMode = currentUserMode
        )
    }

    /**
     * Determine if alert should be suppressed based on context
     */
    fun shouldSuppressAlert(contextState: ContextState): Boolean {
        // 1. User explicitly set a mode that suppresses alerts
        when (contextState.userMode) {
            UserMode.CONCERT_MODE -> {
                Log.d(TAG, "Alert suppressed: CONCERT_MODE active")
                return true
            }
            UserMode.GYM_MODE -> {
                if (contextState.isRhythmicMotion) {
                    Log.d(TAG, "Alert suppressed: GYM_MODE + rhythmic motion")
                    return true
                }
            }
            UserMode.SLEEP_MODE -> {
                // Increase detection threshold in sleep mode
                // (handled in DecisionEngine)
            }
            UserMode.NORMAL -> {
                // Continue with other checks
            }
        }
        
        // 2. Concert/festival environment (loud + rhythmic motion)
        if (contextState.ambientNoiseLevel > CONCERT_NOISE_THRESHOLD && 
            contextState.isRhythmicMotion) {
            Log.d(TAG, "Alert suppressed: Concert/festival environment detected")
            return true
        }
        
        // 3. Sustained exercise pattern
        if (contextState.isRhythmicMotion && 
            contextState.phonePosition == PhonePosition.IN_POCKET) {
            
            val duration = trackMotionDuration("exercise", contextState.isRhythmicMotion)
            
            if (duration > EXERCISE_DURATION_MS) {
                Log.d(TAG, "Alert suppressed: Sustained exercise detected (${duration}ms)")
                return true
            }
        }
        
        // 4. Very high ambient noise alone (could be machinery, traffic)
        if (contextState.ambientNoiseLevel > HIGH_AMBIENT_THRESHOLD) {
            Log.d(TAG, "Alert sensitivity reduced: High ambient noise (${contextState.ambientNoiseLevel} dB)")
            // Don't fully suppress, but note this (DecisionEngine will handle)
        }
        
        return false
    }

    /**
     * Compute context bonus score (0-1)
     * Higher score = more likely to be genuine emergency
     */
    fun computeContextScore(contextState: ContextState): Float {
        var score = 0f
        
        // 1. Time of day (night = higher risk)
        score += when (contextState.timeOfDay) {
            TimeOfDay.NIGHT -> 0.4f
            TimeOfDay.EVENING -> 0.2f
            TimeOfDay.MORNING -> 0.1f
            TimeOfDay.AFTERNOON -> 0.0f
        }
        
        // 2. Phone position (in pocket = less likely to be intentional)
        score += when (contextState.phonePosition) {
            PhonePosition.IN_POCKET -> 0.3f
            PhonePosition.IN_HAND -> 0.1f
            PhonePosition.STATIONARY -> 0.0f
            PhonePosition.UNKNOWN -> 0.1f
        }
        
        // 3. Low ambient noise (easier to hear genuine distress)
        if (contextState.ambientNoiseLevel < 60f) {
            score += 0.2f
        }
        
        // 4. Non-rhythmic motion (not exercise)
        if (!contextState.isRhythmicMotion) {
            score += 0.1f
        }
        
        return score.coerceIn(0f, 1f)
    }

    /**
     * Get current time of day
     */
    private fun getTimeOfDay(): TimeOfDay {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        return when (hour) {
            in 6..11 -> TimeOfDay.MORNING
            in 12..17 -> TimeOfDay.AFTERNOON
            in 18..21 -> TimeOfDay.EVENING
            else -> TimeOfDay.NIGHT
        }
    }

    /**
     * Track duration of continuous motion pattern
     */
    private fun trackMotionDuration(pattern: String, isActive: Boolean): Long {
        val now = System.currentTimeMillis()
        
        if (isActive) {
            if (!motionDurationTracker.containsKey(pattern)) {
                motionDurationTracker[pattern] = now
            }
            return now - motionDurationTracker[pattern]!!
        } else {
            motionDurationTracker.remove(pattern)
            return 0L
        }
    }

    /**
     * Set user mode manually
     */
    fun setUserMode(mode: UserMode) {
        currentUserMode = mode
        Log.i(TAG, "User mode changed to: $mode")
    }

    /**
     * Get current user mode
     */
    fun getUserMode(): UserMode = currentUserMode

    /**
     * Smart mode detection (auto-detect gym, concert, etc.)
     */
    fun autoDetectMode(): UserMode? {
        val context = getCurrentContext()
        
        // Concert detection
        if (context.ambientNoiseLevel > CONCERT_NOISE_THRESHOLD && 
            context.isRhythmicMotion) {
            return UserMode.CONCERT_MODE
        }
        
        // Gym detection (sustained rhythmic motion)
        if (context.isRhythmicMotion && 
            context.phonePosition == PhonePosition.IN_POCKET) {
            val duration = trackMotionDuration("exercise", true)
            if (duration > 2 * 60 * 1000L) { // 2 minutes
                return UserMode.GYM_MODE
            }
        }
        
        return null
    }

    /**
     * Get context explanation for debugging
     */
    fun getContextExplanation(contextState: ContextState): String {
        return buildString {
            appendLine("Context Analysis:")
            appendLine("- Time: ${contextState.timeOfDay}")
            appendLine("- Noise: ${contextState.ambientNoiseLevel.toInt()} dB")
            appendLine("- Position: ${contextState.phonePosition}")
            appendLine("- Rhythmic: ${contextState.isRhythmicMotion}")
            appendLine("- Mode: ${contextState.userMode}")
            
            if (shouldSuppressAlert(contextState)) {
                appendLine("⚠️ Alert would be SUPPRESSED")
            } else {
                appendLine("✓ Alert would be ALLOWED")
            }
        }
    }

    /**
     * Reset tracking state
     */
    fun reset() {
        motionDurationTracker.clear()
        currentUserMode = UserMode.NORMAL
    }
}
