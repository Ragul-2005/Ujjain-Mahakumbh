package com.mahakumbh.crowdsafety.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import com.mahakumbh.crowdsafety.data.SosType

data class SosUiState(val sending: Boolean = false)

@Composable
fun EmergencySosScreen(
    onSosSend: (com.mahakumbh.crowdsafety.data.SosType) -> Unit,
    sosState: SosUiState
) {
    var selectedType by remember { mutableStateOf(com.mahakumbh.crowdsafety.data.SosType.Panic) }
    var showDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Emergency SOS", fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color.Red)
        Spacer(Modifier.height(32.dp))
        Box(contentAlignment = Alignment.Center) {
            Button(
                onClick = { showDialog = true },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.size(120.dp)
            ) {
                Text("SOS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 32.sp)
            }
            if (sosState.sending) {
                BlinkingIndicator("SOS Sent, Help is Coming")
            }
        }
        Spacer(Modifier.height(24.dp))
        SegmentedControl(
            options = listOf("Panic", "Medical", "Security"),
            selectedIndex = selectedType.ordinal,
            onOptionSelected = { selectedType = com.mahakumbh.crowdsafety.data.SosType.values()[it] },
            colors = listOf(Color.Red, Color(0xFF1976D2), Color(0xFF388E3C))
        )
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Send SOS?") },
            text = { Text("Are you sure you want to send a ${selectedType.name} alert?") },
            confirmButton = {
                TextButton(onClick = {
                    onSosSend(selectedType)
                    showDialog = false
                }) { Text("Send", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun BlinkingIndicator(text: String) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        )
    )
    Text(
        text,
        color = Color.Red,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        modifier = Modifier.alpha(alpha).padding(top = 16.dp)
    )
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    colors: List<Color>
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Button(
                onClick = { onOptionSelected(i) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) colors[i] else Color.LightGray,
                    contentColor = if (selected) Color.White else Color.Black
                ),
                shape = CircleShape,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            ) {
                Text(label, fontWeight = FontWeight.Bold)
            }
        }
    }
}
