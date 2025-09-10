package com.mahakumbh.crowdsafety.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahakumbh.crowdsafety.di.Locator
import com.mahakumbh.crowdsafety.data.Donation
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn

@Composable
fun ProfileScreen() {
    val repo = Locator.repo
    val userId = repo.currentUserId.collectAsState().value ?: "anonymous"
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Filled.AccountCircle, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(64.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Visitor Name", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("ID: 12345678", fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("[QR Code for Check-in]", modifier = Modifier.padding(16.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { /* TODO: My Reservations */ }, modifier = Modifier.fillMaxWidth()) {
            Text("My Reservations")
        }
        Spacer(Modifier.height(8.dp))
        var showDonations by remember { mutableStateOf(false) }
        Button(onClick = { showDonations = true }, modifier = Modifier.fillMaxWidth()) {
            Text("My Donations")
        }
        if (showDonations) {
            DonationHistoryDialog(onDismiss = { showDonations = false }, userId = userId)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { /* TODO: My SOS History */ }, modifier = Modifier.fillMaxWidth()) {
            Text("My SOS History")
        }
        Spacer(Modifier.height(16.dp))
        Text("Settings", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dark Mode", modifier = Modifier.weight(1f))
            Switch(checked = false, onCheckedChange = { /* TODO: Dark mode */ })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Language", modifier = Modifier.weight(1f))
            Button(onClick = { /* TODO: Change language */ }) { Text("EN") }
        }
    }
}
