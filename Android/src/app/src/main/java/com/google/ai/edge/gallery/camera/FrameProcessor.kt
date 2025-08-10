package com.google.ai.edge.gallery.camera

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.ai.edge.gallery.ml.DetectedObject
import com.google.ai.edge.gallery.util.ImageProcessingUtils
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

/**
 * Processes camera frames and performs object detection.
 */
class FrameProcessor(
    private val onObjectsDetected: (List<DetectedObject>) -> Unit,
    private val onError: (Exception) -> Unit = {},
    private val analysisIntervalMs: Long = 500 // Process a frame every 500ms by default
) : ImageAnalysis.Analyzer, CoroutineScope {
    
    private val job = Job()
    override val coroutineContext: CoroutineContext = Dispatchers.Default + job
    
    private val executor = Executors.newSingleThreadExecutor()
    private var lastAnalysisTime = 0L
    
    // Frame processing state
    private var isProcessing = false
    private var lastFrame: ImageProxy? = null
    private var lastFrameBitmap: Bitmap? = null
    
    /**
     * Processes the image from the camera.
     */
    override fun analyze(imageProxy: ImageProxy) {
        // Skip if we're already processing
        if (isProcessing || !shouldProcessFrame()) {
            imageProxy.close()
            return
        }
        
        // Close the previous frame if it exists
        lastFrame?.close()
        lastFrame = imageProxy
        
        // Process the frame asynchronously
        processFrame(imageProxy)
    }
    
    /**
     * Determines if we should process the current frame based on the analysis interval.
     */
    private fun shouldProcessFrame(): Boolean {
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastAnalysisTime) >= analysisIntervalMs
    }
    
    /**
     * Processes the frame in a background thread.
     */
    private fun processFrame(imageProxy: ImageProxy) {
        isProcessing = true
        lastAnalysisTime = System.currentTimeMillis()
        
        // Convert ImageProxy to Bitmap on a background thread
        launch(Dispatchers.IO) {
            try {
                // Convert to bitmap (handles rotation if needed)
                val bitmap = ImageProcessingUtils.imageProxyToBitmap(imageProxy)
                lastFrameBitmap?.recycle()
                lastFrameBitmap = bitmap
                
                // Convert to format expected by the model
                val processedBitmap = ImageProcessingUtils.preprocessImage(
                    bitmap,
                    targetWidth = 300,
                    targetHeight = 300
                )
                
                // Notify that we have a new frame for analysis
                withContext(Dispatchers.Main) {
                    onFrameAvailable(processedBitmap)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            } finally {
                isProcessing = false
                // Don't close the image here, we'll close it when we get the next frame
            }
        }
    }
    
    /**
     * Called when a new frame is available for analysis.
     * Subclasses should override this method to perform custom analysis.
     */
    protected open fun onFrameAvailable(bitmap: Bitmap) {
        // Default implementation does nothing
    }
    
    /**
     * Releases all resources used by this processor.
     */
    fun release() {
        job.cancel()
        executor.shutdown()
        lastFrame?.close()
        lastFrame = null
        lastFrameBitmap?.recycle()
        lastFrameBitmap = null
    }
}

/**
 * A frame processor that performs object detection on camera frames.
 */
class ObjectDetectionFrameProcessor(
    private val onObjectsDetected: (List<DetectedObject>) -> Unit,
    private val onError: (Exception) -> Unit = {},
    analysisIntervalMs: Long = 500,
    private val objectDetector: suspend (Bitmap) -> List<DetectedObject>
) : FrameProcessor(onObjectsDetected, onError, analysisIntervalMs) {
    
    // Track the current detection job to cancel if a new frame arrives
    private var detectionJob: Job? = null
    
    override fun onFrameAvailable(bitmap: Bitmap) {
        // Cancel any pending detection
        detectionJob?.cancel()
        
        // Start a new detection
        detectionJob = launch {
            try {
                val detections = withContext(Dispatchers.Default) {
                    objectDetector(bitmap)
                }
                
                // Notify on the main thread
                withContext(Dispatchers.Main) {
                    onObjectsDetected(detections)
                }
            } catch (e: CancellationException) {
                // Ignore cancellation
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }
    
    override fun release() {
        super.release()
        detectionJob?.cancel()
    }
}
