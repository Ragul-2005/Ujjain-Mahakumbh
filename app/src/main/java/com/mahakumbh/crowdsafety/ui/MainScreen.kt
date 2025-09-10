package com.mahakumbh.crowdsafety.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

enum class MainTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Map("Map", Icons.Filled.Map),
    Events("Events", Icons.Filled.Event),
    Profile("Profile", Icons.Filled.Person)
}

enum class Feature {
    SOS, Weather, LostPeople, Donation, Density, Reservation
}

fun Feature.cardInfo(): Triple<Color, ImageVector, String> = when(this) {
    Feature.SOS -> Triple(Color.Red, Icons.Filled.Warning, "Emergency SOS")
    Feature.Weather -> Triple(Color(0xFF1976D2), Icons.Filled.Cloud, "Weather Report")
    Feature.LostPeople -> Triple(Color(0xFFFFC107), Icons.Filled.Search, "Find Lost People")
    Feature.Donation -> Triple(Color(0xFF43A047), Icons.Filled.VolunteerActivism, "Online Donation")
    Feature.Density -> Triple(Color(0xFFFF9800), Icons.Filled.Thermostat, "Density")
    Feature.Reservation -> Triple(Color(0xFF8E24AA), Icons.Filled.Star, "Special Reservation")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onFeatureClick: (Feature) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mahakumbh 2025", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* TODO: Notifications */ }) {
                        Icon(imageVector = Icons.Filled.Notifications, contentDescription = "Notifications")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                MainTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            FeatureGrid(onFeatureClick)
        }
    }
}

@Composable
fun FeatureGrid(onFeatureClick: (Feature) -> Unit) {
    val features = listOf(
        Feature.SOS, Feature.Weather, Feature.LostPeople,
        Feature.Donation, Feature.Density, Feature.Reservation
    )
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        for (row in features.chunked(3)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { feature ->
                    FeatureCard(feature, modifier = Modifier.weight(1f), onClick = { onFeatureClick(feature) })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun FeatureCard(feature: Feature, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (color, icon, label) = feature.cardInfo()
    if (feature == Feature.Donation) {
        // Prominent rounded pill button style for Online Donation
        Card(
            modifier = modifier
                .height(72.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.9f)))) ,
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
    } else if (feature == Feature.Density) {
        // Compact pill for Density feature (icon + short label)
        Card(
            modifier = modifier
                .height(72.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.9f)))) ,
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    } else {
        Card(
            modifier = modifier
                .aspectRatio(1f)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = color)
        ) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}
