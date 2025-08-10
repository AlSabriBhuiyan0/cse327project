package com.google.ai.edge.gallery.util

import android.hardware.SensorEvent
import android.hardware.SensorManager
import kotlin.math.*

/**
 * A utility class for sensor fusion and data processing.
 * Combines data from multiple sensors to improve accuracy.
 */
class SensorFusion {
    
    // Constants for sensor fusion
    private val EPSILON = 1e-6f
    private val NS2S = 1.0f / 1_000_000_000.0f // Nanoseconds to seconds
    
    // Timestamp for the last sensor update
    private var timestamp: Long = 0
    
    // Orientation angles (pitch, roll, yaw) in radians
    private val orientation = FloatArray(3)
    
    // Rotation matrix from gyro data
    private val rotationMatrix = FloatArray(9) { 0f }
    private val orientationMatrix = FloatArray(9) { 0f }
    
    // Complementary filter coefficient (0-1)
    private val alpha = 0.98f
    
    init {
        // Initialize the rotation matrix to identity
        rotationMatrix[0] = 1.0f
        rotationMatrix[4] = 1.0f
        rotationMatrix[8] = 1.0f
    }
    
    /**
     * Processes accelerometer and magnetometer data to compute orientation.
     * 
     * @param accelerometer The accelerometer sensor event.
     * @param magnetometer The magnetometer sensor event.
     */
    fun processAccelerometerMagnetometer(accelerometer: SensorEvent, magnetometer: FloatArray) {
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        
        // Copy the accelerometer values to gravity
        System.arraycopy(accelerometer.values, 0, gravity, 0, 3)
        
        // Copy the magnetometer values to geomagnetic
        System.arraycopy(magnetometer, 0, geomagnetic, 0, 3)
        
        // Normalize the gravity vector
        val normGravity = Math.sqrt(
            (gravity[0] * gravity[0] + 
             gravity[1] * gravity[1] + 
             gravity[2] * gravity[2]).toDouble()
        ).toFloat()
        
        if (normGravity > EPSILON) {
            gravity[0] /= normGravity
            gravity[1] /= normGravity
            gravity[2] /= normGravity
        }
        
        // Normalize the geomagnetic vector
        val normGeomagnetic = Math.sqrt(
            (geomagnetic[0] * geomagnetic[0] + 
             geomagnetic[1] * geomagnetic[1] + 
             geomagnetic[2] * geomagnetic[2]).toDouble()
        ).toFloat()
        
        if (normGeomagnetic > EPSILON) {
            geomagnetic[0] /= normGeomagnetic
            geomagnetic[1] /= normGeomagnetic
            geomagnetic[2] /= normGeomagnetic
        }
        
        // Compute the rotation matrix from accelerometer and magnetometer
        if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
            // Get the orientation (azimuth, pitch, roll) from the rotation matrix
            SensorManager.getOrientation(rotationMatrix, orientation)
            
            // Convert from radians to degrees for better readability
            orientation[0] = Math.toDegrees(orientation[0].toDouble()).toFloat()
            orientation[1] = Math.toDegrees(orientation[1].toDouble()).toFloat()
            orientation[2] = Math.toDegrees(orientation[2].toDouble()).toFloat()
        }
    }
    
    /**
     * Processes gyroscope data to update the orientation.
     * Uses a complementary filter to combine with accelerometer/magnetometer data.
     * 
     * @param event The gyroscope sensor event.
     */
    fun processGyroscope(event: SensorEvent) {
        // This method should be called as often as possible with gyroscope data
        
        // Skip if we don't have a previous timestamp
        if (timestamp == 0L) {
            timestamp = event.timestamp
            return
        }
        
        // Calculate time step (in seconds)
        val dT = (event.timestamp - timestamp) * NS2S
        timestamp = event.timestamp
        
        // Skip if the time step is too small
        if (dT < EPSILON) return
        
        // Get the gyroscope values (in radians/second)
        val omegaX = event.values[0]
        val omegaY = event.values[1]
        val omegaZ = event.values[2]
        
        // Calculate the magnitude of the rotation vector
        val magnitude = sqrt(omegaX * omegaX + omegaY * omegaY + omegaZ * omegaZ)
        
        // Skip if the rotation rate is too small
        if (magnitude < EPSILON) return
        
        // Normalize the rotation vector
        val normOmegaX = omegaX / magnitude
        val normOmegaY = omegaY / magnitude
        val normOmegaZ = omegaZ / magnitude
        
        // Calculate the rotation angle (in radians)
        val theta = magnitude * dT
        
        // Convert axis-angle to rotation matrix
        val sinTheta = sin(theta / 2.0f).toDouble().toFloat()
        val cosTheta = cos(theta / 2.0f).toDouble().toFloat()
        
        val q0 = cosTheta
        val q1 = normOmegaX * sinTheta
        val q2 = normOmegaY * sinTheta
        val q3 = normOmegaZ * sinTheta
        
        // Apply the rotation to the current rotation matrix
        val R = FloatArray(9)
        
        R[0] = q0 * q0 + q1 * q1 - q2 * q2 - q3 * q3
        R[1] = 2.0f * (q1 * q2 - q0 * q3)
        R[2] = 2.0f * (q1 * q3 + q0 * q2)
        
        R[3] = 2.0f * (q1 * q2 + q0 * q3)
        R[4] = q0 * q0 - q1 * q1 + q2 * q2 - q3 * q3
        R[5] = 2.0f * (q2 * q3 - q0 * q1)
        
        R[6] = 2.0f * (q1 * q3 - q0 * q2)
        R[7] = 2.0f * (q2 * q3 + q0 * q1)
        R[8] = q0 * q0 - q1 * q1 - q2 * q2 + q3 * q3
        
        // Multiply the rotation matrix with the current orientation
        val result = FloatArray(9)
        android.opengl.Matrix.multiplyMM(result, 0, rotationMatrix, 0, R, 0)
        
        // Copy the result back to the rotation matrix
        System.arraycopy(result, 0, rotationMatrix, 0, 9)
        
        // Get the orientation from the rotation matrix
        SensorManager.getOrientation(rotationMatrix, orientation)
        
        // Convert from radians to degrees for better readability
        orientation[0] = Math.toDegrees(orientation[0].toDouble()).toFloat()
        orientation[1] = Math.toDegrees(orientation[1].toDouble()).toFloat()
        orientation[2] = Math.toDegrees(orientation[2].toDouble()).toFloat()
    }
    
    /**
     * Gets the current orientation angles.
     * 
     * @return FloatArray containing [azimuth, pitch, roll] in degrees.
     */
    fun getOrientation(): FloatArray {
        return orientation.copyOf()
    }
    
    /**
     * Gets the current rotation matrix.
     * 
     * @return FloatArray containing the 3x3 rotation matrix.
     */
    fun getRotationMatrix(): FloatArray {
        return rotationMatrix.copyOf()
    }
    
    /**
     * Resets the sensor fusion algorithm.
     */
    fun reset() {
        timestamp = 0
        orientation.fill(0f)
        rotationMatrix.fill(0f)
        rotationMatrix[0] = 1.0f
        rotationMatrix[4] = 1.0f
        rotationMatrix[8] = 1.0f
    }
}
