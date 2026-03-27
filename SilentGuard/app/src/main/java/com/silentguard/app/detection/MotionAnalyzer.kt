package com.silentguard.app.detection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.silentguard.app.model.MotionFeatures
import com.silentguard.app.model.PhonePosition
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * Motion-based distress detection using accelerometer and gyroscope
 * 
 * Detects:
 * - Panic running (erratic acceleration)
 * - Struggle/shaking patterns
 * - Falls (sudden downward motion)
 * - Phone in pocket during chaos
 */
class MotionAnalyzer(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    
    private var isMonitoring = false
    private var onMotionDetected: ((Float) -> Unit)? = null
    
    // Circular buffers for sensor data (store last 2 seconds at 50Hz = 100 samples)
    private val bufferSize = 100
    private val accelBuffer = CircularBuffer<Vector3>(bufferSize)
    private val gyroBuffer = CircularBuffer<Vector3>(bufferSize)
    
    // Thresholds (tune during testing)
    private val PANIC_JERK_THRESHOLD = 15.0f        // m/s³
    private val HIGH_VARIANCE_THRESHOLD = 8.0f      
    private val SHAKE_FREQ_MIN = 5.0f               // Hz
    private val SHAKE_FREQ_MAX = 12.0f              // Hz
    private val FALL_Z_THRESHOLD = -5.0f            // Downward acceleration
    private val STRUGGLE_ROTATION_THRESHOLD = 3.0f  // rad/s
    
    private val detectionThreshold = 0.5f
    private val scoreHistory = ArrayDeque<Float>(5)

    companion object {
        private const val TAG = "MotionAnalyzer"
        private const val SAMPLE_RATE_US = 20_000 // 50Hz (20ms intervals)
    }

    data class Vector3(val x: Float, val y: Float, val z: Float) {
        fun magnitude() = sqrt(x*x + y*y + z*z)
        operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    }

    /**
     * Start monitoring motion sensors
     */
    fun startMonitoring(callback: (Float) -> Unit) {
        if (isMonitoring) return
        
        onMotionDetected = callback
        
        sensorManager.registerListener(
            this, 
            accelerometer, 
            SAMPLE_RATE_US
        )
        
        sensorManager.registerListener(
            this, 
            gyroscope, 
            SAMPLE_RATE_US
        )
        
        isMonitoring = true
        
        // Periodic analysis
        CoroutineScope(Dispatchers.Default).launch {
            while (isMonitoring) {
                delay(500) // Analyze every 500ms
                val motionScore = detectPanicMotion()
                
                scoreHistory.add(motionScore)
                if (scoreHistory.size > 5) {
                    scoreHistory.removeFirst()
                }
                
                val avgScore = scoreHistory.average().toFloat()
                
                if (avgScore > detectionThreshold) {
                    onMotionDetected?.invoke(avgScore)
                }
            }
        }
        
        Log.i(TAG, "Motion monitoring started")
    }

    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        isMonitoring = false
        sensorManager.unregisterListener(this)
        onMotionDetected = null
        Log.i(TAG, "Motion monitoring stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val vector = Vector3(event.values[0], event.values[1], event.values[2])
                accelBuffer.add(vector)
            }
            Sensor.TYPE_GYROSCOPE -> {
                val vector = Vector3(event.values[0], event.values[1], event.values[2])
                gyroBuffer.add(vector)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Not used
    }

    /**
     * Main detection logic: analyze motion patterns for panic indicators
     */
    fun detectPanicMotion(): Float {
        if (accelBuffer.size < 20) return 0f // Need minimum data
        
        val features = computeFeatures()
        var score = 0f
        
        // 1. High jerk (sudden acceleration changes = panic/struggle)
        if (features.jerk > PANIC_JERK_THRESHOLD) {
            score += 0.3f
            Log.d(TAG, "High jerk detected: ${features.jerk}")
        }
        
        // 2. High variance (erratic motion)
        if (features.variance > HIGH_VARIANCE_THRESHOLD) {
            score += 0.25f
            Log.d(TAG, "High variance detected: ${features.variance}")
        }
        
        // 3. Shaking frequency (phone shaking in pocket during panic)
        if (features.dominantFrequency in SHAKE_FREQ_MIN..SHAKE_FREQ_MAX) {
            score += 0.2f
            Log.d(TAG, "Shake frequency detected: ${features.dominantFrequency} Hz")
        }
        
        // 4. Fall detection (sudden downward motion then stillness)
        if (features.zAxisChange < FALL_Z_THRESHOLD) {
            score += 0.15f
            Log.d(TAG, "Fall pattern detected: ${features.zAxisChange}")
        }
        
        // 5. Rotation/struggle (gyroscope)
        val avgRotation = gyroBuffer.getAll().map { it.magnitude() }.average().toFloat()
        if (avgRotation > STRUGGLE_ROTATION_THRESHOLD) {
            score += 0.1f
            Log.d(TAG, "Struggle rotation detected: $avgRotation rad/s")
        }
        
        return score.coerceIn(0f, 1f)
    }

    /**
     * Compute motion features from sensor data
     */
    fun computeFeatures(): MotionFeatures {
        val accelData = accelBuffer.getAll()
        
        // 1. Average acceleration magnitude
        val avgMagnitude = accelData.map { it.magnitude() }.average().toFloat()
        
        // 2. Jerk (rate of change of acceleration)
        val jerk = computeJerk(accelData)
        
        // 3. Variance (how erratic the motion is)
        val magnitudes = accelData.map { it.magnitude() }
        val mean = magnitudes.average().toFloat()
        val variance = magnitudes.map { (it - mean).pow(2) }.average().toFloat()
        
        // 4. Dominant frequency (FFT to detect rhythmic patterns)
        val dominantFreq = computeDominantFrequency(accelData)
        
        // 5. Z-axis change (fall detection)
        val zChange = if (accelData.size >= 2) {
            accelData.last().z - accelData.first().z
        } else 0f
        
        return MotionFeatures(
            accelerationMagnitude = avgMagnitude,
            jerk = jerk,
            variance = variance,
            dominantFrequency = dominantFreq,
            zAxisChange = zChange
        )
    }

    /**
     * Compute jerk (derivative of acceleration)
     */
    private fun computeJerk(accelData: List<Vector3>): Float {
        if (accelData.size < 2) return 0f
        
        var totalJerk = 0f
        for (i in 1 until accelData.size) {
            val diff = accelData[i] - accelData[i - 1]
            totalJerk += diff.magnitude()
        }
        
        return totalJerk / accelData.size
    }

    /**
     * Compute dominant frequency using simplified FFT
     */
    private fun computeDominantFrequency(accelData: List<Vector3>): Float {
        if (accelData.size < 10) return 0f
        
        val magnitudes = accelData.map { it.magnitude() }
        val sampleRate = 50f // 50Hz
        
        // Simplified frequency analysis (autocorrelation)
        val maxLag = min(50, magnitudes.size / 2)
        var maxCorr = 0f
        var bestLag = 1
        
        for (lag in 1 until maxLag) {
            var corr = 0f
            for (i in 0 until magnitudes.size - lag) {
                corr += magnitudes[i] * magnitudes[i + lag]
            }
            
            if (corr > maxCorr) {
                maxCorr = corr
                bestLag = lag
            }
        }
        
        return sampleRate / bestLag
    }

    /**
     * Detect phone position based on motion patterns
     */
    fun detectPhonePosition(): PhonePosition {
        if (accelBuffer.size < 20) return PhonePosition.UNKNOWN
        
        val accelData = accelBuffer.getAll()
        val avgMagnitude = accelData.map { it.magnitude() }.average().toFloat()
        val variance = accelData.map { it.magnitude() }.let { mags ->
            val mean = mags.average().toFloat()
            mags.map { (it - mean).pow(2) }.average().toFloat()
        }
        
        return when {
            // Very still = on table
            avgMagnitude < 10.5f && variance < 0.5f -> PhonePosition.STATIONARY
            
            // High motion + high variance = in pocket while moving
            avgMagnitude > 11f && variance > 2f -> PhonePosition.IN_POCKET
            
            // Moderate motion = in hand
            else -> PhonePosition.IN_HAND
        }
    }

    /**
     * Detect rhythmic motion (exercise, dancing)
     */
    fun isRhythmicMotion(): Boolean {
        val features = computeFeatures()
        
        // Rhythmic motion has consistent frequency and moderate variance
        return features.dominantFrequency in 1.5f..4f && 
               features.variance in 1f..5f
    }

    /**
     * Circular buffer implementation
     */
    class CircularBuffer<T>(private val capacity: Int) {
        private val buffer = mutableListOf<T>()
        
        fun add(item: T) {
            if (buffer.size >= capacity) {
                buffer.removeAt(0)
            }
            buffer.add(item)
        }
        
        fun getAll(): List<T> = buffer.toList()
        
        val size: Int get() = buffer.size
        
        fun clear() = buffer.clear()
    }

    /**
     * Get raw sensor data for debugging
     */
    fun getSensorStatus(): String {
        return """
            Accel samples: ${accelBuffer.size}
            Gyro samples: ${gyroBuffer.size}
            Phone position: ${detectPhonePosition()}
            Rhythmic motion: ${isRhythmicMotion()}
        """.trimIndent()
    }

    /**
     * Release resources
     */
    fun release() {
        stopMonitoring()
        accelBuffer.clear()
        gyroBuffer.clear()
    }
}
