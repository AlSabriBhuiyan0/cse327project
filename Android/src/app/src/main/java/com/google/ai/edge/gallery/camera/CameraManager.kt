package com.google.ai.edge.gallery.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages the camera operations and state for the application.
 * Handles camera initialization, preview, capture, and cleanup.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private val tag = "CameraManager"
    
    // Camera state
    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Initializing)
    val cameraState: StateFlow<CameraState> = _cameraState
    
    // Camera control
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService
    
    // Camera configuration
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private val previewResolution = Size(1920, 1080) // 1080p
    
    init {
        cameraExecutor = Executors.newSingleThreadExecutor()
        initializeCamera()
    }
    
    /**
     * Initialize the camera and set up the preview.
     */
    private fun initializeCamera() {
        _cameraState.value = CameraState.Initializing
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                // Camera provider is now guaranteed to be available
                cameraProvider = cameraProviderFuture.get()
                
                // Set up the preview use case
                val preview = Preview.Builder()
                    .setTargetResolution(previewResolution)
                    .build()
                
                // Set up the image capture use case
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetResolution(previewResolution)
                    .build()
                
                // Set up the image analysis use case
                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setTargetResolution(previewResolution)
                    .build()
                
                // Set up the camera selector
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()
                
                // Unbind any bound use cases before rebinding
                cameraProvider?.unbindAll()
                
                // Bind use cases to the lifecycle
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalysis
                )
                
                _cameraState.value = CameraState.Ready
                
            } catch (e: Exception) {
                Log.e(tag, "Camera initialization failed: ${e.message}")
                _cameraState.value = CameraState.Error("Failed to initialize camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    /**
     * Toggle between front and back camera.
     */
    fun toggleCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        initializeCamera()
    }
    
    /**
     * Capture an image and return the result via callback.
     */
    fun captureImage(onImageCaptured: (ByteArray) -> Unit) {
        val imageCapture = imageCapture ?: run {
            _cameraState.value = CameraState.Error("Image capture use case is not ready")
            return
        }
        
        _cameraState.value = CameraState.Capturing
        
        // Create output options
        val outputFileOptions = ImageCapture.OutputFileOptions
            .Builder()
            .build()
        
        // Set up the image capture listener
        imageCapture.takePicture(
            outputFileOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // For now, we'll just log success
                    // In a real implementation, you would process the saved image
                    _cameraState.value = CameraState.CaptureSuccess
                    // TODO: Process the saved image and call onImageCaptured with the result
                }
                
                override fun onError(exception: ImageCaptureException) {
                    val errorMsg = "Image capture failed: ${exception.message}"
                    Log.e(tag, errorMsg, exception)
                    _cameraState.value = CameraState.Error(errorMsg)
                }
            }
        )
    }
    
    /**
     * Clean up resources when the camera is no longer needed.
     */
    fun shutdown() {
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
        camera = null
        imageCapture = null
        imageAnalysis = null
        cameraProvider = null
    }
    
    /**
     * Check if the app has the required camera permissions.
     */
    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Get the camera's sensor orientation.
     */
    fun getSensorOrientation(): Int {
        return camera?.cameraInfo?.let { info ->
            val camera2Info = Camera2CameraInfo.from(info)
            camera2Info.getSensorRotationDegrees()
        } ?: 0
    }
    
    /**
     * Get the current lens facing direction.
     */
    fun getLensFacing() = lensFacing
}

/**
 * Represents the current state of the camera.
 */
sealed class CameraState {
    object Initializing : CameraState()
    object Ready : CameraState()
    object Capturing : CameraState()
    object CaptureSuccess : CameraState()
    data class Error(val message: String) : CameraState()
}

/**
 * Composable function to get the camera preview.
 * 
 * @param modifier Modifier for the preview view
 * @param onCameraInitialized Callback when the camera is initialized
 */
@Composable
fun CameraPreview(
    modifier: android.view.ViewGroup.LayoutParams = android.view.ViewGroup.LayoutParams(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.ViewGroup.LayoutParams.MATCH_PARENT
    ),
    onCameraInitialized: () -> Unit = {}
): PreviewView {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    
    DisposableEffect(Unit) {
        val cameraManager = CameraManager(context, lifecycleOwner)
        
        // Set up the preview surface provider
        previewView.layoutParams = modifier
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        
        onDispose {
            cameraManager.shutdown()
        }
    }
    
    return previewView
}
