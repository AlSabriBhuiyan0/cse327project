package com.google.ai.edge.gallery

/**
 * Data class representing a location for geofencing
 */
data class GeofenceLocation(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float = 100f, // Default radius of 100 meters
    val expirationMs: Long = -1 // Never expire by default
)
