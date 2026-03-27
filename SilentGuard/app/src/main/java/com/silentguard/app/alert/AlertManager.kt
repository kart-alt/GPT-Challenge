package com.silentguard.app.alert

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.silentguard.app.model.TrustedContact
import com.silentguard.app.ui.AlertCancelActivity
import kotlinx.coroutines.*

/**
 * Manages emergency alerts and notifications
 * 
 * Flow:
 * 1. Local alert (sound + vibration)
 * 2. Show cancel screen (10 seconds)
 * 3. If not cancelled → Send SMS to contacts
 * 4. Share location
 */
class AlertManager(private val context: Context) {

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    private var mediaPlayer: MediaPlayer? = null
    private var cancelJob: Job? = null
    private var isCancelled = false
    
    private val countdownDuration = 10_000L // 10 seconds
    
    companion object {
        private const val TAG = "AlertManager"
        
        // Vibration pattern: [delay, vibrate, sleep, vibrate, ...]
        private val ALERT_VIBRATION_PATTERN = longArrayOf(0, 500, 200, 500, 200, 500, 200, 1000)
    }

    /**
     * Trigger emergency alert sequence
     */
    fun triggerAlert(
        confidence: Float,
        trustedContacts: List<TrustedContact>,
        onCancelled: () -> Unit = {},
        onAlertSent: () -> Unit = {}
    ) {
        isCancelled = false
        
        Log.i(TAG, "Emergency alert triggered (confidence: $confidence)")
        
        // Step 1: Local alert (immediate feedback)
        playAlertSound()
        vibrateAlert()
        
        // Step 2: Show cancel activity (full screen)
        showCancelActivity(confidence)
        
        // Step 3: Start countdown
        cancelJob = CoroutineScope(Dispatchers.Main).launch {
            var remainingSeconds = (countdownDuration / 1000).toInt()
            
            while (remainingSeconds > 0 && !isCancelled) {
                Log.d(TAG, "Alert countdown: $remainingSeconds seconds")
                delay(1000)
                remainingSeconds--
            }
            
            if (!isCancelled) {
                // Countdown completed → send alerts
                sendEmergencyAlerts(trustedContacts)
                onAlertSent()
            } else {
                // User cancelled
                Log.i(TAG, "Alert cancelled by user")
                onCancelled()
            }
            
            stopAlertSound()
            stopVibration()
        }
    }

    /**
     * Cancel the alert (user pressed cancel button)
     */
    fun cancelAlert() {
        isCancelled = true
        cancelJob?.cancel()
        stopAlertSound()
        stopVibration()
        
        Log.i(TAG, "Alert cancelled")
    }

    /**
     * Play loud alert sound
     */
    private fun playAlertSound() {
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setVolume(1.0f, 1.0f) // Maximum volume
                prepare()
                start()
            }
            
            Log.d(TAG, "Alert sound playing")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alert sound", e)
        }
    }

    /**
     * Stop alert sound
     */
    private fun stopAlertSound() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
    }

    /**
     * Vibrate phone with alert pattern
     */
    private fun vibrateAlert() {
        try {
            val effect = VibrationEffect.createWaveform(
                ALERT_VIBRATION_PATTERN,
                0 // Repeat from index 0 (continuous)
            )
            vibrator.vibrate(effect)
            
            Log.d(TAG, "Vibration started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vibrate", e)
        }
    }

    /**
     * Stop vibration
     */
    private fun stopVibration() {
        vibrator.cancel()
    }

    /**
     * Show full-screen cancel activity
     */
    private fun showCancelActivity(confidence: Float) {
        val intent = Intent(context, AlertCancelActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_HISTORY
            putExtra("confidence", confidence)
            putExtra("countdown", countdownDuration)
        }
        
        context.startActivity(intent)
    }

    /**
     * Send SMS alerts to trusted contacts
     */
    private suspend fun sendEmergencyAlerts(contacts: List<TrustedContact>) = withContext(Dispatchers.IO) {
        if (contacts.isEmpty()) {
            Log.w(TAG, "No trusted contacts configured")
            return@withContext
        }
        
        // Check SMS permission
        if (!hasSmsPermission()) {
            Log.e(TAG, "SMS permission not granted")
            return@withContext
        }
        
        // Get current location
        val location = getCurrentLocation()
        val locationMessage = if (location != null) {
            "Location: ${location.toGoogleMapsUrl()}"
        } else {
            "Location: unavailable"
        }
        
        // Compose message
        val message = """
            🚨 EMERGENCY ALERT from Silent Guard
            
            An emergency situation has been detected.
            
            $locationMessage
            
            This is an automated alert. Please check on this person immediately.
        """.trimIndent()
        
        // Send to all contacts
        contacts.forEach { contact ->
            try {
                sendSms(contact.phoneNumber, message)
                Log.i(TAG, "Alert sent to ${contact.name} (${contact.phoneNumber})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS to ${contact.name}", e)
            }
        }
        
        Log.i(TAG, "Emergency alerts sent to ${contacts.size} contacts")
    }

    /**
     * Send SMS message
     */
    private fun sendSms(phoneNumber: String, message: String) {
        val smsManager = context.getSystemService(SmsManager::class.java)
        
        // Split message if too long
        val parts = smsManager.divideMessage(message)
        
        if (parts.size == 1) {
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
        } else {
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
        }
    }

    /**
     * Get current location
     */
    private suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permission not granted")
            continuation.resume(null) {}
            return@suspendCancellableCoroutine
        }
        
        try {
            val cancellationTokenSource = CancellationTokenSource()
            
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                continuation.resume(location) {}
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to get location", e)
                continuation.resume(null) {}
            }
            
            // Timeout after 5 seconds
            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
            
            CoroutineScope(Dispatchers.IO).launch {
                delay(5000)
                if (continuation.isActive) {
                    cancellationTokenSource.cancel()
                    continuation.resume(null) {}
                }
            }
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Location security exception", e)
            continuation.resume(null) {}
        }
    }

    /**
     * Check if SMS permission is granted
     */
    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if location permission is granted
     */
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Release resources
     */
    fun release() {
        cancelAlert()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

/**
 * Extension function to convert Location to Google Maps URL
 */
fun Location.toGoogleMapsUrl(): String {
    return "https://maps.google.com/?q=$latitude,$longitude"
}
