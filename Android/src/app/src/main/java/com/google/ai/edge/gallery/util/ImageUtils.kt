package com.google.ai.edge.gallery.util

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.core.graphics.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Utility class for common image processing operations.
 * Handles bitmap conversions, scaling, rotation, and color space transformations.
 */
object ImageUtils {
    private const val MAX_BITMAP_SIZE = 100 * 1024 * 1024 // 100MB
    private val LOCK = Any()
    
    // Bitmap pool for recycling bitmaps
    private val bitmapPool = mutableListOf<Bitmap>()
    
    /**
     * Get a bitmap from the pool or create a new one if none available.
     */
    private fun getBitmapFromPool(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        synchronized(LOCK) {
            val iterator = bitmapPool.iterator()
            while (iterator.hasNext()) {
                val bitmap = iterator.next()
                if (bitmap.width == width && 
                    bitmap.height == height && 
                    bitmap.config == config) {
                    iterator.remove()
                    return bitmap
                }
            }
        }
        return Bitmap.createBitmap(width, height, config)
    }
    
    /**
     * Recycle a bitmap by returning it to the pool.
     */
    fun recycleBitmap(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        
        // Only keep bitmaps in the pool up to a certain size
        val bitmapSize = bitmap.allocationByteCount
        if (bitmapSize > MAX_BITMAP_SIZE) {
            bitmap.recycle()
            return
        }
        
        synchronized(LOCK) {
            bitmapPool.add(bitmap)
            
            // Trim the pool if it gets too large
            while (bitmapPool.size > 10) {
                val removed = bitmapPool.removeAt(0)
                removed.recycle()
            }
        }
    }
    
