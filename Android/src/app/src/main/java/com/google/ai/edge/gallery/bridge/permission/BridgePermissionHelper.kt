package com.google.ai.edge.gallery.bridge.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Helper class to manage permissions required for the Telegram-Gmail bridge
 */
class BridgePermissionHelper(private val activity: FragmentActivity) {
    
    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.POST_NOTIFICATIONS
        )
        
        /**
         * Checks if all required permissions are granted
         */
        fun hasAllPermissions(context: Context): Boolean {
            return REQUIRED_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
    
    // Multiple permissions launcher
    private val requestMultiplePermissionsLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* Result handled by caller */ }

    /**
     * Request all necessary permissions for the bridge feature
     * 
     * @return true if all permissions are already granted, false otherwise
     */
    fun requestAllPermissions(): Boolean {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (missingPermissions.isEmpty()) {
            return true
        }
        
        requestMultiplePermissionsLauncher.launch(missingPermissions)
        return false
    }
    
    /**
     * Check if we should show the rationale for a specific permission
     */
    fun shouldShowRationale(permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
    
    /**
     * Open app settings screen to allow the user to grant permissions manually
     */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }
}

/**
 * Composable that handles permission requests for the bridge feature
 */
@Composable
fun BridgePermissionsHandler(
    context: Context,
    permissionHelper: BridgePermissionHelper,
    onPermissionsGranted: () -> Unit
) {
    var showRationaleDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // Check permissions when the composable is first launched
    LaunchedEffect(Unit) {
        if (BridgePermissionHelper.hasAllPermissions(context)) {
            onPermissionsGranted()
        } else {
            val hasPermissions = permissionHelper.requestAllPermissions()
            if (hasPermissions) {
                onPermissionsGranted()
            }
        }
    }
    
    // Rationale dialog
    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showRationaleDialog = false },
            title = { Text("Permissions Required") },
            text = { Text("The bridge feature needs access to contacts and notifications to function properly. Please grant these permissions to continue.") },
            confirmButton = {
                Button(
                    onClick = {
                        showRationaleDialog = false
                        permissionHelper.requestAllPermissions()
                    }
                ) {
                    Text("Grant Permissions")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationaleDialog = false }) {
                    Text("Not Now")
                }
            }
        )
    }
    
    // Settings dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Permissions Required") },
            text = { Text("The bridge feature needs access to contacts and notifications. Please enable these permissions in app settings.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSettingsDialog = false
                        permissionHelper.openAppSettings()
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Not Now")
                }
            }
        )
    }
}
