package com.mahakumbh.crowdsafety.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LocationSharingScreen() {
    var sharingEnabled by remember { mutableStateOf(false) }
    val friends = listOf("Amit", "Priya", "Rahul")
    val friendSharing = remember { mutableStateListOf(false, false, false) }

    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Share Live Location", modifier = Modifier.weight(1f))
            Switch(checked = sharingEnabled, onCheckedChange = { sharingEnabled = it })
        }
        Spacer(Modifier.height(16.dp))
        Text("Friends")
        friends.forEachIndexed { i, name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, modifier = Modifier.weight(1f))
                Switch(checked = friendSharing[i], onCheckedChange = { friendSharing[i] = it })
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Box(Modifier.height(200.dp), contentAlignment = Alignment.Center) {
                Text("Map with friend pins (to be implemented)")
            }
        }
    }
}
