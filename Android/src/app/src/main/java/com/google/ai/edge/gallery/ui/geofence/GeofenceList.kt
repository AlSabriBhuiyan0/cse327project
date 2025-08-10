package com.google.ai.edge.gallery.ui.geofence

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.model.GeofenceLocation
import com.google.android.gms.maps.model.LatLng

/**
 * Displays a list of geofences with options to manage them.
 */
@Composable
fun GeofenceList(
    geofences: List<GeofenceLocation>,
    onGeofenceSelected: (GeofenceLocation) -> Unit,
    onDeleteGeofence: (GeofenceLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    if (geofences.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No geofences added yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(geofences, key = { it.id }) { geofence ->
                GeofenceItem(
                    geofence = geofence,
                    onSelect = { onGeofenceSelected(geofence) },
                    onDelete = { onDeleteGeofence(geofence) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeofenceItem(
    geofence: GeofenceLocation,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = geofence.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${String.format("%.6f", geofence.latitude)}, ${String.format("%.6f", geofence.longitude)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Radius: ${geofence.radius.toInt()}m",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_geofence),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun GeofenceControls(
    isGeofencingActive: Boolean,
    onStartGeofencing: () -> Unit,
    onStopGeofencing: () -> Unit,
    onAddGeofence: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Button(onClick = if (isGeofencingActive) onStopGeofencing else onStartGeofencing) {
            Text(if (isGeofencingActive) "Stop Monitoring" else "Start Monitoring")
        }
        
        Button(onClick = onAddGeofence) {
            Text("Add Geofence")
        }
    }
}

@Composable
fun GeofenceStatusBanner(
    status: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    status?.let { message ->
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 4.dp,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}
