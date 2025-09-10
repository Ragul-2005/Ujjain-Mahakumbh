package com.mahakumbh.crowdsafety

import android.app.Application
import com.google.firebase.FirebaseApp

// Rename to CrowdApp to avoid name collision with the Composable `App()` function
class CrowdApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase with google-services.json configuration
        FirebaseApp.initializeApp(this)
    }
}
