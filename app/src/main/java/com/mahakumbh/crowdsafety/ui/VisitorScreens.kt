/*
 Project: Mahakumbh CrowdSafety (Android - Jetpack Compose)

 Overview:
 - This file implements Visitor-facing UI: VisitorHomeScreen and VisitorMapScreen,
   plus several lightweight placeholders used while the full feature screens are
   implemented elsewhere.
 - Responsibilities:
    * Build Visitor home cards (weather, lost-people, donation, events, SOS)
    * Load POI assets (assets/poi_sample.geojson, assets/akhada_locations.csv)
    * Manage location permissions, current location and Google Maps Compose markers
    * Use Locator.repo for runtime data (activeVisitors, weather, lostPersonReports, logging SOS)
 - Related packages/files:
    * com.mahakumbh.crowdsafety.data    -> data models, repository interfaces, UserRole, Event, SosType, etc.
    * com.mahakumbh.crowdsafety.di      -> Locator (provides repo)
    * com.mahakumbh.crowdsafety.ui.*    -> other UI screens (DonationScreen, DensityHeatMapScreen, etc.)
    * assets/poi_sample.geojson         -> optional POI GeoJSON used to populate map layers
    * assets/akhada_locations.csv       -> optional CSV with akhada/poi overrides
    * VisitorRoute (app routing)        -> routes used for navigation from this file
 - Notes / Tips:
    * The map uses Google Maps Compose. Marker icons are derived with a small symbolMarker helper.
    * POI lists are force-populated around Triveni Bandh so the map shows meaningful content even without assets.
    * To inspect repository data flows, open Locator and the concrete FeatureRepository implementation under data/.
    * Keep this header updated when responsibilities or asset names change.
*/
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.mahakumbh.crowdsafety.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.mahakumbh.crowdsafety.di.Locator
import com.mahakumbh.crowdsafety.data.UserRole
import com.mahakumbh.crowdsafety.VisitorRoute
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

// Clean placeholder helpers. Keep lightweight placeholders for screens not needed for the immediate map code.

// Minimal support types/functions to satisfy compile until full helpers are reintroduced
private val AppPadding = 12.dp

data class Poi(
    val ll: LatLng,
    val name: String,
    val mapplsPin: String? = null,
    val address: String? = null
)

// Very small clustering stub: returns each point as count=1 (no clustering)
private fun clusterPoints(points: List<LatLng>): List<Pair<LatLng, Int>> = points.map { Pair(it, 1) }

// replace the old symbolMarker helper with a bitmap-drawing helper that creates round flat markers
private fun createCircleBitmapDescriptor(context: Context, colorHex: String, label: String? = null): com.google.android.gms.maps.model.BitmapDescriptor? {
	// draws a circular colored marker with white inner circle and optional emoji/text
	return try {
		val size = (96 * (context.resources.displayMetrics.density)).toInt().coerceAtLeast(64)
		val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(bmp)
		val paint = Paint(Paint.ANTI_ALIAS_FLAG)

		// parse color safely
		val fillColor = try { android.graphics.Color.parseColor(colorHex) } catch (_: Exception) { android.graphics.Color.DKGRAY }

		// outer circle (colored)
		paint.color = fillColor
		val cx = size / 2f
		val cy = size / 2f
		val outerR = size * 0.5f
		canvas.drawCircle(cx, cy, outerR, paint)

		// inner white circle
		paint.color = android.graphics.Color.WHITE
		val innerR = size * 0.34f
		canvas.drawCircle(cx, cy, innerR, paint)

		// optional label/emoji drawn centered
		if (!label.isNullOrBlank()) {
			paint.color = android.graphics.Color.BLACK
			paint.textAlign = Paint.Align.CENTER
			paint.textSize = innerR * 0.9f
			paint.isFakeBoldText = true
			// vertical centering
			val fm = paint.fontMetrics
			val textY = cy - (fm.ascent + fm.descent) / 2f
			canvas.drawText(label, cx, textY, paint)
		}

		com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bmp)
	} catch (_: Exception) {
		null
	}
}

// Find nearest POI name from a point (simple linear search)
private fun findNearestPoiName(list: List<Poi>, point: LatLng): String? {
    if (list.isEmpty()) return null
    var best: Poi? = null
    var bestDist = Double.MAX_VALUE
    for (p in list) {
        val dLat = p.ll.latitude - point.latitude
        val dLng = p.ll.longitude - point.longitude
        val dist = dLat * dLat + dLng * dLng
        if (dist < bestDist) {
            bestDist = dist
            best = p
        }
    }
    return best?.name
}


