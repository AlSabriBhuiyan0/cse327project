package com.google.ai.edge.gallery.camera

import android.content.Context
import android.graphics.Bitmap
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
import com.google.ai.edge.gallery.util.ImageProcessingUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Controller class that manages the camera and its use cases.
 */
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) {
    private val tag = "CameraController"
    
    // Camera state
    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Initializing)
    val cameraState: StateFlow<CameraState> = _cameraState
    
    // Camera control
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    
    // Camera configuration
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var targetResolution = Size(1920, 1080) // Default to 1080p
    
    /**
     * Initializes the camera and sets up the preview.
     */
    suspend fun initialize() = withContext(Dispatchers.Main) {
        _cameraState.value = CameraState.Initializing
        
        try {
            // Get the camera provider
            val cameraProvider = getCameraProvider()
            this@CameraController.cameraProvider = cameraProvider
            
            // Set up the preview use case
            val preview = Preview.Builder()
                .setTargetResolution(targetResolution)
                .build()
            
            // Set up the image capture use case
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetResolution(targetResolution)
                .build()
            
            // Set up the image analysis use case
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setTargetResolution(targetResolution)
                .build()
            
            // Save references to the use cases
            this@CameraController.preview = preview
            this@CameraController.imageCapture = imageCapture
            this@CameraController.imageAnalysis = imageAnalysis
            
            // Set up the camera selector
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()
            
            // Unbind any bound use cases before rebinding
            cameraProvider.unbindAll()
            
            // Bind use cases to the lifecycle
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalysis
            )
            
            _cameraState.value = CameraState.Ready
            Log.d(tag, "Camera initialized successfully")
            
        } catch (e: Exception) {
            val error = "Failed to initialize camera: ${e.message}"
            Log.e(tag, error, e)
            _cameraState.value = CameraState.Error(error)
            throw e
        }
    }
    
    /**
     * Sets up the preview surface.
     */
    fun setupPreview(previewView: PreviewView) {
        val preview = preview ?: return
        preview.setSurfaceProvider(previewView.surfaceProvider)
    }
    
    /**
     * Captures an image and returns it as a Bitmap.
     */
    suspend fun captureImage(): Bitmap? = withContext(Dispatchers.IO) {
        val imageCapture = imageCapture ?: return@withContext null
        
        try {
            // Create a file to save the image
            val photoFile = createTempFile(
                "IMG_", 
                ".jpg", 
                context.getExternalFilesDir("pictures")
            )
            
            // Set up the output file options
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
            
            // Capture the image
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        Log.d(tag, "Image captured successfully: ${photoFile.absolutePath}")
                    }
                    
                    override fun onError(exception: ImageCaptureException) {
                        Log.e(tag, "Error capturing image", exception)
                    }
                }
            )
            
            // Load the captured image as a Bitmap
            val bitmap = ImageProcessingUtils.loadBitmapFromFile(photoFile.absolutePath)
            
            // Clean up the temporary file
            photoFile.delete()
            
            return@withContext bitmap
            
        } catch (e: Exception) {
            Log.e(tag, "Error capturing image", e)
            return@withContext null
        }
    }
    
    /**
     * Toggles between the front and back camera.
     */
    suspend fun toggleCamera() = withContext(Dispatchers.Main) {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        
        // Reinitialize the camera with the new lens facing
        initialize()
    }
    
    /**
     * Sets the target resolution for the camera.
     */
    fun setTargetResolution(width: Int, height: Int) {
        targetResolution = Size(width, height)
        // Reinitialize the camera with the new resolution
        initialize()
    }
    
    /**
     * Gets the available camera resolutions.
     */
    fun getAvailableResolutions(): List<Size> {
        val cameraProvider = cameraProvider ?: return emptyList()
        val cameraInfo = cameraProvider.availableCameraInfos.firstOrNull {
            it.lensFacing == lensFacing
        } ?: return emptyList()
        
        return when (cameraInfo) {
            is Camera2CameraInfo -> {
                val map = cameraInfo.getCameraCharacteristic(
                    android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                )
                map?.getOutputSizes(android.graphics.ImageFormat.YUV_420_888)?.toList() ?: emptyList()
            }
            else -> emptyList()
        }
    }
    
    /**
     * Gets the current camera resolution.
     */
    fun getCurrentResolution(): Size {
        return targetResolution
    }
    
    /**
     * Releases all resources used by the camera.
     */
    fun release() {
        cameraProvider?.unbindAll()
        camera = null
        preview = null
        imageCapture = null
        imageAnalysis = null
        _cameraState.value = CameraState.Closed
    }
    
    /**
     * Gets the camera provider, suspending until it's available.
     */
    private suspend fun getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(context).also { future ->
            future.addListener(
                { continuation.resume(future.get()) },
                ContextCompat.getMainExecutor(context)
            )
        }
    }
    
    /**
     * Creates a temporary file for image capture.
     */
    private fun createTempFile(prefix: String, suffix: String, directory: java.io.File?): java.io.File {
        return java.io.File.createTempFile(
            "${prefix}_${System.currentTimeMillis()}",
            suffix,
            directory
        )
    }
}

/**
 * Represents the current state of the camera.
 */
sealed class CameraState {
    object Initializing : CameraState()
    object Ready : CameraState()
    object Closed : CameraState()
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
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    
    // Set up the camera when the composable is first composed
    LaunchedEffect(Unit) {
        val cameraController = CameraController(context, lifecycleOwner)
        try {
            cameraController.initialize()
            cameraController.setupPreview(previewView)
            onCameraInitialized()
        } catch (e: Exception) {
            Log.e("CameraPreview", "Failed to initialize camera", e)
        }
    }
    
    // Add the PreviewView to the composition
    AndroidView(
        factory = { previewView },
        modifier = modifier,
        update = { view ->
            // Update the view if needed
        }
    )
}
