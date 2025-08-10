package com.google.ai.edge.gallery.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.lifecycle.LifecycleOwner
import com.google.ai.edge.gallery.ml.DetectedObject
import com.google.ai.edge.gallery.ml.TFLiteAnalyzer
import com.google.ai.edge.gallery.sensor.GyroscopeManager
import com.google.ai.edge.gallery.sensor.RotationData
import com.google.ai.edge.gallery.sensor.SensorState
import com.google.ai.edge.gallery.sensor.WifiManager
import com.google.ai.edge.gallery.sensor.WifiState
import com.google.ai.edge.gallery.util.ImageProcessingUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import com.google.ai.edge.gallery.camera.optimization.CameraPerformanceOptimizer

/**
 * A class that integrates camera, sensors, and AI model inference.
 * Manages the lifecycle of all components and provides a unified interface for the UI.
 */
@Singleton
class CameraSensorIntegration @Inject constructor(
    private val context: Context,
    private val tfliteAnalyzer: TFLiteAnalyzer,
    private val gyroscopeManager: GyroscopeManager,
    private val wifiManager: WifiManager,
    private val performanceOptimizer: CameraPerformanceOptimizer
) {
    private val tag = "CameraSensorIntegration"
    
    // Camera and analysis components
    private lateinit var cameraManager: CameraManager
    private lateinit var frameAnalysisPipeline: FrameAnalysisPipeline
    private val analysisExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "CameraAnalysis").apply {
            priority = Thread.NORM_PRIORITY - 1 // Lower priority to avoid jank
        }
    }
    
    // State flows
    private val _cameraState = MutableStateFlow<CameraState>(CameraState.INITIALIZING)
    val cameraState: StateFlow<CameraState> = _cameraState
    
    private val _analysisResults = MutableStateFlow<List<DetectedObject>>(emptyList())
    val analysisResults: StateFlow<List<DetectedObject>> = _analysisResults
    
    private val _processingTimeMs = MutableStateFlow(0L)
    val processingTimeMs: StateFlow<Long> = _processingTimeMs
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing
    
    // Combined sensor state
    val sensorState = combine(
        gyroscopeManager.rotationData,
        gyroscopeManager.sensorState,
        wifiManager.wifiState,
        ::combineSensorData
    )
    
    // Current frame (for visualization)
    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame
    
    init {
        Log.d(tag, "Initializing CameraSensorIntegration")
        initializeCamera()
        initializeSensors()
    }
    
    /**
     * Initializes the camera and frame analysis pipeline.
     */
    private fun initializeCamera() {
        try {
            // Initialize performance optimizer
            performanceOptimizer.initialize()
            
            // Create camera manager with optimized settings
            cameraManager = CameraManager(
                context = context,
                targetResolution = performanceOptimizer.selectOptimalAnalysisResolution(),
                executor = performanceOptimizer.executor
            )
            
            // Configure the frame analysis pipeline with optimized settings
            frameAnalysisPipeline = FrameAnalysisPipeline(
                tfliteAnalyzer = tfliteAnalyzer,
                targetWidth = performanceOptimizer.targetAnalysisWidth,
                targetHeight = performanceOptimizer.targetAnalysisHeight,
                analysisIntervalMs = 300, // Will be overridden by performance optimizer
                maxResults = 5,
                minConfidence = 0.5f,
                iouThreshold = 0.5f,
                frameRateLimiter = performanceOptimizer::shouldProcessFrame
            )
            
            // Observe frame analysis results
            frameAnalysisPipeline.analysisResults
                .collect { results ->
                    _analysisResults.value = results
                    _processingTimeMs.value = frameAnalysisPipeline.lastProcessingTimeMs.value
                }
            
            // Observe processing state
            frameAnalysisPipeline.isProcessing.collect { isProcessing ->
                _isProcessing.value = isProcessing
            }
            
            // Observe camera state
            cameraManager.cameraState.collect { state ->
                _cameraState.value = state
            }
            
            Log.d(tag, "Camera and analysis pipeline initialized")
            
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize camera", e)
            _cameraState.value = CameraState.ERROR(e)
        }
    }
    
    /**
     * Initializes and starts all sensors.
     */
    private fun initializeSensors() {
        try {
            gyroscopeManager.startListening()
            wifiManager.startScanning()
            Log.d(tag, "Sensors initialized")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize sensors", e)
        }
    }
    
    /**
     * Starts the camera preview and analysis.
     */
    fun startCamera(lifecycleOwner: LifecycleOwner) {
        try {
            cameraManager.startCamera(
                lifecycleOwner = lifecycleOwner,
                previewBuilder = previewBuilder,
                analysisBuilder = analysisBuilder,
                analysisExecutor = performanceOptimizer.executor,
                frameProcessor = { image ->
                    // Check if we should process this frame based on performance settings
                    if (performanceOptimizer.shouldProcessFrame()) {
                        processImage(image)
                    } else {
                        // Release the image if we're not processing it
                        image.close()
                    }
                }
            )
            
            _cameraState.value = CameraState.RUNNING
        } catch (e: Exception) {
            Log.e(tag, "Failed to start camera", e)
            _cameraState.value = CameraState.ERROR(e)
        }
    }
    
    /**
     * Stops the camera and releases resources.
     */
    fun stopCamera() {
        try {
            cameraManager.stopCamera()
            performanceOptimizer.release()
            _cameraState.value = CameraState.STOPPED
        } catch (e: Exception) {
            Log.e(tag, "Error stopping camera", e)
            _cameraState.value = CameraState.ERROR(e)
        }
    }
    
    /**
     * Toggles between the front and back camera.
     */
    fun toggleCamera() {
        try {
            cameraManager.toggleCamera()
            Log.d(tag, "Toggled camera")
        } catch (e: Exception) {
            Log.e(tag, "Failed to toggle camera", e)
            _cameraState.value = CameraState.ERROR(e)
        }
    }
    
    /**
     * Captures an image and returns it as a Bitmap.
     */
    suspend fun captureImage(): Bitmap? {
        return try {
            val imageProxy = cameraManager.takePicture()
            val bitmap = ImageProcessingUtils.imageProxyToBitmap(imageProxy)
            imageProxy.close()
            bitmap
        } catch (e: Exception) {
            Log.e(tag, "Failed to capture image", e)
            null
        }
    }
    
    /**
     * Combines data from multiple sensors into a unified state.
     */
    private fun combineSensorData(
        rotationData: RotationData,
        gyroState: SensorState,
        wifiState: WifiState
    ): CombinedSensorState {
        return CombinedSensorState(
            rotationData = rotationData,
            gyroState = gyroState,
            wifiState = wifiState
        )
    }
    
    /**
     * Releases all resources.
     */
    fun release() {
        try {
            cameraManager.release()
            gyroscopeManager.stopListening()
            wifiManager.stopScanning()
            analysisExecutor.shutdown()
            _currentFrame.value?.recycle()
            _currentFrame.value = null
            Log.d(tag, "Resources released")
        } catch (e: Exception) {
            Log.e(tag, "Error releasing resources", e)
        }
    }
    
    /**
     * Data class representing the combined state of all sensors.
     */
    data class CombinedSensorState(
        val rotationData: RotationData,
        val gyroState: SensorState,
        val wifiState: WifiState
    )
}
