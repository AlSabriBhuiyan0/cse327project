                        val geofence = GeofenceLocation(
                            id = nameInput,
                            latitude = latitudeInput.toDouble(),
                            longitude = longitudeInput.toDouble(),
                            radius = radiusInput.toFloat()
                        )
                        onGeofenceAdded(geofence)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
package com.google.ai.edge.gallery.ui.geofence

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.ai.edge.gallery.GeofenceLocation
import com.google.ai.edge.gallery.GeofenceManager
import com.google.ai.edge.gallery.GeofenceRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun GeofenceManagementScreen(
    onBackPressed: () -> Unit,
    geofenceManager: GeofenceManager,
    viewModel: GeofenceManagementViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val geofences by GeofenceRepository.userGeofences.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Geofences") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Geofence")
            }
        }
    ) { paddingValues ->
        if (showAddDialog) {
            AddGeofenceDialog(
                onDismiss = { showAddDialog = false },
                onGeofenceAdded = { geofence ->
                    // Add geofence to repository
                    val added = GeofenceRepository.addGeofence(geofence)
                    if (added) {
                        // Register geofences with the system
                        scope.launch {
                            geofenceManager.registerGeofences(GeofenceRepository.userGeofences.value)
                        }
                    }
                    showAddDialog = false
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (geofences.isEmpty()) {
                EmptyGeofenceView()
            } else {
                GeofenceList(
                    geofences = geofences,
                    onDeleteGeofence = { geofenceId ->
                        GeofenceRepository.removeGeofence(geofenceId)
                        // Re-register the updated list of geofences
                        scope.launch {
                            geofenceManager.registerGeofences(GeofenceRepository.userGeofences.value)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun EmptyGeofenceView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No Geofences Added",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Add your first geofence by clicking the + button",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun GeofenceList(
    geofences: List<GeofenceLocation>,
    onDeleteGeofence: (String) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(geofences) { geofence ->
            GeofenceItem(
                geofence = geofence,
                onDelete = { onDeleteGeofence(geofence.id) }
            )
        }
    }
}

@Composable
fun GeofenceItem(
    geofence: GeofenceLocation,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = geofence.id,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Lat: ${geofence.latitude}, Lng: ${geofence.longitude}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Radius: ${geofence.radius}m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun AddGeofenceDialog(
    onDismiss: () -> Unit,
    onGeofenceAdded: (GeofenceLocation) -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var latitudeInput by remember { mutableStateOf("") }
    var longitudeInput by remember { mutableStateOf("") }
    var radiusInput by remember { mutableStateOf("100") }

    var nameError by remember { mutableStateOf(false) }
    var latitudeError by remember { mutableStateOf(false) }
    var longitudeError by remember { mutableStateOf(false) }
    var radiusError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Geofence") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // Geofence Name
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        nameError = it.isBlank()
                    },
                    label = { Text("Geofence Name") },
                    isError = nameError,
                    supportingText = { if (nameError) Text("Name cannot be empty") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Latitude
                OutlinedTextField(
                    value = latitudeInput,
                    onValueChange = {
                        latitudeInput = it
                        latitudeError = it.toDoubleOrNull() == null
                    },
                    label = { Text("Latitude") },
                    isError = latitudeError,
                    supportingText = { if (latitudeError) Text("Enter a valid number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Longitude
                OutlinedTextField(
                    value = longitudeInput,
                    onValueChange = {
                        longitudeInput = it
                        longitudeError = it.toDoubleOrNull() == null
                    },
                    label = { Text("Longitude") },
                    isError = longitudeError,
                    supportingText = { if (longitudeError) Text("Enter a valid number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Radius
                OutlinedTextField(
                    value = radiusInput,
                    onValueChange = {
                        radiusInput = it
                        radiusError = it.toFloatOrNull() == null || it.toFloatOrNull() ?: 0f <= 0f
                    },
                    label = { Text("Radius (meters)") },
                    isError = radiusError,
                    supportingText = { if (radiusError) Text("Enter a positive number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Validate inputs
                    nameError = nameInput.isBlank()
                    latitudeError = latitudeInput.toDoubleOrNull() == null
                    longitudeError = longitudeInput.toDoubleOrNull() == null
                    radiusError = radiusInput.toFloatOrNull() == null || radiusInput.toFloatOrNull() ?: 0f <= 0f

                    if (!nameError && !latitudeError && !longitudeError && !radiusError) {
