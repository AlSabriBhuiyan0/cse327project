package com.google.ai.edge.gallery.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.Image
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.renderscript.Type
import androidx.annotation.RequiresApi
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.ai.edge.gallery.ui.camera.DetectedObject
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Utility class for image processing operations with performance optimizations.
 * Uses RenderScript for hardware-accelerated YUV to RGB conversion when available.
 */
object ImageProcessingUtils {
    
    private var rs: RenderScript? = null
    private var scriptYuvToRgb: ScriptIntrinsicYuvToRGB? = null
    private var yuvToRgbInputAlloc: Allocation? = null
    private var yuvToRgbOutputAlloc: Allocation? = null
    private val yuvBuffer = ThreadLocal<ByteArray>()
    private val matrix = Matrix()
    
    /**
     * Initializes RenderScript components. Call this from Application.onCreate()
     */
    fun init(rs: RenderScript) {
        this.rs = rs
        scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))
    }
    
    /**
     * Releases resources used by the image processor.
     */
    fun release() {
        yuvToRgbInputAlloc?.destroy()
        yuvToRgbOutputAlloc?.destroy()
        scriptYuvToRgb?.destroy()
        rs?.destroy()
    }
    
    /**
     * Converts an ImageProxy to a Bitmap using hardware-accelerated path when possible.
     * Uses RenderScript for YUV to RGB conversion when available, falls back to software conversion.
     * 
     * @param image The input ImageProxy
     * @param outputConfig Optional output configuration
     * @return Converted Bitmap or null if conversion fails
     */
    fun ImageProxy.toBitmap(outputConfig: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? {
        return try {
            // Try hardware-accelerated path first
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                rs?.let { rs ->
                    return convertYuvToRgbRenderScript(this, rs, outputConfig)
                }
            }
            
            // Fall back to software conversion
            convertYuvToRgbSoftware(this, outputConfig)
        } catch (e: Exception) {
            android.util.Log.e("ImageUtils", "Error converting ImageProxy to Bitmap", e)
            null
        }
    }
    
    @RequiresApi(18)
    private fun convertYuvToRgbRenderScript(
        image: ImageProxy,
        rs: RenderScript,
        outputConfig: Bitmap.Config
    ): Bitmap? {
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        // Get or create thread-local buffer
        var nv21 = yuvBuffer.get()
        if (nv21 == null || nv21.size < ySize + uSize + vSize) {
            nv21 = ByteArray(ySize + uSize + vSize)
            yuvBuffer.set(nv21)
        }
        
        // Copy Y, U, V planes
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        
        // Create or reuse allocations
        val yuvType = Type.Builder(rs, Element.U8(rs))
            .setX(nv21.size)
            .create()
            
        val rgbaType = Type.Builder(rs, Element.RGBA_8888(rs))
            .setX(image.width)
            .setY(image.height)
            .create()
            
        yuvToRgbInputAlloc?.let { if (it.type != yuvType) it.destroy() }
        yuvToRgbOutputAlloc?.let { if (it.type != rgbaType) it.destroy() }
        
        yuvToRgbInputAlloc = yuvToRgbInputAlloc ?: Allocation.createTyped(rs, yuvType)
        yuvToRgbOutputAlloc = yuvToRgbOutputAlloc ?: Allocation.createTyped(rs, rgbaType)
        
        // Copy YUV data to input allocation
        yuvToRgbInputAlloc?.copyFrom(nv21)
        
        // Set input and execute conversion
        scriptYuvToRgb?.setInput(yuvToRgbInputAlloc)
        scriptYuvToRgb?.forEach(yuvToRgbOutputAlloc)
        
        // Create output bitmap and copy from allocation
        val bitmap = Bitmap.createBitmap(image.width, image.height, outputConfig)
        yuvToRgbOutputAlloc?.copyTo(bitmap)
        
        // Apply rotation if needed
        return if (image.imageInfo.rotationDegrees != 0) {
            applyRotation(bitmap, image.imageInfo.rotationDegrees.toFloat())
        } else {
            bitmap
        }
    }
    
    private fun convertYuvToRgbSoftware(
        image: ImageProxy,
        outputConfig: Bitmap.Config
    ): Bitmap? {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        // Get or create thread-local buffer
        var nv21 = yuvBuffer.get()
        if (nv21 == null || nv21.size < ySize + uSize + vSize) {
            nv21 = ByteArray(ySize + uSize + vSize)
            yuvBuffer.set(nv21)
        }
        
        // Copy Y, U, V planes (U and V are swapped in NV21)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        
        // Convert YUV to ARGB
        val argb = IntArray(image.width * image.height)
        val yuv = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            image.width,
            image.height,
            null
        )
        
        yuv.compressToJpeg(
            android.graphics.Rect(0, 0, image.width, image.height),
            100,
            java.io.ByteArrayOutputStream().apply {
                yuv.compressToJpeg(
                    android.graphics.Rect(0, 0, image.width, image.height),
                    100,
                    this
                )
            }
        )
        
        val bitmap = Bitmap.createBitmap(image.width, image.height, outputConfig)
        bitmap.setPixels(argb, 0, image.width, 0, 0, image.width, image.height)
        
        // Apply rotation if needed
        return if (image.imageInfo.rotationDegrees != 0) {
            applyRotation(bitmap, image.imageInfo.rotationDegrees.toFloat())
        } else {
            bitmap
        }
    }
    
    private fun applyRotation(bitmap: Bitmap, rotationDegrees: Float): Bitmap {
        matrix.reset()
        matrix.postRotate(rotationDegrees)
        
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }
    
    /**
     * Preprocesses an image for model inference with optimizations.
     * Resizes, normalizes, and converts the image to the target format.
     * 
     * @param bitmap Input bitmap
     * @param targetWidth Target width
     * @param targetHeight Target height
     * @param normalize Whether to normalize pixel values (default: true)
     * @param mean Mean value for normalization (used if normalize=true)
     * @param std Standard deviation for normalization (used if normalize=true)
     * @return Preprocessed bitmap in ARGB_8888 format
     */
    fun preprocessImage(
        bitmap: Bitmap,
        targetWidth: Int = 300,
        targetHeight: Int = 300,
        normalize: Boolean = true,
        mean: Float = 0f,
        std: Float = 255f
    ): Bitmap {
        // Reuse bitmap if already the correct size and format
        if (bitmap.width == targetWidth && 
            bitmap.height == targetHeight && 
            bitmap.config == Bitmap.Config.ARGB_8888 && 
            !normalize) {
            return bitmap
        }
        
        // Create output bitmap with pooling if possible
        val bitmapPool = BitmapPool.getInstance()
        val output = bitmapPool.acquire(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            ?: Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        
        // Scale and convert in one step
        val canvas = Canvas(output)
        val scaleX = targetWidth.toFloat() / bitmap.width
        val scaleY = targetHeight.toFloat() / bitmap.height
        val scale = minOf(scaleX, scaleY)
        
        matrix.reset()
        matrix.setScale(scale, scale)
        
        canvas.drawBitmap(bitmap, matrix, null)
        
        // Apply normalization if needed
        if (normalize && (mean != 0f || std != 255f)) {
            normalizePixels(output, mean, std)
        }
        
        return output
    }
    
    /**
     * Normalizes pixel values in place.
     */
    private fun normalizePixels(bitmap: Bitmap, mean: Float, std: Float) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (((pixel shr 16) and 0xFF) - mean) / std
            val g = (((pixel shr 8) and 0xFF) - mean) / std
            val b = ((pixel and 0xFF) - mean) / std
            
            pixels[i] = (0xFF shl 24) or
                    ((r.toInt() and 0xFF) shl 16) or
                    ((g.toInt() and 0xFF) shl 8) or
                    (b.toInt() and 0xFF)
        }
        
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }
    
    /**
     * Draws detection boxes and labels on the input bitmap.
     */
    fun drawDetections(
        bitmap: Bitmap,
        detections: List<DetectedObject>,
        textSize: Float = 32f,
        boxColor: Int = Color.RED,
        textColor: Int = Color.WHITE,
        boxStrokeWidth: Float = 4f
    ): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutableBitmap)
        
        val paint = android.graphics.Paint().apply {
            color = boxColor
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = boxStrokeWidth
            isAntiAlias = true
        }
        
        val textPaint = android.graphics.Paint().apply {
            color = textColor
            textSize = textSize
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
        }
        
        val textBgPaint = android.graphics.Paint().apply {
            color = boxColor
            style = android.graphics.Paint.Style.FILL
            alpha = 200
        }
        
        val textPadding = 8f
        val textOffset = textSize + textPadding * 2
        
        detections.forEach { detection ->
            // Scale bounding box from [0,1] to image coordinates
            val left = detection.boundingBox.left * bitmap.width
            val top = detection.boundingBox.top * bitmap.height
            val right = detection.boundingBox.right * bitmap.width
            val bottom = detection.boundingBox.bottom * bitmap.height
            
            // Draw rectangle
            canvas.drawRect(left, top, right, bottom, paint)
            
            // Draw label with background
            val label = "${detection.label} (${(detection.confidence * 100).toInt()}%)"
            val textWidth = textPaint.measureText(label)
            
            // Draw text background
            canvas.drawRect(
                left - 1,
                top - textOffset,
                left + textWidth + textPadding * 2,
                top,
                textBgPaint
            )
            
            // Draw text
            canvas.drawText(
                label,
                left + textPadding,
                top - textPadding,
                textPaint
            )
        }
        
        return mutableBitmap
    }
    
    /**
     * Converts a bitmap to a ByteBuffer for model input.
     */
    fun bitmapToByteBuffer(
        bitmap: Bitmap,
        mean: Float = 0f,
        std: Float = 255f
    ): ByteBuffer {
        val input = ByteBuffer.allocateDirect(bitmap.byteCount)
        input.order(java.nio.ByteOrder.nativeOrder())
        
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(
            pixels, 
            0, 
            bitmap.width, 
            0, 0, 
            bitmap.width, 
            bitmap.height
        )
        
        for (pixel in pixels) {
            // Convert ARGB to RGB and normalize to [0, 1]
            val r = ((pixel shr 16 and 0xFF) / std - mean) / std
            val g = ((pixel shr 8 and 0xFF) / std - mean) / std
            val b = ((pixel and 0xFF) / std - mean) / std
            
            input.putFloat(r)
            input.putFloat(g)
            input.putFloat(b)
        }
        
        return input
    }
    
    /**
     * Calculates the intersection over union between two rectangles.
     */
    fun calculateIOU(rect1: RectF, rect2: RectF): Float {
        val x1 = max(rect1.left, rect2.left)
        val y1 = max(rect1.top, rect2.top)
        val x2 = min(rect1.right, rect2.right)
        val y2 = min(rect1.bottom, rect2.bottom)
        
        val intersection = max(0f, x2 - x1) * max(0f, y2 - y1)
        val area1 = (rect1.right - rect1.left) * (rect1.bottom - rect1.top)
        val area2 = (rect2.right - rect2.left) * (rect2.bottom - rect2.top)
        
        return intersection / (area1 + area2 - intersection + 1e-6f)
    }
    
    /**
     * Applies non-maximum suppression to filter overlapping detections.
     */
    fun applyNMS(
        detections: List<DetectedObject>,
        iouThreshold: Float = 0.5f
    ): List<DetectedObject> {
        val selected = mutableListOf<DetectedObject>()
        val remaining = detections.sortedByDescending { it.confidence }.toMutableList()
        
        while (remaining.isNotEmpty()) {
            // Select the detection with highest confidence
            val selectedDetection = remaining.removeAt(0)
            selected.add(selectedDetection)
            
            // Remove overlapping detections
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val detection = iterator.next()
                val iou = calculateIOU(selectedDetection.boundingBox, detection.boundingBox)
                if (iou > iouThreshold) {
                    iterator.remove()
                }
            }
        }
        
        return selected
    }
}
