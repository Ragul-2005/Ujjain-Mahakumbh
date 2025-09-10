package com.mahakumbh.crowdsafety

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.json.JSONObject
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mahakumbh.crowdsafety.data.*
import com.mahakumbh.crowdsafety.vm.*

import com.google.android.gms.location.LocationServices
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.compose.runtime.rememberCoroutineScope
import com.google.android.gms.maps.CameraUpdateFactory
import kotlinx.coroutines.launch
import com.google.android.gms.location.Priority
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.mahakumbh.crowdsafety.data.SosType
import com.mahakumbh.crowdsafety.data.UserRole
import com.mahakumbh.crowdsafety.di.Locator

@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
	val risks by vm.zoneRisks.collectAsState()
	val heat by vm.heat.collectAsState()
	// Live stream state
	var showZonePicker by remember { mutableStateOf(false) }
	var selectedZone by remember { mutableStateOf<String?>(null) }
	val piBase = remember { "http://172.19.81.89:5000" } // replace with your Pi IP or make configurable
	var zoneCount by remember { mutableStateOf(0) }

	// Poll counts when a zone is selected (robust parsing)
	LaunchedEffect(selectedZone) {
		while (selectedZone != null) {
			try {
				val url = java.net.URL("$piBase/count")
				val conn = url.openConnection() as java.net.HttpURLConnection
				conn.connectTimeout = 2000
				conn.readTimeout = 2000
				conn.requestMethod = "GET"
				conn.doInput = true
				conn.connect()
				val text = conn.inputStream.bufferedReader().use { it.readText() }
				try {
					val json = org.json.JSONObject(text)
					val key = selectedZone ?: "Zone A"
					val rawVal = json.opt(key)
					val persons = when (rawVal) {
						is org.json.JSONObject -> rawVal.optInt("display", rawVal.optInt("raw_last", 0))
						is Number -> rawVal.toInt()
						is String -> rawVal.toIntOrNull() ?: 0
						else -> json.optInt(key.replace(" ", ""), 0)
					}
					zoneCount = persons
				} catch (_: Exception) {
					zoneCount = text.trim().toIntOrNull() ?: 0
				}
				conn.disconnect()
			} catch (_: Exception) {
				// ignore and retry
			}
			kotlinx.coroutines.delay(800)
		}
	}
	Column(Modifier.fillMaxSize().padding(16.dp)) {
		// Invisible top spacer to avoid content being clipped under the app bar (keeps layout consistent with Tasks)
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
		// Live Stream launcher at top of dashboard
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
			Button(onClick = { showZonePicker = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDCEFFF), contentColor = Color.Black), shape = RoundedCornerShape(12.dp)) {
				Icon(Icons.Filled.Videocam, contentDescription = null)
				Spacer(Modifier.width(8.dp))
				Text("Live Stream")
			}
		}
		Spacer(Modifier.height(12.dp))

		if (showZonePicker) {
			ZonePickerDialog(onSelect = { z ->
				selectedZone = z
				showZonePicker = false
			}, onDismiss = { showZonePicker = false })
		}

		// If a zone is selected, show the live stream and count
		selectedZone?.let { zone ->
			Text("Live Stream - $zone", style = MaterialTheme.typography.titleLarge)
			Spacer(Modifier.height(8.dp))
			Text("Persons: $zoneCount", style = MaterialTheme.typography.titleMedium)
			Spacer(Modifier.height(8.dp))
			// Build stream URL using full zone name (URL-encoded) so it matches the Pi /video_feed?zone=<Zone name>
			val streamUrl = "$piBase/video_feed?zone=${java.net.URLEncoder.encode(zone, "UTF-8").replace("+", "%20") }"
			AndroidView(factory = { ctx ->
				android.webkit.WebView(ctx).apply {
					settings.javaScriptEnabled = true
					webViewClient = object : android.webkit.WebViewClient() {}
					loadUrl(streamUrl)
				}
			}, modifier = Modifier.fillMaxWidth().height(220.dp))
			Spacer(Modifier.height(12.dp))
		}

		Text("Live Risk Overview", style = MaterialTheme.typography.titleLarge)
		Spacer(Modifier.height(12.dp))
	// compute counts per zone (prefers presenceRepo.online then mock repo)
	val repo = Locator.repo
	val presenceList by Locator.presenceRepo.online.collectAsState(initial = emptyList())
	val mockVisitors by repo.activeVisitors.collectAsState(initial = emptyList())
	val activeVisitors = if (presenceList.isNotEmpty()) presenceList else mockVisitors
	val counts = activeVisitors.groupingBy { it.zone.ifBlank { "Unknown" } }.eachCount()
	val risksWithCounts = risks.map { z -> Pair(z, counts[z.displayName] ?: 0) }
	RiskTilesRow(risksWithCounts)
		Spacer(Modifier.height(12.dp))
		Text("Density Heatmap", style = MaterialTheme.typography.titleMedium)
		Spacer(Modifier.height(8.dp))
		Heatmap(heat)
	}
}

