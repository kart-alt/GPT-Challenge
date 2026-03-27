package com.silentguard.app

import android.content.Context
import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.silentguard.app.ui.AuthActivity

object AppLockManager : DefaultLifecycleObserver {

    var isUnlocked = false
    private var applicationContext: Context? = null
    
    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        // App returned to foreground
        val context = applicationContext ?: return
        val prefs = context.getSharedPreferences("silent_guard_prefs", Context.MODE_PRIVATE)
        
        if (prefs.getBoolean("app_lock_enabled", false)) {
            if (!isUnlocked) {
                // Not unlocked, show auth screen
                val intent = Intent(context, AuthActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(intent)
            }
        } else {
            // App lock not enabled, always unlocked
            isUnlocked = true
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        // App went to background
        isUnlocked = false
    }
}
