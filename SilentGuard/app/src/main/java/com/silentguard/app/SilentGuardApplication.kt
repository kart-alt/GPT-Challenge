package com.silentguard.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Main application class for Silent Guard
 */
class SilentGuardApplication : Application() {

    companion object {
        private const val TAG = "SilentGuardApp"
        lateinit var instance: SilentGuardApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        Log.i(TAG, "Silent Guard application started")
        
        AppLockManager.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLockManager)
    }
}
