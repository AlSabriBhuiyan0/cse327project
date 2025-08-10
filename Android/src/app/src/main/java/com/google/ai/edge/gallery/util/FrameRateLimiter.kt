package com.google.ai.edge.gallery.util

import android.util.Log

/**
 * A utility class to limit the frame processing rate to a target FPS.
 */
class FrameRateLimiter(private val targetFps: Int) {
    private val tag = "FrameRateLimiter"
    private var lastFrameTimeNs: Long = 0
    private val frameTimeNs: Long = (1_000_000_000 / targetFps.toDouble()).toLong()
    
    /**
     * Determines if a frame should be processed based on the target FPS.
     * @return true if the frame should be processed, false if it should be dropped
     */
    @Synchronized
    fun shouldProcess(): Boolean {
        val now = System.nanoTime()
        val timeSinceLastFrame = now - lastFrameTimeNs
        
        return if (timeSinceLastFrame >= frameTimeNs) {
            lastFrameTime = now - (timeSinceLastFrame % frameTimeNs)
            true
        } else {
            false
        }
    }
    
    /**
     * Gets the time (in nanoseconds) until the next frame should be processed.
     * @return Time in nanoseconds until next frame should be processed, or 0 if ready
     */
    @Synchronized
    fun timeUntilNextFrameNs(): Long {
        val now = System.nanoTime()
        val timeSinceLastFrame = now - lastFrameTimeNs
        return maxOf(0, frameTimeNs - timeSinceLastFrame)
    }
    
    /**
     * Resets the frame timing, causing the next frame to always be processed.
     */
    @Synchronized
    fun reset() {
        lastFrameTime = 0
    }
    
    companion object {
        /**
         * Creates a FrameRateLimiter with a target FPS, ensuring it's within valid bounds.
         * @param targetFps The desired frames per second (clamped between 1 and 60)
         * @return A FrameRateLimiter instance configured with the target FPS
         */
        fun create(targetFps: Int): FrameRateLimiter {
            val clampedFps = targetFps.coerceIn(1, 60) // Reasonable bounds for mobile
            if (targetFps != clampedFps) {
                Log.w("FrameRateLimiter", "Requested FPS $targetFps clamped to $clampedFps")
            }
            return FrameRateLimiter(clampedFps)
        }
    }
}
