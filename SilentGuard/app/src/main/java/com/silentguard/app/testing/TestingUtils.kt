package com.silentguard.app.testing

import android.content.Context
import android.util.Log
import com.silentguard.app.detection.*
import com.silentguard.app.model.*
import kotlinx.coroutines.*

/**
 * Testing utilities for Silent Guard
 * 
 * Use this to simulate distress scenarios and test detection logic
 * without requiring real audio/motion data
 */
class TestingUtils(private val context: Context) {

    companion object {
        private const val TAG = "TestingUtils"
    }

    /**
     * Test Scenario 1: High distress (scream + panic motion)
     * Expected: Alert triggered
     */
    fun testScenario1_HighDistress(
        decisionEngine: DecisionEngine,
        contextValidator: ContextValidator
    ): DetectionResult {
        Log.i(TAG, "=== Test Scenario 1: High Distress ===")
        
        val audioScore = 0.85f      // Strong scream detected
        val motionScore = 0.80f     // Panic running/struggle
        val context = ContextState(
            ambientNoiseLevel = 60f,
            isRhythmicMotion = false,
            phonePosition = PhonePosition.IN_POCKET,
            timeOfDay = TimeOfDay.NIGHT,
            userMode = UserMode.NORMAL
        )
        
        val result = decisionEngine.evaluateDistress(audioScore, motionScore, context)
        
        Log.i(TAG, """
            Audio: $audioScore
            Motion: $motionScore
            Decision: ${result.decision}
            Confidence: ${result.finalConfidence}
            Expected: HIGH_CONFIDENCE
            Actual: ${if (result.decision == DistressDecision.HIGH_CONFIDENCE) "✓ PASS" else "✗ FAIL"}
        """.trimIndent())
        
        return result
    }

    /**
     * Test Scenario 2: Concert false alarm
     * Expected: Alert suppressed
     */
    fun testScenario2_ConcertFalseAlarm(
        decisionEngine: DecisionEngine,
        contextValidator: ContextValidator
    ): DetectionResult {
        Log.i(TAG, "=== Test Scenario 2: Concert False Alarm ===")
        
        val audioScore = 0.70f      // Loud cheering
        val motionScore = 0.60f     // Dancing
        val context = ContextState(
            ambientNoiseLevel = 95f, // Very loud
            isRhythmicMotion = true, // Dancing
            phonePosition = PhonePosition.IN_POCKET,
            timeOfDay = TimeOfDay.EVENING,
            userMode = UserMode.NORMAL
        )
        
        val result = decisionEngine.evaluateDistress(audioScore, motionScore, context)
        
        Log.i(TAG, """
            Audio: $audioScore
            Motion: $motionScore
            Decision: ${result.decision}
            Suppressed: ${result.suppressionReason != null}
            Expected: SUPPRESSED
            Actual: ${if (result.decision == DistressDecision.NO_ALERT) "✓ PASS" else "✗ FAIL"}
        """.trimIndent())
        
        return result
    }

    /**
     * Test Scenario 3: Gym workout (rhythmic motion)
     * Expected: Alert suppressed
     */
    fun testScenario3_GymWorkout(
        decisionEngine: DecisionEngine,
        contextValidator: ContextValidator
    ): DetectionResult {
        Log.i(TAG, "=== Test Scenario 3: Gym Workout ===")
        
        contextValidator.setUserMode(UserMode.GYM_MODE)
        
        val audioScore = 0.40f      // Heavy breathing/grunting
        val motionScore = 0.75f     // Exercise motion
        val context = ContextState(
            ambientNoiseLevel = 65f,
            isRhythmicMotion = true, // Repetitive exercise
            phonePosition = PhonePosition.IN_POCKET,
            timeOfDay = TimeOfDay.MORNING,
            userMode = UserMode.GYM_MODE
        )
        
        val result = decisionEngine.evaluateDistress(audioScore, motionScore, context)
        
        Log.i(TAG, """
            Audio: $audioScore
            Motion: $motionScore
            Decision: ${result.decision}
            Mode: ${context.userMode}
            Expected: SUPPRESSED (GYM_MODE)
            Actual: ${if (result.decision == DistressDecision.NO_ALERT) "✓ PASS" else "✗ FAIL"}
        """.trimIndent())
        
        contextValidator.setUserMode(UserMode.NORMAL) // Reset
        return result
    }

    /**
     * Test Scenario 4: Low confidence (audio only, no motion)
     * Expected: No alert
     */
    fun testScenario4_LowConfidence(
        decisionEngine: DecisionEngine,
        contextValidator: ContextValidator
    ): DetectionResult {
        Log.i(TAG, "=== Test Scenario 4: Low Confidence ===")
        
        val audioScore = 0.65f      // Some distress sound
        val motionScore = 0.10f     // Almost stationary
        val context = ContextState(
            ambientNoiseLevel = 50f,
            isRhythmicMotion = false,
            phonePosition = PhonePosition.STATIONARY,
            timeOfDay = TimeOfDay.AFTERNOON,
            userMode = UserMode.NORMAL
        )
        
        val result = decisionEngine.evaluateDistress(audioScore, motionScore, context)
        
        Log.i(TAG, """
            Audio: $audioScore
            Motion: $motionScore
            Decision: ${result.decision}
            Confidence: ${result.finalConfidence}
            Expected: NO_ALERT (low motion)
            Actual: ${if (result.decision == DistressDecision.NO_ALERT) "✓ PASS" else "✗ FAIL"}
        """.trimIndent())
        
        return result
    }