    /**
     * Convert YUV_420_888 image to ARGB_8888 Bitmap with optimization
     * 
     * @param image The input YUV image
     * @param rotationDegrees Rotation to apply (0, 90, 180, 270)
     * @param flipX Whether to flip the image horizontally
     * @param flipY Whether to flip the image vertically
     * @return The converted bitmap
     */
    fun yuv420ToBitmap(
        image: ImageProxy,
        rotationDegrees: Int = 0,
        flipX: Boolean = false,
        flipY: Boolean = false
    ): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + uSize + vSize)
        
        // U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        
        val yuvImage = YuvImage(
            nv21, ImageFormat.NV21, 
            image.width, image.height, null
        )
        
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, image.width, image.height), 90, out
        )
        
        val imageBytes = out.toByteArray()
        var bitmap = BitmapFactory.decodeByteArray(
            imageBytes, 0, imageBytes.size
        )
        
        // Apply transformations if needed
        if (rotationDegrees != 0 || flipX || flipY) {
            bitmap = transformBitmap(bitmap, rotationDegrees, flipX, flipY)
        }
        
        return bitmap
    }
    
    /**
     * Load a bitmap from resources with proper scaling
     */
    fun decodeResource(
        resources: Resources,
        @DrawableRes resId: Int,
        reqWidth: Int = -1,
        reqHeight: Int = -1
    ): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                BitmapFactory.decodeResource(resources, resId, this)
                
                // Calculate inSampleSize
                inSampleSize = calculateInSampleSize(
                    this.outWidth,
                    this.outHeight,
                    reqWidth,
                    reqHeight
                )
                
                // Decode with inSampleSize set
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            }
            
            BitmapFactory.decodeResource(resources, resId, options)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Transform a bitmap with rotation and flip operations
     * 
     * @param bitmap The source bitmap
     * @param rotationDegrees Rotation in degrees (0, 90, 180, 270)
     * @param flipX Whether to flip horizontally
     * @param flipY Whether to flip vertically
     * @return Transformed bitmap
     */
    fun transformBitmap(
        bitmap: Bitmap,
        rotationDegrees: Int = 0,
        flipX: Boolean = false,
        flipY: Boolean = false
    ): Bitmap {
        if (rotationDegrees == 0 && !flipX && !flipY) {
            return bitmap
        }
        
        val matrix = Matrix()
        
        // Apply rotation
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat())
        }
        
        // Apply flips
        if (flipX || flipY) {
            val scaleX = if (flipX) -1f else 1f
            val scaleY = if (flipY) -1f else 1f
            
            // Adjust for rotation
            if (rotationDegrees % 180 != 0) {
                matrix.postScale(scaleY, scaleX)
            } else {
                matrix.postScale(scaleX, scaleY)
            }
            
            // Adjust translation for flip
            val dx = if (flipX) bitmap.width.toFloat() else 0f
            val dy = if (flipY) bitmap.height.toFloat() else 0f
            matrix.postTranslate(dx, dy)
        }
        
        return try {
            val result = Bitmap.createBitmap(
                if (rotationDegrees % 180 == 0) bitmap.width else bitmap.height,
                if (rotationDegrees % 180 == 0) bitmap.height else bitmap.width,
                Bitmap.Config.ARGB_8888
            )
            
            Canvas(result).apply {
                drawBitmap(bitmap, matrix, null)
            }
            
            result
        } catch (e: Exception) {
            bitmap
        }
    }
    
    /**
     * Apply a color matrix to a bitmap
     */
    fun applyColorMatrix(bitmap: Bitmap, colorMatrix: ColorMatrix): Bitmap {
        val result = bitmap.copy(bitmap.config, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            isAntiAlias = true
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    /**
     * Convert bitmap to byte array for model input
     */
    fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val input = ByteBuffer.allocateDirect(bitmap.byteCount).apply {
            order(java.nio.ByteOrder.nativeOrder())
        }
        
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(
            pixels, 0, bitmap.width, 0, 0, 
            bitmap.width, bitmap.height
        )
        
        for (pixel in pixels) {
            // Convert ARGB to RGB and normalize to [0, 1]
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8 and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            
            input.putFloat(r)
            input.putFloat(g)
            input.putFloat(b)
        }
        
        return input
    }
    
    /**
     * Draw detection boxes on the bitmap with improved rendering
     */
    fun drawDetectionBoxes(
        bitmap: Bitmap,
        detections: List<DetectedObject>,
        textSize: Float = 32f,
        boxStrokeWidth: Float = 4f,
        boxColors: List<Int> = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA),
        textBackgroundColor: Int = Color.argb(200, 0, 0, 0),
        textColor: Int = Color.WHITE,
        showConfidence: Boolean = true,
        showLabel: Boolean = true,
        cornerRadius: Float = 8f
    ): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val textPaint = Paint().apply {
            color = textColor
            textSize = textSize
            style = Paint.Style.FILL
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        
        val textBackgroundPaint = Paint().apply {
            color = textBackgroundColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = boxStrokeWidth
            isAntiAlias = true
        }
        
        val cornerPath = Path()
        val rect = RectF()
        
        for ((index, detection) in detections.withIndex()) {
            // Skip invalid detections
            if (detection.confidence <= 0f) continue
            
            // Scale bounding box from [0,1] to image coordinates
            val left = max(0f, detection.boundingBox.left * width)
            val top = max(0f, detection.boundingBox.top * height)
            val right = min(width, detection.boundingBox.right * width)
            val bottom = min(height, detection.boundingBox.bottom * height)
            
            // Skip invalid boxes
            if (left >= right || top >= bottom) continue
            
            // Set box color based on class index
            val colorIndex = index % boxColors.size
            boxPaint.color = boxColors[colorIndex]
            
            // Draw rounded rectangle
            rect.set(left, top, right, bottom)
            cornerPath.reset()
            cornerPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
            canvas.drawPath(cornerPath, boxPaint)
            
            // Prepare label text
            val label = buildString {
                if (showLabel) append(detection.label)
                if (showLabel && showConfidence) append(" ")
                if (showConfidence) append("(${(detection.confidence * 100).toInt()}%)")
            }
            
            if (label.isNotEmpty()) {
                // Measure text width
                val textBounds = Rect()
                textPaint.getTextBounds(label, 0, label.length, textBounds)
                val textWidth = textBounds.width() + 16f
                val textHeight = textBounds.height() + 8f
                
                // Calculate text position (above the box)
                val textLeft = left.coerceAtLeast(0f)
                val textTop = (top - textHeight).coerceAtLeast(0f)
                val textBottom = textTop + textHeight
                
                // Draw text background
                canvas.drawRoundRect(
                    textLeft,
                    textTop,
                    textLeft + textWidth,
                    textBottom,
                    cornerRadius,
                    cornerRadius,
                    textBackgroundPaint
                )
                
                // Draw text
                canvas.drawText(
                    label,
                    textLeft + 8f,
                    textBottom - 4f,
                    textPaint
                )
            }
        }
        
        return mutableBitmap
    }
    
    /**
     * Read EXIF orientation from a file
     */
    fun getExifOrientation(filePath: String): Int {
        return try {
            val exif = ExifInterface(filePath)
            exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }
    
    /**
     * Apply EXIF orientation to a bitmap
     */
    fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        
        when (orientation) {
            ExifInterface.ORIENTATION_NORMAL -> return bitmap
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        
        return try {
            val result = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            if (result != bitmap) {
                bitmap.recycle()
            }
            result
        } catch (e: OutOfMemoryError) {
            bitmap
        }
    }
}
