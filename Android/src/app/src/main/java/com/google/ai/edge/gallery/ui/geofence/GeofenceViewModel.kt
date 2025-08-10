package com.google.ai.edge.gallery.ui.geofence

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.model.GeofenceLocation
import com.google.ai.edge.gallery.GeofenceManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing geofence-related UI state and interactions.
 */
@HiltViewModel
class GeofenceViewModel @Inject constructor(
    application: Application,
    private val geofenceManager: GeofenceManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GeofenceUiState())
    val uiState: StateFlow<GeofenceUiState> = _uiState.asStateFlow()

    init {
        // Observe geofencing status
        viewModelScope.launch {
            geofenceManager.geofencingStatus.collect { status ->
                _uiState.update { it.copy(geofencingStatus = status) }
            }
        }
    }

    /**
     * Starts monitoring the provided geofence locations.
     */
    fun startGeofencing(locations: List<GeofenceLocation>) {
        viewModelScope.launch {
            val status = geofenceManager.startMonitoringGeofences(locations)
            _uiState.update { it.copy(
                lastOperationStatus = when (status) {
                    is GeofenceManager.GeofencingStatus.Error -> "Error: ${status.message}"
                    else -> "Geofencing started"
                }
            )}
        }
    }

    /**
     * Stops all geofence monitoring.
     */
    fun stopGeofencing() {
        viewModelScope.launch {
            geofenceManager.stopMonitoringGeofences()
            _uiState.update { it.copy(
                lastOperationStatus = "Geofencing stopped"
            )}
        }
    }

    /**
     * Adds a new geofence location.
     */
    fun addGeofence(location: GeofenceLocation) {
        _uiState.update { currentState ->
            val updatedGeofences = currentState.geofenceLocations.toMutableList().apply {
                add(location)
            }
            currentState.copy(geofenceLocations = updatedGeofences)
        }
    }

    /**
     * Removes a geofence by its ID.
     */
    fun removeGeofence(geofenceId: String) {
        _uiState.update { currentState ->
            val updatedGeofences = currentState.geofenceLocations.filter { it.id != geofenceId }
            currentState.copy(geofenceLocations = updatedGeofences)
        }
    }

    /**
     * Updates the current user location on the map.
     */
    fun updateUserLocation(location: Location) {
        _uiState.update { it.copy(
            userLocation = LatLng(location.latitude, location.longitude)
        )}
    }

    /**
     * Updates the selected position on the map.
     */
    fun updateSelectedPosition(position: LatLng) {
        _uiState.update { it.copy(selectedPosition = position) }
    }

    /**
     * Clears any error or status messages.
     */
    fun clearStatus() {
        _uiState.update { it.copy(lastOperationStatus = null) }
    }
}

/**
 * UI state for the geofence management screen.
 */
data class GeofenceUiState(
    val geofenceLocations: List<GeofenceLocation> = emptyList(),
    val userLocation: LatLng? = null,
    val selectedPosition: LatLng? = null,
    val geofencingStatus: GeofenceManager.GeofencingStatus = GeofenceManager.GeofencingStatus.Idle,
    val lastOperationStatus: String? = null,
    val isAddingGeofence: Boolean = false
)
