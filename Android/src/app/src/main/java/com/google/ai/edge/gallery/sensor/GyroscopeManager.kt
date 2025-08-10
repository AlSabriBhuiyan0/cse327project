package com.google.ai.edge.gallery.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.google.ai.edge.gallery.util.AppExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.PI
import kotlin.math.atan2

/**
 * Manages gyroscope sensor events and provides orientation data with sensor fusion.
 * Uses a complementary filter to combine gyroscope and accelerometer data for better accuracy.
 */
class GyroscopeManager(
    private val context: Context,
    private val sensorFusion: Boolean = true,
    private val samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_GAME
) : SensorEventListener {
    private val tag = "GyroscopeManager"
    
    // Sensor manager and sensors
    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    
    // Sensors
    private val gyroscopeSensor: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }
    
    private val accelerometerSensor: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    
    private val magnetometerSensor: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }
    
    // Sensor state
    private val _sensorState = MutableStateFlow<SensorState>(SensorState.Initializing)
    val sensorState: StateFlow<SensorState> = _sensorState.asStateFlow()
    
    // Rotation data
    private val _rotationData = MutableStateFlow(RotationData())
    val rotationData: StateFlow<RotationData> = _rotationData.asStateFlow()
    
    // Sensor fusion parameters
    private var lastAccelerometerData = FloatArray(3)
    private var lastMagnetometerData = FloatArray(3)
    private var lastGyroscopeData = FloatArray(3)
    private var rotationMatrix = FloatArray(9)
    private var orientation = FloatArray(3)
    
    // Complementary filter coefficient (0-1, higher means more trust in gyroscope)
    private val alpha = 0.98f
    
    // Timing and performance tracking
    private var lastUpdateTime: Long = 0
    private var frameCount = 0
    private var lastFpsTime: Long = 0
    private var currentFps = 0f
    
    // Coroutine scope for background processing
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sensorJob: Job? = null
    
    // Threshold for significant movement (in radians/second)
    @VisibleForTesting
    internal var rotationThreshold = 0.1f
    
    // Calibration data
    private var gyroBias = FloatArray(3) { 0f }
    private var isCalibrated = false
    
    /**
     * Start listening to sensor events with optimal settings.
     * Uses a background thread for sensor processing.
     */
    fun start() {
        if (gyroscopeSensor == null) {
            _sensorState.value = SensorState.Error("Gyroscope sensor not available")
            return
        }
        
        // Cancel any existing job
        sensorJob?.cancel()
        
        sensorJob = scope.launch {
            try {
                // Register sensors with specified sampling rate
                val success = withContext(Dispatchers.Main) {
                    var success = true
                    
                    // Register gyroscope
                    success = success && sensorManager.registerListener(
                        this@GyroscopeManager,
                        gyroscopeSensor,
                        samplingPeriodUs
                    )
                    
                    // Register additional sensors for fusion if enabled
                    if (sensorFusion) {
                        success = success && accelerometerSensor?.let { sensor ->
                            sensorManager.registerListener(
                                this@GyroscopeManager,
                                sensor,
                                samplingPeriodUs
                            )
                        } ?: false
                        
                        success = success && magnetometerSensor?.let { sensor ->
                            sensorManager.registerListener(
                                this@GyroscopeManager,
                                sensor,
                                samplingPeriodUs
                            )
                        } ?: false
                    }
                    
                    success
                }
                
                if (success) {
                    _sensorState.value = SensorState.Active
                    lastUpdateTime = System.currentTimeMillis()
                    lastFpsTime = lastUpdateTime
                    Log.i(tag, "Sensor listeners registered successfully")
                } else {
                    _sensorState.value = SensorState.Error("Failed to register one or more sensor listeners")
                    Log.e(tag, "Failed to register one or more sensor listeners")
                }
            } catch (e: Exception) {
                _sensorState.value = SensorState.Error("Error starting sensors: ${e.message}")
                Log.e(tag, "Error starting sensors", e)
            }
        }
    }
    
    /**
     * Stop listening to sensor events and release resources.
     */
    fun stop() {
        try {
            sensorManager.unregisterListener(this)
            sensorJob?.cancel()
            _sensorState.value = SensorState.Stopped
            resetRotationData()
            Log.i(tag, "Sensor listeners unregistered")
        } catch (e: Exception) {
            Log.e(tag, "Error stopping sensors", e)
        }
    }
    
    /**
     * Reset the rotation data and calibration.
     */
    fun resetRotationData() {
        _rotationData.value = RotationData()
        lastUpdateTime = System.currentTimeMillis()
        gyroBias = FloatArray(3) { 0f }
        isCalibrated = false
    }
    
    /**
     * Calibrate the gyroscope to remove bias.
     * Should be called when the device is stationary.
     */
    fun calibrate() {
        scope.launch {
            try {
                _sensorState.value = SensorState.Calibrating
                
                // Collect samples for calibration
                val samples = mutableListOf<FloatArray>()
                val calibrationTime = 2000L // 2 seconds calibration
                val startTime = System.currentTimeMillis()
                
                while (System.currentTimeMillis() - startTime < calibrationTime) {
                    lastGyroscopeData.copyInto(gyroBias)
                    samples.add(gyroscopeSensor?.let { lastGyroscopeData.copyOf() } ?: floatArrayOf())
                    kotlinx.coroutines.delay(50) // Sample every 50ms
                }
                
                // Calculate average bias
                if (samples.isNotEmpty()) {
                    gyroBias = FloatArray(3) { i ->
                        samples.sumOf { it.getOrElse(i) { 0f }.toDouble() }.toFloat() / samples.size
                    }
                    isCalibrated = true
                    Log.i(tag, "Gyroscope calibrated successfully. Bias: ${gyroBias.contentToString()}")
                }
                
                _sensorState.value = SensorState.Active
            } catch (e: Exception) {
                Log.e(tag, "Error during calibration", e)
                _sensorState.value = SensorState.Error("Calibration failed: ${e.message}")
            }
        }
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        
        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - lastUpdateTime).toFloat() / 1000f // Convert to seconds
        
        // Skip if time delta is too large or invalid
        if (deltaTime <= 0 || deltaTime > 0.1f) {
            lastUpdateTime = currentTime
            return
        }
        
        // Update FPS counter
        frameCount++
        if (currentTime - lastFpsTime >= 1000) {
            currentFps = frameCount * 1000f / (currentTime - lastFpsTime)
            frameCount = 0
            lastFpsTime = currentTime
        }
        
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                // Apply calibration if available
                event.values.copyInto(lastGyroscopeData)
                if (isCalibrated) {
                    for (i in lastGyroscopeData.indices) {
                        lastGyroscopeData[i] -= gyroBias[i]
                    }
                }
                
                // Apply sensor fusion if enabled
                if (sensorFusion) {
                    updateOrientationWithFusion(deltaTime)
                } else {
                    updateOrientationGyroOnly(deltaTime)
                }
            }
            
            Sensor.TYPE_ACCELEROMETER -> {
                // Low-pass filter for accelerometer data
                val alpha = 0.8f
                for (i in 0..2) {
                    lastAccelerometerData[i] = alpha * lastAccelerometerData[i] + (1 - alpha) * event.values[i]
                }
            }
            
            Sensor.TYPE_MAGNETIC_FIELD -> {
                // Low-pass filter for magnetometer data
                val alpha = 0.8f
                for (i in 0..2) {
                    lastMagnetometerData[i] = alpha * lastMagnetometerData[i] + (1 - alpha) * event.values[i]
                }
            }
        }
        
        lastUpdateTime = currentTime
    }
    
    /**
     * Update orientation using gyroscope data only.
     */
    private fun updateOrientationGyroOnly(deltaTime: Float) {
        val (x, y, z) = lastGyroscopeData
        
        // Calculate magnitude of rotation vector
        val magnitude = sqrt(x * x + y * y + z * z)
        
        // Only update if significant movement is detected
        if (magnitude > rotationThreshold) {
            // Update rotation data
            _rotationData.value = RotationData(
                x = x,
                y = y,
                z = z,
                magnitude = magnitude,
                fps = currentFps,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Update orientation using sensor fusion (gyroscope + accelerometer + magnetometer).
     */
    private fun updateOrientationWithFusion(deltaTime: Float) {
        // Get rotation matrix from accelerometer and magnetometer
        val rotationMatrix = FloatArray(9)
        val inclinationMatrix = FloatArray(9)
        
        if (SensorManager.getRotationMatrix(
                rotationMatrix,
                inclinationMatrix,
                lastAccelerometerData,
                lastMagnetometerData
            )
        ) {
            // Get orientation angles
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            
            // Convert to degrees
            val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
            val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
            val yaw = Math.toDegrees(orientation[0].toDouble()).toFloat()
            
            // Fuse with gyroscope data using complementary filter
            val alpha = 0.98f
            val fusedX = alpha * lastGyroscopeData[0] + (1 - alpha) * pitch
            val fusedY = alpha * lastGyroscopeData[1] + (1 - alpha) * roll
            val fusedZ = alpha * lastGyroscopeData[2] + (1 - alpha) * yaw
            
            val magnitude = sqrt(fusedX * fusedX + fusedY * fusedY + fusedZ * fusedZ)
            
            // Update rotation data
            _rotationData.value = RotationData(
                x = fusedX,
                y = fusedY,
                z = fusedZ,
                magnitude = magnitude,
                fps = currentFps,
                timestamp = System.currentTimeMillis()
            )
        } else {
            // Fall back to gyroscope-only if sensor fusion fails
            updateOrientationGyroOnly(deltaTime)
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
        when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> Log.d(tag, "Sensor accuracy: High")
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> Log.d(tag, "Sensor accuracy: Medium")
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> Log.d(tag, "Sensor accuracy: Low")
            SensorManager.SENSOR_STATUS_UNRELIABLE -> {
                Log.w(tag, "Sensor accuracy: Unreliable")
                _sensorState.value = SensorState.Error("Sensor accuracy is unreliable")
            }
            else -> Log.d(tag, "Sensor accuracy: Unknown ($accuracy)")
        }
    }
    
    /**
     * Check if the device has a gyroscope sensor.
     */
    fun hasGyroscope(): Boolean {
        return gyroscopeSensor != null
    }
}

/**
 * Represents the current state of the gyroscope sensor.
 */
sealed class SensorState {
    object Initializing : SensorState()
    object Active : SensorState()
    object Calibrating : SensorState()
    object Stopped : SensorState()
    data class Error(val message: String) : SensorState()
}

/**
 * Data class holding rotation information from the gyroscope.
 *
 * @property x Rotation rate around the x-axis (pitch) in radians/second
 * @property y Rotation rate around the y-axis (roll) in radians/second
 * @property z Rotation rate around the z-axis (yaw) in radians/second
 * @property magnitude Magnitude of the rotation vector
 * @property fps Current frames per second of sensor updates
 * @property timestamp Timestamp when the data was captured
 */
data class RotationData(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val magnitude: Float = 0f,
    val fps: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Convert rotation rates to degrees/second for display.
     */
    fun toDegreesPerSecond(): Triple<Float, Float, Float> {
        val radToDeg = 57.2958f // 180 / PI
        return Triple(
            x * radToDeg,
            y * radToDeg,
            z * radToDeg
        )
    }
    
    /**
     * Get a formatted string of the rotation data.
     */
    fun getFormattedString(): String {
        return "X: %.1f°, Y: %.1f°, Z: %.1f°, Mag: %.1f, FPS: %.0f".format(
            Math.toDegrees(x.toDouble()),
            Math.toDegrees(y.toDouble()),
            Math.toDegrees(z.toDouble()),
            magnitude,
            fps
        )
    }
}
