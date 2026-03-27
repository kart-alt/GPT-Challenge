package com.silentguard.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.silentguard.app.R

/**
 * Onboarding activity - first launch setup
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var contactNameInput: TextInputEditText
    private lateinit var contactPhoneInput: TextInputEditText
    private lateinit var addContactButton: Button
    private lateinit var continueButton: Button
    private lateinit var contactCountText: TextView
    
    private var contactsAdded = 0
    private val PERMISSION_REQUEST_CODE = 2001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        
        initViews()
    }

    private fun initViews() {
        contactNameInput = findViewById(R.id.contactNameInput)
        contactPhoneInput = findViewById(R.id.contactPhoneInput)
        addContactButton = findViewById(R.id.addContactButton)
        continueButton = findViewById(R.id.continueButton)
        contactCountText = findViewById(R.id.contactCountText)
        
        addContactButton.setOnClickListener {
            addContact()
        }
        
        continueButton.setOnClickListener {
            completeOnboarding()
        }
        
        continueButton.isEnabled = false
    }

    private fun addContact() {
        val name = contactNameInput.text.toString().trim()
        val phone = contactPhoneInput.text.toString().trim()
        
        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Please enter name and phone", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!isValidPhoneNumber(phone)) {
            Toast.makeText(this, "Please enter valid phone number", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Save contact (in production, use Room database)
        val prefs = getSharedPreferences("silent_guard_prefs", MODE_PRIVATE)
        prefs.edit()
            .putString("contact_${contactsAdded}_name", name)
            .putString("contact_${contactsAdded}_phone", phone)
            .apply()
        
        contactsAdded++
        
        Toast.makeText(this, "Contact added: $name", Toast.LENGTH_SHORT).show()
        
        // Clear inputs
        contactNameInput.text?.clear()
        contactPhoneInput.text?.clear()
        
        // Update button and count
        continueButton.isEnabled = true
        continueButton.text = "Continue ($contactsAdded contact${if (contactsAdded > 1) "s" else ""} added)"
        contactCountText.text = "$contactsAdded contact${if (contactsAdded > 1) "s" else ""} added"
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        return phone.matches(Regex("^[+]?[0-9]{10,15}$"))
    }

    private fun completeOnboarding() {
        if (contactsAdded == 0) {
            Toast.makeText(this, "Add at least one contact", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Request permissions
        requestPermissions()
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.POST_NOTIFICATIONS
        )
        
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Mark onboarding complete
            val prefs = getSharedPreferences("silent_guard_prefs", MODE_PRIVATE)
            prefs.edit()
                .putBoolean("first_launch", false)
                .putInt("contacts_count", contactsAdded)
                .apply()
            
            // Go to main activity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
