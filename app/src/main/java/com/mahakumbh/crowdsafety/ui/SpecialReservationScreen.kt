package com.mahakumbh.crowdsafety.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import com.mahakumbh.crowdsafety.di.Locator
import java.util.UUID
import com.mahakumbh.crowdsafety.data.Reservation
import com.mahakumbh.crowdsafety.data.ReservationType
import androidx.compose.foundation.Image as FImage
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale

@Composable
fun SpecialReservationScreen() {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Pregnant") }
    var slotAssigned by remember { mutableStateOf(false) }
    // These would normally be assigned by backend logic
    var slotTime by remember { mutableStateOf("01:33 pm") }
    var zone by remember { mutableStateOf("Zone A") }

    val ctx = LocalContext.current
    val repo = Locator.repo
    var reservationId by remember { mutableStateOf<String?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // image pickers for upload/capture
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // save to cache and upload (mock) - for demo we'll pass the uri.toString()
            val ok = repo.uploadEligibilityDocuments(repo.currentUserId.value ?: "anon", reservationId ?: "", idProof = uri.toString(), doctorCertificate = null)
            Toast.makeText(ctx, if (ok) "ID uploaded" else "Upload failed", Toast.LENGTH_SHORT).show()
        }
    }
    val pickDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val ok = repo.uploadEligibilityDocuments(repo.currentUserId.value ?: "anon", reservationId ?: "", idProof = null, doctorCertificate = uri.toString())
            Toast.makeText(ctx, if (ok) "Doctor cert uploaded" else "Upload failed", Toast.LENGTH_SHORT).show()
        }
    }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        bmp?.let {
            // save temporarily and upload
            val fileName = "capture-${UUID.randomUUID()}.png"
            try {
                val file = File(ctx.cacheDir, fileName)
                val fos = FileOutputStream(file)
                it.compress(Bitmap.CompressFormat.PNG, 90, fos)
                fos.flush(); fos.close()
                val ok = repo.uploadEligibilityDocuments(repo.currentUserId.value ?: "anon", reservationId ?: "", idProof = file.absolutePath, doctorCertificate = null)
                Toast.makeText(ctx, if (ok) "Captured and uploaded" else "Upload failed", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { e.printStackTrace(); Toast.makeText(ctx, "Capture failed", Toast.LENGTH_SHORT).show() }
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {

    if (!slotAssigned) {
            Text(text = "Crowd Safety", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Full name", fontStyle = FontStyle.Italic) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Age
            OutlinedTextField(
                value = age,
                onValueChange = { age = it },
                placeholder = { Text("Age", fontStyle = FontStyle.Italic) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Type pills
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Type:", modifier = Modifier.padding(start = 4.dp))
                val types = listOf("Pregnant", "Elderly")
                for (t in types) {
                    val selected = selectedType == t
                    Surface(
                        tonalElevation = if (selected) 4.dp else 0.dp,
                        shape = RoundedCornerShape(24.dp),
                        color = if (selected) Color(0xFFDCC6FF) else Color(0xFFefe8f6),
                        modifier = Modifier
                            .clickable { selectedType = t }
                    ) {
                        Text(text = t, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = Color(0xFF3B2B5A))
                    }
                }
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text("Notes (optional)", fontStyle = FontStyle.Italic) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(8.dp))

            // Big rounded submit button
            Button(onClick = {
                if (name.isBlank()) {
                    Toast.makeText(ctx, "Please enter name", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                // create reservation model and ask repo to persist (mock)
                val rid = "res-${UUID.randomUUID()}"
                val r = Reservation(id = rid, userId = repo.currentUserId.value ?: "anon", type = if (selectedType=="Pregnant") ReservationType.Pregnant else ReservationType.Elderly, slot = "01:33 pm", zone = "Zone A")
                try {
                    repo.makeReservation(r)
                    reservationId = r.id
                    slotTime = r.slot
                    zone = r.zone
                        // generate QR for reservation including user id and name: "reservation:<id>:<userId>:<name>"
                        val uid = repo.currentUserId.value ?: "anon"
                        val safeName = name.replace(":", "-")
                        val payload = "reservation:${r.id}:${uid}:${safeName}"
                        val qr = com.mahakumbh.crowdsafety.ui.generateQrBitmap(payload, 512, 512)
                    qrBitmap = qr
                    slotAssigned = true
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Reservation failed", Toast.LENGTH_SHORT).show()
                }
            }, modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDCC6FF))) {
                Text("Request Priority Slot", color = Color(0xFF3B2B5A), fontSize = 18.sp)
            }

        } else {
            // Add a prominent header at the very top when confirmed (user requested)
            Text(text = "Reservation Confirmed", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            // Confirmation card
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF5B5560))) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Reservation Confirmed", style = MaterialTheme.typography.titleLarge)
                    Text("Name: ${name}", style = MaterialTheme.typography.bodyLarge)
                    Text("Type: ${selectedType}", style = MaterialTheme.typography.bodyLarge)
                    Text("Slot: ${slotTime}", style = MaterialTheme.typography.bodyLarge)
                    Text("Zone: ${zone}", style = MaterialTheme.typography.bodyLarge)

                            // QR box (generate when reservationId is set)
                            Card(modifier = Modifier.size(160.dp), shape = RoundedCornerShape(8.dp)) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    if (qrBitmap != null) {
                                        FImage(painter = BitmapPainter(qrBitmap!!.asImageBitmap()), contentDescription = "Reservation QR", modifier = Modifier.size(140.dp), contentScale = ContentScale.Fit)
                                    } else {
                                        Text("QR", color = Color.Black)
                                    }
                                }
                            }

                    // Share / Save row
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        // circular share
                        Surface(shape = CircleShape, color = Color(0xFFDCC6FF), modifier = Modifier.size(56.dp).clickable {
                            // share the QR bitmap if present
                            if (qrBitmap != null) {
                                // reuse DonationScreen share helper if available
                                try { com.mahakumbh.crowdsafety.ui.shareBitmap(ctx, qrBitmap!!, "reservation-${reservationId}.png"); Toast.makeText(ctx, "Shared", Toast.LENGTH_SHORT).show() } catch (e: Exception) { Toast.makeText(ctx, "Share failed", Toast.LENGTH_SHORT).show() }
                            } else {
                                Toast.makeText(ctx, "No QR to share", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Save", modifier = Modifier.clickable {
                            if (qrBitmap != null) {
                                com.mahakumbh.crowdsafety.ui.saveBitmapToGallery(ctx, qrBitmap!!, "reservation-${reservationId}.png"); Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
                            } else Toast.makeText(ctx, "No QR to save", Toast.LENGTH_SHORT).show()
                        })
                    }

                    Spacer(Modifier.height(8.dp))

                    // Upload / Capture buttons
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = { pickImageLauncher.launch("image/*") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDCC6FF))) {
                                Text("Upload ID Proof", color = Color(0xFF3B2B5A))
                            }
                            Button(onClick = { pickDocLauncher.launch("image/*") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDCC6FF))) {
                                Text("Upload Doctor Cert", color = Color(0xFF3B2B5A))
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = { takePictureLauncher.launch(null) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDCC6FF))) {
                                Text("Capture ID", color = Color(0xFF3B2B5A))
                            }
                            Button(onClick = { takePictureLauncher.launch(null) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDCC6FF))) {
                                Text("Capture Doctor Cert", color = Color(0xFF3B2B5A))
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Submit Documents", modifier = Modifier.clickable { Toast.makeText(ctx, "Documents submitted", Toast.LENGTH_SHORT).show() }, color = Color.White)
                }
            }
        }
    }
}
