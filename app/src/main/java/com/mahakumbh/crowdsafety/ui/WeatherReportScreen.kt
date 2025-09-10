package com.mahakumbh.crowdsafety.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector

// Dummy WeatherUiModel for preview
class WeatherUiModel(
    val temp: Int = 32,
    val condition: String = "Sunny",
    val icon: ImageVector = Icons.Filled.WbSunny,
    val humidity: Int = 60,
    val rainChance: Int = 10,
    val windSpeed: Int = 8,
    val hourly: List<WeatherHour> = List(12) { WeatherHour("${6+it} AM", 30+it, Icons.Filled.WbSunny) }
)
class WeatherHour(val time: String, val temp: Int, val icon: ImageVector)

@Composable
fun WeatherReportScreen(weather: WeatherUiModel) {
    Column(Modifier.padding(16.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1976D2)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = weather.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("${weather.temp}°C", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 32.sp)
                    Text(weather.condition, color = Color.White, fontSize = 18.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            WeatherStat("Humidity", "${weather.humidity}%", Icons.Filled.WaterDrop)
            WeatherStat("Rain", "${weather.rainChance}%", Icons.Filled.Umbrella)
            WeatherStat("Wind", "${weather.windSpeed} km/h", Icons.Filled.Air)
        }
        Spacer(Modifier.height(16.dp))
        Text("Hourly Forecast", fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            weather.hourly.forEach { hour ->
                WeatherHourCard(hour)
            }
        }
    }
}

@Composable
fun WeatherStat(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Icon(imageVector = icon, contentDescription = label, tint = Color(0xFF1976D2), modifier = Modifier.size(28.dp))
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp)
    }
}

@Composable
fun WeatherHourCard(hour: WeatherHour) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(8.dp).width(72.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFBBDEFB))
    ) {
        Column(
            Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(hour.time, fontSize = 12.sp)
            Icon(imageVector = hour.icon, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(24.dp))
            Text("${hour.temp}°", fontWeight = FontWeight.Bold)
        }
    }
}
