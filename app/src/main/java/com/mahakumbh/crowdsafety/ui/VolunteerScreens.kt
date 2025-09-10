@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mahakumbh.crowdsafety.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahakumbh.crowdsafety.data.*
import com.mahakumbh.crowdsafety.di.Locator
import com.mahakumbh.crowdsafety.vm.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.compose.runtime.rememberCoroutineScope
import com.mahakumbh.crowdsafety.util.NotificationHelper
import kotlinx.coroutines.flow.first
import com.google.android.gms.maps.CameraUpdateFactory
import kotlinx.coroutines.launch
import com.mahakumbh.crowdsafety.vm.WeatherViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.viewinterop.AndroidView
import com.mahakumbh.crowdsafety.ZonePickerDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.MultiFormatReader
import com.google.zxing.Result
import androidx.compose.ui.text.style.TextAlign
import android.widget.Toast

@Composable
fun VolunteerTaskDashboard(navController: NavHostController) {
    val vm = TasksViewModel()
    val tasks by vm.tasks.collectAsState()
        val repo = Locator.repo
        // Prefer Firestore presence when available; fall back to mock repo activeVisitors so UI is never empty.
        val presenceList by Locator.presenceRepo.online.collectAsState(initial = emptyList())
        val mockVisitors by repo.activeVisitors.collectAsState(initial = emptyList())
        val activeVisitors: List<com.mahakumbh.crowdsafety.data.ActiveVisitor> = if (presenceList.isNotEmpty()) presenceList else mockVisitors
    var selectedTask by remember { mutableStateOf<TaskItem?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var scannedReservation by remember { mutableStateOf<com.mahakumbh.crowdsafety.data.Reservation?>(null) }
    var scannedVisitor by remember { mutableStateOf<com.mahakumbh.crowdsafety.data.ActiveVisitor?>(null) }
    var scannedVisitorName by remember { mutableStateOf<String?>(null) }
    var showReservationDialog by remember { mutableStateOf(false) }
    var showAssociateDialog by remember { mutableStateOf(false) }
    var showVisitorsDialog by remember { mutableStateOf(false) }
    // Navigator-based flow for lost items list (previously used AlertDialog)

    // Launcher for live camera QR scan for special reservation
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result: com.journeyapps.barcodescanner.ScanIntentResult? ->
        val contents = result?.contents
        if (!contents.isNullOrBlank()) {
            val parts = contents.split(":" )
            val rid = if (parts.size >= 2 && parts[0] == "reservation") parts[1] else contents
            // lookup reservation and visitor in repository snapshot
            coroutineScope.launch {
                try {
                    val reservations = repo.reservations.first()
                    val res = reservations.firstOrNull { it.id == rid }
                    if (res != null) {
                        scannedReservation = res
                        val visitors = repo.activeVisitors.first()
                        scannedVisitor = visitors.firstOrNull { it.id == res.userId }
                        // try to surface a friendly name if the reservation stored it in userId or metadata
                        scannedVisitorName = null
                        // If reservation.userId looks like a composite (id|name) try parsing - fallback handled below
                        // (Main QR payload format may include name in parts[3])
                        if (parts.size >= 4) scannedVisitorName = parts[3].replace("-", ":")
                        showReservationDialog = true
                    } else {
                        // Not found locally — still open an approval dialog so volunteer can approve/send alert.
                        // Create a synthetic reservation record for the dialog (best-effort).
                        val syntheticUser = parts.getOrNull(2) ?: "unknown"
                        val syntheticName = parts.getOrNull(3)?.replace("-", ":") ?: ""
                        val synthetic = com.mahakumbh.crowdsafety.data.Reservation(
                            id = rid,
                            userId = syntheticUser,
                            type = com.mahakumbh.crowdsafety.data.ReservationType.Pregnant,
                            slot = "Unknown",
                            zone = "Unknown",
                            status = com.mahakumbh.crowdsafety.data.ReservationStatus.Active
                        )
                        scannedReservation = synthetic
                        val visitors = repo.activeVisitors.first()
                        scannedVisitor = visitors.firstOrNull { it.id == syntheticUser }
                        scannedVisitorName = if (syntheticName.isNotBlank()) syntheticName else null
                        // if visitor isn't online but QR contained a name, attach a minimal visitor-like display
                        if (scannedVisitor == null && syntheticName.isNotBlank()) {
                            scannedVisitor = com.mahakumbh.crowdsafety.data.ActiveVisitor(
                                id = syntheticUser,
                                role = com.mahakumbh.crowdsafety.data.UserRole.Visitor,
                                lat = 0.0,
                                lng = 0.0,
                                zone = "",
                                checkInTime = System.currentTimeMillis()
                            )
                        }
                        showReservationDialog = true
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error reading reservation", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (result != null) {
            Toast.makeText(context, "No QR detected", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Invisible top spacer button to avoid first feature clipping under the app bar
        item {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(0.dp)
            ) { /* spacer */ }
        }

        // Special Reservation Scan feature as a full-width feature button
        item {
            Button(
                onClick = { scanLauncher.launch(ScanOptions()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDFFFE0), contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CropFree, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Special Reservation Scan", style = MaterialTheme.typography.titleLarge)
                        Text("Scan reservation QR codes quickly", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        // New Event full-width feature button (replaces PostEventCard)
        item {
            // Navigate to the dedicated Event Management page (compose + list)
            Button(
                onClick = { navController.navigate("vol_events") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3D9FF), contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("New Event", style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Active Visitors feature button: full-width, icon + label to match Home screen buttons
        item {
            Button(
                onClick = { navController.navigate(com.mahakumbh.crowdsafety.VolunteerRoute.ActiveVisitors.route) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFE0E6), contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Active Visitors", style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        // Lost People management (volunteer) - light blue background per request
        item {
            Button(
                onClick = { navController.navigate(com.mahakumbh.crowdsafety.VolunteerRoute.LostPeople.route) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD7EEFF), contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FindInPage, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Lost People Info", style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        // Lost Thing Info (light neon) - navigates to a dedicated lost-items list screen
        item {
            Button(
                onClick = { navController.navigate("vol_lost_items") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB2FF59), contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FindInPage, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Lost Thing Info", style = MaterialTheme.typography.titleLarge)
                }
            }
        }

    // Live Stream button removed from Tasks page — accessible from Dashboard now

        // SOS header moved below the Live Stream button
        item {
            Text(
                text = "Active SOS Reports",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${tasks.size} active emergency reports",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Prominent SOS banner so volunteers always see there's an active alert and can open it immediately
        item {
            val sosTasks = tasks.filter { it.emergencyType != null }
            if (sosTasks.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("${sosTasks.size} active SOS alert(s)", fontWeight = FontWeight.Bold)
                            Text("Tap Show to view the latest alert", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = { 
                            // navigate to the most recent SOS task detail screen
                            val latest = sosTasks.maxByOrNull { it.id } ?: sosTasks.first()
                            navController.navigate(com.mahakumbh.crowdsafety.VolunteerRoute.AlertDetail.createRoute(latest.id))
                        }) {
                            Text("Show")
                        }
                    }
                }
            }
        }

        items(tasks) { task ->
            TaskCard(
                task = task,
                onTaskClick = { selectedTask = task },
                onAccept = { vm.accept(task.id) },
                onResolve = { vm.done(task.id) },
                navController = navController
            )
        }

        item {
            // Visitors needing assistance
            if (activeVisitors.any { it.needsAssist || it.assisted }) {
                Text("Visitors needing assistance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                activeVisitors.filter { it.needsAssist || it.assisted }.forEach { v ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${v.id} — ${v.zone}", fontWeight = FontWeight.SemiBold)
                                Text(if (v.assisted) "Assisted" else "Needs help", style = MaterialTheme.typography.bodySmall)
                            }
                            if (v.assisted) Icon(Icons.Filled.CheckCircle, contentDescription = "Assisted", tint = Color(0xFF43A047))
                        }
                    }
                }
            }
        }
    }

    if (showVisitorsDialog) {
        AlertDialog(
            onDismissRequest = { showVisitorsDialog = false },
            title = { Text("Visitors Information") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (activeVisitors.isEmpty()) {
                        Text("No visitors currently online")
                    } else {
                        activeVisitors.forEach { v ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Person, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    val label = if (v.displayName.isNotBlank()) v.displayName else v.id
                                    Text(label, fontWeight = FontWeight.SemiBold)
                                    Text(v.zone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(if (v.isOnline) "Online" else "Offline", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showVisitorsDialog = false }) { Text("Close") } }
        )
    }

    // Replaced the inline AlertDialog with a dedicated navigation screen: see "vol_lost_items" route.

    selectedTask?.let { task ->
        TaskDetailDialog(
            task = task,
            onDismiss = { selectedTask = null },
            onAccept = { 
                vm.accept(task.id)
                selectedTask = null
            },
            onResolve = { 
                vm.done(task.id)
                selectedTask = null
            },
            onEscalate = {
                // Handle escalation
                selectedTask = null
            }
        )
    }

    // (Reservation verifier moved to special scanner card at the top)

    if (showReservationDialog && scannedReservation != null) {
        ReservationDetailsDialog(
            reservation = scannedReservation!!,
            visitor = scannedVisitor,
            visitorName = scannedVisitorName,
            onDismiss = {
                showReservationDialog = false
                scannedReservation = null
                scannedVisitor = null
                scannedVisitorName = null
            },
            onApprove = {
                // mark reservation as verified and notify the visitor with details
                scannedReservation?.let { r ->
                    repo.verifyReservation(r.id, repo.currentUserId.value ?: "volunteer_local")
                    // Friendly details used in dialog (keep consistent)
                    val defaultAkharaLocation = "Triveni Bandh Prayag, Pura Padain, Prayagraj, Uttar Pradesh"
                    val loc = if (r.zone.isNullOrBlank() || r.zone == "Unknown") defaultAkharaLocation else r.zone
                    val slot = if (r.slot.isBlank() || r.slot == "Unknown") {
                        val slotTime = System.currentTimeMillis() + 10 * 60 * 1000L
                        android.text.format.DateFormat.format("hh:mm a", java.util.Date(slotTime)).toString()
                    } else r.slot
                    val visitorLabel = scannedVisitorName ?: scannedVisitor?.id ?: "visitor"
                    val title = "Reservation accepted"
                    val body = "Hi $visitorLabel — your reservation ${r.id} at $loc for $slot has been accepted. Please proceed to the location."
                    // Send a local notification (mock of targeted message to visitor)
                    NotificationHelper.showNotification(context, r.id.hashCode(), title, body)
                    Toast.makeText(context, "Reservation ${r.id} accepted and visitor notified", Toast.LENGTH_SHORT).show()
                }
                showReservationDialog = false
                scannedReservation = null
                scannedVisitor = null
                scannedVisitorName = null
            }
            , onAssociate = { showAssociateDialog = true }
        )
    }
    if (showAssociateDialog) {
        AssociateVisitorDialog(activeVisitors = activeVisitors, onSelect = { v ->
            scannedVisitor = v
            showAssociateDialog = false
        }, onDismiss = { showAssociateDialog = false })
    }
}

@Composable
fun VolunteerAlertScreen(navController: NavHostController, taskId: String) {
    val repo = Locator.repo
    val tasks by repo.tasks.collectAsState(initial = emptyList())
    val task = tasks.firstOrNull { it.id == taskId }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TopAppBar(title = { Text("Alert Details") }, navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
        })
        Spacer(modifier = Modifier.height(12.dp))
        task?.let { t ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Type: ${t.emergencyType ?: "Unknown"}")
                    Text("Location: ${t.location}")
                    Text("Priority: ${t.priority}")
                    t.message?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Message:", fontWeight = FontWeight.Medium)
                        Text(msg)
                    }
                }
            }
        } ?: Column { Text("Alert not found", color = Color.Red) }
    }
}

@Composable
fun VolunteerLostItemScreen(navController: NavHostController, taskId: String) {
    val repo = Locator.repo
    val tasks by repo.tasks.collectAsState(initial = emptyList())
    val task = tasks.firstOrNull { it.id == taskId }
    val ctx = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TopAppBar(title = { Text("Lost Item") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } })
        Spacer(Modifier.height(12.dp))
        if (task == null) {
            Text("Item not found", color = Color.Red)
            return@Column
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(task.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("Location: ${task.location}", style = MaterialTheme.typography.bodyMedium)
                task.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text("ID: ${task.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Action buttons: Finding (report progress), Mark Found, Resolve, Accept
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                // report progress: create an incident or update message
                try {
                    repo.logSos(com.mahakumbh.crowdsafety.data.SosType.Security, repo.currentUserId.value ?: "volunteer", com.mahakumbh.crowdsafety.data.UserRole.Volunteer, 0.0, 0.0)
                    Toast.makeText(ctx, "Marked as being searched", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) { Toast.makeText(ctx, "Unable to mark", Toast.LENGTH_SHORT).show() }
            }) { Text("Mark as Finding") }

            Button(onClick = {
                // Mark as found: complete the task and notify reporter
                try {
                    repo.completeTask(task.id)
                    Toast.makeText(ctx, "Marked as found/resolved", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) { Toast.makeText(ctx, "Unable to update", Toast.LENGTH_SHORT).show() }
            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047))) { Text("Mark Found", color = Color.White) }

            Button(onClick = {
                // Quick resolve (same as complete)
                try {
                    repo.completeTask(task.id)
                    Toast.makeText(ctx, "Task resolved", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                } catch (_: Exception) { Toast.makeText(ctx, "Unable to resolve", Toast.LENGTH_SHORT).show() }
            }) { Text("Resolve") }

            Button(onClick = {
                try {
                    repo.acceptTask(task.id)
                    Toast.makeText(ctx, "Accepted", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) { Toast.makeText(ctx, "Unable to accept", Toast.LENGTH_SHORT).show() }
            }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Accept") }
        }
    }
}

@Composable
fun VolunteerLostItemsListScreen(navController: NavHostController) {
    val repo = Locator.repo
    val tasks by repo.tasks.collectAsState(initial = emptyList())
    val lostItems = tasks.filter { t ->
        (t.title.startsWith("🔎 Lost Item") || t.title.contains("Lost Item", ignoreCase = true) || (t.message != null && t.emergencyType == null))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Lost Items") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } })
        Spacer(Modifier.height(12.dp))
        if (lostItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No lost items reported yet")
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(lostItems) { item ->
                Card(modifier = Modifier.fillMaxWidth().clickable { navController.navigate(com.mahakumbh.crowdsafety.VolunteerRoute.LostItemDetail.createRoute(item.id)) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(item.title, fontWeight = FontWeight.Bold)
                        Text("Location: ${item.location}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        item.message?.let { msg -> Text(msg, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}

// Generate approximately `count` LatLng points forming a circle around `center` with radius in meters.
private fun generateCirclePoints(center: com.google.android.gms.maps.model.LatLng, radiusMeters: Int, count: Int): List<com.google.android.gms.maps.model.LatLng> {
    val out = mutableListOf<com.google.android.gms.maps.model.LatLng>()
    val earth = 6371000.0
    val latRad = Math.toRadians(center.latitude)
    for (i in 0 until count) {
        val angle = 2.0 * Math.PI * i.toDouble() / count.toDouble()
        val dx = radiusMeters * Math.cos(angle)
        val dy = radiusMeters * Math.sin(angle)
        // convert meter offsets to degrees
        val dLat = (dy / earth) * (180.0 / Math.PI)
        val dLng = (dx / (earth * Math.cos(latRad))) * (180.0 / Math.PI)
        out.add(com.google.android.gms.maps.model.LatLng(center.latitude + dLat, center.longitude + dLng))
    }
    return out
}

// Generate 4 corner points for a rectangle (polygon) centered at `center` with given width/height in meters.
private fun generateBoxPoints(center: com.google.android.gms.maps.model.LatLng, widthMeters: Int, heightMeters: Int): List<com.google.android.gms.maps.model.LatLng> {
    val earth = 6371000.0
    val latRad = Math.toRadians(center.latitude)
    // half sizes
    val halfW = widthMeters / 2.0
    val halfH = heightMeters / 2.0

    val dLatH = (halfH / earth) * (180.0 / Math.PI)
    val dLngW = (halfW / (earth * Math.cos(latRad))) * (180.0 / Math.PI)

    val nw = com.google.android.gms.maps.model.LatLng(center.latitude + dLatH, center.longitude - dLngW)
    val ne = com.google.android.gms.maps.model.LatLng(center.latitude + dLatH, center.longitude + dLngW)
    val se = com.google.android.gms.maps.model.LatLng(center.latitude - dLatH, center.longitude + dLngW)
    val sw = com.google.android.gms.maps.model.LatLng(center.latitude - dLatH, center.longitude - dLngW)
    return listOf(nw, ne, se, sw)
}

@Composable
fun EventComposeDialog(onDismiss: () -> Unit, onPosted: () -> Unit) {
    val eventRepo = remember { com.mahakumbh.crowdsafety.data.FirestoreEventRepository() }
    val events by eventRepo.events.collectAsState()
    var selectedEventId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var minutesFromNow by remember { mutableStateOf("60") }
    var posting by remember { mutableStateOf(false) }

    // When the selectedEventId changes, prefill the form
    LaunchedEffect(selectedEventId, events) {
        val ev = events.firstOrNull { it.id == selectedEventId }
        if (ev != null) {
            name = ev.name
            location = ev.location
            // compute minutesFromNow as relative; fallback to 60
            val diff = (ev.startTime - System.currentTimeMillis()) / 60000L
            minutesFromNow = if (diff > 0) diff.toString() else "60"
        } else if (selectedEventId == null) {
            name = ""
            location = ""
            minutesFromNow = "60"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compose / Edit Event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Existing events list (compact) to pick for edit
                if (events.isNotEmpty()) {
                    Text("Posted events", style = MaterialTheme.typography.titleSmall)
                    Column(Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
                        events.forEach { ev ->
                            Row(Modifier.fillMaxWidth().clickable { selectedEventId = ev.id }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(ev.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (ev.id == selectedEventId) FontWeight.Bold else FontWeight.Normal)
                                    Text(ev.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (ev.id == selectedEventId) Text("Selected", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Divider(Modifier.padding(vertical = 8.dp))
                }

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Event name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = minutesFromNow, onValueChange = { minutesFromNow = it.filter { ch -> ch.isDigit() } }, label = { Text("Starts in (min)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Row {
                // Update if an event is selected
                if (!selectedEventId.isNullOrBlank()) {
                    TextButton(onClick = {
                        val ev = events.firstOrNull { it.id == selectedEventId } ?: return@TextButton
                        val start = System.currentTimeMillis() + (minutesFromNow.toLongOrNull() ?: 60L) * 60 * 1000L
                        val updated = ev.copy(name = name, location = if (location.isBlank()) "Main Stage" else location, startTime = start, endTime = start + 60 * 60 * 1000L)
                        eventRepo.updateEvent(updated)
                        onPosted()
                    }) { Text("Update") }

                    TextButton(onClick = {
                        selectedEventId?.let { id -> eventRepo.deleteEvent(id) }
                        // clear selection and form
                        selectedEventId = null
                        name = ""
                        location = ""
                        minutesFromNow = "60"
                        onPosted()
                    }) { Text("Delete") }
                }

                // Always allow posting new events
                TextButton(onClick = {
                    if (name.isBlank()) return@TextButton
                    posting = true
                    val start = System.currentTimeMillis() + (minutesFromNow.toLongOrNull() ?: 60L) * 60 * 1000L
                    val ev = com.mahakumbh.crowdsafety.data.Event(id = "", name = name, description = null, startTime = start, endTime = start + 60 * 60 * 1000L, location = if (location.isBlank()) "Main Stage" else location)
                    eventRepo.postEvent(ev)
                    posting = false
                    // reset form
                    selectedEventId = null
                    name = ""
                    location = ""
                    minutesFromNow = "60"
                    onPosted()
                }) { Text("Post") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun ReservationDetailsDialog(
    reservation: com.mahakumbh.crowdsafety.data.Reservation,
    visitor: com.mahakumbh.crowdsafety.data.ActiveVisitor?,
    visitorName: String? = null,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onAssociate: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reservation Details", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reservation ID: ${reservation.id}")
                Text("Type: ${reservation.type}")
                // Show a friendly location for the zone (fallback to a default Akhara address)
                val defaultAkharaLocation = "Triveni Bandh Prayag, Pura Padain, Prayagraj, Uttar Pradesh"
                val displayLocation = if (reservation.zone.isNullOrBlank() || reservation.zone == "Unknown") defaultAkharaLocation else reservation.zone
                Text("Location: $displayLocation")
                // Provide a sensible default slot when none exists (next 10 minutes)
                val displaySlot = if (reservation.slot.isBlank() || reservation.slot == "Unknown") {
                    val slotTime = System.currentTimeMillis() + 10 * 60 * 1000L
                    android.text.format.DateFormat.format("hh:mm a", java.util.Date(slotTime)).toString()
                } else reservation.slot
                Text("Slot: $displaySlot")
                Text("Status: ${reservation.status}")
                Spacer(Modifier.height(8.dp))
                // If a friendly visitor name was supplied from QR payload, show it prominently
                visitorName?.let { name ->
                    Text("Visitor: $name", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }

                visitor?.let {
                    // Show ID and role/zone if available
                    Text("Visitor ID: ${it.id}")
                    Text("Role: ${it.role}")
                    Text("Zone: ${it.zone}")
                } ?: Column {
                    Text("Visitor not currently online")
                    onAssociate?.let { cb ->
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = cb) { Text("Associate Visitor") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApprove) { Text("Approve") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun AssociateVisitorDialog(
    activeVisitors: List<com.mahakumbh.crowdsafety.data.ActiveVisitor>,
    onSelect: (com.mahakumbh.crowdsafety.data.ActiveVisitor) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Associate Visitor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select an active visitor to associate with this reservation:")
                activeVisitors.forEach { v ->
                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(v) }
                        .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("${v.id}")
                            Text(v.zone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun ReservationVerifierCard() {
    val repo = Locator.repo
    var reservationId by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var verified by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    var decodeError by remember { mutableStateOf<String?>(null) }
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // decode QR from image
            val bmp = uriToBitmap(ctx, it)
            if (bmp != null) {
                val decoded = decodeQrFromBitmap(bmp)
                if (decoded != null) {
                    // expected format: reservation:<id>:...
                    val parts = decoded.split(":")
                    val rid = if (parts.size >= 2 && parts[0] == "reservation") parts[1] else decoded
                    reservationId = rid
                    verifyLocal(repo, rid) { success -> verified = success }
                } else {
                    decodeError = "No QR detected in image"
                }
            } else {
                decodeError = "Unable to load image"
            }
        }
    }
    // Live camera QR scan using ZXing embedded ScanContract
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result: com.journeyapps.barcodescanner.ScanIntentResult? ->
        val contents = result?.contents
        if (!contents.isNullOrBlank()) {
            val parts = contents.split(":")
            val rid = if (parts.size >= 2 && parts[0] == "reservation") parts[1] else contents
            reservationId = rid
            verifyLocal(repo, rid) { success -> verified = success }
        } else if (result != null) {
            decodeError = "No QR detected"
        }
    }

    Card(modifier = Modifier.fillMaxWidth().height(100.dp), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = reservationId, onValueChange = { reservationId = it }, label = { Text("Reservation ID") }, modifier = Modifier.weight(1f))
                Button(onClick = {
                    if (reservationId.isBlank()) return@Button
                    verifying = true
                    val result = repo.verifyReservation(reservationId, verifierId = "volunteer_local")
                    verified = result
                    verifying = false
                }) {
                    if (verifying) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text("Verify")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { pickImageLauncher.launch("image/*") }) { Text("Scan from Image") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { scanLauncher.launch(ScanOptions()) }) { Text("Scan with Camera") }
            }
            if (verified) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Badge, contentDescription = "Verified", tint = Color(0xFF43A047))
                    Spacer(Modifier.width(8.dp))
                    Text("Reservation verified. Provide assistance as needed.")
                }
            }
            decodeError?.let { Text(it, color = Color(0xFFD32F2F)) }
        }
    }
}

private fun uriToBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri).use { stream -> BitmapFactory.decodeStream(stream) }
    } catch (e: Exception) { null }
}

private fun decodeQrFromBitmap(bitmap: Bitmap): String? {
    return try {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val result: Result = MultiFormatReader().decode(bitmap)
        result.text
    } catch (e: Exception) {
        null
    }
}

private fun verifyLocal(repo: com.mahakumbh.crowdsafety.data.CrowdRepository, reservationId: String, cb: (Boolean) -> Unit) {
    // run sync verification (mock repo is synchronous)
    try {
        val ok = repo.verifyReservation(reservationId, "volunteer_local")
        cb(ok)
    } catch (e: Exception) { cb(false) }
}

@Composable
fun TaskCard(
    task: TaskItem,
    onTaskClick: () -> Unit,
    onAccept: () -> Unit,
    onResolve: () -> Unit,
    navController: NavHostController
) {
    val isSosAlert = task.emergencyType != null
    val priorityColor = when (task.status) {
        TaskStatus.Pending -> if (isSosAlert) Color(0xFFD32F2F) else Color(0xFFD32F2F)
        TaskStatus.InProgress -> Color(0xFFFFB300)
        TaskStatus.Completed -> Color(0xFF43A047)
    }
    
    val cardColor = if (isSosAlert && task.status == TaskStatus.Pending) {
        Color(0xFFD32F2F).copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTaskClick() },
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSosAlert && task.status == TaskStatus.Pending) 8.dp else 4.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Task Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            isSosAlert && task.status == TaskStatus.Pending -> Icons.Filled.Emergency
                            task.status == TaskStatus.Pending -> Icons.Filled.PriorityHigh
                            task.status == TaskStatus.InProgress -> Icons.Filled.PlayArrow
                            task.status == TaskStatus.Completed -> Icons.Filled.CheckCircle
                            else -> Icons.Filled.Info
                        },
                        contentDescription = "Status",
                        tint = priorityColor,
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSosAlert) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSosAlert && task.status == TaskStatus.Pending) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = task.status.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = priorityColor,
                        fontWeight = FontWeight.Medium
                    )
                    if (isSosAlert) {
                        Text(
                            text = "SOS ALERT",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD32F2F),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // Compact header Show Alert pill so volunteers see it immediately
                        TextButton(
                            onClick = { navController.navigate(com.mahakumbh.crowdsafety.VolunteerRoute.AlertDetail.createRoute(task.id)) },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF7043)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                        ) {
                            Text("Show Alert", fontSize = 12.sp)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Task Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Location: ${task.location}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Priority: ${task.priority}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "ID: ${task.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action Buttons (primary actions shown inline)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (task.status == TaskStatus.Pending) {
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Accept")
                    }
                }

                if (task.status == TaskStatus.InProgress) {
                    Button(
                        onClick = onResolve,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Resolve")
                    }
                }
            }

            // Prominent SOS quick action placed below primary buttons so it's always visible
            if (isSosAlert) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { navController.navigate(com.mahakumbh.crowdsafety.VolunteerRoute.AlertDetail.createRoute(task.id)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7043))
                ) {
                    Text("Show Alert", color = Color.White)
                }
            }

            // LOST item info quick action: show visitor-posted lost-item details
            val isLostItem = task.title.startsWith("🔎 Lost Item") || (task.message != null && task.emergencyType == null && task.title.contains("Lost Item", ignoreCase = true))
            var showLostItemInfo by remember { mutableStateOf(false) }
            if (isLostItem) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { navController.navigate(com.mahakumbh.crowdsafety.VolunteerRoute.LostItemDetail.createRoute(task.id)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFE0B2))
                ) {
                    Text("LOST thing info")
                }
            }
        }
    }
}

@Composable
fun TaskDetailDialog(
    task: TaskItem,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    onResolve: () -> Unit,
    onEscalate: () -> Unit
) {
    var showAlertMsg by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = "Task Details",
                fontWeight = FontWeight.Bold
            )
        },
        text = { 
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailRow("Title", task.title)
                DetailRow("Location", task.location)
                DetailRow("Status", task.status.name)
                DetailRow("Priority", task.priority)
                DetailRow("ID", task.id)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Emergency Type: ${task.emergencyType ?: "Unknown"}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "Visitor Location: ${task.visitorLocation ?: "Unknown"}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                task.message?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Message:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Text(text = msg, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
    
    // Action Buttons
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (task.status == TaskStatus.Pending) {
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Accept Task")
            }
        }
        
        if (task.status == TaskStatus.InProgress) {
            Button(
                onClick = onResolve,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Mark Resolved")
            }
        }
        // If this is an SOS task, surface a button that shows the alert message / visitor info
        if (task.emergencyType != null) {
            Button(
                onClick = { showAlertMsg = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF7043)
                )
            ) {
                Text("Create one alert")
            }
        }
        
        Button(
            onClick = onEscalate,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFD32F2F)
            )
        ) {
            Text("Escalate to Supervisor")
        }
    }

    if (showAlertMsg) {
        AlertDialog(
            onDismissRequest = { showAlertMsg = false },
            title = { Text("Alert Message") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(task.title, fontWeight = FontWeight.Bold)
                    Text("Type: ${task.emergencyType}")
                    Text("Visitor Location: ${task.visitorLocation ?: "Unknown"}")
                    task.message?.let { m -> Text("Message: $m") }
                    // Optionally show more context if available
                }
            },
            confirmButton = {
                TextButton(onClick = { showAlertMsg = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun VolunteerCrowdMapView() {
    val context = LocalContext.current
    val repo = Locator.repo
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    // Start focused on Triveni Bandh (Pura Padain, Prayagraj)
    val triveniCenter = LatLng(25.419518, 81.889800)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(triveniCenter, 15.5f)
    }
    val coroutineScope = rememberCoroutineScope()
    val currentLatLng = remember { mutableStateOf<LatLng?>(null) }
    val activeVisitors by repo.activeVisitors.collectAsState(initial = emptyList())
    
    // Mock visitor locations for demonstration
    val visitorLocations = remember {
        listOf(
            LatLng(20.5937, 78.9629), // Zone A
            LatLng(20.5938, 78.9630), // Zone B
            LatLng(20.5936, 78.9628), // Zone C
            LatLng(20.5939, 78.9631), // Zone D
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) {
            val token = CancellationTokenSource()
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                .addOnSuccessListener { location ->
                    location?.let {
                        val ll = LatLng(it.latitude, it.longitude)
                        currentLatLng.value = ll
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(ll, 16f))
                        }
                    }
                }
        }
    }

    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!(fine || coarse)) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else {
            val token = CancellationTokenSource()
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                .addOnSuccessListener { location ->
                    location?.let {
                        val ll = LatLng(it.latitude, it.longitude)
                        currentLatLng.value = ll
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(ll, 16f))
                        }
                    }
                }
        }
    }

    // Default camera to a world view so the map renders even before location arrives
    if (cameraPositionState.position.zoom == 0f) {
        coroutineScope.launch {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(20.5937, 78.9629), 15f))
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Map Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Crowd Map View",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Real-time visitor locations and crowd density",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Google Map
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            GoogleMap(
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    mapType = com.google.maps.android.compose.MapType.NORMAL,
                    isMyLocationEnabled = currentLatLng.value != null,
                    isTrafficEnabled = true,
                    isIndoorEnabled = true,
                    isBuildingEnabled = true
                ),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = true,
                    zoomControlsEnabled = true,
                    compassEnabled = true,
                    mapToolbarEnabled = true
                )
            ) {
                // Current location marker
                currentLatLng.value?.let { ll ->
                    Marker(
                        state = MarkerState(ll),
                        title = "Your Location",
                        snippet = "Volunteer Position"
                    )
                }
                
                // Zone markers with visitor density
                visitorLocations.forEachIndexed { index, location ->
                    val zoneName = "Zone ${('A' + index)}"
                    val density = when (index) {
                        0 -> "High" // Zone A
                        1 -> "Medium" // Zone B
                        2 -> "Low" // Zone C
                        else -> "Medium" // Zone D
                    }
                    
                    Marker(
                        state = MarkerState(location),
                        title = zoneName,
                        snippet = "Visitor Density: $density"
                    )
                }
                
                // Active visitors markers
                activeVisitors.forEach { visitor ->
                    val visitorLocation = LatLng(visitor.lat, visitor.lng)
                    val markerTitle = if (visitor.role == UserRole.Visitor) {
                        "Visitor ${visitor.id}"
                    } else {
                        "Volunteer ${visitor.id}"
                    }
                    val markerSnippet = "Zone: ${visitor.zone} • Online: ${if (visitor.isOnline) "Yes" else "No"}"
                    
                    Marker(
                        state = MarkerState(visitorLocation),
                        title = markerTitle,
                        snippet = markerSnippet
                    )
                }

                // ---- Draw an orange rectangular boundary and dotted outline around Triveni Bandh ----
                val boundaryCenter = LatLng(25.419518, 81.889800)
                // width/height in meters for the rectangle (tweak to fit exact area)
                val boxWidthMeters = 2200
                val boxHeightMeters = 1600
                val boundaryPoints = generateBoxPoints(boundaryCenter, boxWidthMeters, boxHeightMeters)

                // translucent orange fill and thin orange stroke (box)
                com.google.maps.android.compose.Polygon(points = boundaryPoints, fillColor = Color(0x22FF9800), strokeColor = Color(0xFFFF9800), strokeWidth = 3f)

                // dotted outline: small orange circle markers placed along the polygon edges
                // sample along each segment to create the dotted appearance
                val dotCountPerEdge = 12
                for (i in boundaryPoints.indices) {
                    val a = boundaryPoints[i]
                    val b = boundaryPoints[(i + 1) % boundaryPoints.size]
                    for (k in 0 until dotCountPerEdge) {
                        val t = k.toDouble() / dotCountPerEdge.toDouble()
                        val lat = a.latitude + (b.latitude - a.latitude) * t
                        val lng = a.longitude + (b.longitude - a.longitude) * t
                        com.google.maps.android.compose.Circle(center = LatLng(lat, lng), radius = 8.0, strokeColor = Color(0x00FFFFFF), strokeWidth = 0f, fillColor = Color(0xFFFF9800))
                    }
                }

                // ---- Precise location marker / accuracy circle ----
                currentLatLng.value?.let { ll ->
                    // small solid blue dot for exact location
                    com.google.maps.android.compose.Circle(center = ll, radius = 5.0, strokeColor = Color(0xFF1565C0), strokeWidth = 0f, fillColor = Color(0xFF1565C0))
                    // accuracy ring (translucent)
                    com.google.maps.android.compose.Circle(center = ll, radius = 40.0, strokeColor = Color(0x331565C0), strokeWidth = 0f, fillColor = Color(0x331565C0))

                    Marker(state = MarkerState(ll), title = "You (precise)", snippet = "Exact GPS location")
                }
            }

            // Map Controls Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Legend",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = "Active Visitors: ${activeVisitors.size}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        color = Color(0xFFD32F2F),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "High Density",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        color = Color(0xFFFFB300),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Medium Density",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        color = Color(0xFF43A047),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Low Density",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("👤", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Active Visitors",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Zone Status Summary
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Zone Status Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ZoneStatusCard("Zone A", "HIGH", Color(0xFFD32F2F), modifier = Modifier.weight(1f))
                    ZoneStatusCard("Zone B", "MEDIUM", Color(0xFFFFB300), modifier = Modifier.weight(1f))
                    ZoneStatusCard("Zone C", "LOW", Color(0xFF43A047), modifier = Modifier.weight(1f))
                    ZoneStatusCard("Zone D", "MEDIUM", Color(0xFFFFB300), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ZoneStatusCard(zoneName: String, status: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = zoneName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = status,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun GateStatusCard(
    gateName: String, 
    status: String, 
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = gateName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun VolunteerCheckInScreen() {
    val context = LocalContext.current
    val repo = Locator.repo
    val role by repo.currentUserRole.collectAsState()
    val id by repo.currentUserId.collectAsState()
    var selectedZone by remember { mutableStateOf("Zone A") }
    var isCheckedIn by remember { mutableStateOf(false) }
    var checkInTime by remember { mutableStateOf("") }
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    val zones = listOf("Zone A", "Zone B", "Zone C", "Zone D")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Invisible top spacer to avoid the header being clipped by the app bar
        item {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(0.dp)
            ) { /* spacer */ }
        }
        item {
            // Header
            Text(
                text = "Volunteer Check-in",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            // Zone Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Select Zone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    zones.forEach { zone ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedZone = zone }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedZone == zone,
                                onClick = { selectedZone = zone }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = zone,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }

        item {
            // Check-in Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Check-in Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    if (isCheckedIn) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Checked In",
                                tint = Color(0xFF43A047),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Currently checked in to $selectedZone",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFF43A047)
                            )
                        }
                        
                        if (checkInTime.isNotEmpty()) {
                            Text(
                                text = "Check-in time: $checkInTime",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "Not currently checked in",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            // Check-in Button
            Button(
                onClick = { 
                    if (!isCheckedIn) {
                        // Check in with location
                        val token = CancellationTokenSource()
                        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                            .addOnSuccessListener { location ->
                                location?.let {
                                    repo.checkInVisitor(
                                        id ?: "anonymous",
                                        role ?: UserRole.Volunteer,
                                        it.latitude,
                                        it.longitude,
                                        selectedZone
                                    )
                                    isCheckedIn = true
                                    checkInTime = "Now"
                                }
                            }
                            .addOnFailureListener {
                                // Fallback to default location if GPS fails
                                repo.checkInVisitor(
                                    id ?: "anonymous",
                                    role ?: UserRole.Volunteer,
                                    20.5937, // Default location
                                    78.9629,
                                    selectedZone
                                )
                                isCheckedIn = true
                                checkInTime = "Now"
                            }
                    } else {
                        // Check out
                        repo.checkOutVisitor(id ?: "anonymous")
                        isCheckedIn = false
                        checkInTime = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCheckedIn) {
                        Color(0xFFD32F2F)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isCheckedIn) "Check Out" else "Check In",
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun VolunteerDashboard(navController: androidx.navigation.NavHostController) {
    val zoneVm = DashboardViewModel()
    val risks by zoneVm.zoneRisks.collectAsState()
    val heat by zoneVm.heat.collectAsState()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            // Header
            Text(
                text = "Volunteer Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Real-time crowd monitoring and zone status",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
                Spacer(modifier = Modifier.height(12.dp))
                // Live Stream feature button (full-width)
                var showZonePickerLocal by remember { mutableStateOf(false) }
                var selectedZoneLocal by remember { mutableStateOf<String?>(null) }
                val piBaseLocal = remember { "http://172.19.81.89:5000" }
                var zoneCountLocal by remember { mutableStateOf(0) }

                Button(
                    onClick = { showZonePickerLocal = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDCEFFF), contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Videocam, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("Live Stream", style = MaterialTheme.typography.titleLarge)
                    }
                }

                if (showZonePickerLocal) {
                    ZonePickerDialog(onSelect = { z ->
                        showZonePickerLocal = false
                        // navigate to dedicated LiveStream page with zone argument
                        navController.navigate(com.mahakumbh.crowdsafety.VolunteerRoute.LiveStream.createRoute(z))
                    }, onDismiss = { showZonePickerLocal = false })
                }
        }

        item {
            // Zone Risk Overview
            Text(
                text = "Zone Risk Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                risks.forEach { zone ->
                    ZoneRiskCard(zone = zone)
                }
            }
        }

        item {
            // Density Heatmap
            Text(
                text = "Visitor Density Heatmap",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    DensityHeatmap(heat = heat)
                }
            }
        }

        item {
            // Quick Stats
            Text(
                text = "Quick Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Total Zones",
                    value = "${risks.size}",
                    icon = Icons.Filled.LocationOn,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                
                StatCard(
                    title = "High Risk",
                    value = "${risks.count { it.risk > 0.7f }}",
                    icon = Icons.Filled.Warning,
                    color = Color(0xFFD32F2F),
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Safe Zones",
                    value = "${risks.count { it.risk < 0.4f }}",
                    icon = Icons.Filled.CheckCircle,
                    color = Color(0xFF43A047),
                    modifier = Modifier.weight(1f)
                )
                
                StatCard(
                    title = "Avg Wait",
                    value = "${risks.map { it.minutesToCritical }.average().toInt()} min",
                    icon = Icons.Filled.Schedule,
                    color = Color(0xFFFFB300),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ZoneRiskCard(zone: ZoneRisk) {
    val riskColor = when {
        zone.risk > 0.7f -> Color(0xFFD32F2F) // Red
        zone.risk > 0.4f -> Color(0xFFFFB300) // Yellow
        else -> Color(0xFF43A047) // Green
    }
    
    val riskText = when {
        zone.risk > 0.7f -> "HIGH RISK"
        zone.risk > 0.4f -> "MODERATE"
        else -> "SAFE"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = riskColor.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = zone.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Risk: ${(zone.risk * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = riskColor,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "Critical in: ${zone.minutesToCritical} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Icon(
                    imageVector = when {
                        zone.risk > 0.7f -> Icons.Filled.Warning
                        zone.risk > 0.4f -> Icons.Filled.Info
                        else -> Icons.Filled.CheckCircle
                    },
                    contentDescription = "Risk Level",
                    tint = riskColor,
                    modifier = Modifier.size(32.dp)
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = riskText,
                    style = MaterialTheme.typography.bodySmall,
                    color = riskColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DensityHeatmap(heat: List<HeatCell>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (heat.isNotEmpty()) {
            heat.forEach { cell ->
                val intensity = cell.intensity
                val color = when {
                    intensity > 0.7f -> Color(0x66E53935) // Red
                    intensity > 0.4f -> Color(0x66FFB300) // Yellow
                    else -> Color(0x6643A047) // Green
                }
                val xSize = size.width / 10f
                val ySize = size.height / 5f
                drawRect(
                    color = color,
                    topLeft = Offset(cell.x * xSize, cell.y * ySize),
                    size = Size(xSize - 2f, ySize - 2f)
                )
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
