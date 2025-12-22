package com.withyou.app

import android.app.Application
import com.google.firebase.FirebaseApp
import timber.log.Timber

class ContentSyncApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            // Debug build: plant full debug tree
            Timber.plant(Timber.DebugTree())
        } else {
            // Release build: only log warnings and errors to reduce performance impact
            Timber.plant(ReleaseTree())
        }
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        Timber.d("ContentSync Application started")
    }
    
    /**
     * Release tree - only logs warnings and errors to avoid log spam and performance impact
     * This prevents excessive logging in release builds while keeping critical errors visible
     */
    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // Only log warnings, errors, and WTF in release
            if (priority >= android.util.Log.WARN) {
                android.util.Log.println(priority, tag, message)
                if (t != null) {
                    android.util.Log.println(priority, tag, android.util.Log.getStackTraceString(t))
                }
            }
        }
    }
}

