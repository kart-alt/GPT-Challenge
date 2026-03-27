package com.silentguard.app.ui

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.graphics.Color
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.silentguard.app.R
import com.silentguard.app.alert.AlertManager
import com.silentguard.app.service.DistressDetectionService

/**
 * Main activity - Dashboard and control center
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusCard: MaterialCardView
    private lateinit var statusText: TextView
    private lateinit var statusDescription: TextView
    private lateinit var audioScoreText: TextView
    private lateinit var motionScoreText: TextView
    private lateinit var confidenceText: TextView
    private lateinit var statusIcon: ImageView
    private lateinit var toggleButton: com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
    private lateinit var settingsButton: android.widget.ImageButton
    
    private lateinit var testAudioButton: MaterialButton
    private lateinit var testMotionButton: MaterialButton
    private lateinit var historyButton: MaterialButton
    
    private lateinit var normalModeCard: MaterialCardView
    private lateinit var gymModeCard: MaterialCardView
    private lateinit var concertModeCard: MaterialCardView
    
    // Charts variables
    private lateinit var liveChart: LineChart
    private val audioEntries = ArrayList<Entry>()
    private val motionEntries = ArrayList<Entry>()
    private var chartTime = 0f

    // Animation state
    private var pulseAnimator: android.animation.Animator? = null
    private var lastAudioPct = 0
    private var lastMotionPct = 0
    private var lastConfPct = 0
    
    private var isServiceRunning = false
    
    // Hardware Trigger State
    private var volumeDownCount = 0
    private var lastVolumeDownTime = 0L
    
    private val PERMISSION_REQUEST_CODE = 1001
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.SEND_SMS,
        Manifest.permission.POST_NOTIFICATIONS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Cold start lock check
        val lockPrefs = getSharedPreferences("silent_guard_prefs", android.content.Context.MODE_PRIVATE)
        if (lockPrefs.getBoolean("app_lock_enabled", false) && !com.silentguard.app.AppLockManager.isUnlocked) {
            val intent = Intent(this, AuthActivity::class.java).apply {
                putExtra("FROM_LAUNCH", true)
            }
            startActivity(intent)
            finish()
            return
        }
        
        // Check if first launch
        val prefs = getSharedPreferences("silent_guard_prefs", MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("first_launch", true)
        
        if (isFirstLaunch) {
            // Show onboarding
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        
        setContentView(R.layout.activity_main)
        
        initViews()
        checkPermissions()
        updateUI()
        playEntranceAnimations()
    }

    private fun initViews() {
        // Status Card
        statusCard = findViewById(R.id.statusCard)
        statusText = findViewById(R.id.statusText)
        statusDescription = findViewById(R.id.statusDescription)
        audioScoreText = findViewById(R.id.audioScore)
        motionScoreText = findViewById(R.id.motionScore)
        confidenceText = findViewById(R.id.confidenceText)
        statusIcon = findViewById(R.id.statusIcon)
        
        // Buttons
        toggleButton = findViewById(R.id.toggleButton)
        settingsButton = findViewById(R.id.settingsButton)
        
        // Mode Cards
        normalModeCard = findViewById(R.id.normalModeCard)
        gymModeCard = findViewById(R.id.gymModeCard)
        concertModeCard = findViewById(R.id.concertModeCard)
        
        setupChart()
        
        // Toggle Button — with bounce feedback
        toggleButton.setOnClickListener {
            animateFabBounce()
            if (isServiceRunning) {
                stopProtection()
            } else {
                startProtection()
            }
        }
        
        // Settings Button
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        // Mode Cards Click Listeners
        normalModeCard.setOnClickListener {
            setMode("normal")
            Toast.makeText(this, "Normal Mode Activated", Toast.LENGTH_SHORT).show()
        }
        
        gymModeCard.setOnClickListener {
            setMode("gym")
            Toast.makeText(this, "Gym Mode Activated - Motion alerts reduced", Toast.LENGTH_SHORT).show()
        }
        
        concertModeCard.setOnClickListener {
            setMode("concert")
            Toast.makeText(this, "Concert Mode Activated - Alerts suppressed in loud environments", Toast.LENGTH_SHORT).show()
        }

        // Test Buttons (Demo)
        testAudioButton = findViewById(R.id.testAudioButton)
        testMotionButton = findViewById(R.id.testMotionButton)
        historyButton = findViewById(R.id.historyButton)

        testAudioButton.setOnClickListener {
            Toast.makeText(this, "Testing Audio Detection...", Toast.LENGTH_SHORT).show()
            updateDetectionScores(audio = 0.85f, motion = 0.10f, confidence = 0.60f)
        }

        testMotionButton.setOnClickListener {
            Toast.makeText(this, "Testing Motion Detection...", Toast.LENGTH_SHORT).show()
            updateDetectionScores(audio = 0.05f, motion = 0.90f, confidence = 0.55f)
        }

        historyButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.testButton).setOnClickListener {
            triggerTestAlert()
        }
    }
    
    private fun setupChart() {
        liveChart = findViewById(R.id.liveChart)
        
        liveChart.apply {
            description.isEnabled = false
            setTouchEnabled(false)
            isDragEnabled = false
            setScaleEnabled(false)
            setDrawGridBackground(false)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.WHITE
            }
            
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                textColor = Color.WHITE
            }
            axisRight.isEnabled = false
            
            legend.textColor = Color.WHITE
        }
    }

    private fun triggerTestAlert() {
        // Trigger alert for testing/demo
        val prefs = getSharedPreferences("silent_guard_prefs", MODE_PRIVATE)
        val contactsCount = prefs.getInt("contacts_count", 0)

        if (contactsCount == 0) {
            Toast.makeText(this, "Add a contact first in onboarding!", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Test alert triggered!", Toast.LENGTH_SHORT).show()

        // Trigger alert through the service so SMS actually sends
        val intent = Intent(this, DistressDetectionService::class.java).apply {
            action = DistressDetectionService.ACTION_TRIGGER_TEST_ALERT
        }
        startService(intent)
    }
    
    private fun setMode(mode: String) {
        // Reset all cards
        normalModeCard.strokeWidth = 0
        gymModeCard.strokeWidth = 0
        concertModeCard.strokeWidth = 0
        
        // Highlight selected mode
        when (mode) {
            "normal" -> normalModeCard.strokeWidth = 4
            "gym" -> gymModeCard.strokeWidth = 4
            "concert" -> concertModeCard.strokeWidth = 4
        }
        
        // Save mode preference
        val prefs = getSharedPreferences("silent_guard_prefs", MODE_PRIVATE)
        prefs.edit().putString("user_mode", mode).apply()
    }

    private fun checkPermissions() {
        val permissionsNeeded = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            
            if (!allGranted) {
                showPermissionDialog()
            }
        }
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("Silent Guard needs microphone, location, and SMS permissions to protect you in emergencies.")
            .setPositiveButton("Grant") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun startProtection() {
        val allPermissionsGranted = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        
        if (!allPermissionsGranted) {
            Toast.makeText(this, "Please grant all permissions", Toast.LENGTH_SHORT).show()
            checkPermissions()
            return
        }
        
        // Request battery optimization exemption
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestIgnoreBatteryOptimizations()
        }
        
        // Start detection service
        DistressDetectionService.start(this)
        
        isServiceRunning = true
        updateUI()
        
        Toast.makeText(this, "Protection enabled", Toast.LENGTH_SHORT).show()
    }

    private fun stopProtection() {
        DistressDetectionService.stop(this)
        
        isServiceRunning = false
        updateUI()
        
        Toast.makeText(this, "Protection disabled", Toast.LENGTH_SHORT).show()
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Battery optimization settings not available
            }
        }
    }

    private fun updateUI() {
        if (isServiceRunning) {
            statusText.text = "Protection Active"
            statusDescription.text = "Monitoring your safety 24/7"
            toggleButton.text = "Stop Protection"
            toggleButton.setIconResource(R.drawable.ic_close)
            toggleButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.danger)
            startPulse()
        } else {
            statusText.text = "Protection Disabled"
            statusDescription.text = "Tap to start protection"
            toggleButton.text = "Start Protection"
            toggleButton.setIconResource(R.drawable.ic_play)
            toggleButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.success)
            stopPulse()
        }
    }

    // ── Animations ──────────────────────────────────────────────────────────

    /** Slide-up entrance for status card and bottom buttons on first load */
    private fun playEntranceAnimations() {
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in)
        statusCard.alpha = 0f
        statusCard.animate().alpha(1f).setDuration(0).start() // ensure visible
        statusCard.startAnimation(slideUp)

        val delay = 120L
        val views = listOf<android.view.View>(
            findViewById(R.id.historyButton),
            findViewById(R.id.testButton),
            findViewById(R.id.testAudioButton),
            findViewById(R.id.testMotionButton)
        )
        views.forEachIndexed { i, v ->
            v.alpha = 0f
            v.animate()
                .alpha(1f)
                .translationYBy(-30f)
                .setStartDelay(delay * (i + 1))
                .setDuration(350)
                .withStartAction { v.translationY = 30f }
                .start()
        }
    }

    /** Infinite scale pulse on the shield icon */
    private fun startPulse() {
        if (pulseAnimator?.isRunning == true) return
        val scaleX = ObjectAnimator.ofFloat(statusIcon, "scaleX", 1f, 1.14f)
        val scaleY = ObjectAnimator.ofFloat(statusIcon, "scaleY", 1f, 1.14f)
        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 900
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            // loop by restarting on end
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    val reverseX = ObjectAnimator.ofFloat(statusIcon, "scaleX", 1.14f, 1f)
                    val reverseY = ObjectAnimator.ofFloat(statusIcon, "scaleY", 1.14f, 1f)
                    AnimatorSet().apply {
                        playTogether(reverseX, reverseY)
                        duration = 900
                        interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(a: android.animation.Animator) {
                                if (isServiceRunning) startPulse()
                            }
                        })
                        start()
                    }
                }
            })
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        statusIcon.scaleX = 1f
        statusIcon.scaleY = 1f
    }

    /** FAB scale bounce on tap */
    private fun animateFabBounce() {
        toggleButton.animate()
            .scaleX(0.88f).scaleY(0.88f)
            .setDuration(80)
            .withEndAction {
                toggleButton.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(160)
                    .setInterpolator(android.view.animation.OvershootInterpolator())
                    .start()
            }.start()
    }

    /** Animate a TextView's number from its current value to newPct */
    private fun animateScore(view: TextView, fromPct: Int, toPct: Int) {
        ValueAnimator.ofInt(fromPct, toPct).apply {
            duration = 350
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { view.text = "${it.animatedValue as Int}%" }
            start()
        }
    }
    
    /**
     * Update detection scores — animates counters and chart
     */
    fun updateDetectionScores(audio: Float, motion: Float, confidence: Float) {
        val audioPct = (audio * 100).toInt()
        val motionPct = (motion * 100).toInt()
        val confPct = (confidence * 100).toInt()

        animateScore(audioScoreText, lastAudioPct, audioPct)
        animateScore(motionScoreText, lastMotionPct, motionPct)
        animateScore(confidenceText, lastConfPct, confPct)

        lastAudioPct = audioPct
        lastMotionPct = motionPct
        lastConfPct = confPct
        
        // Push data to Live Chart
        chartTime++
        audioEntries.add(Entry(chartTime, audio * 100))
        motionEntries.add(Entry(chartTime, motion * 100))
        
        // Keep max 20 points for fluid visualization
        if (audioEntries.size > 20) {
            audioEntries.removeAt(0)
            motionEntries.removeAt(0)
        }
        
        val audioDataSet = LineDataSet(audioEntries, "Audio \uD83C\uDFBC").apply {
            color = Color.parseColor("#E74C3C")
            setDrawCircles(false)
            lineWidth = 2f
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
        
        val motionDataSet = LineDataSet(motionEntries, "Motion \uD83D\uDD25").apply {
            color = Color.parseColor("#3498DB")
            setDrawCircles(false)
            lineWidth = 2f
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
        
        liveChart.data = LineData(audioDataSet, motionDataSet)
        liveChart.notifyDataSetChanged()
        liveChart.invalidate()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
    
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            val currentTime = System.currentTimeMillis()
            
            // Reset counter if it's been more than 3 seconds since last press
            if (currentTime - lastVolumeDownTime > 3000) {
                volumeDownCount = 1
            } else {
                volumeDownCount++
            }
            
            lastVolumeDownTime = currentTime
            
            // Trigger SOS on 5 rapid presses
            if (volumeDownCount >= 5) {
                volumeDownCount = 0 // Reset
                Toast.makeText(this, "Hardware SOS Triggered!", Toast.LENGTH_LONG).show()
                triggerTestAlert() // Reuse existing tested alert flow
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
