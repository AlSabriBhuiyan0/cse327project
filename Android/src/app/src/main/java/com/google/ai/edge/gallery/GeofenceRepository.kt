package com.google.ai.edge.gallery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton repository for sharing geofencing data across components
 */
object GeofenceRepository {
    // StateFlow to hold the ID of the most recently entered geofence
    private val _lastEnteredGeofenceId = MutableStateFlow<String?>(null)
    val lastEnteredGeofenceId: StateFlow<String?> = _lastEnteredGeofenceId.asStateFlow()

    // StateFlow to hold the list of user-defined geofences
    private val _userGeofences = MutableStateFlow<List<GeofenceLocation>>(emptyList())
    val userGeofences: StateFlow<List<GeofenceLocation>> = _userGeofences.asStateFlow()

    /**
     * Updates the last entered geofence ID
     *
     * @param geofenceId The ID of the geofence that was entered
     */
    fun updateLastEnteredGeofence(geofenceId: String) {
        _lastEnteredGeofenceId.value = geofenceId
    }

    /**
     * Adds a new geofence to the user-defined list
     *
     * @param geofence The geofence location to add
     * @return True if added successfully, false if a geofence with the same ID already exists
     */
    fun addGeofence(geofence: GeofenceLocation): Boolean {
        val currentList = _userGeofences.value

        // Check if a geofence with this ID already exists
        if (currentList.any { it.id == geofence.id }) {
            return false
        }

        // Add the new geofence to the list
        _userGeofences.value = currentList + geofence
        return true
    }

    /**
     * Removes a geofence from the user-defined list by ID
     *
     * @param geofenceId The ID of the geofence to remove
     * @return True if removed successfully, false if not found
     */
    fun removeGeofence(geofenceId: String): Boolean {
        val currentList = _userGeofences.value
        val newList = currentList.filter { it.id != geofenceId }

        if (newList.size < currentList.size) {
            _userGeofences.value = newList
            return true
        }

        return false
    }

    /**
     * Updates all geofences at once
     *
     * @param geofences The new list of geofences
     */
    fun updateGeofences(geofences: List<GeofenceLocation>) {
        _userGeofences.value = geofences
    }
}
