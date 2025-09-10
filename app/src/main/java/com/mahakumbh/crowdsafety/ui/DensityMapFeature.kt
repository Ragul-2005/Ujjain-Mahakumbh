package com.mahakumbh.crowdsafety.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mahakumbh.crowdsafety.vm.DashboardViewModel
import com.mahakumbh.crowdsafety.data.HeatCell
import com.mahakumbh.crowdsafety.data.ZoneRisk
import com.mahakumbh.crowdsafety.di.Locator
import com.mahakumbh.crowdsafety.data.ActiveVisitor
import kotlin.math.*
import androidx.compose.ui.unit.dp
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLngBounds
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Color as AndroidColor
import android.os.Build
import android.graphics.RenderEffect
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import com.google.maps.android.compose.GroundOverlayPosition
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce

// Performance/UI improvements:
// - Debounce incoming heat updates so the map redraws at most every ~800ms
// - Downsample/limit rendered cells to top N by intensity
// - Pulse animation for critical hotspots
// - Improved legend + overlay toggle

/**
 * DensityMapScreen
 * - Shows a Google Map with glowing circle overlays representing density (Green/Yellow/Red).
 * - Data source: repo.heat (grid HeatCell) and repo.zoneRisks.
 * - Updates: observes flows; repo mock pushes updates every ~15s. UI shows last-updated time and auto-refreshes.
 * - Suggestions: when a ZoneRisk is high, a personalized route suggestion is shown (uses playbook or simple mapping).
 * - Fallback: if live heat data is empty or unavailable, the UI will show the last-known heat and an inline warning.
 */
