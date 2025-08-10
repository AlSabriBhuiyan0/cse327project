package com.google.ai.edge.gallery.ui.geofence

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.model.GeofenceLocation
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceScreen(
    onBackClick: () -> Unit,
    viewModel: GeofenceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Check location permissions
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // Get last known location if permission granted
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let { viewModel.updateUserLocation(it) }
                }
            }
        }
    }

    // Check permissions on first launch
    LaunchedEffect(Unit) {
        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        if (requiredPermissions.any {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }) {
            locationPermissionLauncher.launch(requiredPermissions)
        } else {
            // Get last known location if permission already granted
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let { viewModel.updateUserLocation(it) }
            }
        }
    }

    // Handle status messages
    LaunchedEffect(uiState.lastOperationStatus) {
        if (uiState.lastOperationStatus != null) {
            // Auto-clear status after 3 seconds
            kotlinx.coroutines.delay(3000)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Geofence Manager") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            GeofenceControls(
                isGeofencingActive = uiState.geofencingStatus is GeofenceManager.GeofencingStatus.Monitoring,
                onStartGeofencing = { viewModel.startGeofencing(uiState.geofenceLocations) },
                onStopGeofencing = { viewModel.stopGeofencing() },
                onAddGeofence = {
                    // Show dialog to add new geofence
                    // This would be implemented with a dialog
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            // Map view
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(
                    uiState.userLocation ?: LatLng(0.0, 0.0),
                    15f
                )
            }

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                onMapClick = { latLng ->
                    viewModel.updateSelectedPosition(latLng)
                }
            ) {
                // Add markers for geofences
                uiState.geofenceLocations.forEach { geofence ->
                    val position = LatLng(geofence.latitude, geofence.longitude)
                    Marker(
                        state = MarkerState(position = position),
                        title = geofence.name,
                        snippet = "Radius: ${geofence.radius}m"
                    )
                    
                    // Add circle for geofence radius
                    Circle(
                        center = position,
                        radius = geofence.radius.toDouble(),
                        fillColor = android.graphics.Color.argb(64, 0, 0, 255),
                        strokeColor = android.graphics.Color.argb(255, 0, 0, 255),
                        strokeWidth = 2f
                    )
                }
                
                // Show user location
                uiState.userLocation?.let { userLoc ->
                    val userLatLng = LatLng(userLoc.latitude, userLoc.longitude)
                    Marker(
                        state = MarkerState(position = userLatLng),
                        title = "You are here",
                        icon = bitmapDescriptorFromVector(
                            context,
                            android.R.drawable.ic_menu_mylocation
                        )
                    )
                }
            }

            // Status banner
            GeofenceStatusBanner(
                status = uiState.lastOperationStatus,
                onDismiss = { viewModel.clearStatus() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun bitmapDescriptorFromVector(context: android.content.Context, vectorResId: Int) =
    com.google.maps.android.compose.bitmapDescriptor(context) {
        // Convert vector drawable to bitmap
        val drawable = ContextCompat.getDrawable(context, vectorResId)
        drawable?.let { d ->
            d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
            d.draw(it)
        }
    }
