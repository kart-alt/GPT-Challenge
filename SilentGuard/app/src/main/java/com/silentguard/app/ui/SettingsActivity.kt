package com.silentguard.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import com.google.android.material.switchmaterial.SwitchMaterial
import com.silentguard.app.R
import com.silentguard.app.service.DistressDetectionService

class SettingsActivity : AppCompatActivity() {

    private lateinit var sensitivitySeekBar: SeekBar
    private lateinit var sensitivityValueText: TextView
    private lateinit var notificationSwitch: SwitchMaterial
    private lateinit var vibrationSwitch: SwitchMaterial
    private lateinit var autoStartSwitch: SwitchMaterial
    
    // Security
    private lateinit var appLockSwitch: SwitchMaterial
    private lateinit var biometricSwitch: SwitchMaterial
    private lateinit var biometricContainer: android.view.View

    companion object {
        const val SETUP_PIN_REQUEST = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        loadSettings()
    }

    private fun initViews() {
        // Back button
        findViewById<android.widget.ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        // Sensitivity SeekBar
        sensitivitySeekBar = findViewById(R.id.sensitivitySeekBar)
        sensitivityValueText = findViewById(R.id.sensitivityValueText)

        sensitivitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sensitivity = progress.toFloat() / 100f
                sensitivityValueText.text = "$progress%"
                // Pop animation on the label
                sensitivityValueText.animate()
                    .scaleX(1.25f).scaleY(1.25f)
                    .setDuration(80)
                    .withEndAction {
                        sensitivityValueText.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(120)
                            .setInterpolator(android.view.animation.OvershootInterpolator())
                            .start()
                    }.start()
                saveSensitivity(sensitivity)
                // Broadcast to running service so it takes effect immediately
                broadcastSensitivity(sensitivity)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Notification Switch
        notificationSwitch = findViewById(R.id.notificationSwitch)
        notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveSetting("notifications", isChecked)
        }

