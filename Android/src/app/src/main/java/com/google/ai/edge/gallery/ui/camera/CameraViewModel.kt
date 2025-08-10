package com.google.ai.edge.gallery.ui.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.camera.CameraSensorIntegration
import com.google.ai.edge.gallery.camera.FrameAnalysisPipeline
import com.google.ai.edge.gallery.ml.DetectedObject
import com.google.ai.edge.gallery.sensor.WifiManager
import com.google.ai.edge.gallery.sensor.WifiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val wifiManager: WifiManager,
    private val cameraSensorIntegration: CameraSensorIntegration
) : ViewModel() {
    
    // Camera state
    private val _cameraState = cameraSensorIntegration.cameraState
    val cameraState: StateFlow<CameraSensorIntegration.CameraState> = _cameraState
    
    // Analysis results
    val analysisResults = cameraSensorIntegration.analysisResults
    val processingTimeMs = cameraSensorIntegration.processingTimeMs
    val isProcessing = cameraSensorIntegration.isProcessing
    
    // Sensor states
    val sensorState = cameraSensorIntegration.sensorState
    val currentFrame = cameraSensorIntegration.currentFrame
    
    // Camera controls
    var isCameraActive by mutableStateOf(false)
        private set
    
    var cameraLensFacing by mutableStateOf(CameraSelector.LENS_FACING_BACK)
        private set
    
    // Wifi state
    private val _wifiState = MutableStateFlow<WifiState>(WifiState.Disconnected)
    val wifiState: StateFlow<WifiState> = _wifiState
    
    private val _availableNetworks = MutableStateFlow<List<String>>(emptyList())
    val availableNetworks: StateFlow<List<String>> = _availableNetworks
    
    // Background jobs
    private var wifiScanJob: Job? = null
    private var sensorUpdateJob: Job? = null
    
    init {
        // Observe WiFi state changes
        viewModelScope.launch {
            wifiManager.wifiState.collect { state ->
                _wifiState.value = state
            }
        }
        
        // Observe available networks
        viewModelScope.launch {
            wifiManager.availableNetworks.collect { networks ->
                _availableNetworks.value = networks.map { it.ssid }
            }
        }
        
        // Observe sensor state changes
        observeSensorState()
    }
    
    /**
     * Starts the camera preview and analysis.
     */
    fun startCamera(lifecycleOwner: android.app.Activity) {
        if (isCameraActive) return
        
        try {
            cameraSensorIntegration.startCamera(lifecycleOwner)
            isCameraActive = true
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Failed to start camera", e)
        }
    }
    
    /**
     * Stops the camera and releases resources.
     */
    fun stopCamera() {
        if (!isCameraActive) return
        
        try {
            cameraSensorIntegration.stopCamera()
            isCameraActive = false
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Error stopping camera", e)
        }
    }
    
    /**
     * Toggles between front and back camera.
     */
    fun toggleCamera() {
        try {
            cameraSensorIntegration.toggleCamera()
            cameraLensFacing = if (cameraLensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Failed to toggle camera", e)
        }
    }
    
    /**
     * Captures an image and returns it as a Bitmap.
     */
    suspend fun captureImage(): Bitmap? {
        return try {
            cameraSensorIntegration.captureImage()
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Failed to capture image", e)
            null
        }
    }
    
    /**
     * Starts WiFi scanning.
     */
    fun startWifiScan() {
        wifiScanJob?.cancel()
        wifiScanJob = viewModelScope.launch {
            while (true) {
                wifiManager.startScan()
                delay(10000) // Scan every 10 seconds
            }
        }
    }
    
    /**
     * Stops WiFi scanning.
     */
    fun stopWifiScan() {
        wifiScanJob?.cancel()
        wifiScanJob = null
    }
    
    /**
     * Observes sensor state changes and updates the UI accordingly.
     */
    private fun observeSensorState() {
        sensorUpdateJob?.cancel()
        sensorUpdateJob = viewModelScope.launch {
            cameraSensorIntegration.sensorState.collect { sensorState ->
                // Handle sensor state updates
                Log.d("CameraViewModel", "Sensor state updated: $sensorState")
                
                // Example: Update UI based on sensor state
                when (sensorState.gyroState) {
                    is com.google.ai.edge.gallery.sensor.SensorState.Active -> {
                        // Gyroscope is active
                    }
                    is com.google.ai.edge.gallery.sensor.SensorState.Error -> {
                        // Handle gyroscope error
                        Log.e("CameraViewModel", "Gyroscope error: ${sensorState.gyroState.error}")
                    }
                    else -> {
                        // Gyroscope is inactive or initializing
                    }
                }
                
                // Handle WiFi state
                when (sensorState.wifiState) {
                    is WifiState.Connected -> {
                        // WiFi is connected
                    }
                    is WifiState.Disconnected -> {
                        // WiFi is disconnected
                    }
                    is WifiState.Error -> {
                        // Handle WiFi error
                        Log.e("CameraViewModel", "WiFi error: ${sensorState.wifiState.error}")
                    }
                    else -> {
                        // WiFi is scanning or initializing
                    }
                }
            }
        }
    }
                
    /**
     * Releases all resources.
     */
    override fun onCleared() {
        super.onCleared()
        
        // Cancel background jobs
        wifiScanJob?.cancel()
        sensorUpdateJob?.cancel()
        
        // Release camera and sensor resources
        if (isCameraActive) {
            stopCamera()
        }
        
        // Release WiFi resources
        stopWifiScan()
        
        // Release camera sensor integration
        cameraSensorIntegration.release()
    }
}

/**
 * Sealed class representing the state of the camera.
 */
sealed class CameraState {
    object INITIALIZING : CameraState()
    object ACTIVE : CameraState()
    object INACTIVE : CameraState()
    data class ERROR(val error: Throwable) : CameraState()
}
