package com.silentguard.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.silentguard.app.R
import com.silentguard.app.service.DistressDetectionService

/**
 * Full-screen activity shown when emergency is detected
 * User has 10 seconds to cancel the alert
 */
class AlertCancelActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var countdownText: TextView
    private lateinit var cancelButton: Button
    private lateinit var confirmButton: Button
    
    private var countdownTimer: CountDownTimer? = null
    private var isCancelled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert_cancel)
        
        // Make full screen
        window.statusBarColor = getColor(android.R.color.holo_red_dark)
        
        initViews()
        startCountdown()
    }

    private fun initViews() {
        titleText = findViewById(R.id.alertTitleText)
        countdownText = findViewById(R.id.countdownText)
        cancelButton = findViewById(R.id.cancelButton)
        confirmButton = findViewById(R.id.confirmButton)
        
        val confidence = intent.getFloatExtra("confidence", 0f)
        titleText.text = "EMERGENCY DETECTED\nConfidence: ${(confidence * 100).toInt()}%"
        
        cancelButton.setOnClickListener {
            cancelAlert()
        }
        
        confirmButton.setOnClickListener {
            confirmAlert()
        }
    }

    private fun startCountdown() {
        val countdownDuration = intent.getLongExtra("countdown", 10000L)
        
        countdownTimer = object : CountDownTimer(countdownDuration, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000.0)
                countdownText.text = "%.1f".format(seconds)
            }

            override fun onFinish() {
                if (!isCancelled) {
                    // Alert will be sent (handled by AlertManager)
                    finish()
                }
            }
        }.start()
    }

    private fun cancelAlert() {
        isCancelled = true
        countdownTimer?.cancel()
        
        // Notify service to cancel alert
        val intent = Intent(this, DistressDetectionService::class.java).apply {
            action = DistressDetectionService.ACTION_CANCEL_ALERT
        }
        startService(intent)
        
        finish()
    }

    private fun confirmAlert() {
        isCancelled = false
        countdownTimer?.cancel()
        
        // Let alert proceed immediately
        finish()
    }

    override fun onBackPressed() {
        // Prevent back button (user must explicitly cancel)
        cancelAlert()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
    }
}
