package com.silentguard.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.silentguard.app.R
import com.silentguard.app.alert.AlertManager
import com.silentguard.app.alert.EvidenceManager
import com.silentguard.app.detection.*
import com.silentguard.app.model.*
import com.silentguard.app.ui.MainActivity
import kotlinx.coroutines.*

/**
 * Foreground service for continuous distress detection
 * 
 * Runs in background and monitors:
 * - Audio signals
 * - Motion patterns
 * - Environmental context
 * 
 * Triggers alerts when distress is detected
 */
class DistressDetectionService : Service() {

    private lateinit var audioClassifier: AudioClassifier
    private lateinit var motionAnalyzer: MotionAnalyzer
    private lateinit var contextValidator: ContextValidator
    private lateinit var decisionEngine: DecisionEngine
    private lateinit var alertManager: AlertManager
    private lateinit var evidenceManager: EvidenceManager
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var isRunning = false
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Detection state
    private var currentAudioScore = 0f
    private var currentMotionScore = 0f
    
    companion object {
        private const val TAG = "DistressDetectionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "distress_detection_channel"
        private const val WAKE_LOCK_TAG = "SilentGuard::DetectionWakeLock"
        
        const val ACTION_START_DETECTION = "com.silentguard.START_DETECTION"
        const val ACTION_STOP_DETECTION = "com.silentguard.STOP_DETECTION"
        const val ACTION_CANCEL_ALERT = "com.silentguard.CANCEL_ALERT"
        const val ACTION_TRIGGER_TEST_ALERT = "com.silentguard.TRIGGER_TEST_ALERT"
        const val ACTION_UPDATE_SENSITIVITY = "com.silentguard.UPDATE_SENSITIVITY"
        const val EXTRA_SENSITIVITY = "sensitivity"
        private const val MAX_HISTORY_ENTRIES = 100
        
        /**
         * Start the detection service
         */
        fun start(context: Context) {
            val intent = Intent(context, DistressDetectionService::class.java).apply {
                action = ACTION_START_DETECTION
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * Stop the detection service
         */
        fun stop(context: Context) {
            val intent = Intent(context, DistressDetectionService::class.java).apply {
                action = ACTION_STOP_DETECTION
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
        
        // Initialize components
        audioClassifier = AudioClassifier(this)
        motionAnalyzer = MotionAnalyzer(this)
        contextValidator = ContextValidator(this, audioClassifier, motionAnalyzer)
        decisionEngine = DecisionEngine(this, contextValidator)
        alertManager = AlertManager(this)
        evidenceManager = EvidenceManager(this)
        
        // Create notification channel
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DETECTION -> {
                startDetection()
            }
            ACTION_STOP_DETECTION -> {
                stopDetection()
                stopSelf()
            }
            ACTION_CANCEL_ALERT -> {
                alertManager.cancelAlert()
                evidenceManager.stopRecording()
            }
            ACTION_TRIGGER_TEST_ALERT -> {
                triggerEmergencyAlert(0.99f)
            }
            ACTION_UPDATE_SENSITIVITY -> {
                val sensitivity = intent.getFloatExtra(EXTRA_SENSITIVITY, 0.5f)
                decisionEngine.setSensitivity(sensitivity)
                Log.i(TAG, "Sensitivity updated to $sensitivity")
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Start detection monitoring
     */
    private fun startDetection() {
        if (isRunning) {
            Log.d(TAG, "Detection already running")
            return
        }
        
        isRunning = true
        
        // Start foreground service
        val notification = createNotification("Silent Guard is protecting you", false)
        startForeground(NOTIFICATION_ID, notification)
        
        // Acquire wake lock (partial - CPU only, not screen)
        acquireWakeLock()

        // Apply saved sensitivity
        val prefs = getSharedPreferences("silent_guard_prefs", Context.MODE_PRIVATE)
        val savedSensitivity = prefs.getFloat("sensitivity", 0.5f)
        decisionEngine.setSensitivity(savedSensitivity)
        Log.d(TAG, "Loaded sensitivity: $savedSensitivity")
        
        // Initialize audio classifier
        serviceScope.launch {
            val initialized = audioClassifier.initialize()
            
            if (initialized) {
                // Start audio monitoring
                audioClassifier.startMonitoring { audioScore ->
                    currentAudioScore = audioScore
                    Log.d(TAG, "Audio score: $audioScore")
                }
                
                // Start motion monitoring
                motionAnalyzer.startMonitoring { motionScore ->
                    currentMotionScore = motionScore
                    Log.d(TAG, "Motion score: $motionScore")
                }
                
                // Start periodic evaluation
                startPeriodicEvaluation()
                
                Log.i(TAG, "Detection started successfully")
            } else {
                Log.e(TAG, "Failed to initialize audio classifier")
                stopDetection()
            }
        }
    }

    /**
     * Periodic evaluation of all signals
     */
    private fun startPeriodicEvaluation() {
        serviceScope.launch {
            while (isRunning) {
                try {
                    evaluateDistress()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in evaluation", e)
                }
                
                delay(1000) // Evaluate every second
            }
        }
    }

    /**
     * Main evaluation logic
     */
    private fun evaluateDistress() {
        // Get current context
        val context = contextValidator.getCurrentContext()
        
        // Evaluate through decision engine
        val result = decisionEngine.evaluateDistress(
            audioScore = currentAudioScore,
            motionScore = currentMotionScore,
            context = context
        )
        
        // Check if alert should be triggered
        if (result.decision == DistressDecision.HIGH_CONFIDENCE) {
            if (decisionEngine.shouldTriggerAlert()) {
                triggerEmergencyAlert(result.finalConfidence)
            }
        }
        
        // Update notification with current status
        updateNotification(result)
    }

    /**
     * Trigger emergency alert
     */
    private fun triggerEmergencyAlert(confidence: Float) {
        Log.w(TAG, "EMERGENCY DETECTED! Confidence: $confidence")
        
        // Get trusted contacts from preferences
        val contacts = getTrustedContacts()
        
        // Start recording evidence silently in the background
        evidenceManager.startRecording()
        
        // Trigger alert
        alertManager.triggerAlert(
            confidence = confidence,
            trustedContacts = contacts,
            onCancelled = {
                Log.i(TAG, "Alert cancelled by user")
                evidenceManager.stopRecording() // Stop recording if alert is cancelled
                resumeDetection()
            },
            onAlertSent = {
                Log.i(TAG, "Emergency alerts sent")
                resumeDetection()
            }
        )
        
        // Pause detection during alert
        pauseDetection()
    }

    /**
     * Pause detection temporarily
     */
    private fun pauseDetection() {
        audioClassifier.stopMonitoring()
        motionAnalyzer.stopMonitoring()
    }

    /**
     * Resume detection after alert
     */
    private fun resumeDetection() {
        audioClassifier.startMonitoring { audioScore ->
            currentAudioScore = audioScore
        }
        
        motionAnalyzer.startMonitoring { motionScore ->
            currentMotionScore = motionScore
        }
    }

    /**
     * Stop detection monitoring
     */
    private fun stopDetection() {
        isRunning = false
        
        audioClassifier.stopMonitoring()
        motionAnalyzer.stopMonitoring()
        
        releaseWakeLock()
        
        Log.i(TAG, "Detection stopped")
    }

    /**
     * Acquire partial wake lock to keep detection running
     */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ).apply {
            acquire(10 * 60 * 60 * 1000L) // 10 hours max
        }
        
        Log.d(TAG, "Wake lock acquired")
    }

    /**
     * Release wake lock
     */
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
        
        Log.d(TAG, "Wake lock released")
    }

    /**
     * Create notification channel (required for Android O+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Distress Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous distress monitoring"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create foreground notification
     */
    private fun createNotification(message: String, isAlert: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Silent Guard")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_shield) // You'll need to add this icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        
        if (isAlert) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
        }
        
        return builder.build()
    }

    /**
     * Update notification with current status
     */
    private fun updateNotification(result: DetectionResult) {
        val message = when {
            result.decision == DistressDecision.HIGH_CONFIDENCE -> 
                "⚠️ High distress detected!"
            result.finalConfidence > 0.3f -> 
                "Monitoring (confidence: ${(result.finalConfidence * 100).toInt()}%)"
            else -> 
                "Silent Guard is protecting you"
        }
        
        val notification = createNotification(message, result.decision == DistressDecision.HIGH_CONFIDENCE)
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Get trusted contacts from SharedPreferences
     */
    private fun getTrustedContacts(): List<TrustedContact> {
        val prefs = getSharedPreferences("silent_guard_prefs", Context.MODE_PRIVATE)
        val contactsJson = prefs.getString("trusted_contacts", "[]") ?: "[]"
        
        return if (contactsJson == "[]") {
            // For demo: return dummy contacts if none configured
            listOf(
                TrustedContact(1, "Emergency Contact", "1234567890", true)
            )
        } else {
            val typeList = object : TypeToken<List<TrustedContact>>() {}.type
            try {
                Gson().fromJson(contactsJson, typeList)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        stopDetection()
        
        audioClassifier.release()
        motionAnalyzer.release()
        alertManager.release()
        evidenceManager.release()
        
        serviceScope.cancel()
        
        Log.i(TAG, "Service destroyed")
    }
}