        // Vibration Switch
        vibrationSwitch = findViewById(R.id.vibrationSwitch)
        vibrationSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveSetting("vibration", isChecked)
        }

        // Auto-start Switch
        autoStartSwitch = findViewById(R.id.autoStartSwitch)
        autoStartSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveSetting("auto_start", isChecked)
        }

        // Security Switches
        appLockSwitch = findViewById(R.id.appLockSwitch)
        biometricSwitch = findViewById(R.id.biometricSwitch)
        biometricContainer = findViewById(R.id.biometricContainer)

        appLockSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val intent = Intent(this, AuthActivity::class.java).apply {
                    putExtra("SETUP_MODE", true)
                }
                startActivityForResult(intent, SETUP_PIN_REQUEST)
            } else {
                saveSetting("app_lock_enabled", false)
                biometricContainer.visibility = android.view.View.GONE
            }
        }

        biometricSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveSetting("biometric_enabled", isChecked)
        }

        // Security Switches
        appLockSwitch = findViewById(R.id.appLockSwitch)
        biometricSwitch = findViewById(R.id.biometricSwitch)
        biometricContainer = findViewById(R.id.biometricContainer)

        appLockSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val intent = Intent(this, AuthActivity::class.java).apply {
                    putExtra("SETUP_MODE", true)
                }
                startActivityForResult(intent, SETUP_PIN_REQUEST)
            } else {
                saveSetting("app_lock_enabled", false)
                biometricContainer.visibility = android.view.View.GONE
            }
        }

        biometricSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveSetting("biometric_enabled", isChecked)
        }

        // Battery Optimization Button
        findViewById<com.google.android.material.button.MaterialButton>(R.id.batteryOptButton)
            .setOnClickListener {
                showBatteryOptimizationDialog()
            }

        // Test Alert Button
        findViewById<com.google.android.material.button.MaterialButton>(R.id.testAlertButton)
            .setOnClickListener {
                testAlert()
            }

        // About Button
        findViewById<com.google.android.material.button.MaterialButton>(R.id.aboutButton)
            .setOnClickListener {
                showAboutDialog()
            }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("silent_guard_prefs", MODE_PRIVATE)

        // Load sensitivity (default 50%)
        val sensitivity = prefs.getFloat("sensitivity", 0.5f)
        sensitivitySeekBar.progress = (sensitivity * 100).toInt()
        sensitivityValueText.text = "${(sensitivity * 100).toInt()}%"

        // Load other settings
        notificationSwitch.isChecked = prefs.getBoolean("notifications", true)
        vibrationSwitch.isChecked = prefs.getBoolean("vibration", true)
        autoStartSwitch.isChecked = prefs.getBoolean("auto_start", false)
        
        val appLockEnabled = prefs.getBoolean("app_lock_enabled", false)
        // Temporarily remove listener to avoid triggering setup
        appLockSwitch.setOnCheckedChangeListener(null)
        appLockSwitch.isChecked = appLockEnabled
        
        // Re-attach listener
        appLockSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val intent = Intent(this, AuthActivity::class.java).apply {
                    putExtra("SETUP_MODE", true)
                }
                startActivityForResult(intent, SETUP_PIN_REQUEST)
            } else {
                saveSetting("app_lock_enabled", false)
                biometricContainer.visibility = android.view.View.GONE
            }
        }
        
        biometricSwitch.isChecked = prefs.getBoolean("biometric_enabled", true)
        
        if (appLockEnabled) {
            val biometricManager = BiometricManager.from(this)
            val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                biometricContainer.visibility = android.view.View.VISIBLE
            } else {
                biometricContainer.visibility = android.view.View.GONE
            }
        } else {
            biometricContainer.visibility = android.view.View.GONE
        }
    }

    private fun saveSensitivity(sensitivity: Float) {
        val prefs = getSharedPreferences("silent_guard_prefs", MODE_PRIVATE)
        prefs.edit().putFloat("sensitivity", sensitivity).apply()
    }

    private fun broadcastSensitivity(sensitivity: Float) {
        val intent = Intent(this, DistressDetectionService::class.java).apply {
            action = DistressDetectionService.ACTION_UPDATE_SENSITIVITY
            putExtra(DistressDetectionService.EXTRA_SENSITIVITY, sensitivity)
        }
        startService(intent)
    }

    private fun saveSetting(key: String, value: Boolean) {
        val prefs = getSharedPreferences("silent_guard_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean(key, value).apply()
    }

    private fun testAlert() {
        Toast.makeText(this, "Test alert triggered!", Toast.LENGTH_SHORT).show()
        
        // Trigger alert through the service for testing
        val intent = Intent(this, DistressDetectionService::class.java).apply {
            action = DistressDetectionService.ACTION_TRIGGER_TEST_ALERT
        }
        startService(intent)
    }

    private fun showBatteryOptimizationDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Battery Optimization")
            .setMessage("""
                To ensure Silent Guard runs continuously:
                
                1. Go to Settings → Apps → Silent Guard
                2. Tap "Battery"
                3. Select "Unrestricted" or "Don't optimize"
                
                This prevents the app from being killed in background.
            """.trimIndent())
            .setPositiveButton("Open Settings") { _, _ ->
                openBatterySettings()
            }
            .setNegativeButton("OK", null)
            .create()
        dialog.show()
    }

    private fun openBatterySettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAboutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Silent Guard")
            .setMessage("""
                Version 1.0
                
                Silent Guard uses AI to automatically detect emergency situations and alert your trusted contacts.
                
                Privacy First:
                • All processing on-device
                • No cloud uploads
                • No continuous recording
                
                © 2026 Silent Guard
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SETUP_PIN_REQUEST) {
            if (resultCode == RESULT_OK) {
                saveSetting("app_lock_enabled", true)
                loadSettings()
            } else {
                appLockSwitch.isChecked = false
            }
        }
    }
}
