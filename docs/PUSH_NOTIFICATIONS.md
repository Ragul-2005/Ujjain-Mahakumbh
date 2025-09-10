# Implementing Push Notifications for Weather Alerts

This document outlines the architecture and implementation steps for sending real-time weather alerts to visitors using push notifications. We will use Firebase Cloud Messaging (FCM), which is the standard and most robust solution for Android.

## 1. Architecture Overview

The system consists of two main components:

1.  **Backend Service (Server-Side)**: A server-side component is responsible for monitoring weather data from a reliable API, determining if an alert condition is met, and triggering the push notification via FCM.
2.  **Android App (Client-Side)**: The app is responsible for receiving the notification from FCM and displaying it to the user.

### Flow Diagram

`[Weather API] -> [Backend Service] -> [Firebase Cloud Messaging] -> [Visitor's Device]`

## 2. Backend Implementation (Conceptual)

A backend service (e.g., a Cloud Function, a dedicated server) would perform the following actions:

-   **Fetch Weather Data**: Periodically fetch data from a real-world weather API for the event's location.
-   **Evaluate Alert Conditions**: Implement logic to check for critical weather conditions (e.g., heavy rainfall, high temperature, storm warning).
-   **Trigger FCM**: If an alert condition is met, use the Firebase Admin SDK to compose and send a notification. It's best to use **topic-based messaging** (e.g., send to a `weather_alerts` topic that all visitor apps subscribe to).

#### Example Notification Payload:

```json
{
  "message": {
    "topic": "weather_alerts",
    "notification": {
      "title": "Weather Alert: Heavy Rain Expected",
      "body": "Heavy rain is expected in 30 minutes near Sangam Zone. Please seek shelter."
    },
    "data": {
      "screen": "weather",
      "alert_level": "HIGH"
    }
  }
}
```

## 3. Android App Implementation (Client-Side)

Here are the steps to set up the app to receive push notifications.

### Step 1: Add Firebase Dependencies

Add the Firebase Messaging dependency to your `app/build.gradle.kts` file:

```kotlin
dependencies {
    // ... other dependencies
    implementation(platform("com.google.firebase:firebase-bom:33.1.1"))
    implementation("com.google.firebase:firebase-messaging-ktx")
}
```

### Step 2: Set Up Firebase Project

1.  Go to the [Firebase Console](https://console.firebase.google.com/).
2.  Create a new project and register your Android app using its package name (`com.mahakumbh.crowdsafety`).
3.  Download the `google-services.json` file and place it in the `app/` directory of your project.

### Step 3: Create a Messaging Service

Create a new Kotlin file, for example, `MyFirebaseMessagingService.kt`, to handle incoming messages.

```kotlin
package com.mahakumbh.crowdsafety.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mahakumbh.crowdsafety.R

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Handle FCM messages here.
        remoteMessage.notification?.let {
            sendNotification(it.title, it.body)
        }
    }

    private fun sendNotification(title: String?, messageBody: String?) {
        val channelId = "weather_alert_channel"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.mahakumbh_logo) // Replace with a proper icon
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Since Android Oreo, notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId,
                "Weather Alerts",
                NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build())
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // You can send this token to your server if you need to target specific devices
        // For this app, we will use topic subscription instead.
    }
}
```

### Step 4: Register the Service in `AndroidManifest.xml`

Add the service to your `app/src/main/AndroidManifest.xml` inside the `<application>` tag:

```xml
<service
    android:name=".service.MyFirebaseMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

### Step 5: Subscribe to a Topic

To receive weather alerts, the app should subscribe to the `weather_alerts` topic when it starts.

```kotlin
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging

// Call this when the app starts, e.g., in MainActivity's onCreate
Firebase.messaging.subscribeToTopic("weather_alerts")
    .addOnCompleteListener {
        // Handle success or failure
    }
```

### Step 6: Request Notification Permission (Android 13+)

For Android 13 (API 33) and higher, you must request runtime permission to post notifications.

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

@Composable
fun RequestNotificationPermission() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission grant or denial
    }

    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        // Launch the permission request
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
```