    /**
     * Test Scenario 5: Night time bonus (increased sensitivity)
     * Expected: Alert triggered
     */
    fun testScenario5_NightBonus(
        decisionEngine: DecisionEngine,
        contextValidator: ContextValidator
    ): DetectionResult {
        Log.i(TAG, "=== Test Scenario 5: Night Time Bonus ===")
        
        val audioScore = 0.60f      // Moderate distress
        val motionScore = 0.55f     // Some panic motion
        val context = ContextState(
            ambientNoiseLevel = 40f, // Quiet night
            isRhythmicMotion = false,
            phonePosition = PhonePosition.IN_POCKET,
            timeOfDay = TimeOfDay.NIGHT, // Night = bonus
            userMode = UserMode.NORMAL
        )
        
        val result = decisionEngine.evaluateDistress(audioScore, motionScore, context)
        
        Log.i(TAG, """
            Audio: $audioScore
            Motion: $motionScore
            Time: ${context.timeOfDay}
            Decision: ${result.decision}
            Confidence: ${result.finalConfidence}
            Expected: MEDIUM/HIGH (night bonus)
            Actual: ${if (result.finalConfidence > 0.5f) "✓ PASS" else "✗ FAIL"}
        """.trimIndent())
        
        return result
    }

    /**
     * Run all test scenarios
     */
    fun runAllTests(
        decisionEngine: DecisionEngine,
        contextValidator: ContextValidator
    ) {
        Log.i(TAG, "\n\n========================================")
        Log.i(TAG, "RUNNING ALL TEST SCENARIOS")
        Log.i(TAG, "========================================\n")
        
        val results = mutableListOf<Pair<String, Boolean>>()
        
        // Test 1
        val test1 = testScenario1_HighDistress(decisionEngine, contextValidator)
        results.add("High Distress" to (test1.decision == DistressDecision.HIGH_CONFIDENCE))
        
        delay(100)
        
        // Test 2
        val test2 = testScenario2_ConcertFalseAlarm(decisionEngine, contextValidator)
        results.add("Concert False Alarm" to (test2.decision == DistressDecision.NO_ALERT))
        
        delay(100)
        
        // Test 3
        val test3 = testScenario3_GymWorkout(decisionEngine, contextValidator)
        results.add("Gym Workout" to (test3.decision == DistressDecision.NO_ALERT))
        
        delay(100)
        
        // Test 4
        val test4 = testScenario4_LowConfidence(decisionEngine, contextValidator)
        results.add("Low Confidence" to (test4.decision == DistressDecision.NO_ALERT))
        
        delay(100)
        
        // Test 5
        val test5 = testScenario5_NightBonus(decisionEngine, contextValidator)
        results.add("Night Bonus" to (test5.finalConfidence > 0.5f))
        
        // Summary
        Log.i(TAG, "\n\n========================================")
        Log.i(TAG, "TEST SUMMARY")
        Log.i(TAG, "========================================")
        
        var passed = 0
        results.forEach { (name, success) ->
            Log.i(TAG, "${if (success) "✓" else "✗"} $name")
            if (success) passed++
        }
        
        Log.i(TAG, "\nPassed: $passed/${results.size}")
        Log.i(TAG, "========================================\n\n")
    }

    /**
     * Generate synthetic audio data (for testing without model)
     */
    fun generateSyntheticAudio(type: AudioType): FloatArray {
        val duration = 2.0 // seconds
        val sampleRate = 16000
        val samples = (duration * sampleRate).toInt()
        val audio = FloatArray(samples)
        
        when (type) {
            AudioType.DISTRESS_SCREAM -> {
                // High frequency, high amplitude, irregular
                for (i in audio.indices) {
                    val t = i.toFloat() / sampleRate
                    audio[i] = (0.8 * kotlin.math.sin(2 * Math.PI * 1500 * t) + 
                               0.3 * kotlin.math.sin(2 * Math.PI * 2800 * t) +
                               0.1 * Math.random().toFloat()).toFloat()
                }
            }
            AudioType.NORMAL_SPEECH -> {
                // Moderate frequency, moderate amplitude
                for (i in audio.indices) {
                    val t = i.toFloat() / sampleRate
                    audio[i] = (0.3 * kotlin.math.sin(2 * Math.PI * 200 * t)).toFloat()
                }
            }
            AudioType.LAUGHTER -> {
                // Varied frequency, rhythmic
                for (i in audio.indices) {
                    val t = i.toFloat() / sampleRate
                    val modulation = kotlin.math.sin(2 * Math.PI * 5 * t)
                    audio[i] = (0.5 * kotlin.math.sin(2 * Math.PI * 400 * t) * modulation).toFloat()
                }
            }
            AudioType.SILENCE -> {
                // Low amplitude noise
                for (i in audio.indices) {
                    audio[i] = (0.01 * Math.random()).toFloat()
                }
            }
        }
        
        return audio
    }

    enum class AudioType {
        DISTRESS_SCREAM,
        NORMAL_SPEECH,
        LAUGHTER,
        SILENCE
    }

    private fun delay(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            // Ignore
        }
    }
}

/**
 * Extension to easily add test button to MainActivity
 */
fun createTestButton(context: Context): android.widget.Button {
    return android.widget.Button(context).apply {
        text = "Run Tests"
        setOnClickListener {
            // Initialize components
            val audioClassifier = AudioClassifier(context)
            val motionAnalyzer = MotionAnalyzer(context)
            val contextValidator = ContextValidator(context, audioClassifier, motionAnalyzer)
            val decisionEngine = DecisionEngine(context, contextValidator)
            
            // Run tests
            val testingUtils = TestingUtils(context)
            testingUtils.runAllTests(decisionEngine, contextValidator)
            
            android.widget.Toast.makeText(
                context, 
                "Tests completed. Check Logcat for results.", 
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}
