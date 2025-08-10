package com.google.ai.edge.gallery

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager class for handling geofence operations including registration, monitoring, and permission handling.
 * This class integrates with the GeofenceForegroundService to ensure geofencing works in the background.
 */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "GeofenceManager"

    // State flow for tracking geofencing status
    private val _geofencingStatus = MutableStateFlow<GeofencingStatus>(GeofencingStatus.Idle)
    val geofencingStatus: StateFlow<GeofencingStatus> = _geofencingStatus.asStateFlow()

    // GeofencingClient - the main entry point for interacting with the geofencing APIs
    private val geofencingClient: GeofencingClient by lazy {
        LocationServices.getGeofencingClient(context)
    }

    // PendingIntent used for geofence transitions
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = "${context.packageName}.ACTION_GEOFENCE_EVENT"
        }
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Current list of active geofences
    private var activeGeofences = emptyList<GeofenceLocation>()

    /**
     * Starts monitoring the provided geofence locations
     * @param locations List of locations to monitor
     * @return GeofencingStatus indicating the result of the operation
     */
    fun startMonitoringGeofences(locations: List<GeofenceLocation> = emptyList()): GeofencingStatus {
        return if (hasRequiredPermissions()) {
            _geofencingStatus.value = GeofencingStatus.Starting
            
            if (locations.isNotEmpty()) {
                activeGeofences = locations
            }
            
            if (activeGeofences.isEmpty()) {
                _geofencingStatus.value = GeofencingStatus.Error("No geofences to monitor")
                return GeofencingStatus.Error("No geofences to monitor")
            }
            
            // Start the foreground service for background monitoring
            GeofenceForegroundService.startService(context)
            
            // Register the geofences
            registerGeofences(activeGeofences)
            GeofencingStatus.Started
        } else {
            val error = "Missing required location permissions"
            _geofencingStatus.value = GeofencingStatus.Error(error)
            GeofencingStatus.Error(error)
        }
    }
    
    /**
     * Stops monitoring all geofences
     */
    fun stopMonitoringGeofences() {
        removeGeofences()
        GeofenceForegroundService.stopService(context)
        _geofencingStatus.value = GeofencingStatus.Stopped
    }
    
    /**
     * Checks if the app has the required permissions for geofencing
     */
    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Gets the list of required permissions for geofencing
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /**
     * Registers a list of geofence locations for monitoring
     * @param locations List of GeofenceLocation objects to monitor
     */
    private fun registerGeofences(locations: List<GeofenceLocation>) {
        if (!hasRequiredPermissions()) {
            _geofencingStatus.value = GeofencingStatus.Error("Missing required location permissions")
            return
        }

        if (locations.isEmpty()) {
            _geofencingStatus.value = GeofencingStatus.Error("No locations provided for geofencing")
            return
        }

        // Build geofence objects from the provided locations
        val geofenceList = locations.map { location ->
            Geofence.Builder()
                .setRequestId(location.id)
                .setCircularRegion(
                    location.latitude,
                    location.longitude,
                    location.radius
                )
                .setExpirationDuration(location.expirationMs)
                .setNotificationResponsiveness(1000) // 1 second
                .setLoiteringDelay(5 * 60 * 1000) // 5 minutes
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_ENTER or
                    Geofence.GEOFENCE_TRANSITION_EXIT or
                    Geofence.GEOFENCE_TRANSITION_DWELL
                )
                .build()
        }

        // Create the geofencing request
        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofenceList)
            .build()

        // Add the geofences
        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully added ${locations.size} geofences")
                _geofencingStatus.value = GeofencingStatus.Monitoring(locations.size)
            }
            .addOnFailureListener { e ->
                val error = "Failed to add geofences: ${e.message}"
                Log.e(TAG, error)
                _geofencingStatus.value = GeofencingStatus.Error(error)
            }
    }

    /**
     * Removes all active geofences
     */
    private fun removeGeofences() {
        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully removed geofences")
                _geofencingStatus.value = GeofencingStatus.Stopped
            }
            .addOnFailureListener { e ->
                val error = "Failed to remove geofences: ${e.message}"
                Log.e(TAG, error)
                _geofencingStatus.value = GeofencingStatus.Error(error)
            }
    }
    
    /**
     * Sealed class representing the different states of geofencing
     */
    sealed class GeofencingStatus {
        object Idle : GeofencingStatus()
        object Starting : GeofencingStatus()
        data class Monitoring(val activeGeofences: Int) : GeofencingStatus()
        object Started : GeofencingStatus()
        object Stopped : GeofencingStatus()
        data class Error(val message: String) : GeofencingStatus()
    }
}
