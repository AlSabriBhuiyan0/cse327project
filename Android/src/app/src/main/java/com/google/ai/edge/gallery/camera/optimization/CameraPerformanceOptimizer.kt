package com.google.ai.edge.gallery.camera.optimization

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import com.google.ai.edge.gallery.util.BitmapPool
import com.google.ai.edge.gallery.util.FrameRateLimiter
import com.google.ai.edge.gallery.util.ImageProcessingUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Centralized class for camera performance optimizations.
 * Handles frame rate control, resolution selection, and resource management.
 */
class CameraPerformanceOptimizer(
    private val context: Context,
    private val targetFps: Int = 30,
    private val targetAnalysisWidth: Int = 640,
    private val targetAnalysisHeight: Int = 480
) {
    private val tag = "CameraOptimizer"
    
    // Thread management
    private val cameraExecutor by lazy {
        val threadCount = max(1, Runtime.getRuntime().availableProcessors() - 1)
        Executors.newFixedThreadPool(threadCount) { r ->
            Thread(r, "CameraX-optimized").apply {
                priority = Thread.NORM_PRIORITY - 1 // Lower priority to avoid jank
            }
        }
    }
    
    // Performance monitoring
    private val frameTimes = ArrayDeque<Long>()
    private val frameTimeWindow = 30 // Number of frames to average
    private var lastFrameTime = 0L
    
    // State
    private val _performanceState = MutableStateFlow<PerformanceState>(PerformanceState.Idle)
    val performanceState: StateFlow<PerformanceState> = _performanceState
    
    // Components
    private val frameRateLimiter = FrameRateLimiter(targetFps)
    private lateinit var bitmapPool: BitmapPool
    
    // Coroutine scope for async operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    /**
     * Initializes the performance optimizer.
     * Should be called when the camera is being set up.
     */
    fun initialize() {
        bitmapPool = BitmapPool.getInstance()
        Log.d(tag, "Initialized camera performance optimizer")
    }
    
    /**
     * Creates an optimized Preview use case configuration.
     */
    fun createPreviewBuilder(): Preview.Builder {
        return Preview.Builder()
            .setTargetRotation(context.display?.rotation ?: 0)
            .setDefaultResolution(selectOptimalPreviewResolution())
            .setIoExecutor(cameraExecutor)
    }
    
    /**
     * Creates an optimized ImageAnalysis use case configuration.
     */
    fun createAnalysisBuilder(): ImageAnalysis.Builder {
        return ImageAnalysis.Builder()
            .setTargetRotation(context.display?.rotation ?: 0)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setDefaultResolution(selectOptimalAnalysisResolution())
            .setIoExecutor(cameraExecutor)
    }
    
    /**
     * Processes a frame through the optimization pipeline.
     * @return true if the frame should be processed, false if it should be dropped
     */
    fun shouldProcessFrame(): Boolean {
        // Check frame rate limiter first
        if (!frameRateLimiter.shouldProcess()) {
            return false
        }
        
        // Update frame timing statistics
        val now = System.nanoTime()
        if (lastFrameTime > 0) {
            frameTimes.addLast(now - lastFrameTime)
            if (frameTimes.size > frameTimeWindow) {
                frameTimes.removeFirst()
            }
            
            // Update performance state
            if (frameTimes.size >= 5) { // Wait for a few frames to stabilize
                val avgFrameTime = frameTimes.average().toLong()
                val currentFps = 1_000_000_000.0 / avgFrameTime
                
                _performanceState.value = PerformanceState.Running(
                    currentFps = currentFps.toFloat(),
                    targetFps = targetFps,
                    frameTimeMs = avgFrameTime / 1_000_000.0f
                )
                
                // Dynamically adjust processing if needed
                if (currentFps < targetFps * 0.8) {
                    // Consider reducing processing load
                    Log.w(tag, "Frame rate below target: ${currentFps.toInt()} < $targetFps")
                }
            }
        }
        lastFrameTime = now
        
        return true
    }
    
    /**
     * Releases all resources used by the optimizer.
     */
    fun release() {
        scope.launch(Dispatchers.IO) {
            try {
                cameraExecutor.shutdown()
                bitmapPool.clear()
                frameTimes.clear()
                ImageProcessingUtils.release()
                _performanceState.value = PerformanceState.Idle
                Log.d(tag, "Released camera performance resources")
            } catch (e: Exception) {
                Log.e(tag, "Error releasing camera resources", e)
            }
        }
    }
    
    /**
     * Gets the optimal preview resolution based on device capabilities.
     */
    private fun selectOptimalPreviewResolution(): Size {
        // In a real implementation, you would query the camera characteristics
        // and select the best resolution based on the display size and camera capabilities
        return Size(targetAnalysisWidth, targetAnalysisHeight)
    }
    
    /**
     * Gets the optimal analysis resolution based on performance requirements.
     */
    private fun selectOptimalAnalysisResolution(): Size {
        // For analysis, we typically want a smaller resolution than preview
        // to improve performance
        return Size(
            min(targetAnalysisWidth, 1280), 
            min(targetAnalysisHeight, 960)
        )
    }
    
    /**
     * Represents the current performance state of the camera pipeline.
     */
    sealed class PerformanceState {
        object Idle : PerformanceState()
        data class Running(
            val currentFps: Float,
            val targetFps: Int,
            val frameTimeMs: Float
        ) : PerformanceState()
    }
    
    companion object {
        private const val TAG = "CameraPerformance"
        
        /**
         * Creates a ResolutionSelector optimized for the given use case.
         */
        fun createResolutionSelector(
            targetAspectRatio: Int = AspectRatioStrategy.RATIO_16_9,
            targetResolution: Size? = null
        ): ResolutionSelector {
            return ResolutionSelector.Builder()
                .setAspectRatioStrategy(
                    AspectRatioStrategy(
                        targetAspectRatio,
                        AspectRatioStrategy.FALLBACK_RULE_AUTO
                    )
                )
                .apply {
                    targetResolution?.let { resolution ->
                        setResolutionStrategy(
                            ResolutionStrategy(
                                resolution,
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
                            )
                        )
                    }
                }
                .build()
        }
    }
}

/**
 * Extension function to safely close an ImageProxy after processing.
 */
fun ImageProxy.safeClose() {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            close()
        } else {
            // For older versions, ensure we're not closing an image that's already closed
            try {
                image?.close()
            } catch (e: Exception) {
                // Ignore
            }
            close()
        }
    } catch (e: Exception) {
        // Ignore
    }
}
