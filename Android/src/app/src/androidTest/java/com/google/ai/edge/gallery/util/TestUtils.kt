package com.google.ai.edge.gallery.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.uiautomator.UiDevice

class TestUtils {
    
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val device = UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
    
    fun grantCameraPermissions() {
        grantPermissions(Manifest.permission.CAMERA)
    }
    
    fun grantLocationPermissions() {
        grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
    
    fun revokeCameraPermissions() {
        revokePermissions(Manifest.permission.CAMERA)
    }
    
    fun revokeLocationPermissions() {
        revokePermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
    
    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun grantPermissions(vararg permissions: String) {
        val command = "pm grant ${context.packageName} ${permissions.joinToString(" ")}"
        device.executeShellCommand(command)
    }
    
    private fun revokePermissions(vararg permissions: String) {
        val command = "pm revoke ${context.packageName} ${permissions.joinToString(" ")}"
        device.executeShellCommand(command)
    }
    
    companion object {
        const val TEST_TIMEOUT = 5000L
    }
}
