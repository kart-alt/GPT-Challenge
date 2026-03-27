package com.silentguard.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.silentguard.app.R

class AuthActivity : AppCompatActivity() {

    private var enteredPin = ""
    private var isSetupMode = false
    private var setupFirstPin = ""
    private lateinit var pinDots: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        isSetupMode = intent.getBooleanExtra("SETUP_MODE", false)

        val titleText = findViewById<TextView>(R.id.authTitleText)
        val subtitleText = findViewById<TextView>(R.id.authSubtitleText)
        val btnBiometric = findViewById<ImageButton>(R.id.btnBiometric)

        if (isSetupMode) {
            titleText.text = "Set up App Lock"
            subtitleText.text = "Enter a 4-digit PIN"
            btnBiometric.visibility = View.INVISIBLE
        } else {
            val biometricManager = BiometricManager.from(this)
            val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS && isBiometricEnabled()) {
                btnBiometric.visibility = View.VISIBLE
                btnBiometric.setOnClickListener { showBiometricPrompt() }
                showBiometricPrompt()
            } else {
                btnBiometric.visibility = View.INVISIBLE
            }
        }

        pinDots = listOf(
            findViewById(R.id.dot1),
            findViewById(R.id.dot2),
            findViewById(R.id.dot3),
            findViewById(R.id.dot4)
        )

        setupNumpad()
    }

    private fun setupNumpad() {
        val buttons = listOf(
            R.id.btn0 to "0", R.id.btn1 to "1", R.id.btn2 to "2",
            R.id.btn3 to "3", R.id.btn4 to "4", R.id.btn5 to "5",
            R.id.btn6 to "6", R.id.btn7 to "7", R.id.btn8 to "8",
            R.id.btn9 to "9"
        )

        for ((id, number) in buttons) {
            findViewById<Button>(id).setOnClickListener {
                if (enteredPin.length < 4) {
                    enteredPin += number
                    updateDots()
                    if (enteredPin.length == 4) {
                        handlePinEntered()
                    }
                }
            }
        }

        findViewById<ImageButton>(R.id.btnDelete).setOnClickListener {
            if (enteredPin.isNotEmpty()) {
                enteredPin = enteredPin.dropLast(1)
                updateDots()
            }
        }
    }

    private fun updateDots() {
        for (i in 0 until 4) {
            val resId = if (i < enteredPin.length) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty
            pinDots[i].setBackgroundResource(resId)
        }
    }

    private fun handlePinEntered() {
        val prefs = getSharedPreferences("silent_guard_prefs", Context.MODE_PRIVATE)
        if (isSetupMode) {
            if (setupFirstPin.isEmpty()) {
                setupFirstPin = enteredPin
                enteredPin = ""
                updateDots()
                findViewById<TextView>(R.id.authSubtitleText).text = "Confirm 4-digit PIN"
            } else {
                if (setupFirstPin == enteredPin) {
                    prefs.edit().putString("app_pin", enteredPin)
                        .putBoolean("app_lock_enabled", true)
                        .apply()
                    Toast.makeText(this, "PIN saved", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    shakeDotsAndClear("PINs do not match. Try again")
                    setupFirstPin = ""
                }
            }
        } else {
            val savedPin = prefs.getString("app_pin", "")
            if (enteredPin == savedPin) {
                // Success
                AppLockManager.isUnlocked = true
                if (intent.getBooleanExtra("FROM_LAUNCH", false)) {
                    startActivity(Intent(this, MainActivity::class.java))
                }
                finish()
            } else {
                shakeDotsAndClear("Incorrect PIN")
            }
        }
    }

    private fun shakeDotsAndClear(message: String) {
        val container = findViewById<View>(R.id.pinDotsContainer)
        container.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
        findViewById<TextView>(R.id.authSubtitleText).text = message
        
        // Vibrate on error
        val vibrator = ContextCompat.getSystemService(this, android.os.Vibrator::class.java)
        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        
        // Reset after short delay
        container.postDelayed({
            enteredPin = ""
            updateDots()
            val sub = if (isSetupMode) "Enter a 4-digit PIN" else "Enter PIN to continue"
            findViewById<TextView>(R.id.authSubtitleText).text = sub
        }, 800)
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    AppLockManager.isUnlocked = true
                    if (intent.getBooleanExtra("FROM_LAUNCH", false)) {
                        startActivity(Intent(this@AuthActivity, MainActivity::class.java))
                    }
                    finish()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Just fallback to PIN
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock SilentGuard")
            .setSubtitle("Use your biometric credential")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
            
        biometricPrompt.authenticate(promptInfo)
    }

    private fun isBiometricEnabled(): Boolean {
        val prefs = getSharedPreferences("silent_guard_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("biometric_enabled", true)
    }
    
    // Prevent back button from bypassing lock
    override fun onBackPressed() {
        if (isSetupMode) {
            super.onBackPressed()
        } else {
            // Close the app entirely if they back out of the lock screen
            finishAffinity()
        }
    }
}
