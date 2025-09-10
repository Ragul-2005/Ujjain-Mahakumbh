package com.mahakumbh.crowdsafety.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.mahakumbh.crowdsafety.di.Locator
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.mahakumbh.crowdsafety.util.NotificationHelper
import com.mahakumbh.crowdsafety.data.Event
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.animateContentSize

@Composable
fun EventScheduleScreen() {
    // Use Firestore-backed events for cross-device sync
    val eventRepo = remember { com.mahakumbh.crowdsafety.data.FirestoreEventRepository() }
    val events by eventRepo.events.collectAsState()
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val ctx = LocalContext.current
    // Access app repository to schedule reminders (if implemented)
    val repo = Locator.repo
    val featureRepo = repo as? com.mahakumbh.crowdsafety.data.FeatureRepository

    Column(Modifier.padding(16.dp)) {
        Text("Event Schedule", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        if (events.isEmpty()) {
            Text("No upcoming events")
        }
        events.forEach { ev ->
            val timeLabel = try { sdf.format(Date(ev.startTime)) } catch (_: Exception) { "" }
            EventCard(EventUi(timeLabel, ev.name, ev.location), onReminder = {
                try {
                    // schedule reminder via FeatureRepository if available
                    featureRepo?.setEventReminder(ev.id, repo.currentUserId.value ?: "guest")
                    // immediate feedback
                    Toast.makeText(ctx, "Reminder set for ${ev.name}", Toast.LENGTH_SHORT).show()
                    NotificationHelper.showNotification(ctx, ev.id.hashCode(), "Reminder set", "${ev.name} at ${ev.location}")
                } catch (e: Exception) {
                    android.util.Log.e("EventSchedule", "failed to set reminder", e)
                    Toast.makeText(ctx, "Failed to set reminder", Toast.LENGTH_SHORT).show()
                }
            })
            Spacer(Modifier.height(12.dp))
        }
    }
}

data class EventUi(val time: String, val title: String, val location: String)

@Composable
fun EventCard(event: EventUi, onReminder: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(event.time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(event.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AttractiveRemindButton(onClick = onReminder, modifier = Modifier.height(44.dp))
        }
    }
}

@Composable
fun AttractiveRemindButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val gradient = Brush.horizontalGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
    )
    Card(
        modifier = modifier
            .shadow(6.dp, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(Color.Transparent),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(brush = gradient, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .animateContentSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(imageVector = Icons.Filled.Alarm, contentDescription = null, tint = Color.White)
                Text(text = "Remind", color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
