package com.mahakumbh.crowdsafety.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mahakumbh.crowdsafety.data.AlertLevel
import com.mahakumbh.crowdsafety.data.WeatherAlert
import com.mahakumbh.crowdsafety.data.WeatherData
import com.mahakumbh.crowdsafety.data.WeatherType
import com.mahakumbh.crowdsafety.vm.WeatherViewModel

@Composable
fun WeatherCard(vm: WeatherViewModel = viewModel(), onClick: () -> Unit) {
    val weatherData by vm.weatherData.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = weatherData.weatherType.toIcon(),
                contentDescription = weatherData.weatherType.name,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = "${weatherData.temperature.toInt()}°C",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = weatherData.weatherType.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(vm: WeatherViewModel = viewModel(), onBack: () -> Unit) {
    val weatherData by vm.weatherData.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weather Report") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(it)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WeatherSummary(weatherData)
            Spacer(Modifier.height(24.dp))
            WeatherDetails(weatherData)
            Spacer(Modifier.height(24.dp))
            WeatherAlerts(weatherData.alerts)
        }
    }
}

@Composable
private fun WeatherSummary(weatherData: WeatherData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "${weatherData.temperature.toInt()}°C",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = weatherData.weatherType.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = weatherData.weatherType.toIcon(),
                contentDescription = weatherData.weatherType.name,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun WeatherDetails(weatherData: WeatherData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        WeatherDetailItem(
            icon = Icons.Default.WaterDrop,
            label = "Humidity",
            value = "${weatherData.humidity.toInt()}%"
        )
        WeatherDetailItem(
            icon = Icons.Default.Cloud,
            label = "Rainfall",
            value = "${weatherData.rainfall} mm"
        )
    }
}

@Composable
private fun WeatherDetailItem(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(8.dp))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
    }
}

@Composable
private fun WeatherAlerts(alerts: List<WeatherAlert>) {
    Column {
        Text(
            text = "Live Alerts",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        if (alerts.isEmpty()) {
            Text(text = "No active weather alerts.", style = MaterialTheme.typography.bodyLarge)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(alerts) { alert ->
                    WeatherAlertItem(alert)
                }
            }
        }
    }
}

@Composable
private fun WeatherAlertItem(alert: WeatherAlert) {
    Card(
        modifier = Modifier.width(300.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = alert.level.toColor())
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = alert.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = alert.message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

private fun WeatherType.toIcon(): ImageVector = when (this) {
    WeatherType.Sunny -> Icons.Default.WbSunny
    WeatherType.Cloudy -> Icons.Default.Cloud
    WeatherType.Rain -> Icons.Default.Grain
    WeatherType.Storm -> Icons.Default.Thunderstorm
    WeatherType.ExtremeHeat -> Icons.Default.LocalFireDepartment
}

private fun AlertLevel.toColor(): Color = when (this) {
    AlertLevel.Advisory -> Color(0xFFFFA726) // Orange
    AlertLevel.Watch -> Color(0xFFEF5350) // Red
    AlertLevel.Warning -> Color(0xFFD32F2F) // Dark Red
}
