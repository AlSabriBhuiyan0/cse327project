package com.google.ai.edge.gallery.camera

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.graphics.toRectF
import com.google.ai.edge.gallery.ml.DetectedObject
import com.google.ai.edge.gallery.ml.TFLiteAnalyzer
import com.google.ai.edge.gallery.util.ImageProcessingUtils
import com.google.ai.edge.gallery.camera.optimization.CameraPerformanceOptimizer
import com.google.ai.edge.gallery.util.BitmapPool
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

/**
 * A pipeline for processing camera frames and performing object detection.
 * Handles frame preprocessing, model inference, and post-processing.
 */
typealias FrameRateLimiter = () -> Boolean

/**
 * A pipeline for processing camera frames and performing object detection with performance optimizations.
 * Handles frame preprocessing, model inference, and post-processing with configurable frame rate limiting.
 *
 * @property tfliteAnalyzer The TFLite model analyzer for object detection
 * @property targetWidth Target width for model input
 * @property targetHeight Target height for model input
 * @property analysisIntervalMs Minimum time between frame processing (ms)
 * @property maxResults Maximum number of detection results to return
 * @property minConfidence Minimum confidence threshold for detections
 * @property iouThreshold IOU threshold for non-maximum suppression
 * @property frameRateLimiter Optional frame rate limiter function (returns true if frame should be processed)
 */
