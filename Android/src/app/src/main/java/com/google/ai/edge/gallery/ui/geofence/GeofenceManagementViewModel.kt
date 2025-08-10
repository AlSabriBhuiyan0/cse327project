package com.google.ai.edge.gallery.ui.geofence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.GeofenceLocation
import com.google.ai.edge.gallery.GeofenceManager
import com.google.ai.edge.gallery.GeofenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GeofenceManagementViewModel @Inject constructor(
    private val geofenceManager: GeofenceManager
) : ViewModel() {

    // UI state for the geofence management screen
    private val _uiState = MutableStateFlow<GeofenceManagementUiState>(GeofenceManagementUiState.Loading)
    val uiState: StateFlow<GeofenceManagementUiState> = _uiState.asStateFlow()

    init {
        // Collect user geofences from repository
        viewModelScope.launch {
            GeofenceRepository.userGeofences.collectLatest { geofences ->
                _uiState.value = GeofenceManagementUiState.Success(geofences)
            }
        }
    }

    /**
     * Adds a new geofence
     */
    fun addGeofence(geofence: GeofenceLocation) {
        val added = GeofenceRepository.addGeofence(geofence)
        if (added) {
            registerGeofences()
        }
    }

    /**
     * Removes a geofence by ID
     */
    fun removeGeofence(geofenceId: String) {
        val removed = GeofenceRepository.removeGeofence(geofenceId)
        if (removed) {
            registerGeofences()
        }
    }

    /**
     * Registers the current list of geofences with the system
     */
    private fun registerGeofences() {
        viewModelScope.launch {
            geofenceManager.registerGeofences(GeofenceRepository.userGeofences.value)
        }
    }
}

/**
 * UI state for the geofence management screen
 */
sealed class GeofenceManagementUiState {
    object Loading : GeofenceManagementUiState()
    data class Success(val geofences: List<GeofenceLocation>) : GeofenceManagementUiState()
    data class Error(val message: String) : GeofenceManagementUiState()
}
