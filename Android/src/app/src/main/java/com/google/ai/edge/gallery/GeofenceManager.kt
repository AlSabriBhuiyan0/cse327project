package com.google.ai.edge.gallery

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager class for handling geofence operations
 */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "GeofenceManager"

    // GeofencingClient - the main entry point for interacting with the geofencing APIs
    private val geofencingClient: GeofencingClient by lazy {
        LocationServices.getGeofencingClient(context)
    }

    // PendingIntent used for geofence transitions
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Registers a list of geofence locations for monitoring
     *
     * @param locations List of GeofenceLocation objects to monitor
     * @return Boolean indicating if registration was attempted (permissions are granted)
     */
    fun registerGeofences(locations: List<GeofenceLocation>): Boolean {
        // Check for location permissions first
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Missing ACCESS_FINE_LOCATION permission")
            return false
        }

        // If we have no locations to monitor, return early
        if (locations.isEmpty()) {
            Log.w(TAG, "No locations provided for geofencing")
            return false
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
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }

        // Create the geofencing request
        val geofencingRequest = GeofencingRequest.Builder().apply {
            // Trigger when the device enters the geofence
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            addGeofences(geofenceList)
        }.build()

        // Add the geofences
        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully added ${locations.size} geofences")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to add geofences: ${e.message}")
            }

        return true
    }

    /**
     * Removes all active geofences
     */
    fun removeGeofences() {
        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully removed geofences")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to remove geofences: ${e.message}")
            }
    }
}
