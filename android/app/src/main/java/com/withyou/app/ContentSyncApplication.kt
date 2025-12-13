package com.withyou.app

import android.app.Application
import com.google.firebase.FirebaseApp
import timber.log.Timber

class ContentSyncApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        Timber.d("ContentSync Application started")
    }
}

