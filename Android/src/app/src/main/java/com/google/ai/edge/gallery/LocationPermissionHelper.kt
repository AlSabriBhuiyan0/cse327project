package com.google.ai.edge.gallery

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Helper class for managing location permissions
 */
class LocationPermissionHelper(private val activity: FragmentActivity) {

    companion object {
        const val FINE_LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION
        const val BACKGROUND_LOCATION_PERMISSION = Manifest.permission.ACCESS_BACKGROUND_LOCATION
    }

    // Request launcher for fine location permission
    private val requestFineLocationPermissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // Fine location granted, now request background location if needed
                requestBackgroundLocationIfNeeded()
            } else {
                // Permission denied, show rationale if needed
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, FINE_LOCATION_PERMISSION)) {
                    showPermissionRationale(
                        "Location Permission Required",
                        "This app needs location permission to notify you when you enter specific areas.",
                        FINE_LOCATION_PERMISSION
                    )
                } else {
                    // User denied with "Don't ask again", send to settings
                    showSettingsPrompt()
                }
            }
        }

    // Request launcher for background location permission (Android 10+)
    private val requestBackgroundLocationPermissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (!isGranted) {
                // Permission denied, show rationale if needed
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, BACKGROUND_LOCATION_PERMISSION)) {
                    showPermissionRationale(
                        "Background Location Permission Required",
                        "This app needs background location permission to detect when you enter areas even when the app is not in use.",
                        BACKGROUND_LOCATION_PERMISSION
                    )
                } else {
                    // User denied with "Don't ask again", send to settings
                    showSettingsPrompt()
                }
            }
        }

    /**
     * Starts the permission request flow for location permissions
     * @return true if all permissions are already granted, false otherwise
     */
    fun requestLocationPermissions(): Boolean {
        // Check for fine location permission first
        if (!hasPermission(activity, FINE_LOCATION_PERMISSION)) {
            requestFineLocationPermissionLauncher.launch(FINE_LOCATION_PERMISSION)
            return false
        }

        // If fine location is granted, check for background location if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!hasPermission(activity, BACKGROUND_LOCATION_PERMISSION)) {
                requestBackgroundLocationIfNeeded()
                return false
            }
        }

        // All needed permissions are already granted
        return true
    }

    /**
     * Request background location permission for Android 10+ with explanation
     */
    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AlertDialog.Builder(activity)
                .setTitle("Background Location Required")
                .setMessage("To receive notifications when you enter tracked areas, this app needs permission to access your location in the background.")
                .setPositiveButton("Grant") { _, _ ->
                    requestBackgroundLocationPermissionLauncher.launch(BACKGROUND_LOCATION_PERMISSION)
                }
                .setNegativeButton("Not Now") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .show()
        }
    }

    /**
     * Shows a dialog explaining why the permission is needed with option to request again
     */
    private fun showPermissionRationale(title: String, message: String, permission: String) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Grant") { _, _ ->
                when (permission) {
                    FINE_LOCATION_PERMISSION -> requestFineLocationPermissionLauncher.launch(permission)
                    BACKGROUND_LOCATION_PERMISSION -> requestBackgroundLocationPermissionLauncher.launch(permission)
                }
            }
            .setNegativeButton("Not Now") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    /**
     * Shows a dialog prompting the user to go to settings to enable permissions
     */
    private fun showSettingsPrompt() {
        AlertDialog.Builder(activity)
            .setTitle("Permissions Required")
            .setMessage("Some features require location permissions that were denied. Please enable them in app settings.")
            .setPositiveButton("Settings") { _, _ ->
                // Open app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", activity.packageName, null)
                intent.data = uri
                activity.startActivity(intent)
            }
            .setNegativeButton("Not Now") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    /**
     * Check if a permission is granted
     */
    private fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}
