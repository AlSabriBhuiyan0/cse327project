package com.google.ai.edge.gallery.geofence

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.ai.edge.gallery.model.GeofenceLocation
import com.google.android.gms.location.Geofence
import com.google.android.gms.maps.model.LatLng

object GeofenceTestUtils {
    
    /**
     * Creates a test geofence location with default values
     */
    fun createTestGeofence(
        id: String = "test_geofence_${System.currentTimeMillis()}",
        name: String = "Test Geofence",
        latitude: Double = 37.4220,
        longitude: Double = -122.0840,
        radius: Float = 100f,
        transitionTypes: Int = Geofence.GEOFENCE_TRANSITION_ENTER or 
                            Geofence.GEOFENCE_TRANSITION_EXIT or
                            Geofence.GEOFENCE_TRANSITION_DWELL
    ): GeofenceLocation {
        return GeofenceLocation(
            id = id,
            name = name,
            latitude = latitude,
            longitude = longitude,
            radius = radius,
            transitionTypes = transitionTypes
        )
    }

    /**
     * Creates a test location with the given coordinates
     */
    fun createTestLocation(provider: String, latitude: Double, longitude: Double): Location {
        return Location(provider).apply {
            this.latitude = latitude
            this.longitude = longitude
            accuracy = 5f
            time = System.currentTimeMillis()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                elapsedRealtimeNanos = System.currentTimeMillis() * 1_000_000
            }
        }
    }

    /**
     * Simulates a location update by directly setting the test provider location
     */
    fun simulateLocationUpdate(latitude: Double, longitude: Double) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        // Create a test location
        val testLocation = createTestLocation(LocationManager.GPS_PROVIDER, latitude, longitude)
        
        // Set the test provider location
        locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, testLocation)
    }
    
    /**
     * Calculates a new LatLng that is a certain distance (in meters) away from the given point
     * at the specified bearing (in degrees)
     */
    fun calculateNewPosition(origin: LatLng, distanceMeters: Double, bearingDegrees: Double): LatLng {
        val earthRadius = 6371000.0 // meters
        val bearing = Math.toRadians(bearingDegrees)
        val lat1 = Math.toRadians(origin.latitude)
        val lon1 = Math.toRadians(origin.longitude)
        
        val lat2 = Math.asin(
            Math.sin(lat1) * Math.cos(distanceMeters / earthRadius) +
            Math.cos(lat1) * Math.sin(distanceMeters / earthRadius) * Math.cos(bearing)
        )
        
        val lon2 = lon1 + Math.atan2(
            Math.sin(bearing) * Math.sin(distanceMeters / earthRadius) * Math.cos(lat1),
            Math.cos(distanceMeters / earthRadius) - Math.sin(lat1) * Math.sin(lat2)
        )
        
        return LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }
}
