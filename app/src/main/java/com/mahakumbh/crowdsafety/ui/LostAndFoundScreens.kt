package com.mahakumbh.crowdsafety.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.mahakumbh.crowdsafety.data.ReportStatus
import com.mahakumbh.crowdsafety.data.UserRole
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mahakumbh.crowdsafety.data.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image as FImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import com.mahakumbh.crowdsafety.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mahakumbh.crowdsafety.vm.LostAndFoundViewModel
import com.mahakumbh.crowdsafety.di.Locator
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportLostPersonScreen(
    onBack: () -> Unit, 
    onSubmit: () -> Unit, 
    viewModel: LostAndFoundViewModel = viewModel()
) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var clothing by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val genderOptions = listOf("Male", "Female", "Other", "Prefer not to say")
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report a Missing Person") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = age,
                onValueChange = { age = it },
                label = { Text("Age") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
            )
            // Gender Dropdown
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Gender", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = gender,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        genderOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    gender = option
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = clothing,
                onValueChange = { clothing = it },
                label = { Text("Clothing Description") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Last Known Location") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Photo Upload Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Add a clear photo of the person",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Photo Preview / Placeholder
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .clickable { /* Handle photo selection */ },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUri != null) {
                        // In a real app, load the actual image using Coil or Glide
                        FImage(
                            painter = painterResource(id = R.drawable.mahakumbh_logo),
                            contentDescription = "Selected photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No photo selected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Photo Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Camera Button
                    Button(
                        onClick = {
                            // In a real app, this would launch the camera
                            // For now, we'll just use a placeholder
                            photoUri = "camera_placeholder"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = ButtonDefaults.outlinedButtonBorder
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Take Photo",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Camera")
                    }
                    
                    // Gallery Button
                    Button(
                        onClick = {
                            // In a real app, this would open the gallery
                            // For now, we'll just use a placeholder
                            photoUri = "gallery_placeholder"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = ButtonDefaults.outlinedButtonBorder
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Choose from Gallery",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Gallery")
                    }
                }
                
                // Photo Help Text
                if (photoUri == null) {
                    Text(
                        "A clear photo helps in identification",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            // Error message if any
            if (showError) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            
            // Submit Button
            Button(
                onClick = {
                    // Validate inputs
                    if (name.isBlank() || age.isBlank() || gender.isBlank() || location.isBlank()) {
                        showError = true
                        errorMessage = "Please fill in all required fields"
                        return@Button
                    }
                    
                    val ageValue = age.toIntOrNull()
                    if (ageValue == null || ageValue <= 0 || ageValue > 120) {
                        showError = true
                        errorMessage = "Please enter a valid age"
                        return@Button
                    }
                    
                    showError = false
                    isLoading = true
                    
                    // In a real app, you would upload the photo here and get a URL
                    // For now, we'll just use a placeholder
                    val finalPhotoUri = photoUri ?: ""
                    
                    // Submit the report
                    viewModel.addReport(
                        name = name,
                        age = ageValue,
                        gender = gender,
                        clothingDescription = clothing,
                        lastKnownLocation = location,
                        photoUrl = finalPhotoUri
                    )
                    isLoading = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    LaunchedEffect(true) {
                        delay(1000)
                        isLoading = false
                        onSubmit()
                    }
                } else {
                    Text("Submit Report")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LostPersonListScreen(
    onBack: () -> Unit, 
    onReportClick: (String) -> Unit,
    onNavigateToReport: () -> Unit,
    viewModel: LostAndFoundViewModel = viewModel()
) {
    // Collect reports from the ViewModel (backed by FirestoreLostRepository). Provide an
    // initial empty list so Compose has a stable starting value.
    val reports by viewModel.reports.collectAsState(initial = emptyList())

    // When Firestore is not available or there are no active reports, show a small
    // set of sample reports so the Visitor UI isn't empty (useful for demo/debug).
    val sampleReports = listOf(
        LostPersonReport(id = "sample-1", reporterId = "system", name = "Raju Sharma", age = 72, gender = "Male", clothingDescription = "Blue shirt, white dhoti", lastKnownLocation = "Sangam Ghat", photoUrl = "", status = ReportStatus.Active, timestamp = System.currentTimeMillis()),
        LostPersonReport(id = "sample-2", reporterId = "system", name = "Sita Devi", age = 65, gender = "Female", clothingDescription = "Red saree", lastKnownLocation = "Hanuman Garhi", photoUrl = "", status = ReportStatus.Active, timestamp = System.currentTimeMillis() - 3600000)
    )

    val visibleReports = if (reports.isEmpty()) sampleReports else reports

    val currentUserRole by Locator.repo.currentUserRole.collectAsState()

    // Small runtime diagnostics: read projectId and lastUpdated from the FirestoreLostRepository
    val projectId = com.mahakumbh.crowdsafety.di.Locator.lostRepo.projectId
    val lastUpdated by com.mahakumbh.crowdsafety.di.Locator.lostRepo.lastUpdated.collectAsState(initial = 0L)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Reports") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                // quick status shown on the right to help debug cross-device Firestore sync
                actions = {
                    val ageSec = if (lastUpdated == 0L) "-" else ((System.currentTimeMillis() - lastUpdated) / 1000).toString() + "s"
                    Text("proj:${projectId ?: "-"} • upd:$ageSec", style = MaterialTheme.typography.bodySmall, modifier = androidx.compose.ui.Modifier.padding(end = 12.dp))
                }
            )
        },
        floatingActionButton = {
            // Show the report FAB to any logged-in user (Visitor or Volunteer)
            if (currentUserRole != null) {
                FloatingActionButton(onClick = onNavigateToReport) {
                    Icon(Icons.Filled.Add, contentDescription = "Report a missing person")
                }
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(visibleReports) { report ->
                LostPersonCard(report = report, onClick = { onReportClick(report.id) }, onDelete = { id ->
                    viewModel.removeReport(id)
                })
            }
        }
    }
}

@Composable
fun LostPersonCard(report: LostPersonReport, onClick: () -> Unit, onDelete: (String) -> Unit = {}) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Replaced the confusing plus icon with a more appropriate person icon
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (report.photoUrl.isNotEmpty()) {
                     FImage(
                        painter = painterResource(id = R.drawable.mahakumbh_logo), // Placeholder for actual image
                        contentDescription = "Photo of ${report.name}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Person Icon",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(report.name, style = MaterialTheme.typography.titleLarge)
                Text("Age: ${report.age}, Gender: ${report.gender}", style = MaterialTheme.typography.bodyMedium)
                Text("Last Seen: ${report.lastKnownLocation}", style = MaterialTheme.typography.bodyMedium)
            }

            // Show delete button for volunteers only
            val currentUserRole by Locator.repo.currentUserRole.collectAsState()
            if (currentUserRole == UserRole.Volunteer) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete report")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Report") },
            text = { Text("Are you sure you want to delete this report? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(report.id)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LostPersonDetailScreen(
    reportId: String,
    onBack: () -> Unit,
    viewModel: LostAndFoundViewModel = viewModel()
) {
    val reports by viewModel.reports.collectAsState()
    val report = reports.find { it.id == reportId }
    val currentUserRole by com.mahakumbh.crowdsafety.di.Locator.repo.currentUserRole.collectAsState()

    if (report == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Report Details") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Report not found.")
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (report.photoUrl.isNotEmpty()) {
                FImage(
                    painter = painterResource(id = R.drawable.mahakumbh_logo),
                    contentDescription = "Photo of ${report.name}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Text("Name: ${report.name}", style = MaterialTheme.typography.titleLarge)
            Text("Age: ${report.age}", style = MaterialTheme.typography.bodyLarge)
            Text("Gender: ${report.gender}", style = MaterialTheme.typography.bodyLarge)
            Text("Clothing: ${report.clothingDescription}", style = MaterialTheme.typography.bodyMedium)
            Text("Last Known Location: ${report.lastKnownLocation}", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Status: ${report.status.name}",
                style = MaterialTheme.typography.bodyLarge,
                color = when(report.status) {
                    ReportStatus.Active -> MaterialTheme.colorScheme.error
                    ReportStatus.Resolved -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )

            if (currentUserRole == UserRole.Volunteer && report.status == ReportStatus.Active) {
                Button(
                    onClick = { 
                        viewModel.resolveReport(report.id)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mark as Resolved")
                }
            }
        }
    }
}
