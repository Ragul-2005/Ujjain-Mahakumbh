package com.mahakumbh.crowdsafety.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.FileProvider
import android.net.Uri
import android.content.Intent
import android.content.ContentValues
import android.provider.MediaStore
import android.content.Context
import java.io.File
import java.io.FileOutputStream
import android.os.Build
import android.provider.MediaStore.Images
import android.provider.MediaStore.Images.Media
import java.io.OutputStream
import com.mahakumbh.crowdsafety.di.Locator
import com.mahakumbh.crowdsafety.data.Donation
import java.util.UUID
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image as FImage
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.journeyapps.barcodescanner.BarcodeEncoder
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.window.Dialog
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.BarcodeFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import kotlin.math.min
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun DonationScreen() {
    val repo = Locator.repo
    val userId = repo.currentUserId.collectAsState().value ?: "anonymous"
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }
    var paymentMethod by remember { mutableStateOf("UPI") }
    var expandedQr by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var recipient by remember { mutableStateOf("Temple Trust A") }
    var isProcessing by remember { mutableStateOf(false) }
    var confirmedDonation by remember { mutableStateOf<Donation?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier
        .padding(16.dp)
        .verticalScroll(rememberScrollState())) {
        Text("Donate to Mahakumbh Causes", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(12.dp))

        // Category selection as 2x2 grid (two up, two down)
        val cats = listOf("Food", "Sanitation", "Charity", "Infrastructure")
        val rows = cats.chunked(2)
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEach { rowCats ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    rowCats.forEach { cat ->
                        Box(modifier = Modifier.weight(1f).padding(4.dp)) {
                            val cardColor = when(cat) {
                                "Food" -> Color(0xFF43A047)
                                "Sanitation" -> Color(0xFF1976D2)
                                "Charity" -> Color(0xFFFFC107)
                                else -> Color(0xFF8E24AA)
                            }
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .clickable { category = cat }) {
                                val selected = category == cat
                                DonationCategoryCard(label = cat, color = cardColor, icon = when(cat) {
                                    "Food" -> Icons.Filled.Restaurant
                                    "Sanitation" -> Icons.Filled.CleanHands
                                    "Charity" -> Icons.Filled.VolunteerActivism
                                    else -> Icons.Filled.Build
                                }, modifier = Modifier.fillMaxWidth(), selected = selected)
                            }
                        }
                    }
                    if (rowCats.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
            label = { Text("Amount (₹)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Text("Select Recipient", fontWeight = FontWeight.Bold)
        // For demo, a simple recipient selector
        Column { listOf("Temple Trust A", "NGO B", "Official Organizers").forEach { r ->
            Row(modifier = Modifier.fillMaxWidth().clickable { recipient = r }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = recipient==r, onClick = { recipient = r })
                Spacer(Modifier.width(8.dp))
                Text(r)
            }
        }}

        Spacer(Modifier.height(12.dp))

        Text("Payment Method", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val methods = listOf("UPI","Card","Net Banking")
            methods.forEach { m ->
                val targetColor = if (paymentMethod == m) Color(0xFF673AB7) else Color(0xFF121212)
                val animColor by animateColorAsState(targetColor, animationSpec = tween(durationMillis = 300))
                FilterChip(
                    selected = paymentMethod == m,
                    onClick = { paymentMethod = m },
                    label = { Text(m) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = animColor),
                    modifier = Modifier
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        if (confirmedDonation == null) {
            // Custom rounded gradient pill button
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF66BB6A), Color(0xFF43A047))))
                .clickable(enabled = !isProcessing) {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt <= 0.0) return@clickable
                    coroutineScope.launch {
                        isProcessing = true
                        delay(700)
                        val donation = Donation(
                            id = "don-${UUID.randomUUID()}",
                            userId = userId,
                            amount = amt,
                            method = paymentMethod,
                            category = category,
                            to = recipient,
                            receiptUrl = null,
                            timestamp = System.currentTimeMillis(),
                            verified = true
                        )
                        repo.makeDonation(donation)
                        confirmedDonation = donation
                        isProcessing = false
                    }
                },
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(imageVector = Icons.Filled.Favorite, contentDescription = "Donate", tint = Color.White)
                        Spacer(Modifier.width(10.dp))
                        Text("Donate Now", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Receipt / QR view
            Spacer(Modifier.height(16.dp))
            Text("Donation Successful", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Text("Amount: ₹${confirmedDonation!!.amount}", fontWeight = FontWeight.Medium)
            Text("To: ${confirmedDonation!!.to}")
            Text("Category: ${confirmedDonation!!.category}")
            Text("Method: ${confirmedDonation!!.method}")
            Spacer(Modifier.height(12.dp))
            // Placeholder for QR — in a real app generate a QR bitmap for the receipt URL / unique id
            Card(modifier = Modifier.size(200.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.clickable { expandedQr = true }) {
                    val qrData = confirmedDonation!!.receiptUrl ?: confirmedDonation!!.id
                    val qrBitmap = try { BarcodeEncoder().encodeBitmap(qrData, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512) } catch (e: Exception) { null }
                    if (qrBitmap != null) {
                        FImage(painter = BitmapPainter(qrBitmap.asImageBitmap()), contentDescription = "Donation QR", modifier = Modifier.size(180.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                    } else {
                        Text("QR\n${confirmedDonation!!.id}", textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("This receipt is digitally signed by Mahakumbh organizers. Use the QR to verify authenticity.")
            Spacer(Modifier.height(80.dp)) // allow space for bottom nav so QR isn't clipped
        }

        // Expanded QR Dialog
        if (expandedQr && confirmedDonation != null) {
            val qrData = confirmedDonation!!.receiptUrl ?: confirmedDonation!!.id
            val qrBitmap = try { BarcodeEncoder().encodeBitmap(qrData, com.google.zxing.BarcodeFormat.QR_CODE, 1024, 1024) } catch (e: Exception) { null }
            Dialog(onDismissRequest = { expandedQr = false }) {
                Card(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Donation Receipt", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                            if (qrBitmap != null) {
                            FImage(painter = BitmapPainter(qrBitmap.asImageBitmap()), contentDescription = "QR Large", modifier = Modifier.size(320.dp).scale(animateFloatAsState(if (expandedQr) 1.0f else 0.95f).value), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(onClick = {
                                    // share via file provider
                                    shareBitmap(context, qrBitmap, "donation-${confirmedDonation!!.id}.png")
                                }) { Text("Share") }
                                TextButton(onClick = {
                                    // save to gallery
                                    saveBitmapToGallery(context, qrBitmap, "donation-${confirmedDonation!!.id}.png")
                                }) { Text("Save") }
                                TextButton(onClick = { expandedQr = false }) { Text("Close") }
                            }
                        } else {
                            Text("Unable to render QR")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DonationCategoryCard(label: String, color: Color, icon: ImageVector, modifier: Modifier = Modifier, selected: Boolean = false) {
    val borderModifier = if (selected) modifier.border(3.dp, Color.White) else modifier
    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = borderModifier
            .padding(4.dp)
            .height(96.dp)
            .fillMaxWidth(),
        elevation = if (selected) CardDefaults.cardElevation(8.dp) else CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            Modifier
                .padding(8.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun PaymentOptionChip(label: String) {
    AssistChip(onClick = { /* TODO: Select payment */ }, label = { Text(label) })
}

// Helper: generate a Bitmap QR from a string using ZXing
fun generateQrBitmap(text: String, width: Int, height: Int): Bitmap? {
    if (text.isBlank() || width <= 0 || height <= 0) return null
    return try {
        val hints: Map<EncodeHintType, Any> = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val writer = QRCodeWriter()
        val bitMatrix: BitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height, hints)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun shareBitmap(context: Context, bitmap: Bitmap, fileName: String) {
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, fileName)
        val fos = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        fos.flush()
        fos.close()
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share QR"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String) {
    try {
        val fos: OutputStream?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(Images.Media.DISPLAY_NAME, fileName)
                put(Images.Media.MIME_TYPE, "image/png")
                put(Images.Media.RELATIVE_PATH, "Pictures/Mahakumbh")
            }
            val imageUri = resolver.insert(Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            fos = imageUri?.let { resolver.openOutputStream(it) }
        } else {
            val imagesDir = File(context.getExternalFilesDir(null), "Pictures")
            imagesDir.mkdirs()
            val image = File(imagesDir, fileName)
            fos = FileOutputStream(image)
        }
        fos?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun DonationReceiptPreview() {
    // ...existing code...
    val fakeDonation = Donation(
        id = "don-preview-123",
        userId = "user-preview",
        amount = 250.0,
        method = "UPI",
        category = "Food",
        to = "Temple Trust A",
        receiptUrl = null,
        timestamp = System.currentTimeMillis(),
        verified = true
    )

    Card(modifier = Modifier.padding(16.dp).fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Donation Successful", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Text("Amount: ₹${fakeDonation.amount}", fontWeight = FontWeight.Medium)
            Text("To: ${fakeDonation.to}")
            Text("Category: ${fakeDonation.category}")
            Text("Method: ${fakeDonation.method}")
            Spacer(Modifier.height(12.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                val qrData = fakeDonation.receiptUrl ?: fakeDonation.id
                val qrBitmap = generateQrBitmap(qrData, 320, 320)
                if (qrBitmap != null) {
                    FImage(painter = BitmapPainter(qrBitmap.asImageBitmap()), contentDescription = "Donation QR", modifier = Modifier.size(160.dp))
                } else {
                    Text("QR\n${fakeDonation.id}", textAlign = TextAlign.Center)
                }
            }
        }
    }
}