// Zone picker dialog
@Composable
fun ZonePickerDialog(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
	val zones = listOf("Zone A", "Zone B", "Zone C")
	AlertDialog(onDismissRequest = onDismiss, title = { Text("Select Zone") }, text = {
		Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
			zones.forEach { z ->
				Button(onClick = { onSelect(z) }, modifier = Modifier.fillMaxWidth()) { Text(z) }
			}
		}
	}, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
private fun RiskTilesRow(risksWithCounts: List<Pair<ZoneRisk, Int>>) {
	Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		risksWithCounts.take(3).forEach { (z, people) -> RiskTile(z, people = people, modifier = Modifier.weight(1f)) }
	}
}

@Composable
private fun RiskTile(z: ZoneRisk, people: Int = 0, modifier: Modifier = Modifier) {
	val color = when {
		z.risk > 0.7f -> Color(0xFFE53935)
		z.risk > 0.4f -> Color(0xFFFFB300)
		else -> Color(0xFF43A047)
	}
	Card(modifier) {
		Column(Modifier.padding(12.dp)) {
			Text(z.displayName, fontWeight = FontWeight.SemiBold)
			Spacer(Modifier.height(8.dp))
			Text("Risk: ${(z.risk * 100).toInt()}%", color = color)
			Text("TTC: ${z.minutesToCritical} min")
			Spacer(Modifier.height(6.dp))
			Text("People: $people", fontWeight = FontWeight.SemiBold)
		}
	}
}

@Composable
private fun Heatmap(cells: List<HeatCell>) {
	Card(Modifier.fillMaxWidth().height(220.dp)) {
		Canvas(Modifier.fillMaxSize()) {
			if (cells.isNotEmpty()) {
				cells.forEach { cell ->
					val intensity = cell.intensity
					val color = when {
						intensity > 0.7f -> Color(0x66E53935)
						intensity > 0.4f -> Color(0x66FFB300)
						else -> Color(0x6643A047)
					}
					val xSize = size.width / 10f
					val ySize = size.height / 5f
					drawRect(
						color = color,
						topLeft = androidx.compose.ui.geometry.Offset(cell.x * xSize, cell.y * ySize),
						size = androidx.compose.ui.geometry.Size(xSize - 2f, ySize - 2f)
					)
				}
			}
			val path = Path().apply {
				moveTo(size.width * 0.1f, size.height * 0.8f)
				lineTo(size.width * 0.5f, size.height * 0.5f)
				lineTo(size.width * 0.9f, size.height * 0.6f)
			}
			drawPath(path, Color(0x880062FF), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
		}
	}
}

@Composable
fun PlaybooksScreen(vm: PlaybooksViewModel = viewModel()) {
	val playbooks by vm.playbooks.collectAsState()
	Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
		Text("Playbooks", style = MaterialTheme.typography.titleLarge)
		playbooks.forEach { p -> PlaybookCard(p, onSimulate = { vm.simulate(p.id) }, onActivate = { vm.activate(p.id) }) }
	}
}

@Composable
private fun PlaybookCard(p: Playbook, onSimulate: () -> Unit, onActivate: () -> Unit) {
	Card(Modifier.fillMaxWidth()) {
		Column(Modifier.padding(12.dp)) {
			Text(p.title, fontWeight = FontWeight.SemiBold)
			Text(p.steps, color = Color.Gray)
			if (p.isActive) Text("Active", color = Color(0xFF43A047))
			Spacer(Modifier.height(8.dp))
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				Button(onClick = onSimulate) { Text("Simulate") }
				Button(onClick = onActivate) { Text("Activate") }
			}
		}
	}
}

@Composable
fun TasksScreen(vm: TasksViewModel = viewModel()) {
	val tasks by vm.tasks.collectAsState()
	Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
		Text("Field Tasks", style = MaterialTheme.typography.titleLarge)
		tasks.forEach { t -> TaskCard(t, onAccept = { vm.accept(t.id) }, onDone = { vm.done(t.id) }) }
	}
}