class FrameAnalysisPipeline @JvmOverloads constructor(
    private val tfliteAnalyzer: TFLiteAnalyzer,
    val targetWidth: Int = 300,
    val targetHeight: Int = 300,
    private val analysisIntervalMs: Long = 300,
    private val maxResults: Int = 5,
    private val minConfidence: Float = 0.5f,
    private val iouThreshold: Float = 0.5f,
    private val frameRateLimiter: FrameRateLimiter? = null
) : ImageAnalysis.Analyzer, CoroutineScope {
    
    private val tag = "FrameAnalysisPipeline"
    
    // Coroutine scope for background processing
    private val job = Job()
    override val coroutineContext: CoroutineContext = Dispatchers.Default + job
    
    // State flows for analysis results and processing status
    private val _analysisResults = MutableStateFlow<List<DetectedObject>>(emptyList())
    val analysisResults: StateFlow<List<DetectedObject>> = _analysisResults
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing
    
    private val _lastProcessingTimeMs = MutableStateFlow(0L)
    val lastProcessingTimeMs: StateFlow<Long> = _lastProcessingTimeMs
    
    // Frame processing state
    private val lastAnalysisTime = AtomicLong(0L)
    private val isProcessingFrame = AtomicBoolean(false)
    private var processingJob: Job? = null
    
    // Thread pool for parallel processing
    private val frameProcessingExecutor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceAtLeast(2).coerceAtMost(4)
    ) { r ->
        Thread(r, "FrameProcessor-${System.currentTimeMillis() % 1000}").apply {
            priority = Thread.NORM_PRIORITY - 1 // Lower priority to avoid jank
        }
    }
    
    // Bitmap pool for recycling bitmaps
    private val bitmapPool = BitmapPool.getInstance()
    
    // Buffer for the current frame (safely published)
    @Volatile
    private var currentFrame: ImageProxy? = null
    
    @Volatile
    private var currentBitmap: Bitmap? = null
    
    /**
     * Analyzes the given image frame with performance optimizations.
     * Uses frame rate limiting and proper resource management.
     */
    override fun analyze(image: ImageProxy) {
        try {
            // Check frame rate limiter if provided
            if (frameRateLimiter != null && !frameRateLimiter()) {
                image.close()
                return
            }
            
            // Skip if already processing or not enough time has passed
            if (isProcessingFrame.get() || !shouldProcessFrame()) {
                image.close()
                return
            }
            
            // Close the previous frame
            currentFrame?.close()
            currentFrame = image
            
            // Process the frame in the background
            processFrame(image)
        } catch (e: Exception) {
            Log.e(tag, "Error in frame analysis", e)
            try {
                image.close()
            } catch (closeError: Exception) {
                Log.e(tag, "Error closing image", closeError)
            }
        }
    }
    
    /**
     * Determines if the current frame should be processed based on timing and system load.
     */
    private fun shouldProcessFrame(): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastFrame = currentTime - lastAnalysisTime.get()
        
        // Check if enough time has passed since last frame
        if (timeSinceLastFrame < analysisIntervalMs) {
            return false
        }
        
        // Update the last analysis time optimistically
        return lastAnalysisTime.compareAndSet(
            lastAnalysisTime.get(),
            currentTime - (timeSinceLastFrame % analysisIntervalMs)
        )
    }
    
    /**
     * Processes the frame in the background with performance optimizations.
     * Uses a thread pool for parallel processing and recycles resources properly.
     */
    private fun processFrame(image: ImageProxy) {
        // Skip if already processing
        if (!isProcessingFrame.compareAndSet(false, true)) {
            return
        }
        
        // Cancel any existing processing job
        processingJob?.cancel()
        
        processingJob = launch(Dispatchers.IO + NonCancellable) {
            try {
                _isProcessing.value = true
                val startTime = System.currentTimeMillis()
                
                // Convert ImageProxy to Bitmap using the bitmap pool
                val bitmap = ImageProcessingUtils.imageProxyToBitmap(image)
                
                // Update the current bitmap (for visualization)
                currentBitmap?.releaseToPool()
                currentBitmap = bitmap
                
                // Process the frame
                val results = processImage(bitmap)
                
                // Update the results on the main thread
                withContext(Dispatchers.Main) {
                    // Update timing information
                    val processingTime = System.currentTimeMillis() - startTime
                    _lastProcessingTimeMs.value = processingTime
                    
                    // Log performance metrics
                    if (Log.isLoggable(tag, Log.DEBUG)) {
                        Log.d(tag, "Frame processed in ${processingTime}ms")
                    }
                    
                    _analysisResults.value = results
                    lastAnalysisTime = System.currentTimeMillis()
                }
                
            } catch (e: Exception) {
                Log.e(tag, "Error processing frame", e)
            } finally {
                // Ensure resources are released
                try {
                    if (!image.isClosed) {
                        image.close()
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error closing image", e)
                } finally {
                    _isProcessing.value = false
                    isProcessingFrame.set(false)
                }
            }
        }
    }
    
    /**
     * Processes the image and returns the detection results.
     */
    private fun processImage(bitmap: Bitmap): List<DetectedObject> {
        // Preprocess the image
        val processedBitmap = ImageProcessingUtils.preprocessImage(
            bitmap,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )
        
        // Run object detection
        val detections = tfliteAnalyzer.analyze(processedBitmap)
        
        // Filter and sort results
        return detections
            .filter { it.confidence >= minConfidence }
            .sortedByDescending { it.confidence }
            .take(maxResults)
    }
    
    /**
     * Maps the detection coordinates from the model output to the original image coordinates.
     */
    fun mapToOriginalCoordinates(
        detection: DetectedObject,
        imageWidth: Int,
        imageHeight: Int
    ): RectF {
        val scaleX = imageWidth.toFloat() / targetWidth
        val scaleY = imageHeight.toFloat() / targetHeight
        
        return RectF(
            detection.boundingBox.left * scaleX,
            detection.boundingBox.top * scaleY,
            detection.boundingBox.right * scaleX,
            detection.boundingBox.bottom * scaleY
        )
    }
    
    /**
     * Gets the current frame bitmap for visualization.
     */
    fun getCurrentFrame(): Bitmap? {
        return currentBitmap
    }
    
    /**
     * Releases all resources used by the pipeline.
     * This method is idempotent and thread-safe.
     */
    fun release() {
        try {
            // Cancel any ongoing processing
            processingJob?.cancel()
            job.cancel()
            
            // Shutdown the executor
            frameProcessingExecutor.shutdownNow()
            
            // Clean up resources
            val frameToClose = currentFrame
            if (frameToClose != null && !frameToClose.isClosed) {
                try {
                    frameToClose.close()
                } catch (e: Exception) {
                    Log.e(tag, "Error closing frame", e)
                }
            }
            
            // Recycle bitmaps through the pool
            currentBitmap?.releaseToPool()
            
            // Clear references
            currentFrame = null
            currentBitmap = null
            
            // Clear analysis results
            _analysisResults.value = emptyList()
            _isProcessing.value = false
            
            Log.d(tag, "Frame analysis pipeline released")
            
        } catch (e: Exception) {
            Log.e(tag, "Error releasing pipeline", e)
        }
    }
    
    companion object {
        // Default configuration
        const val DEFAULT_TARGET_SIZE = 300
        const val DEFAULT_ANALYSIS_INTERVAL_MS = 300L
        const val DEFAULT_MAX_RESULTS = 5
        const val DEFAULT_MIN_CONFIDENCE = 0.5f
        const val DEFAULT_IOU_THRESHOLD = 0.5f
    }
}
