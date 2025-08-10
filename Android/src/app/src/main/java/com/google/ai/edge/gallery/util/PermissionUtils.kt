package com.google.ai.edge.gallery.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.google.ai.edge.gallery.R

/**
 * Checks if all the required permissions are granted.
 *
 * @param context The context to check permissions in.
 * @param permissions The list of permissions to check.
 * @return `true` if all permissions are granted, `false` otherwise.
 */
fun hasPermissions(context: Context, vararg permissions: String): Boolean {
    return permissions.all { permission ->
        val result = ContextCompat.checkSelfPermission(context, permission)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
            permission == Manifest.permission.ACCESS_BACKGROUND_LOCATION) {
            // Special handling for background location permission
            result == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            result == PackageManager.PERMISSION_GRANTED
        }
    }
}

/**
 * Enum class representing different permission groups used in the app.
 */
enum class PermissionGroup(val permissions: List<String>) {
    CAMERA(listOf(Manifest.permission.CAMERA)),
    LOCATION(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    ),
    BACKGROUND_LOCATION(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            emptyList()
        }
    ),
    SENSORS(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            listOf(Manifest.permission.BODY_SENSORS)
        } else {
            emptyList()
        }
    )
}

/**
 * Composable function that handles runtime permissions with a more flexible API.
 *
 * @param requiredPermissions List of permission groups to request.
 * @param onPermissionsGranted Callback when all permissions are granted.
 * @param onPermissionsDenied Callback when any permission is denied.
 * @param showRationale Optional callback to show custom rationale UI.
 * @param content The content to display when permissions are granted.
 */
@Composable
fun PermissionHandler(
    requiredPermissions: List<PermissionGroup> = listOf(
        PermissionGroup.CAMERA,
        PermissionGroup.LOCATION
    ),
    onPermissionsGranted: () -> Unit,
    onPermissionsDenied: (List<String>) -> Unit = {},
    showRationale: ((onConfirm: () -> Unit, onDismiss: () -> Unit) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val permissionsToRequest = remember(requiredPermissions) {
        requiredPermissions.flatMap { it.permissions }.distinct().toTypedArray()
    }
    
    var showRationaleDialog by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }
    
    // Check if we need to show rationale for any permission
    val shouldShowRationale = remember(permissionsToRequest) {
        permissionsToRequest.any { permission ->
            android.app.ActivityCompat.shouldShowRequestPermissionRationale(
                context as android.app.Activity,
                permission
            )
        }
    }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val deniedPermissions = permissionsMap.filter { !it.value }.keys.toList()
        val allGranted = deniedPermissions.isEmpty()
        
        if (allGranted) {
            onPermissionsGranted()
        } else {
            onPermissionsDenied(deniedPermissions)
        }
    }
    
    // Check permissions on first composition
    LaunchedEffect(permissionsToRequest) {
        if (hasPermissions(context, *permissionsToRequest)) {
            onPermissionsGranted()
        } else if (!permissionRequested) {
            permissionRequested = true
            if (shouldShowRationale && showRationale != null) {
                showRationaleDialog = true
            } else {
                permissionLauncher.launch(permissionsToRequest)
            }
        }
    }
    
    // Show rationale dialog if needed
    if (showRationaleDialog) {
        if (showRationale != null) {
            DisposableEffect(Unit) {
                onDispose { showRationaleDialog = false }
            }
            showRationale(
                onConfirm = {
                    showRationaleDialog = false
                    permissionLauncher.launch(permissionsToRequest)
                },
                onDismiss = {
                    showRationaleDialog = false
                    onPermissionsDenied(emptyList())
                }
            )
        } else {
            PermissionRationaleDialog(
                onDismiss = {
                    showRationaleDialog = false
                    onPermissionsDenied(emptyList())
                },
                onConfirm = {
                    showRationaleDialog = false
                    permissionLauncher.launch(permissionsToRequest)
                }
            )
        }
    }
    
    // Show content if permissions are granted
    if (hasPermissions(context, *permissionsToRequest)) {
        content()
    }
}

/**
 * Dialog that explains why the app needs certain permissions.
 */
@Composable
private fun PermissionRationaleDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permission_required)) },
        text = { Text(stringResource(R.string.camera_location_permission_rationale)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

/**
 * Helper class to manage app permissions with a more object-oriented approach.
 */
class AppPermissions(private val context: Context) {
    
    /**
     * Checks if all required permissions for a feature are granted.
     *
     * @param permissionGroups The permission groups to check.
     * @return `true` if all permissions are granted, `false` otherwise.
     */
    fun hasPermissions(vararg permissionGroups: PermissionGroup): Boolean {
        val permissions = permissionGroups.flatMap { it.permissions }.toTypedArray()
        return hasPermissions(context, *permissions)
    }
    
    /**
     * Checks if the app should show a rationale for any of the requested permissions.
     *
     * @param permissionGroups The permission groups to check.
     * @return `true` if rationale should be shown, `false` otherwise.
     */
    fun shouldShowRationale(vararg permissionGroups: PermissionGroup): Boolean {
        val activity = context as? android.app.Activity ?: return false
        return permissionGroups.any { group ->
            group.permissions.any { permission ->
                android.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            }
        }
    }
    
    /**
     * Checks if a permission is permanently denied (user checked "Don't ask again").
     *
     * @param permission The permission to check.
     * @return `true` if the permission is permanently denied, `false` otherwise.
     */
    fun isPermissionPermanentlyDenied(permission: String): Boolean {
        val activity = context as? android.app.Activity ?: return false
        return !android.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) &&
               ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Gets the list of permanently denied permissions from the given groups.
     *
     * @param permissionGroups The permission groups to check.
     * @return List of permanently denied permissions.
     */
    fun getPermanentlyDeniedPermissions(vararg permissionGroups: PermissionGroup): List<String> {
        return permissionGroups.flatMap { group ->
            group.permissions.filter { isPermissionPermanentlyDenied(it) }
        }
    }
    
    companion object {
        /**
         * Creates a launcher for requesting permissions with callbacks.
         *
         * @param onPermissionsGranted Callback when all permissions are granted.
         * @param onPermissionsDenied Callback when some permissions are denied.
         * @return A launcher that can be used to request permissions.
         */
        @Composable
        fun createPermissionLauncher(
            onPermissionsGranted: () -> Unit,
            onPermissionsDenied: (List<String>) -> Unit = {}
        ) = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissionsMap ->
            val deniedPermissions = permissionsMap.filter { !it.value }.keys.toList()
            if (deniedPermissions.isEmpty()) {
                onPermissionsGranted()
            } else {
                onPermissionsDenied(deniedPermissions)
            }
        }
    }
}
