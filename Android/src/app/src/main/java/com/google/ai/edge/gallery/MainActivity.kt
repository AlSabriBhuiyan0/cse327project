package com.google.ai.edge.gallery

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.ai.edge.gallery.ui.theme.GalleryTheme
import com.google.firebase.analytics.FirebaseAnalytics
// Removed Firebase KTX imports to avoid dependency on analytics-ktx
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  private var firebaseAnalytics: FirebaseAnalytics? = null

  // Inject GeofenceManager
  @Inject
  lateinit var geofenceManager: GeofenceManager

  // Location permission helper
  private lateinit var locationPermissionHelper: LocationPermissionHelper

  override fun onCreate(savedInstanceState: Bundle?) {
    firebaseAnalytics =
      runCatching { FirebaseAnalytics.getInstance(this) }
        .onFailure { exception ->
          // Firebase Analytics can throw an exception if google-services is not set up, e.g.,
          // missing google-services.json.
          Log.w(TAG, "Firebase Analytics is not available", exception)
        }
        .getOrNull()

    installSplashScreen()

    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // Fix for three-button nav not properly going edge-to-edge.
      // See: https://issuetracker.google.com/issues/298296168
      window.isNavigationBarContrastEnforced = false
    }

    // Initialize location permission helper
    locationPermissionHelper = LocationPermissionHelper(this)

    setContent { GalleryTheme { Surface(modifier = Modifier.fillMaxSize()) { GalleryApp() } } }
    // Keep the screen on while the app is running for better demo experience.
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    // Initialize geofencing when the activity is created
    setupGeofencing()

    // Observe geofence events
    observeGeofenceEvents()
  }

  /**
   * Sets up geofencing by requesting permissions and registering geofence locations
   */
  private fun setupGeofencing() {
    // Request location permissions first
    if (locationPermissionHelper.requestLocationPermissions()) {
      // Permissions are already granted, register geofences
      registerSampleGeofences()
    }
    // If permissions aren't granted, the permission flow will continue in onResume
  }

  /**
   * Registers sample geofence locations for demonstration
   * In a real app, you would get these locations from your database or user preferences
   */
  private fun registerSampleGeofences() {
    // Example locations - replace with actual locations relevant to your app
    val geofenceLocations = getGeofenceLocations()

    // Register the geofences
    addGeofences(geofenceLocations)
  }

  /**
   * Gets a list of geofence locations to monitor
   * This could be replaced with data from a database, API, or user preferences
   *
   * @return List of GeofenceLocation objects
   */
  private fun getGeofenceLocations(): List<GeofenceLocation> {
    // In a real app, you would fetch this data from a database or API
    return listOf(
      GeofenceLocation(
        id = "university_campus",
        latitude = 23.8175, // Example coordinates for North South University
        longitude = 90.4272,
        radius = 200f // 200 meters
      ),
      GeofenceLocation(
        id = "library",
        latitude = 23.8165,
        longitude = 90.4260,
        radius = 50f // 50 meters
      ),
      GeofenceLocation(
        id = "cafeteria",
        latitude = 23.8170,
        longitude = 90.4265,
        radius = 30f // 30 meters
      )
    )
  }

  /**
   * Adds multiple geofences for monitoring
   *
   * @param locations List of geofence locations to monitor
   */
  private fun addGeofences(locations: List<GeofenceLocation>) {
    if (locations.isEmpty()) {
      Log.d(TAG, "No geofence locations provided")
      return
    }

    Log.d(TAG, "Adding ${locations.size} geofences")
    geofenceManager.registerGeofences(locations)
  }

  override fun onResume() {
    super.onResume()
    // Check permissions again in case the user returned from settings
    if (locationPermissionHelper.requestLocationPermissions()) {
      // Permissions granted, register geofences if needed
      registerSampleGeofences()
    }
  }

  /**
   * Observes geofence events from the GeofenceRepository
   */
  private fun observeGeofenceEvents() {
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        GeofenceRepository.lastEnteredGeofenceId.collectLatest { geofenceId ->
          if (geofenceId != null) {
            // Display a toast message when a geofence is entered
            val message = "Entered geofence: $geofenceId"
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            Log.d(TAG, message)
          }
        }
      }
    }
  }

  companion object {
    private const val TAG = "AGMainActivity"
  }
}
