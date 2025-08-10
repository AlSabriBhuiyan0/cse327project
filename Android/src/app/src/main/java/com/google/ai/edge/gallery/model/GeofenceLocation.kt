package com.google.ai.edge.gallery.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Data class representing a geofence location with its properties.
 * 
 * @param id Unique identifier for the geofence
 * @param name User-friendly name for the geofence location
 * @param latitude Latitude of the geofence center
 * @param longitude Longitude of the geofence center
 * @param radius Radius in meters around the center point
 * @param transitionTypes Bitmask of GEOFENCE_TRANSITION flags that this geofence monitors
 * @param loiteringDelayMs The number of milliseconds to wait before triggering GEOFENCE_TRANSITION_DWELL
 * @param expirationMs Expiration time in milliseconds relative to the geofence creation time
 * @param notificationMessage Optional custom message to show when geofence is triggered
 */
@Parcelize
data class GeofenceLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float = 100f, // Default 100 meters
    val transitionTypes: Int = Geofence.GEOFENCE_TRANSITION_ENTER or 
                             Geofence.GEOFENCE_TRANSITION_EXIT or
                             Geofence.GEOFENCE_TRANSITION_DWELL,
    val loiteringDelayMs: Int = 5 * 60 * 1000, // 5 minutes
    val expirationMs: Long = Geofence.NEVER_EXPIRE,
    val notificationMessage: String? = null
) : Parcelable {
    companion object {
        // Default radius in meters
        const val DEFAULT_RADIUS = 100f
        
        // Default loitering delay (5 minutes)
        const val DEFAULT_LOITERING_DELAY_MS = 5 * 60 * 1000
    }
}
