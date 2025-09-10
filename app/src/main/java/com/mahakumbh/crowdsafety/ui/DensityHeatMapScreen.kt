package com.mahakumbh.crowdsafety.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mahakumbh.crowdsafety.di.Locator
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import kotlinx.coroutines.launch

@Composable
fun DensityHeatMapScreen() {
    val repo = Locator.repo
    val activeVisitors by repo.activeVisitors.collectAsState(initial = emptyList())
    var overlayEnabled by remember { mutableStateOf(true) }
    val triveni = LatLng(25.419518, 81.889800)
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(triveni, 15f) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Legend + toggle
        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFDFFFEF), shape = RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).background(Color(0xFF4CAF50), shape = CircleShape))
                Text("Low", color = Color(0xFF388E3C))
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.size(12.dp).background(Color(0xFFFFC107), shape = CircleShape))
                Text("Medium", color = Color(0xFFF57C00))
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.size(12.dp).background(Color(0xFFEF5350), shape = CircleShape))
                Text("High", color = Color(0xFFD32F2F))
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Overlay", color = Color(0xFF555555))
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = overlayEnabled, onCheckedChange = { overlayEnabled = it })
            }
        }

        // alert
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Crowd alert: Bridge C", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("88% estimated capacity — Bridge C congested — use Bridge D or Gate 4", color = Color.White)
            }
        }

        // Map with simple heat circles
        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp)) {
            // disable built-in zoom controls and provide our own so we can position them above the bottom nav
            GoogleMap(
                modifier = Modifier.fillMaxSize().padding(top = 8.dp, bottom = 96.dp),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = true)
            ) {
                // compute density buckets by rounding lat/lng to cluster nearby
                val buckets = mutableMapOf<Pair<Int,Int>, Int>()
                val coords = mutableMapOf<Pair<Int,Int>, LatLng>()
                for (v in activeVisitors) {
                    val key = Pair((v.lat * 10000).toInt(), (v.lng * 10000).toInt())
                    buckets[key] = (buckets[key] ?: 0) + 1
                    coords[key] = LatLng(v.lat, v.lng)
                }

                // If there are no active visitors, show demo hotspots around Triveni so the UI is visible
                if (buckets.isEmpty()) {
                    val demoOffsets = listOf(
                        Pair(0.0003, 0.0002),
                        Pair(-0.00025, 0.00015),
                        Pair(0.00018, -0.0003)
                    )
                    for ((i, off) in demoOffsets.withIndex()) {
                        val center = LatLng(triveni.latitude + off.first, triveni.longitude + off.second)
                        val alpha = 0.35f - i * 0.08f
                        val radius = 300.0 * (1.0 + i * 0.5)
                        com.google.maps.android.compose.Circle(center = center, radius = radius, fillColor = Color(0xFFFF7043).copy(alpha = alpha), strokeColor = Color.Transparent)
                    }
                } else {
                    for ((k, count) in buckets) {
                        val center = coords[k] ?: continue
                        // stronger alpha and larger radius so circles are visible at common zooms
                        val alpha = (0.18f * count).coerceAtMost(0.6f)
                        val radius = 250.0 * kotlin.math.sqrt(count.toDouble())
                        com.google.maps.android.compose.Circle(center = center, radius = radius, fillColor = Color(0xFFFF7043).copy(alpha = alpha), strokeColor = Color.Transparent)
                    }
                }

                // show markers for active visitors (draw after circles so markers stay visible)
                for (v in activeVisitors) {
                    val ll = LatLng(v.lat, v.lng)
                    Marker(state = MarkerState(position = ll), title = "Visitor ${v.id}")
                }
            }

            // legend overlay inside map for better alignment (slightly inset)
            Card(modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 12.dp), shape = RoundedCornerShape(12.dp)) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF4CAF50), shape = CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("Low")
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFFFC107), shape = CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("Medium")
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFEF5350), shape = CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("High")
                }
            }

            // custom zoom buttons column (above FAB and bottom nav)
            Column(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallFloatingActionButton(onClick = {
                    coroutineScope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomIn()) }
                }) { Text("+") }
                SmallFloatingActionButton(onClick = {
                    coroutineScope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomOut()) }
                }) { Text("-") }
            }

            FloatingActionButton(onClick = { coroutineScope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(triveni, 15f)) } }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp)) {
                Icon(imageVector = Icons.Default.Directions, contentDescription = "Find Safe Route")
            }
        }
    }
}
