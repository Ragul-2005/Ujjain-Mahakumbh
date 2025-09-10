package com.mahakumbh.crowdsafety.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahakumbh.crowdsafety.data.FirestoreLostRepository
import com.mahakumbh.crowdsafety.data.LostPersonReport
import com.mahakumbh.crowdsafety.di.Locator
import com.mahakumbh.crowdsafety.data.ReportStatus
import com.mahakumbh.crowdsafety.util.NotificationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LostManagementScreen(onBack: () -> Unit) {
    val repo = Locator.lostRepo
    val reports by repo.reports.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var clothing by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }

    LaunchedEffect(selectedId, reports) {
        val sel = reports.firstOrNull { it.id == selectedId }
        if (sel != null) {
            name = sel.name
            age = sel.age.toString()
            gender = sel.gender
            clothing = sel.clothingDescription
            location = sel.lastKnownLocation
        } else {
            name = ""
            age = ""
            gender = ""
            clothing = ""
            location = ""
        }
    }

    val ctx = LocalContext.current

    Scaffold(topBar = {
        TopAppBar(title = { Text("Lost People - Manage") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
        })
    }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = age, onValueChange = { age = it.filter { ch -> ch.isDigit() } }, label = { Text("Age") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = gender, onValueChange = { gender = it }, label = { Text("Gender") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = clothing, onValueChange = { clothing = it }, label = { Text("Clothing description") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Last known location") }, modifier = Modifier.fillMaxWidth())

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!selectedId.isNullOrBlank()) {
                            TextButton(onClick = {
                                // update
                                val rpt = reports.firstOrNull { it.id == selectedId } ?: return@TextButton
                                val updated = rpt.copy(name = name, age = age.toIntOrNull() ?: rpt.age, gender = gender, clothingDescription = clothing, lastKnownLocation = location, status = ReportStatus.Active)
                                repo.updateReport(updated)
                                NotificationHelper.showNotification(ctx, 0, "Lost report updated", "Updated: ${updated.name}")
                                selectedId = null
                            }) { Icon(Icons.Filled.Edit, contentDescription = null); Text("Update") }

                            TextButton(onClick = {
                                selectedId?.let { repo.deleteReport(it) }
                                selectedId = null
                                NotificationHelper.showNotification(ctx, 0, "Lost report removed", "Report deleted")
                            }) { Icon(Icons.Filled.Delete, contentDescription = null); Text("Delete") }
                        }

                        TextButton(onClick = {
                            if (name.isBlank()) return@TextButton
                            val newRpt = LostPersonReport(id = "", reporterId = "", name = name, age = age.toIntOrNull() ?: 0, gender = gender, clothingDescription = clothing, lastKnownLocation = location, photoUrl = "", status = ReportStatus.Active, timestamp = System.currentTimeMillis())
                            repo.postReport(newRpt)
                            NotificationHelper.showNotification(ctx, 0, "Lost report posted", "${newRpt.name} — ${newRpt.lastKnownLocation}")
                            name = ""; age = ""; gender = ""; clothing = ""; location = ""
                        }) { Text("Post") }
                    }
                }
            }

            Text("Posted reports", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(reports) { rpt ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { selectedId = rpt.id }, shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(rpt.name, fontWeight = FontWeight.Bold)
                            Text("Age: ${rpt.age}  •  Gender: ${rpt.gender}", style = MaterialTheme.typography.bodySmall)
                            Text("Clothing: ${rpt.clothingDescription}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Last seen: ${rpt.lastKnownLocation}", style = MaterialTheme.typography.bodySmall)
                            Text("Status: ${rpt.status}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
