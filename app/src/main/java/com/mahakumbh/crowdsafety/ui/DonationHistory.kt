package com.mahakumbh.crowdsafety.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mahakumbh.crowdsafety.di.Locator
import com.mahakumbh.crowdsafety.data.Donation

@Composable
fun DonationHistoryDialog(onDismiss: () -> Unit, userId: String) {
    val repo = Locator.repo
    val donations by repo.donations.collectAsState(initial = emptyList())
    val my = donations.filter { it.userId == userId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("My Donations") },
        text = {
            if (my.isEmpty()) {
                Text("No donations yet.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                    items(my) { d ->
                        Column(Modifier.padding(8.dp)) {
                            Text("₹${d.amount} • ${d.to}")
                            Text("${d.category} • ${java.time.Instant.ofEpochMilli(d.timestamp)}", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
