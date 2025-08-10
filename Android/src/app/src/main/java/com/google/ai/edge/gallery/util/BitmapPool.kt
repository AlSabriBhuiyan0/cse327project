package com.google.ai.edge.gallery.util

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.max

/**
 * A pool of reusable bitmaps to avoid memory allocations during frame processing.
 */
class BitmapPool {
    private val tag = "BitmapPool"
    private val pool = mutableListOf<Bitmap>()
    
    /**
     * Acquires a bitmap from the pool or creates a new one if none is available.
     */
    @Synchronized
    fun acquire(width: Int, height: Int, config: Bitmap.Config): Bitmap? {
        try {
            // Try to find a matching bitmap in the pool
            val index = pool.indexOfFirst { 
                !it.isRecycled && it.width == width && 
                it.height == height && it.config == config 
            }
            
            return if (index >= 0) {
                pool.removeAt(index).apply { 
                    eraseColor(0) // Clear previous content
                }
            } else {
                Bitmap.createBitmap(width, height, config)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error acquiring bitmap from pool", e)
            return null
        }
    }
    
    /**
     * Returns a bitmap to the pool for reuse.
     */
    @Synchronized
    fun release(bitmap: Bitmap) {
        try {
            if (!bitmap.isRecycled && !pool.contains(bitmap)) {
                // Limit pool size to prevent memory issues
                if (pool.size < MAX_POOL_SIZE) {
                    pool.add(bitmap)
                } else {
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error releasing bitmap to pool", e)
        }
    }
    
    /**
     * Clears all bitmaps from the pool.
     */
    @Synchronized
    fun clear() {
        pool.forEach { 
            try {
                if (!it.isRecycled) it.recycle()
            } catch (e: Exception) {
                Log.e(tag, "Error recycling bitmap", e)
            }
        }
        pool.clear()
    }
    
    companion object {
        private const val MAX_POOL_SIZE = 10
        
        @Volatile
        private var instance: BitmapPool? = null
        
        fun getInstance(): BitmapPool {
            return instance ?: synchronized(this) {
                instance ?: BitmapPool().also { instance = it }
            }
        }
    }
}

/**
 * Extension function to safely release a bitmap to the pool.
 */
fun Bitmap?.releaseToPool() {
    this?.let { BitmapPool.getInstance().release(it) }
}
