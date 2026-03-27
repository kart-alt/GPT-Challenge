package com.silentguard.app.model

/**
 * Represents the detection decision made by the engine
 */
enum class DistressDecision {
    NO_ALERT,           // Confidence too low
    MEDIUM_CONFIDENCE,  // Requires consecutive detections
    HIGH_CONFIDENCE     // Immediate alert
}

/**
 * Context state for environmental awareness
 */
data class ContextState(
    val ambientNoiseLevel: Float,        // dB level (0-120)
    val isRhythmicMotion: Boolean,       // Dancing, exercise detection
    val phonePosition: PhonePosition,    
    val timeOfDay: TimeOfDay,
    val userMode: UserMode = UserMode.NORMAL
)

enum class PhonePosition {
    IN_HAND,        // Low motion, screen likely active
    IN_POCKET,      // High motion, screen off
    STATIONARY,     // On table/desk
    UNKNOWN
}

enum class TimeOfDay {
    MORNING,    // 6am-12pm
    AFTERNOON,  // 12pm-6pm
    EVENING,    // 6pm-10pm
    NIGHT       // 10pm-6am (higher risk)
}

enum class UserMode {
    NORMAL,
    CONCERT_MODE,   // Suppress alerts
    GYM_MODE,       // Suppress exercise-related alerts
    SLEEP_MODE      // Reduce sensitivity
}

/**
 * Motion features extracted from accelerometer/gyroscope
 */
data class MotionFeatures(
    val accelerationMagnitude: Float,    // Average m/s²
    val jerk: Float,                     // Rate of acceleration change (m/s³)
    val variance: Float,                 // Motion variability
    val dominantFrequency: Float,        // Hz - for rhythmic detection
    val zAxisChange: Float               // Fall detection
)

/**
 * Detection result with metadata
 */
data class DetectionResult(
    val timestamp: Long,
    val audioScore: Float,
    val motionScore: Float,
    val contextScore: Float,
    val finalConfidence: Float,
    val decision: DistressDecision,
    val suppressionReason: String? = null
)

/**
 * Trusted contact for emergency alerts
 */
data class TrustedContact(
    val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val isPrimary: Boolean = false
)

/**
 * Detection log entry for history tracking
 */
data class DetectionLog(
    val id: Long = 0,
    val timestamp: Long,
    val confidence: Float,
    val wasAlertSent: Boolean,
    val wasCancelled: Boolean,
    val audioScore: Float,
    val motionScore: Float,
    val location: String? = null
)