@Composable
private fun TaskCard(t: TaskItem, onAccept: () -> Unit, onDone: () -> Unit) {
	Card(Modifier.fillMaxWidth()) {
		Column(Modifier.padding(12.dp)) {
			Text(t.title, fontWeight = FontWeight.SemiBold)
			Text(t.assignee)
			Text("Location: ${t.location}", color = Color.Gray)
			Text("Status: ${t.status}")
			Spacer(Modifier.height(8.dp))
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				Button(onClick = onAccept) { Text("Accept") }
				Button(onClick = onDone) { Text("Done") }
			}
		}
	}
}

@Composable
fun IncidentsScreen(vm: IncidentsViewModel = viewModel()) {
	val incidents by vm.incidents.collectAsState()
	Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
		Text("Incidents & Alerts", style = MaterialTheme.typography.titleLarge)
		incidents.forEach { i -> IncidentTile("${i.severity}: ${i.message}") }
	}
}

@Composable
private fun IncidentTile(text: String) {
	Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(text) } }
}

@Composable
fun AnalyticsScreen(vm: AnalyticsViewModel = viewModel()) {
	val kpis by vm.kpis.collectAsState()
	Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Text("KPIs", style = MaterialTheme.typography.titleLarge)
		kpis.forEach { k -> KpiRow(k.label, k.value) }
	}
}

@Composable
private fun KpiRow(label: String, value: String) {
	Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
		Text(label)
		Text(value, fontWeight = FontWeight.SemiBold)
	}
}

@Composable
fun SettingsScreen() {
	Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
		Text("Settings", style = MaterialTheme.typography.titleLarge)
		Text("Use mocked data: ON")
		Text("Edge mode fallback: Enabled")
		Text("Language: English (tap to change)")
	}
}

@Composable
fun MapScreen() {
	val context = LocalContext.current
	val repo = Locator.repo
	val userId by repo.currentUserId.collectAsState()
	val userRole by repo.currentUserRole.collectAsState()
	val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
	val cameraPositionState = rememberCameraPositionState {
		position = CameraPosition.fromLatLngZoom(LatLng(20.0, 0.0), 2f)
	}
	val coroutineScope = rememberCoroutineScope()
	val currentLatLng = remember { mutableStateOf<LatLng?>(null) }

	val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
		contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
	) { granted ->
		val ok = granted[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
			granted[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
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
		val fine = androidx.core.content.ContextCompat.checkSelfPermission(
			context, android.Manifest.permission.ACCESS_FINE_LOCATION
		) == android.content.pm.PackageManager.PERMISSION_GRANTED
		val coarse = androidx.core.content.ContextCompat.checkSelfPermission(
			context, android.Manifest.permission.ACCESS_COARSE_LOCATION
		) == android.content.pm.PackageManager.PERMISSION_GRANTED
		if (!(fine || coarse)) {
			permissionLauncher.launch(arrayOf(
				android.Manifest.permission.ACCESS_FINE_LOCATION,
				android.Manifest.permission.ACCESS_COARSE_LOCATION
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
	// camera already initialized above with default; no direct assignments required

	Box(Modifier.fillMaxSize()) {
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
			currentLatLng.value?.let { ll ->
				Marker(state = MarkerState(ll), title = "You")
			}
		}

		var expanded by remember { mutableStateOf(false) }
		FloatingActionButton(
			modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
			onClick = { expanded = true }
			) { Icon(imageVector = Icons.Default.Report, contentDescription = "SOS") }
		DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.align(Alignment.BottomEnd)) {
			DropdownMenuItem(text = { Text("Panic") }, onClick = {
				expanded = false
				currentLatLng.value?.let { ll ->
					if (userId != null && userRole != null) repo.logSos(SosType.Panic, userId!!, userRole!!, ll.latitude, ll.longitude)
				}
			})
			DropdownMenuItem(text = { Text("Medical") }, onClick = {
				expanded = false
				currentLatLng.value?.let { ll ->
					if (userId != null && userRole != null) repo.logSos(SosType.Medical, userId!!, userRole!!, ll.latitude, ll.longitude)
				}
			})
			DropdownMenuItem(text = { Text("Security") }, onClick = {
				expanded = false
				currentLatLng.value?.let { ll ->
					if (userId != null && userRole != null) repo.logSos(SosType.Security, userId!!, userRole!!, ll.latitude, ll.longitude)
				}
			})
		}
	}
}
