package com.silentguard.app.detection

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.silentguard.app.model.*

/**
 * Core decision-making engine that fuses multiple signals
 * 
 * Combines:
 * - Audio classification score
 * - Motion analysis score
 * - Environmental context
 * 
 * Outputs: Emergency confidence score + decision
 */
class DecisionEngine(
    private val androidContext: Context,
    private val contextValidator: ContextValidator
) {

    // Weights for multi-signal fusion
    private var audioWeight = 0.50f
    private var motionWeight = 0.30f
    private var contextWeight = 0.20f
    
    // Detection thresholds (Tuned for testing/demo)
    private var highConfidenceThreshold = 0.10f
    private var mediumConfidenceThreshold = 0.05f
    
    // Consecutive detection tracking
    private val consecutiveDetections = ArrayDeque<DistressDecision>(3)
    private val detectionHistory = mutableListOf<DetectionResult>()
    
    // Rate limiting (prevent spam)
    private var lastAlertTime = 0L
    private val minAlertInterval = 30_000L // 30 seconds between alerts

    companion object {
        private const val TAG = "DecisionEngine"
        private const val MAX_HISTORY_SIZE = 100
    }

    /**
     * Main evaluation function: determine if distress is detected
     * 
     * @return DetectionResult with confidence score and decision
     */
    fun evaluateDistress(
        audioScore: Float,
        motionScore: Float,
        context: ContextState
    ): DetectionResult {
        
        val timestamp = System.currentTimeMillis()
        
        // Step 1: Check if context suppresses alert
        if (contextValidator.shouldSuppressAlert(context)) {
            val result = DetectionResult(
                timestamp = timestamp,
                audioScore = audioScore,
                motionScore = motionScore,
                contextScore = 0f,
                finalConfidence = 0f,
                decision = DistressDecision.NO_ALERT,
                suppressionReason = "Context suppression: ${context.userMode}"
            )
            
            recordDetection(result)
            return result
        }
        
        // Step 2: Compute context bonus
        val contextScore = contextValidator.computeContextScore(context)
        
        // Step 3: Apply mode-specific adjustments
        val (adjustedAudio, adjustedMotion) = applyModeAdjustments(
            audioScore, 
            motionScore, 
            context.userMode
        )
        
        // Step 4: Weighted fusion
        val rawConfidence = 
            (adjustedAudio * audioWeight) +
            (adjustedMotion * motionWeight) +
            (contextScore * contextWeight)
        
        // Step 5: Apply temporal smoothing
        val smoothedConfidence = temporalSmoothing(rawConfidence)
        
        // Step 6: Make decision
        val decision = makeDecision(smoothedConfidence)
        
        // Step 7: Validate with consecutive detection logic
        val finalDecision = validateWithHistory(decision)
        
        val result = DetectionResult(
            timestamp = timestamp,
            audioScore = adjustedAudio,
            motionScore = adjustedMotion,
            contextScore = contextScore,
            finalConfidence = smoothedConfidence,
            decision = finalDecision,
            suppressionReason = null
        )
        
        recordDetection(result)
        logDecision(result, audioScore, motionScore, context)
        
        return result
    }

    /**
     * Apply user mode adjustments to sensitivity
     */
    private fun applyModeAdjustments(
        audioScore: Float,
        motionScore: Float,
        mode: UserMode
    ): Pair<Float, Float> {
        return when (mode) {
            UserMode.SLEEP_MODE -> {
                // Increase threshold (reduce sensitivity) during sleep
                Pair(audioScore * 0.8f, motionScore * 0.8f)
            }
            UserMode.GYM_MODE -> {
                // Reduce motion weight in gym
                Pair(audioScore, motionScore * 0.5f)
            }
            UserMode.CONCERT_MODE -> {
                // Should be suppressed earlier, but reduce if it gets here
                Pair(audioScore * 0.3f, motionScore * 0.5f)
            }
            UserMode.NORMAL -> {
                Pair(audioScore, motionScore)
            }
        }
    }

    /**
     * Temporal smoothing to reduce jitter
     */
    private fun temporalSmoothing(currentScore: Float): Float {
        // Use exponential moving average
        val alpha = 0.3f // Smoothing factor
        
        if (detectionHistory.isEmpty()) {
            return currentScore
        }
        
        val previousScore = detectionHistory.lastOrNull()?.finalConfidence ?: currentScore
        return alpha * currentScore + (1 - alpha) * previousScore
    }

    /**
     * Make initial decision based on confidence threshold
     */
    private fun makeDecision(confidence: Float): DistressDecision {
        return when {
            confidence >= highConfidenceThreshold -> DistressDecision.HIGH_CONFIDENCE
            confidence >= mediumConfidenceThreshold -> DistressDecision.MEDIUM_CONFIDENCE
            else -> DistressDecision.NO_ALERT
        }
    }

    /**
     * Validate decision with consecutive detection logic
     * 
     * - HIGH_CONFIDENCE: Trigger immediately
     * - MEDIUM_CONFIDENCE: Require 2 consecutive medium+ detections
     * - NO_ALERT: Clear history
     */
    private fun validateWithHistory(decision: DistressDecision): DistressDecision {
        consecutiveDetections.add(decision)
        
        if (consecutiveDetections.size > 3) {
            consecutiveDetections.removeFirst()
        }
        
        return when (decision) {
            DistressDecision.HIGH_CONFIDENCE -> {
                // Immediate alert
                DistressDecision.HIGH_CONFIDENCE
            }
            
            DistressDecision.MEDIUM_CONFIDENCE -> {
                // Check if we have 2+ medium/high detections in recent history
                val recentMediumOrHigh = consecutiveDetections.count { 
                    it == DistressDecision.MEDIUM_CONFIDENCE || 
                    it == DistressDecision.HIGH_CONFIDENCE 
                }
                
                if (recentMediumOrHigh >= 2) {
                    Log.i(TAG, "Medium confidence upgraded to HIGH (consecutive detections)")
                    DistressDecision.HIGH_CONFIDENCE
                } else {
                    DistressDecision.NO_ALERT
                }
            }
            
            DistressDecision.NO_ALERT -> {
                DistressDecision.NO_ALERT
            }
        }
    }

    /**
     * Check if enough time has passed since last alert (rate limiting)
     */
    fun shouldTriggerAlert(): Boolean {
        val now = System.currentTimeMillis()
        
        if (now - lastAlertTime < minAlertInterval) {
            Log.d(TAG, "Alert rate limited (too soon since last alert)")
            return false
        }
        
        lastAlertTime = now
        return true
    }

    /**
     * Record detection in history
     */
    private fun recordDetection(result: DetectionResult) {
        detectionHistory.add(result)
        
        // Keep history size manageable
        if (detectionHistory.size > MAX_HISTORY_SIZE) {
            detectionHistory.removeAt(0)
        }

        // Save to SharedPreferences for History view
        saveHistoryToPrefs()
    }

    private fun saveHistoryToPrefs() {
        try {
            val prefs = androidContext.getSharedPreferences("silent_guard_prefs", Context.MODE_PRIVATE)
            val gson = Gson()
            val json = gson.toJson(detectionHistory)
            prefs.edit().putString("detection_history", json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save history: ${e.message}")
        }
    }

    /**
     * Get detection statistics
     */
    fun getStatistics(): DetectionStatistics {
        if (detectionHistory.isEmpty()) {
            return DetectionStatistics(0, 0, 0, 0f, 0f)
        }
        
        val totalDetections = detectionHistory.size
        val highConfidenceCount = detectionHistory.count { 
            it.decision == DistressDecision.HIGH_CONFIDENCE 
        }
        val suppressedCount = detectionHistory.count { 
            it.suppressionReason != null 
        }
        val avgConfidence = detectionHistory.map { it.finalConfidence }.average().toFloat()
        val avgAudioScore = detectionHistory.map { it.audioScore }.average().toFloat()
        
        return DetectionStatistics(
            totalDetections = totalDetections,
            highConfidenceCount = highConfidenceCount,
            suppressedCount = suppressedCount,
            averageConfidence = avgConfidence,
            averageAudioScore = avgAudioScore
        )
    }

    /**
     * Get recent detection history
     */
    fun getRecentHistory(count: Int = 10): List<DetectionResult> {
        return detectionHistory.takeLast(count)
    }

    /**
     * Update detection weights (for tuning)
     */
    fun updateWeights(audio: Float, motion: Float, context: Float) {
        val sum = audio + motion + context
        audioWeight = audio / sum
        motionWeight = motion / sum
        contextWeight = context / sum
        
        Log.i(TAG, "Weights updated: audio=$audioWeight, motion=$motionWeight, context=$contextWeight")
    }

    /**
     * Update thresholds (for tuning)
     */
    fun updateThresholds(high: Float, medium: Float) {
        highConfidenceThreshold = high
        mediumConfidenceThreshold = medium
        
        Log.i(TAG, "Thresholds updated: high=$high, medium=$medium")
    }

    /**
     * Set sensitivity from the Settings screen (0.0 = least sensitive, 1.0 = most sensitive).
     * Maps to high-confidence threshold range: 0.25 (low sensitivity) → 0.04 (high sensitivity).
     */
    fun setSensitivity(sensitivity: Float) {
        val clamped = sensitivity.coerceIn(0f, 1f)
        // Inverse: higher sensitivity → lower threshold needed to trigger
        highConfidenceThreshold = 0.25f - (clamped * 0.21f)   // 0.25 → 0.04
        mediumConfidenceThreshold = 0.10f - (clamped * 0.08f) // 0.10 → 0.02
        Log.i(TAG, "Sensitivity set to $clamped → high=$highConfidenceThreshold, medium=$mediumConfidenceThreshold")
    }

    /**
     * Reset state
     */
    fun reset() {
        consecutiveDetections.clear()
        detectionHistory.clear()
        lastAlertTime = 0L
    }

    /**
     * Detailed logging for debugging
     */
    private fun logDecision(
        result: DetectionResult,
        rawAudio: Float,
        rawMotion: Float,
        context: ContextState
    ) {
        Log.d(TAG, """
            |═══════════════════════════════════════
            |DETECTION EVALUATION
            |───────────────────────────────────────
            |Audio:   ${rawAudio.format()} → ${result.audioScore.format()} (weight: $audioWeight)
            |Motion:  ${rawMotion.format()} → ${result.motionScore.format()} (weight: $motionWeight)
            |Context: ${result.contextScore.format()} (weight: $contextWeight)
            |───────────────────────────────────────
            |Final Confidence: ${result.finalConfidence.format()}
            |Decision: ${result.decision}
            |───────────────────────────────────────
            |Context Details:
            |  - Time: ${context.timeOfDay}
            |  - Noise: ${context.ambientNoiseLevel.toInt()} dB
            |  - Position: ${context.phonePosition}
            |  - Mode: ${context.userMode}
            |═══════════════════════════════════════
        """.trimMargin())
    }

    private fun Float.format() = "%.3f".format(this)
}

/**
 * Detection statistics data class
 */
data class DetectionStatistics(
    val totalDetections: Int,
    val highConfidenceCount: Int,
    val suppressedCount: Int,
    val averageConfidence: Float,
    val averageAudioScore: Float
)
