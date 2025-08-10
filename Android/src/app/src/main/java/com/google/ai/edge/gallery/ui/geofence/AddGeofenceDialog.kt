package com.google.ai.edge.gallery.ui.geofence

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.model.GeofenceLocation
import com.google.android.gms.maps.model.LatLng
import java.util.*

@Composable
fun AddGeofenceDialog(
    currentPosition: LatLng?,
    onDismiss: () -> Unit,
    onConfirm: (GeofenceLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("100") }
    var includeEnter by remember { mutableStateOf(true) }
    var includeExit by remember { mutableStateOf(true) }
    var includeDwell by remember { mutableStateOf(true) }
    
    // Pre-fill with current position if available
    val latitude = remember(currentPosition) { currentPosition?.latitude?.toString() ?: "0.0" }
    val longitude = remember(currentPosition) { currentPosition?.longitude?.toString() ?: "0.0" }
    
    val isFormValid = name.isNotBlank() && 
                     radius.toFloatOrNull()?.let { it > 0 } == true &&
                     (includeEnter || includeExit || includeDwell)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Add Geofence",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Location Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { /* Read-only */ },
                        label = { Text("Latitude") },
                        modifier = Modifier.weight(1f),
                        readOnly = true,
                        leadingIcon = { 
                            Icon(
                                Icons.Default.LocationOn, 
                                contentDescription = null
                            ) 
                        }
                    )
                    
                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { /* Read-only */ },
                        label = { Text("Longitude") },
                        modifier = Modifier.weight(1f),
                        readOnly = true
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = radius,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.toFloatOrNull() != null) {
                            radius = newValue
                        }
                    },
                    label = { Text("Radius (meters)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Trigger on:",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = includeEnter,
                            onCheckedChange = { includeEnter = it }
                        )
                        Text("Enter")
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = includeExit,
                            onCheckedChange = { includeExit = it }
                        )
                        Text("Exit")
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = includeDwell,
                            onCheckedChange = { includeDwell = it }
                        )
                        Text("Dwell")
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            if (isFormValid) {
                                val transitionTypes = mutableListOf<Int>()
                                if (includeEnter) transitionTypes.add(android.location.Geofence.GEOFENCE_TRANSITION_ENTER)
                                if (includeExit) transitionTypes.add(android.location.Geofence.GEOFENCE_TRANSITION_EXIT)
                                if (includeDwell) transitionTypes.add(android.location.Geofence.GEOFENCE_TRANSITION_DWELL)
                                
                                val geofence = GeofenceLocation(
                                    id = UUID.randomUUID().toString(),
                                    name = name,
                                    latitude = latitude.toDouble(),
                                    longitude = longitude.toDouble(),
                                    radius = radius.toFloat(),
                                    transitionTypes = transitionTypes.fold(0) { acc, type -> acc or type }
                                )
                                onConfirm(geofence)
                                onDismiss()
                            }
                        },
                        enabled = isFormValid
                    ) {
                        Text(stringResource(R.string.add))
                    }
                }
            }
        }
    }
}
