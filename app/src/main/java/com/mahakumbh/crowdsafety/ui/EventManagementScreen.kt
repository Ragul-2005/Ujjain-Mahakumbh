package com.mahakumbh.crowdsafety.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahakumbh.crowdsafety.data.Event
import com.mahakumbh.crowdsafety.di.Locator
import com.mahakumbh.crowdsafety.util.NotificationHelper
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventManagementScreen(onBack: () -> Unit) {
    val repo = remember { com.mahakumbh.crowdsafety.data.FirestoreEventRepository() }
    val events by repo.events.collectAsState()
    val ctx = LocalContext.current

    // Form state
    var selectedEventId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var minutesFromNow by remember { mutableStateOf("60") }

    // Prefill when selecting an event
    LaunchedEffect(selectedEventId, events) {
        val ev = events.firstOrNull { it.id == selectedEventId }
        if (ev != null) {
            name = ev.name
            location = ev.location
            val diff = (ev.startTime - System.currentTimeMillis()) / 60000L
            minutesFromNow = if (diff > 0) diff.toString() else "60"
        } else if (selectedEventId == null) {
            name = ""
            location = ""
            minutesFromNow = "60"
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Event Management") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
        })
    }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Create or edit events", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            // Top: form
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Event name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = minutesFromNow, onValueChange = { minutesFromNow = it.filter { ch -> ch.isDigit() } }, label = { Text("Starts in (min)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!selectedEventId.isNullOrBlank()) {
                    Button(onClick = {
                        val ev = events.firstOrNull { it.id == selectedEventId } ?: return@Button
                        val start = System.currentTimeMillis() + (minutesFromNow.toLongOrNull() ?: 60L) * 60 * 1000L
                        val updated = ev.copy(name = name, location = if (location.isBlank()) "Main Stage" else location, startTime = start, endTime = start + 60 * 60 * 1000L)
                        repo.updateEvent(updated)
                        selectedEventId = null
                        name = ""
                        location = ""
                        minutesFromNow = "60"
                    }) { Text("Update") }

                    OutlinedButton(onClick = {
                        selectedEventId?.let { id -> repo.deleteEvent(id) }
                        selectedEventId = null
                        name = ""
                        location = ""
                        minutesFromNow = "60"
                    }) { Text("Delete") }
                }

                Button(onClick = {
                    if (name.isBlank()) return@Button
                    val start = System.currentTimeMillis() + (minutesFromNow.toLongOrNull() ?: 60L) * 60 * 1000L
                    val ev = Event(id = "", name = name, description = null, startTime = start, endTime = start + 60 * 60 * 1000L, location = if (location.isBlank()) "Main Stage" else location)
                    repo.postEvent(ev)
                    // clear
                    selectedEventId = null
                    name = ""
                    location = ""
                    minutesFromNow = "60"
                }) { Text("Post") }

                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    // quick reminder of real-time behavior
                    NotificationHelper.showNotification(ctx, 0, "Events", "Events will sync to visitors' devices via Firestore")
                }) { Text("Info") }
            }

            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(12.dp))

            // Below: list of posted events (scrollable)
            Text("Posted events", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(events) { ev ->
                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { selectedEventId = ev.id }, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(ev.name, style = MaterialTheme.typography.titleMedium, fontWeight = if (ev.id == selectedEventId) FontWeight.Bold else FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Location: ${ev.location}", style = MaterialTheme.typography.bodySmall)
                            Text("Starts: ${try { sdf.format(Date(ev.startTime)) } catch (_: Exception) { "" }}")
                            Text("Ends: ${try { sdf.format(Date(ev.endTime)) } catch (_: Exception) { "" }}")
                            Text("ID: ${ev.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