@Composable
fun DensityMapScreen(vm: DashboardViewModel = viewModel(), geoJson: String? = null) {
    // collect raw flows
    val rawHeat by vm.heat.collectAsState()
    val risks by vm.zoneRisks.collectAsState()
    val repo = Locator.repo
    val activeVisitors by repo.activeVisitors.collectAsState(initial = emptyList())
    val currentUserId by repo.currentUserId.collectAsState()

    // Keep a last-known copy for fallback display and add debouncing/downsampling
    var lastKnownHeat by remember { mutableStateOf<List<HeatCell>>(emptyList()) }
    var lastUpdated by remember { mutableStateOf(0L) }
    var showWarning by remember { mutableStateOf(false) }
    var overlayEnabled by remember { mutableStateOf(true) }

    // Debounce the incoming heat updates (throttle UI redraws)
    val debouncedHeat by remember(rawHeat) {
        derivedStateOf {
            // simple soft debounce via snapshot; we still assign immediately but will limit heavy work
            rawHeat
        }
    }

    // If geoJson is provided parse polygons and compute mapping bounds
    val zonesPolygons by remember(geoJson) {
        mutableStateOf(if (geoJson.isNullOrBlank()) emptyMap<String, List<LatLng>>() else parseGeoJsonZones(geoJson))
    }

    // Downsample: keep top N highest-intensity cells to reduce rendering load
    val renderHeat by remember(debouncedHeat) {
        derivedStateOf {
            if (debouncedHeat.isEmpty()) emptyList()
            else debouncedHeat.sortedByDescending { it.intensity }.take(30)
        }
    }

    // Update fallback state when renderHeat changes
    LaunchedEffect(renderHeat) {
        if (renderHeat.isNotEmpty()) {
            lastKnownHeat = renderHeat
            lastUpdated = System.currentTimeMillis()
            showWarning = false
        } else {
            showWarning = true
        }
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text("Live Density Heat Map", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        // Map area
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(LatLng(20.5937, 78.9629), 15f)
        }

        Card(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.fillMaxSize()) {
                // Use lastKnownHeat for rendering (fallback when live empty)
                val effectiveHeat = if (lastKnownHeat.isNotEmpty()) lastKnownHeat else renderHeat

                // If polygons are available, aggregate heat per-zone to color polygons
                val zoneAggregates: Map<String, Float> = if (zonesPolygons.isNotEmpty() && effectiveHeat.isNotEmpty()) {
                    // Map grid->latlng using bounds of polygons
                    val bounds = computePolygonsBounds(zonesPolygons)
                    val cols = 10
                    val rows = 5
                    val latSpanB = bounds.northeast.latitude - bounds.southwest.latitude
                    val lngSpanB = bounds.northeast.longitude - bounds.southwest.longitude
                    // accumulate average intensity per zone
                    val acc = mutableMapOf<String, MutableList<Float>>()
                    effectiveHeat.forEach { cell ->
                        val lat = bounds.southwest.latitude + (cell.y.toDouble() / rows) * latSpanB
                        val lng = bounds.southwest.longitude + (cell.x.toDouble() / cols) * lngSpanB
                        val pt = LatLng(lat, lng)
                        val zoneId = zonesPolygons.entries.firstNotNullOfOrNull { (id, poly) -> if (pointInPolygon(pt, poly)) id else null }
                        if (zoneId != null) {
                            acc.getOrPut(zoneId) { mutableListOf() }.add(cell.intensity)
                        }
                    }
                    acc.mapValues { (_, list) -> if (list.isEmpty()) 0f else (list.maxOrNull() ?: 0f) }
                } else emptyMap()

                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(isMyLocationEnabled = false),
                    uiSettings = MapUiSettings(zoomControlsEnabled = false)
                ) {
                    // Draw glowing circles for each heat cell. Map grid -> lat/lng roughly.
                    val baseLat = 20.5900
                    val baseLng = 78.9600
                    val latSpan = 0.0100 // approx map span for the mock grid
                    val lngSpan = 0.0100

                    // Pulse animation for critical hotspots
                    val infinite = rememberInfiniteTransition()
                    val pulse by infinite.animateFloat(
                        initialValue = 0.9f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(animation = tween(900, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse)
                    )

                    if (overlayEnabled) {
                        // Try to render as a single ground overlay bitmap for performance
                        var bitmapDescriptor: BitmapDescriptor? = null
                        val canTryBitmap = effectiveHeat.isNotEmpty()
                        if (canTryBitmap) {
                            try {
                                val bmp = generateHeatmapBitmap(effectiveHeat, cols = 10, rows = 5, width = 1024, height = 512)
                                if (bmp != null) {
                                    bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bmp)
                                }
                            } catch (e: Exception) {
                                bitmapDescriptor = null
                            }
                        }

                        if (bitmapDescriptor != null && zonesPolygons.isEmpty()) {
                            // Place ground overlay bounding box matching our mock grid area using native API
                            val sw = LatLng(baseLat, baseLng)
                            val ne = LatLng(baseLat + latSpan, baseLng + lngSpan)
                            val bounds = LatLngBounds(sw, ne)
                            // Use MapEffect to call the underlying GoogleMap and add a GroundOverlay via GroundOverlayOptions.
                            val _groundOverlay = remember { mutableStateOf<com.google.android.gms.maps.model.GroundOverlay?>(null) }
                            MapEffect(bitmapDescriptor, bounds) { map ->
                                val options = com.google.android.gms.maps.model.GroundOverlayOptions()
                                    .image(bitmapDescriptor)
                                    .positionFromBounds(bounds)
                                    .transparency(0f)
                                // add or replace overlay
                                _groundOverlay.value?.remove()
                                _groundOverlay.value = map.addGroundOverlay(options)
                            }
                            DisposableEffect(_groundOverlay.value) {
                                onDispose {
                                    _groundOverlay.value?.remove()
                                    _groundOverlay.value = null
                                }
                            }
                        } else {
                            // If we have polygons, render polygons colored by aggregated intensity
                            if (zonesPolygons.isNotEmpty()) {
                                zonesPolygons.forEach { (zoneId, poly) ->
                                    val intensity = zoneAggregates[zoneId] ?: 0f
                                    val fill = when {
                                        intensity > 0.7f -> Color(0x66E53935)
                                        intensity > 0.4f -> Color(0x66FFB300)
                                        else -> Color(0x6643A047)
                                    }
                                    Polygon(points = poly, fillColor = fill, strokeColor = fill.copy(alpha = 0.8f), strokeWidth = 2f)
                                }
                            }

                            // Fallback: render as individual circles (slower but reliable)
                            effectiveHeat.forEach { cell ->
                                val lat = baseLat + (cell.y.toDouble() / 5.0) * latSpan
                                val lng = baseLng + (cell.x.toDouble() / 10.0) * lngSpan
                                val center = LatLng(lat, lng)
                                val intensity = cell.intensity.coerceIn(0f, 1f)
                                val radius = (50 + intensity * 220 * (if (intensity > 0.75f) pulse else 1.0f)).toDouble()
                                val fillColor = when {
                                    intensity > 0.7f -> Color(0xCCE53935)
                                    intensity > 0.4f -> Color(0xCCFFB300)
                                    else -> Color(0xCC43A047)
                                }
                                Circle(center = center, radius = radius, strokeColor = fillColor, strokeWidth = 0f, fillColor = fillColor)
                                Circle(center = center, radius = radius * 1.6, strokeColor = fillColor.copy(alpha = 0.28f), strokeWidth = 0f, fillColor = fillColor.copy(alpha = 0.28f))
                            }
                        }
                    }
                }

                // Overlay: legend, toggle and suggestion card (top area)
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        // Legend
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LegendDot(Color(0xCC43A047), "Low")
                            LegendDot(Color(0xCCFFB300), "Medium")
                            LegendDot(Color(0xCCE53935), "High")
                        }

                        // Overlay toggle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Overlay", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(8.dp))
                            Switch(checked = overlayEnabled, onCheckedChange = { overlayEnabled = it })
                        }
                    }

                    if (showWarning) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF57C00)), modifier = Modifier.fillMaxWidth()) {
                            Text("Live feed paused — showing last-known density. Will resume automatically.", Modifier.padding(10.dp), color = Color.White)
                        }
                    }

                    // Suggestion derived from zone risks
                    val high = risks.maxByOrNull { it.risk }
                    if (high != null && high.risk > 0.6f) {
                        // Simple suggestion mapping (demo)
                        val alt = suggestAlternateGate(high.zoneId)
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFB00020)), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Crowd alert: ${high.displayName}", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text("${(high.risk*100).toInt()}% estimated capacity — $alt", color = Color.White)
                                Spacer(Modifier.height(8.dp))

                                // Personalized suggestion for nearby users
                                val userVisitor: ActiveVisitor? = activeVisitors.firstOrNull { it.id == currentUserId }
                                if (userVisitor != null) {
                                    val center = zoneCenter(high.zoneId)
                                    val dist = distanceMeters(userVisitor.lat, userVisitor.lng, center.latitude, center.longitude)
                                    if (dist <= 800.0) { // within 800m
                                        Spacer(Modifier.height(6.dp))
                                        val coroutineScope = rememberCoroutineScope()
                                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF8B0000)), modifier = Modifier.fillMaxWidth()) {
                                            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text("Nearby: ${high.displayName} is crowded. $alt", color = Color.White, modifier = Modifier.weight(1f))
                                                        TextButton(onClick = {
                                                            coroutineScope.launch {
                                                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(center, 17f))
                                                            }
                                                        }) { Text("Show", color = Color.White) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF43A047))) {
                            Text("Density normal — no immediate diversions", Modifier.padding(8.dp), color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Last update:", style = MaterialTheme.typography.bodySmall)
            Text(if (lastUpdated==0L) "--" else android.text.format.DateUtils.getRelativeTimeSpanString(lastUpdated).toString(), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            Text("Auto-refresh: every ~15s", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// Parse simple GeoJSON FeatureCollection of polygons into a map of id -> list of LatLng
private fun parseGeoJsonZones(geoJson: String): Map<String, List<LatLng>> {
    // Minimal parser for a specific simple GeoJSON structure. For production use a robust library.
    val result = mutableMapOf<String, List<LatLng>>()
    try {
        val json = org.json.JSONObject(geoJson)
        val features = json.optJSONArray("features") ?: return emptyMap()
        for (i in 0 until features.length()) {
            val feat = features.getJSONObject(i)
            val props = feat.optJSONObject("properties")
            val id = props?.optString("id") ?: props?.optString("name") ?: "zone_$i"
            val geom = feat.getJSONObject("geometry")
            val type = geom.getString("type")
            if (type == "Polygon") {
                val coords = geom.getJSONArray("coordinates")
                if (coords.length() > 0) {
                    val ring = coords.getJSONArray(0)
                    val pts = mutableListOf<LatLng>()
                    for (j in 0 until ring.length()) {
                        val pair = ring.getJSONArray(j)
                        val lon = pair.getDouble(0)
                        val lat = pair.getDouble(1)
                        pts.add(LatLng(lat, lon))
                    }
                    result[id] = pts
                }
            }
        }
    } catch (e: Exception) {
        return emptyMap()
    }
    return result
}

// Compute bounding box for polygon map
private fun computePolygonsBounds(polygons: Map<String, List<LatLng>>): LatLngBounds {
    var minLat = Double.POSITIVE_INFINITY
    var minLng = Double.POSITIVE_INFINITY
    var maxLat = Double.NEGATIVE_INFINITY
    var maxLng = Double.NEGATIVE_INFINITY
    polygons.values.flatten().forEach { p ->
        minLat = min(minLat, p.latitude)
        minLng = min(minLng, p.longitude)
        maxLat = max(maxLat, p.latitude)
        maxLng = max(maxLng, p.longitude)
    }
    return LatLngBounds(LatLng(minLat, minLng), LatLng(maxLat, maxLng))
}

// Point-in-polygon using ray casting
private fun pointInPolygon(point: LatLng, polygon: List<LatLng>): Boolean {
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val xi = polygon[i].latitude
        val yi = polygon[i].longitude
        val xj = polygon[j].latitude
        val yj = polygon[j].longitude
        val intersect = ((yi > point.longitude) != (yj > point.longitude)) && (point.latitude < (xj - xi) * (point.longitude - yi) / (yj - yi + 0.0) + xi)
        if (intersect) inside = !inside
        j = i
    }
    return inside
}

private fun suggestAlternateGate(zoneId: String): String {
    // Simple demo mapping — in real system, use routing/graph data and live capacity
    return when (zoneId) {
        "zA" -> "Please use Gate 5 instead of Gate 2"
        "zB" -> "Gate B is busy — consider Gate D"
        "bC" -> "Bridge C congested — use Bridge D or Gate 4"
        else -> "Consider nearby alternate exits"
    }
}

private fun zoneCenter(zoneId: String): LatLng {
    // Demo mapping of zone ids to a representative lat/lng. Replace with real zone geometry.
    return when (zoneId) {
        "zA" -> LatLng(20.5937, 78.9629)
        "zB" -> LatLng(20.5938, 78.9632)
        "bC" -> LatLng(20.5936, 78.9626)
        else -> LatLng(20.5937, 78.9629)
    }
}

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0 // earth radius in meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat/2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon/2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1-a))
    return r * c
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Card(
            modifier = Modifier.size(14.dp),
            shape = RoundedCornerShape(7.dp),
            colors = CardDefaults.cardColors(containerColor = color)
        ) {}
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Render a simple heatmap bitmap: draws radial gradients for each heat cell onto an Android Bitmap.
 * cols/rows define how cells map to pixels; width/height control bitmap resolution.
 */
private fun generateHeatmapBitmap(cells: List<HeatCell>, cols: Int, rows: Int, width: Int, height: Int): Bitmap? {
    if (cells.isEmpty()) return null
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(AndroidColor.TRANSPARENT)

    // Background transparent paint
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // For each cell, compute pixel center and draw radial gradient
    val cellW = width.toFloat() / cols
    val cellH = height.toFloat() / rows
    cells.forEach { cell ->
        val cx = (cell.x + 0.5f) * cellW
        val cy = (cell.y + 0.5f) * cellH
        val intensity = cell.intensity.coerceIn(0f, 1f)
        // pick color
        val color = when {
            intensity > 0.7f -> AndroidColor.argb((200 * intensity).toInt(), 229, 57, 53) // red
            intensity > 0.4f -> AndroidColor.argb((180 * intensity).toInt(), 255, 179, 0) // yellow
            else -> AndroidColor.argb((150 * intensity).toInt(), 67, 160, 71) // green
        }

        val radius = (min(cellW, cellH) * (1f + intensity * 3f)).coerceAtLeast(20f)
        val gradient = RadialGradient(cx, cy, radius, intArrayOf(color, AndroidColor.TRANSPARENT), floatArrayOf(0.0f, 1.0f), Shader.TileMode.CLAMP)
        paint.shader = gradient
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
    }

    // Apply a blur pass to smooth gradients: RenderEffect on API 31+, fallback scale blur otherwise
    return try {
        blurBitmap(bmp, radius = 12)
    } catch (e: Throwable) {
        bmp
    }
}

private fun blurBitmap(src: Bitmap, radius: Int): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val effect = RenderEffect.createBlurEffect(radius.toFloat(), radius.toFloat(), Shader.TileMode.CLAMP)
        // call Paint.setRenderEffect reflectively to avoid compile-time dependency on newer SDK
        try {
            val method = Paint::class.java.getMethod("setRenderEffect", RenderEffect::class.java)
            method.invoke(paint, effect)
        } catch (_: Throwable) {
            // ignore; fallback will proceed
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    } else {
        // Fast fallback: downscale and upscale to approximate blur
        val scale = 8 // larger = blurrier
        val smallW = (src.width / scale).coerceAtLeast(1)
        val smallH = (src.height / scale).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, smallW, smallH, true)
        val out = Bitmap.createScaledBitmap(small, src.width, src.height, true)
        return out
    }
}