@Composable
fun VisitorHomeScreen(onNavigate: (String) -> Unit) {
    val repo = Locator.repo
    val featureRepo = repo as? com.mahakumbh.crowdsafety.data.FeatureRepository
    val context = LocalContext.current
    val weather by repo.weatherData.collectAsState(initial = null)
    // Prefer Firestore-backed lost-person reports so posts are realtime across devices.
    // Fallback to the mock repo flow if needed elsewhere, but use the shared singleton here.
    val lostReports by Locator.lostRepo.reports.collectAsState(initial = emptyList())
    // Default sample reports shown when Firestore list is empty (useful for demo/debug)
    val sampleLostReports = listOf(
        com.mahakumbh.crowdsafety.data.LostPersonReport(
            id = "sample-1",
            reporterId = "system",
            name = "Raju Sharma",
            age = 72,
            gender = "Male",
            clothingDescription = "Blue shirt, white dhoti",
            lastKnownLocation = "Sangam Ghat",
            photoUrl = "",
            status = com.mahakumbh.crowdsafety.data.ReportStatus.Active,
            timestamp = System.currentTimeMillis()
        ),
        com.mahakumbh.crowdsafety.data.LostPersonReport(
            id = "sample-2",
            reporterId = "system",
            name = "Sita Devi",
            age = 65,
            gender = "Female",
            clothingDescription = "Red saree",
            lastKnownLocation = "Hanuman Garhi",
            photoUrl = "",
            status = com.mahakumbh.crowdsafety.data.ReportStatus.Active,
            timestamp = System.currentTimeMillis() - 3600000
        )
    )
    val visibleLostReports = if (lostReports.isEmpty()) sampleLostReports else lostReports
    val eventsState = if (featureRepo != null) {
        featureRepo.events.collectAsState(initial = featureRepo.getEvents())
    } else {
        remember { mutableStateOf(emptyList<com.mahakumbh.crowdsafety.data.Event>()) }
    }
    val events = eventsState.value

    // standard height for feature buttons/cards on the Home screen
    val featureCardHeight = 72.dp

    Column(modifier = Modifier
        .fillMaxSize()
        // ensure content sits below system status bars and add a small extra top inset
        .statusBarsPadding()
        .padding(top = 8.dp)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
    // ...existing content...

        // small invisible button spacer to prevent the weather card from clipping under the top bar
        Button(
            onClick = { /* spacer - intentionally empty */ },
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent)
        ) { }

        // Weather (click to open detailed weather screen)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(VisitorRoute.Weather.route) },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF6A4B9C))
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WbSunny, contentDescription = "Weather", tint = Color(0xFFFFF3E0), modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(text = weather?.temperature?.let { "${it.toInt()}°C" } ?: "--°C", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color(0xFFFFF3E0))
                    Text(text = weather?.weatherType?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Unknown", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFF3E0).copy(alpha = 0.9f))
                }
            }
        }

        // Finding Lost People (prominent gradient card — keep purple theme)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(featureCardHeight)
                .clickable { onNavigate(VisitorRoute.LostPeople.route) },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF7C4DFF))
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(id = com.mahakumbh.crowdsafety.R.string.home_finding_lost), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(id = com.mahakumbh.crowdsafety.R.string.home_report_missing), color = Color.White.copy(alpha = 0.95f), style = MaterialTheme.typography.bodyMedium)
                }
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.12f)) {
                    Text("${visibleLostReports.size}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White)
                }
            }
        }

        // Donation (boxed card with light pink background)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(featureCardHeight)
                .border(BorderStroke(1.dp, Color(0xFFFFCCD9)), shape = RoundedCornerShape(12.dp))
                .clickable { onNavigate(VisitorRoute.Donation.route) },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE6F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFD36BAF), modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text(stringResource(id = com.mahakumbh.crowdsafety.R.string.home_online_donation), style = MaterialTheme.typography.headlineSmall, color = Color(0xFF2D0B1A))
            }
        }

        // Finding Lost Things (navigates to dedicated Lost Items screen)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(featureCardHeight)
                .border(BorderStroke(1.dp, Color(0xFFFFE0B2)), shape = RoundedCornerShape(12.dp))
                .clickable { onNavigate(VisitorRoute.LostItems.route) },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FindInPage, contentDescription = null, tint = Color(0xFFBF360C), modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Finding Lost Things", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF3E2723))
                    Text("Post an item you lost and volunteers will be notified", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Density Map (boxed card with light sky-blue background)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(featureCardHeight)
                .border(BorderStroke(1.dp, Color(0xFFBEE9FF)), shape = RoundedCornerShape(12.dp))
                .clickable { onNavigate(VisitorRoute.Density.route) },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F9FF)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GridOn, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text(stringResource(id = com.mahakumbh.crowdsafety.R.string.home_density_map), style = MaterialTheme.typography.headlineSmall, color = Color(0xFF0B3B5C))
            }
        }

        // Reservation (boxed card with light yellow background)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(featureCardHeight)
                .border(BorderStroke(1.dp, Color(0xFFFFF3CC)), shape = RoundedCornerShape(12.dp))
                .clickable { onNavigate(VisitorRoute.Reservation.route) },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9E6)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFFB388FF), modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(id = com.mahakumbh.crowdsafety.R.string.home_priority_reservation), style = MaterialTheme.typography.headlineSmall, color = Color(0xFF2B2B20))
                    Text(stringResource(id = com.mahakumbh.crowdsafety.R.string.home_priority_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Event Schedule feature (boxed, light green)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(featureCardHeight)
                .border(BorderStroke(1.dp, Color(0xFFDFFFE6)), shape = RoundedCornerShape(12.dp))
                .clickable { onNavigate(VisitorRoute.Events.route) },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8FFF0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Event, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(id = com.mahakumbh.crowdsafety.R.string.home_event_schedule), style = MaterialTheme.typography.headlineSmall, color = Color(0xFF103018))
                    Text(stringResource(id = com.mahakumbh.crowdsafety.R.string.home_event_schedule_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Bottom Emergency SOS card (sits above bottom nav) - style to match other feature cards
        var showSosDialog by remember { mutableStateOf(false) }
        Box(modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 88.dp), // ensure space above bottom navigation
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(featureCardHeight)
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F)),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(id = com.mahakumbh.crowdsafety.R.string.home_emergency_sos), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(id = com.mahakumbh.crowdsafety.R.string.home_tap_for_assistance), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                    }
                    Surface(modifier = Modifier.widthIn(min = 88.dp), shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.12f)) {
                        Text(stringResource(id = com.mahakumbh.crowdsafety.R.string.home_help), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White, textAlign = TextAlign.Center)
                    }
                }
            }

            // Make entire card tappable and keep same size/padding so it behaves like the other feature cards
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(featureCardHeight)
                .padding(horizontal = 16.dp)
                .clickable { showSosDialog = true }
            ) {}
        }

        if (showSosDialog) {
            Dialog(onDismissRequest = { showSosDialog = false }) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2630)),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(id = com.mahakumbh.crowdsafety.R.string.sos_title), color = Color(0xFFFF6F61), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(id = com.mahakumbh.crowdsafety.R.string.sos_select_type), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))

                        val userId = repo.currentUserId.value ?: "anonymous"
                        val role = repo.currentUserRole.value ?: com.mahakumbh.crowdsafety.data.UserRole.Visitor
                        val triveniLat = 25.419518
                        val triveniLng = 81.889800

                            Button(onClick = {
                            repo.logSos(com.mahakumbh.crowdsafety.data.SosType.Panic, userId, role, triveniLat, triveniLng)
                            Toast.makeText(context, context.getString(com.mahakumbh.crowdsafety.R.string.panic_sent), Toast.LENGTH_SHORT).show()
                            showSosDialog = false
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(stringResource(id = com.mahakumbh.crowdsafety.R.string.sos_panic), color = Color.White)
                        }

                        Button(onClick = {
                            repo.logSos(com.mahakumbh.crowdsafety.data.SosType.Medical, userId, role, triveniLat, triveniLng)
                            Toast.makeText(context, context.getString(com.mahakumbh.crowdsafety.R.string.medical_sent), Toast.LENGTH_SHORT).show()
                            showSosDialog = false
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(stringResource(id = com.mahakumbh.crowdsafety.R.string.sos_medical), color = Color.White)
                        }

                        Button(onClick = {
                            repo.logSos(com.mahakumbh.crowdsafety.data.SosType.Security, userId, role, triveniLat, triveniLng)
                            Toast.makeText(context, context.getString(com.mahakumbh.crowdsafety.R.string.security_sent), Toast.LENGTH_SHORT).show()
                            showSosDialog = false
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(stringResource(id = com.mahakumbh.crowdsafety.R.string.sos_security), color = Color.White)
                        }

                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showSosDialog = false }) { Text(stringResource(id = com.mahakumbh.crowdsafety.R.string.cancel), color = MaterialTheme.colorScheme.onSurface) }
                    }
                }
            }
        }

    // Events preview removed from home; use Event Schedule feature card above to open full schedule.
    }
}

@Composable
fun VisitorSosDialog(onDismiss: () -> Unit, onSelected: (Any) -> Unit = {}) { /* no-op */ }

@Composable fun VisitorLostPeopleScreen(onReportClick: (String) -> Unit = {}) {
    // read from shared lostRepo; show sample if empty
    val reports by Locator.lostRepo.reports.collectAsState(initial = emptyList())
    val visible = if (reports.isEmpty()) {
        listOf(
            com.mahakumbh.crowdsafety.data.LostPersonReport(
                id = "sample-1", reporterId = "system", name = "Raju Sharma", age = 72, gender = "Male",
                clothingDescription = "Blue shirt, white dhoti", lastKnownLocation = "Sangam Ghat", photoUrl = "", status = com.mahakumbh.crowdsafety.data.ReportStatus.Active, timestamp = System.currentTimeMillis()
            ),
            com.mahakumbh.crowdsafety.data.LostPersonReport(
                id = "sample-2", reporterId = "system", name = "Sita Devi", age = 65, gender = "Female",
                clothingDescription = "Red saree", lastKnownLocation = "Hanuman Garhi", photoUrl = "", status = com.mahakumbh.crowdsafety.data.ReportStatus.Active, timestamp = System.currentTimeMillis() - 3600000
            )
        )
    } else reports

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Active Reports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible) { rpt ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onReportClick(rpt.id) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(rpt.name, fontWeight = FontWeight.Bold)
                        Text("Age: ${rpt.age}  •  Gender: ${rpt.gender}", style = MaterialTheme.typography.bodySmall)
                        Text("Last seen: ${rpt.lastKnownLocation}", style = MaterialTheme.typography.bodySmall)
                        Text("Clothing: ${rpt.clothingDescription}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
// Feature screens are provided in separate files (DonationScreen, DensityHeatMapScreen, SpecialReservationScreen, EventScheduleScreen, etc.)
@Composable
fun VisitorAlertsScreen() {
    // Sample alerts data — in real app this should come from repo/flow
    data class AlertItem(val title: String, val type: String, val level: String)

    val sample = listOf(
        AlertItem("Yellow risk predicted in Zone B in 7 min", "Type: Yellow", "yellow"),
        AlertItem("UMS A3 offline - ticket created", "Type: Green", "green"),
        AlertItem("Reverse flow detected near Bridge C", "Type: Red", "red")
    )

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
        .padding(top = 12.dp)) {

        Text("Alerts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))

        androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(sample) { alert ->
                val bgColor = when (alert.level) {
                    "yellow" -> androidx.compose.ui.graphics.Color(0xFFF3E8C8)
                    "green" -> androidx.compose.ui.graphics.Color(0xFFE8F6EF)
                    "red" -> androidx.compose.ui.graphics.Color(0xFF4D0F0F)
                    else -> MaterialTheme.colorScheme.surface
                }
                val borderColor = when (alert.level) {
                    "yellow" -> androidx.compose.ui.graphics.Color(0xFFF0C419)
                    "green" -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
                    "red" -> androidx.compose.ui.graphics.Color(0xFFD32F2F)
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    colors = if (alert.level == "red") CardDefaults.cardColors(containerColor = bgColor) else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {

                        // Icon with colored background for non-red alerts; for red use dark card
                        Box(modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(borderColor), contentAlignment = Alignment.Center) {
                            val icon = when (alert.level) {
                                "yellow" -> Icons.Default.Info
                                "green" -> Icons.Default.CheckCircle
                                else -> Icons.Default.Warning
                            }
                            Icon(icon, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(alert.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = if (alert.level == "red") androidx.compose.ui.graphics.Color(0xFFFF6B6B) else MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(6.dp))
                            Text(alert.type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
@Composable
fun VisitorProfileScreen() {
    val repo = Locator.repo
    val ctx = LocalContext.current
    val role = repo.currentUserRole.value ?: com.mahakumbh.crowdsafety.data.UserRole.Visitor
    val userId = repo.currentUserId.value ?: "Unknown"

    var selectedZone by remember { mutableStateOf<String?>("A") }

    Column(modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(16.dp)) {

        // invisible spacer button to prevent header from clipping under system UI
        Button(
            onClick = { /* spacer */ },
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent)
        ) { }

        // Header card with avatar icon and title
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF7C4DFF)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                // Profile drawable
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.mahakumbh.crowdsafety.R.drawable.ic_profile),
                    contentDescription = "Profile",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(Color(0xFF9C7CFF))
                        .padding(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("Visitor Profile", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Emergency Management System", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFF3E0).copy(alpha = 0.9f))
            }
        }

        Spacer(Modifier.height(14.dp))

        // Account information card
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Account Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Role:", style = MaterialTheme.typography.bodyMedium)
                    Text(role.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ID:", style = MaterialTheme.typography.bodyMedium)
                    Text(userId, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Zone check-in card
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Zone Check-in", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text("Select Zone:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))

                val zones = listOf("A", "B", "C", "D")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (z in zones) {
                        val isSelected = selectedZone == z
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clickable { selectedZone = z },
                            shape = RoundedCornerShape(22.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = if (isSelected) 6.dp else 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = "Zone $z", style = MaterialTheme.typography.bodyMedium, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    Toast.makeText(ctx, "Checked in to Zone ${selectedZone ?: "A"}", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                    Icon(imageVector = Icons.Default.Login, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Check In", color = Color.White)
                }
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun VisitorMapScreen() {
    val context = LocalContext.current
    val repo = Locator.repo
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    // start centered at Triveni Bandh so the map shows the requested location immediately
    val triveniCenter = LatLng(25.419518, 81.889800)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(triveniCenter, 15f)
    }
    val coroutineScope = rememberCoroutineScope()
    val currentLatLng = remember { mutableStateOf<LatLng?>(null) }
    val activeVisitors by repo.activeVisitors.collectAsState(initial = emptyList())
    // Category toggles for POI layers - default to false so nothing is shown until the user selects a category
    // Akharas shown by default per request
    var showAkharas by remember { mutableStateOf(true) }
    var showTemples by remember { mutableStateOf(false) }
    var showGhats by remember { mutableStateOf(false) }
    var showAttractions by remember { mutableStateOf(false) }
    var showLostFound by remember { mutableStateOf(false) }
    var showParking by remember { mutableStateOf(false) }
    var showEmergency by remember { mutableStateOf(false) }
    var showConvenience by remember { mutableStateOf(false) }

    // Load POI GeoJSON from assets and parse into category lists
    val assets = LocalContext.current.assets
    val poiJson = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            assets.open("poi_sample.geojson").bufferedReader().use { r -> poiJson.value = r.readText() }
        } catch (_: Exception) { poiJson.value = null }
    }

    // Optional: load CSV with exact akhada / place coordinates if provided in assets/akhada_locations.csv
    val akhadaCsv = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            assets.open("akhada_locations.csv").bufferedReader().use { r -> akhadaCsv.value = r.readText() }
        } catch (_: Exception) { akhadaCsv.value = null }
    }

    val akharas = remember { mutableStateListOf<Poi>() }
    val temples = remember { mutableStateListOf<Poi>() }
    val ghats = remember { mutableStateListOf<Poi>() }
    val attractions = remember { mutableStateListOf<Poi>() }
    val lostFound = remember { mutableStateListOf<Poi>() }
    val parkings = remember { mutableStateListOf<Poi>() }
    val emergencies = remember { mutableStateListOf<Poi>() }
    val conveniences = remember { mutableStateListOf<Poi>() }

    LaunchedEffect(poiJson.value) {
        akharas.clear(); temples.clear(); ghats.clear(); attractions.clear(); lostFound.clear(); parkings.clear(); emergencies.clear(); conveniences.clear()
        val text = poiJson.value ?: return@LaunchedEffect
        try {
            val json = org.json.JSONObject(text)
            val features = json.optJSONArray("features") ?: return@LaunchedEffect
                for (i in 0 until features.length()) {
                val feat = features.getJSONObject(i)
                val prop = feat.optJSONObject("properties") ?: continue
                val cat = prop.optString("category")
                    val name = prop.optString("name", prop.optString("title", "Site"))
                    val geom = feat.optJSONObject("geometry") ?: continue
                if (geom.optString("type") != "Point") continue
                val coords = geom.optJSONArray("coordinates") ?: continue
                val lon = coords.optDouble(0)
                val lat = coords.optDouble(1)
                val ll = LatLng(lat, lon)
                when (cat.lowercase()) {
                        "akhara" -> akharas.add(Poi(ll, name))
                        "temple" -> temples.add(Poi(ll, name))
                        "ghat" -> ghats.add(Poi(ll, name))
                        "attraction" -> attractions.add(Poi(ll, name))
                        "lost_found" -> lostFound.add(Poi(ll, name))
                        "parking" -> parkings.add(Poi(ll, name))
                        "emergency" -> emergencies.add(Poi(ll, name))
                        "convenience" -> conveniences.add(Poi(ll, name))
                }
            }
            // Force-populate every category with nearby POIs around Triveni Bandh so the map always shows the requested location
            val triveni = LatLng(25.419518, 81.889800)
            // Generate offsets distributed around Triveni so POIs are spread around the boundary.
            // We'll convert radii in meters to degrees (approx): 1 deg lat ~ 111320 m.
            fun generateCircleOffsets(count: Int, minRadiusM: Double, maxRadiusM: Double, centerLat: Double): List<Pair<Double, Double>> {
                val rnd = java.util.Random(42) // deterministic placement for now
                val latRad = Math.toRadians(centerLat)
                val latMetersPerDeg = 111320.0
                val lngMetersPerDeg = 111320.0 * Math.cos(latRad)
                val out = mutableListOf<Pair<Double, Double>>()
                for (i in 0 until count) {
                    val t = i.toDouble() / count.toDouble()
                    // spread evenly by angle plus a small random jitter
                    val angle = t * Math.PI * 2.0 + (rnd.nextDouble() - 0.5) * 0.4
                    val radius = minRadiusM + rnd.nextDouble() * (maxRadiusM - minRadiusM)
                    val dLatDeg = Math.cos(angle) * (radius / latMetersPerDeg)
                    val dLngDeg = Math.sin(angle) * (radius / lngMetersPerDeg)
                    out.add(Pair(dLatDeg, dLngDeg))
                }
                return out
            }

            // distinct name + pin lists per category (5 each)
            val akharaNames = listOf(
                "Shri Shambhu Panchayati Atal Akhada",
                "Shri Shambhu Panchayati Atal Akhada",
                "Akhil Bharatiya Shri Panch Nirvani Ani Akhada",
                "Shri Panchayati Akhada Mahanirvani",
                "Shri Taponidhi Panchayati Shri Niranjani Akhada"
            )
            val akharaPins = listOf("ufv6ue","ufv6ue","a8e5m0","li5ekk","6abolg")
            val akharaAddresses = List(5) { "Triveni Bandh Prayag, Pura Padain, Prayagraj, Uttar Pradesh, 211020" }

            val templeNames = listOf("Lete Hue Hanuman Temple","Triveni Ghat Temple","Bajrang Mandir","Shiv Mandir","Kundeshwar Temple")
            val templePins = List(5) { i -> "tpin${i+1}" }

            val ghatNames = listOf("Triveni Ghat","Sangam North Ghat","Sangam East Steps","Bathing Place","Prayag Ghat")
            val ghatPins = List(5) { i -> "gpin${i+1}" }

            val attractionNames = listOf("Triveni Bandh Viewpoint","Cultural Stage","Festival Plaza","Heritage Walk","Boat Jetty")
            val attractionPins = List(5) { i -> "apin${i+1}" }

            val lostNames = List(5) { i -> "Lost & Found #${i+1}" }
            val lostPins = List(5) { i -> "lpin${i+1}" }

            val parkingNames = List(5) { i -> "Parking Area ${i+1}" }
            val parkingPins = List(5) { i -> "ppin${i+1}" }

            val emergencyNames = listOf("First Aid Station A","First Aid Station B","Medical Camp 1","Medical Camp 2","Ambulance Point")
            val emergencyPins = List(5) { i -> "empin${i+1}" }

            val convenienceNames = listOf("Public Toilet 1","Public Toilet 2","Info Desk 1","Water Stall 1","Help Kiosk 1")
            val conveniencePins = List(5) { i -> "cpin${i+1}" }

            fun populateWithNames(list: MutableList<Poi>, names: List<String>, pins: List<String>, addresses: List<String>? = null, minRadiusM: Double = 200.0, maxRadiusM: Double = 900.0) {
                list.clear()
                if (names.isEmpty()) return
                // generate offsets around the circle sized per-request
                val offs = generateCircleOffsets(names.size, minRadiusM, maxRadiusM, triveni.latitude)
                for (i in names.indices) {
                    val off = offs[i % offs.size]
                    val lat = triveni.latitude + off.first
                    val lng = triveni.longitude + off.second
                    val pin = pins.getOrNull(i)
                    val addr = addresses?.getOrNull(i)
                    list.add(Poi(LatLng(lat, lng), names[i], pin, addr))
                }
            }

            // Populate categories using circular distribution; tweak radii so Akharas stay on land side
            populateWithNames(akharas, akharaNames, akharaPins, akharaAddresses, minRadiusM = 250.0, maxRadiusM = 700.0)
            populateWithNames(temples, templeNames, templePins, minRadiusM = 180.0, maxRadiusM = 800.0)
            populateWithNames(ghats, ghatNames, ghatPins, minRadiusM = 120.0, maxRadiusM = 400.0)
            populateWithNames(attractions, attractionNames, attractionPins, minRadiusM = 300.0, maxRadiusM = 1000.0)
            populateWithNames(lostFound, lostNames, lostPins, minRadiusM = 100.0, maxRadiusM = 500.0)
            populateWithNames(parkings, parkingNames, parkingPins, minRadiusM = 600.0, maxRadiusM = 1200.0)
            populateWithNames(emergencies, emergencyNames, emergencyPins, minRadiusM = 200.0, maxRadiusM = 600.0)
            populateWithNames(conveniences, convenienceNames, conveniencePins, minRadiusM = 100.0, maxRadiusM = 400.0)
        } catch (_: Exception) { /* ignore parse errors for now */ }
    }

    // Parse akhada CSV and append POIs when present. CSV format: name,lat,lng,category
    LaunchedEffect(akhadaCsv.value) {
        val text = akhadaCsv.value ?: return@LaunchedEffect
        try {
            text.lineSequence().forEachIndexed { idx, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                // skip header if present
                if (idx == 0 && line.lowercase().contains("name") && line.lowercase().contains("lat")) return@forEachIndexed
                val parts = line.split(',').map { it.trim() }
                if (parts.size < 4) return@forEachIndexed
                val name = parts[0]
                val lat = parts[1].toDoubleOrNull()
                val lng = parts[2].toDoubleOrNull()
                val cat = parts[3].lowercase()
                val pin = parts.getOrNull(4)?.takeIf { it.isNotBlank() }
                if (lat == null || lng == null) return@forEachIndexed
                val poi = Poi(LatLng(lat, lng), name, pin)
                when (cat) {
                    "akhara" -> if (!akharas.any { it.name == name }) akharas.add(poi)
                    "temple" -> if (!temples.any { it.name == name }) temples.add(poi)
                    "ghat" -> if (!ghats.any { it.name == name }) ghats.add(poi)
                    "attraction" -> if (!attractions.any { it.name == name }) attractions.add(poi)
                    "lost_found" -> if (!lostFound.any { it.name == name }) lostFound.add(poi)
                    "parking" -> if (!parkings.any { it.name == name }) parkings.add(poi)
                    "emergency" -> if (!emergencies.any { it.name == name }) emergencies.add(poi)
                    "convenience" -> if (!conveniences.any { it.name == name }) conveniences.add(poi)
                    else -> { /* ignore unknown category */ }
                }
            }
        } catch (_: Exception) { }
    }
    
    // Mock locations for help teams and important places
    val helpTeamLocations = remember {
        listOf(
            LatLng(20.5937, 78.9629), // Medical Team
            LatLng(20.5938, 78.9630), // Security Team
            LatLng(20.5936, 78.9628), // Emergency Response
            LatLng(20.5939, 78.9631), // Information Desk
        )
    }

    // Ensure there are some POIs around Triveni Bandh if lists are empty
    val triveni = LatLng(25.419518, 81.889800)
    LaunchedEffect(Unit) {
        if (akharas.isEmpty()) {
            akharas.add(Poi(LatLng(triveni.latitude + 0.0001, triveni.longitude + 0.0001), "Shri Panchayati Akhada Mahanirvani"))
            akharas.add(Poi(LatLng(triveni.latitude + 0.0004, triveni.longitude - 0.0002), "Shri Shambhu Panchayati Atal Akhada"))
            akharas.add(Poi(LatLng(triveni.latitude - 0.0003, triveni.longitude + 0.0002), "Shri Taponidhi Panchayati Shri Niranjani Akhada"))
            akharas.add(Poi(LatLng(triveni.latitude - 0.0005, triveni.longitude - 0.0004), "Shri Panchayati Anand Akhada"))
            akharas.add(Poi(LatLng(triveni.latitude + 0.0005, triveni.longitude + 0.0003), "Shri Panchadashnam Juna Akhada"))
        }
        if (temples.isEmpty()) {
            temples.add(Poi(LatLng(triveni.latitude + 0.0006, triveni.longitude + 0.0006), "Lete Hue Hanuman Temple"))
        }
        if (ghats.isEmpty()) {
            ghats.add(Poi(LatLng(triveni.latitude + 0.0002, triveni.longitude - 0.0006), "Triveni Ghat"))
        }
        if (attractions.isEmpty()) {
            attractions.add(Poi(LatLng(triveni.latitude - 0.0007, triveni.longitude + 0.0001), "Triveni Bandh Viewpoint"))
        }
        if (lostFound.isEmpty()) {
            lostFound.add(Poi(LatLng(triveni.latitude + 0.0002, triveni.longitude + 0.0002), "Lost & Found Center"))
        }
        if (parkings.isEmpty()) {
            parkings.add(Poi(LatLng(triveni.latitude - 0.0002, triveni.longitude - 0.0002), "Shiv Baba Parking"))
        }
        if (emergencies.isEmpty()) {
            emergencies.add(Poi(LatLng(triveni.latitude + 0.0003, triveni.longitude - 0.0001), "First Aid Station"))
        }
        if (conveniences.isEmpty()) {
            conveniences.add(Poi(LatLng(triveni.latitude - 0.0004, triveni.longitude + 0.0004), "Public Toilets"))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) {
            fused.lastLocation.addOnSuccessListener { location ->
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
            fused.lastLocation.addOnSuccessListener { location ->
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

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
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
                    text = "Visitor Map",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Find help teams, safe zones, and important locations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

    Spacer(Modifier.height(12.dp))

        // Explore grid (circular tiles)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            shape = RoundedCornerShape(12.dp)
        ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Explore", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                val categories = listOf(
                    Triple("Akhara", Icons.Filled.TrendingUp, { showAkharas } ),
                    Triple("Temples", Icons.Filled.Event, { showTemples } ),
                    Triple("Ghats", Icons.Filled.Place, { showGhats } ),
                    Triple("Attractions", Icons.Filled.Place, { showAttractions } ),
                    Triple("Lost & Found", Icons.Filled.FindInPage, { showLostFound } ),
                    Triple("Parking", Icons.Filled.LocalParking, { showParking } ),
                    Triple("Emergency", Icons.Filled.LocalHospital, { showEmergency } ),
                    Triple("Convenience", Icons.Filled.Wc, { showConvenience } )
                )

                // per-category enabled color pairs (start, end)
                val enabledColorPairs = listOf(
                    Pair(Color(0xFFB388FF), Color(0xFF7C4DFF)), // Akhara - purple
                    Pair(Color(0xFFFFD54F), Color(0xFFFFA000)), // Temples - gold/orange
                    Pair(Color(0xFF81D4FA), Color(0xFF4FC3F7)), // Ghats - light blue
                    Pair(Color(0xFFF06292), Color(0xFF8E24AA)), // Attractions - pink/purple
                    Pair(Color(0xFFF48FB1), Color(0xFFEC407A)), // Lost & Found - pink
                    Pair(Color(0xFFFF8A65), Color(0xFFD32F2F)), // Parking - orange/red
                    Pair(Color(0xFF00796B), Color(0xFF26A69A)), // Emergency - teal/green
                    Pair(Color(0xFF7CB342), Color(0xFFC5E1A5))  // Convenience - green
                )

                val tileSize = 64.dp
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (rowStart in categories.indices step 4) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (col in 0..3) {
                                val idx = rowStart + col
                                if (idx < categories.size) {
                                    val (label, icon, isEnabledGetter) = categories[idx]
                                    val enabled = isEnabledGetter()
                                    val colorPair = enabledColorPairs.getOrNull(idx) ?: Pair(Color(0xFFB388FF), Color(0xFF7C4DFF))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .size(tileSize)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(
                                                    if (enabled) Brush.verticalGradient(
                                                        listOf(colorPair.first, colorPair.second)
                                                    ) else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface))
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = label,
                                                tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Box(modifier = Modifier.matchParentSize().clickable {
                                                // toggle the category and animate camera to the first POI
                                                when (label) {
                                                    "Akhara" -> showAkharas = !showAkharas
                                                    "Temples" -> showTemples = !showTemples
                                                    "Ghats" -> showGhats = !showGhats
                                                    "Attractions" -> showAttractions = !showAttractions
                                                    "Lost & Found" -> showLostFound = !showLostFound
                                                    "Parking" -> showParking = !showParking
                                                    "Emergency" -> showEmergency = !showEmergency
                                                    "Convenience" -> showConvenience = !showConvenience
                                                }
                                                val list = when (label) {
                                                    "Akhara" -> akharas
                                                    "Temples" -> temples
                                                    "Ghats" -> ghats
                                                    "Attractions" -> attractions
                                                    "Lost & Found" -> lostFound
                                                    "Parking" -> parkings
                                                    "Emergency" -> emergencies
                                                    "Convenience" -> conveniences
                                                    else -> null
                                                }
                                                val targetPoi = list?.firstOrNull()
                                                if (targetPoi != null) {
                                                    coroutineScope.launch {
                                                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(targetPoi.ll, 16f))
                                                    }
                                                } else {
                                                    // fallback to Triveni Bandh center
                                                    val triveni = LatLng(25.419518, 81.889800)
                                                    coroutineScope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(triveni, 15f)) }
                                                }
                                            }) {}
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Surface(shape = RoundedCornerShape(8.dp), color = Color.Transparent) {
                                            Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 60.dp))
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text("Important Dates", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("- Shahi Snan: Apr 12, 08:00\n- Aarti: Daily 06:00 & 18:00", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Google Map
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(560.dp)
                .padding(horizontal = AppPadding)
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
                // Draw a visible boundary around Triveni Bandh so the map shows the base template by default
                Circle(
                    center = triveni,
                    radius = 1600.0, // larger radius (~1.6 km) for clearer boundary
                    strokeColor = androidx.compose.ui.graphics.Color(0xFFBDB2F5),
                    strokeWidth = 3f,
                    fillColor = androidx.compose.ui.graphics.Color(0x33BDB2F5) // light, more transparent fill
                )

                // Current location marker
                currentLatLng.value?.let { ll ->
                    Marker(
                        state = MarkerState(ll),
                        title = "Your Location",
                        snippet = "Visitor Position"
                    )
                }
                
                // Help team markers
                for (index in helpTeamLocations.indices) {
                    val location = helpTeamLocations[index]
                    val teamName = when (index) {
                        0 -> "Medical Team"
                        1 -> "Security Team"
                        2 -> "Emergency Response"
                        else -> "Information Desk"
                    }
                    Marker(
                        state = MarkerState(location),
                        title = teamName,
                        snippet = "Tap for directions"
                    )
                }
                
                // Active visitors markers
                for (visitor in activeVisitors) {
                    val visitorLocation = LatLng(visitor.lat, visitor.lng)
                    val markerTitle = if (visitor.role == UserRole.Visitor) "Visitor ${visitor.id}" else "Volunteer ${visitor.id}"
                    val markerSnippet = "Zone: ${visitor.zone} • Online: ${if (visitor.isOnline) "Yes" else "No"}"
                    Marker(
                        state = MarkerState(visitorLocation),
                        title = markerTitle,
                        snippet = markerSnippet
                    )
                }
                // --- POI markers (category-specific symbol markers) ---
                var selectedPOI by remember { mutableStateOf<Pair<LatLng, String>?>(null) }

                if (showAkharas) {
                    val clusters = clusterPoints(akharas.map { it.ll })
                    for ((ll, cnt) in clusters) {
                        val nearest = findNearestPoiName(akharas, ll) ?: "Akhara"
                        Marker(
                            state = MarkerState(ll),
                            title = nearest,
                            snippet = if (cnt > 1) "${cnt} sites" else nearest,
                            icon = createCircleBitmapDescriptor(LocalContext.current, "#FF7043", "⚔️"),
                            onClick = {
                                selectedPOI = Pair(ll, nearest)
                                false
                            }
                        )
                    }
                }
                if (showTemples) {
                    val clusters = clusterPoints(temples.map { it.ll })
                    for ((ll, cnt) in clusters) {
                        val nearest = findNearestPoiName(temples, ll) ?: "Temple"
                        Marker(
                            state = MarkerState(ll),
                            title = nearest,
                            snippet = if (cnt > 1) "${cnt} temples" else nearest,
                            icon = createCircleBitmapDescriptor(LocalContext.current, "#FFA000", "ॐ"),
                            onClick = {
                                selectedPOI = Pair(ll, nearest)
                                false
                            }
                        )
                    }
                }
                if (showGhats) {
                    val clusters = clusterPoints(ghats.map { it.ll })
                    for ((ll, cnt) in clusters) {
                        val nearest = findNearestPoiName(ghats, ll) ?: "Ghat"
                        Marker(
                            state = MarkerState(ll),
                            title = nearest,
                            snippet = if (cnt > 1) "${cnt} ghats" else nearest,
                            icon = createCircleBitmapDescriptor(LocalContext.current, "#4FC3F7", "📍"),
                            onClick = {
                                selectedPOI = Pair(ll, nearest)
                                false
                            }
                        )
                    }
                }
                if (showAttractions) {
                    val clusters = clusterPoints(attractions.map { it.ll })
                    for ((ll, cnt) in clusters) {
                        val nearest = findNearestPoiName(attractions, ll) ?: "Attraction"
                        Marker(
                            state = MarkerState(ll),
                            title = nearest,
                            snippet = if (cnt > 1) "${cnt} places" else nearest,
                            icon = createCircleBitmapDescriptor(LocalContext.current, "#8E24AA", "📸"),
                            onClick = {
                                selectedPOI = Pair(ll, nearest)
                                false
                            }
                        )
                    }
                }
                if (showLostFound) {
                    val clusters = clusterPoints(lostFound.map { it.ll })
                    for ((ll, cnt) in clusters) {
                        val nearest = findNearestPoiName(lostFound, ll) ?: "Lost & Found"
                        Marker(
                            state = MarkerState(ll),
                            title = nearest,
                            snippet = nearest,
                            icon = createCircleBitmapDescriptor(LocalContext.current, "#F06292", "🔎"),
                            onClick = {
                                selectedPOI = Pair(ll, nearest)
                                false
                            }
                        )
                    }
                }
                if (showParking) {
                    val clusters = clusterPoints(parkings.map { it.ll })
                    for ((ll, cnt) in clusters) {
                        val nearest = findNearestPoiName(parkings, ll) ?: "Parking"
                        Marker(
                            state = MarkerState(ll),
                            title = nearest,
                            snippet = if (cnt > 1) "${cnt} spots" else nearest,
                            icon = createCircleBitmapDescriptor(LocalContext.current, "#D32F2F", "P"),
                            onClick = {
                                selectedPOI = Pair(ll, nearest)
                                false
                            }
                        )
                    }
                }
                if (showEmergency) {
                    val clusters = clusterPoints(emergencies.map { it.ll })
                    for ((ll, cnt) in clusters) {
                        val nearest = findNearestPoiName(emergencies, ll) ?: "Emergency"
                        Marker(
                            state = MarkerState(ll),
                            title = nearest,
                            snippet = "First Aid / Police",
                            icon = createCircleBitmapDescriptor(LocalContext.current, "#E53935", "✚"),
                            onClick = {
                                selectedPOI = Pair(ll, nearest)
                                false
                            }
                        )
                    }
                }
                if (showConvenience) {
                    val clusters = clusterPoints(conveniences.map { it.ll })
                    for ((ll, cnt) in clusters) {
                        val nearest = findNearestPoiName(conveniences, ll) ?: "Convenience"
                        Marker(
                            state = MarkerState(ll),
                            title = nearest,
                            snippet = "Toilet / Water",
                            icon = createCircleBitmapDescriptor(LocalContext.current, "#7CB342", "🚻"),
                            onClick = {
                                selectedPOI = Pair(ll, nearest)
                                false
                            }
                        )
                    }
                }

                // small popup overlay when a marker is selected
                selectedPOI?.let { (latlng, label) ->
                    // convert latlng to a screen-aligned overlay using a simple top-center card
                    // For now we show a static card at top-center when selected
                    // The card also contains a 'Navigate' and 'Close' action
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Card(
                            modifier = Modifier
                                .padding(top = 80.dp)
                                .widthIn(max = 340.dp)
                                .wrapContentHeight(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(text = "Lat: ${String.format("%.4f", latlng.latitude)}, Lng: ${String.format("%.4f", latlng.longitude)}", style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = {
                                        // animate camera to this POI
                                        coroutineScope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latlng, 17f)) }
                                    }) {
                                        Text("Zoom")
                                    }
                                    TextButton(onClick = { selectedPOI = null }) { Text("Close") }
                                }
                            }
                        }
                    }
                }
            }

            // (legend overlay removed from map so it doesn't hide map content)

            // Map left-side panel toggle
            var showLeftPanel by remember { mutableStateOf(false) }
            IconButton(onClick = { showLeftPanel = !showLeftPanel }, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(imageVector = Icons.Default.Menu, contentDescription = "Open places list", tint = MaterialTheme.colorScheme.onSurface)
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showLeftPanel,
                enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)),
                exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300))
            ) {
                Card(modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .padding(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Places", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.lazy.LazyColumn {
                            items(akharas) { poi ->
                                Row(modifier = Modifier.fillMaxWidth().clickable {
                                    coroutineScope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(poi.ll, 17f)) }
                                    showLeftPanel = false
                                }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(poi.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    IconButton(onClick = { /* copy pin */ }) { Icon(imageVector = Icons.Default.Share, contentDescription = null) }
                                }
                                Divider()
                            }
                        }
                    }
                }
            }

            // Center FAB
            FloatingActionButton(
                onClick = {
                    currentLatLng.value?.let { ll ->
                        coroutineScope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(ll, 16f)) }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Center map", tint = Color.White)
            }

            // explore panel previously overlayed on the map: move it below the map for better layout
    }

    // Legend removed per request
        // Places list (Akhadas)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Places", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                val ctx = LocalContext.current
                for (idx in akharas.indices) {
                    val poi = akharas[idx]
                    val distStr = remember(poi) {
                        currentLatLng.value?.let { loc ->
                            val results = FloatArray(1)
                            android.location.Location.distanceBetween(loc.latitude, loc.longitude, poi.ll.latitude, poi.ll.longitude, results)
                            "${String.format("%.2f", results[0] / 1000.0)} km"
                        } ?: ""
                    }
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(poi.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                                if (!poi.address.isNullOrBlank()) {
                                    Text(poi.address ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                }
                                if (distStr.isNotBlank()) Text(distStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { coroutineScope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(poi.ll, 17f)) } }) { Text("Center") }
                                if (poi.mapplsPin != null) {
                                    TextButton(onClick = {
                                        val cb = ctx.getSystemService(android.content.ClipboardManager::class.java)
                                        val clip = android.content.ClipData.newPlainText("Mappls Pin", poi.mapplsPin)
                                        cb.setPrimaryClip(clip)
                                        Toast.makeText(ctx, "Mappls pin copied", Toast.LENGTH_SHORT).show()
                                    }) { Text("Copy Pin") }
                                }
                            }
                        }
                        if (idx < akharas.size - 1) Divider()
                    }
                }
            }
        }
    // (Explore panel removed here — Explore is shown above the map)
        // Quick Actions
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
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            // Center to nearest help team on the map
                            val target = helpTeamLocations.firstOrNull() ?: triveni
                            coroutineScope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, 16f)) }
                            Toast.makeText(context, "Centered to nearest help team", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = "Find Help",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Find Help")
                        }
                    }
                    
                    Button(
                        onClick = {
                            // Show safe-zones area by centering to Triveni Bandh
                            coroutineScope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(triveni, 15f)) }
                            Toast.makeText(context, "Showing safe zones (approx)", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Safe Zones",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Safe Zones")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VisitorLostItemsScreen(navController: androidx.navigation.NavHostController) {
    val repo = Locator.repo
    val featureRepo = repo as? com.mahakumbh.crowdsafety.data.FeatureRepository
    // collect lost items from FeatureRepository if available, else from Locator.lostRepo (keeps sample behavior)
    // Derive posted items from repo tasks (collectAsState must be called from a @Composable)
    var posted by remember { mutableStateOf(listOf<com.mahakumbh.crowdsafety.data.LostItemReport>()) }
    val tasks by Locator.repo.tasks.collectAsState(initial = emptyList())
    LaunchedEffect(tasks) {
        try {
            posted = tasks.filter { t -> t.title.startsWith("🔎 Lost Item") }.map { t ->
                com.mahakumbh.crowdsafety.data.LostItemReport(
                    id = t.id,
                    reporterId = Locator.repo.currentUserId.value ?: "anonymous",
                    title = t.title.removePrefix("🔎 Lost Item: "),
                    description = t.message ?: "",
                    location = t.visitorLocation ?: "",
                    timestamp = System.currentTimeMillis()
                )
            }
        } catch (_: Exception) { /* ignore mapping errors */ }
    }

    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var editItem by remember { mutableStateOf<com.mahakumbh.crowdsafety.data.LostItemReport?>(null) }
    val ctx = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        TopAppBar(
            title = { Text("Finding Lost Things", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
        )

        Spacer(Modifier.height(12.dp))

        // Post form card (styled like the dialog screenshot)
        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2630))) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Report Lost Item", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Item name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Last known location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { title = ""; desc = ""; location = "" }) { Text("Cancel", color = Color(0xFFBDB2F5)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val id = "li-${System.currentTimeMillis()}"
                        val item = com.mahakumbh.crowdsafety.data.LostItemReport(id = id, reporterId = repo.currentUserId.value ?: "anonymous", title = title.ifBlank { "Lost item" }, description = desc, location = location.ifBlank { "Unknown" }, timestamp = System.currentTimeMillis())
                        try {
                            (repo as? com.mahakumbh.crowdsafety.data.FeatureRepository)?.reportLostItem(item)
                            // update local posted list for immediate UX
                            posted = listOf(item) + posted
                            title = ""; desc = ""; location = ""
                            Toast.makeText(ctx, "Reported lost item", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Unable to report lost item", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Submit") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text("Reported items", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (posted.isEmpty()) {
            Text("No items posted yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(posted) { it ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(it.title, fontWeight = FontWeight.Bold)
                                    Text(it.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { editItem = it }) { Text("Edit") }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(it.description)
                        }
                    }
                }
            }
        }

        // Edit dialog
        if (editItem != null) {
            val cur = editItem!!
            var etitle by remember { mutableStateOf(cur.title) }
            var edesc by remember { mutableStateOf(cur.description) }
            var eloc by remember { mutableStateOf(cur.location) }
            Dialog(onDismissRequest = { editItem = null }) {
                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Edit Lost Item", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = etitle, onValueChange = { etitle = it }, label = { Text("Item name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = edesc, onValueChange = { edesc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = eloc, onValueChange = { eloc = it }, label = { Text("Last known location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { editItem = null }) { Text("Cancel") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                // Persist changes: update local posted and, if possible, repo via reportLostItem
                                val updated = cur.copy(title = etitle, description = edesc, location = eloc)
                                posted = posted.map { if (it.id == cur.id) updated else it }
                                try {
                                    (repo as? com.mahakumbh.crowdsafety.data.FeatureRepository)?.reportLostItem(updated)
                                } catch (_: Exception) { }
                                editItem = null
                            }) { Text("Save") }
                        }
                    }
                }
            }
        }
    }
}
