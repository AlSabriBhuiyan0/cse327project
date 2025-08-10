package com.google.ai.edge.gallery.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Manages sensor data collection and processing.
 * Handles multiple sensor types and provides processed data via StateFlow.
 */
class SensorManager(
    private val context: Context,
    private val sensorTypes: List<Int> = DEFAULT_SENSOR_TYPES
) : SensorEventListener {
    private val tag = "SensorManager"
    
    // Sensor manager and sensors
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var sensors: List<Sensor> = emptyList()
    
    // Sensor data state
    private val _sensorData = MutableStateFlow<Map<Int, FloatArray>>(emptyMap())
    val sensorData: StateFlow<Map<Int, FloatArray>> = _sensorData.asStateFlow()
    
    // Sensor availability state
    private val _sensorState = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val sensorState: StateFlow<Map<Int, Boolean>> = _sensorState.asStateFlow()
    
    // Sampling period in microseconds (default: 100Hz)
    var samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_FASTEST
        set(value) {
            if (field != value) {
                field = value
                restartSensors()
            }
        }
    
    // Sensor data processing
    private val sensorDataBuffer = mutableMapOf<Int, FloatArray>()
    private var isInitialized = false
    
    // Default sensor types to monitor
    companion object {
        val DEFAULT_SENSOR_TYPES = listOf(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_LINEAR_ACCELERATION,
            Sensor.TYPE_ROTATION_VECTOR
        )
        
        // Sensor names for logging
        private val SENSOR_NAMES = mapOf(
            Sensor.TYPE_ACCELEROMETER to "Accelerometer",
            Sensor.TYPE_GYROSCOPE to "Gyroscope",
            Sensor.TYPE_MAGNETIC_FIELD to "Magnetometer",
            Sensor.TYPE_LINEAR_ACCELERATION to "Linear Acceleration",
            Sensor.TYPE_ROTATION_VECTOR to "Rotation Vector"
        )
    }
    
    init {
        initializeSensors()
    }
    
    /**
     * Initializes the sensors and checks availability.
     */
    private fun initializeSensors() {
        if (isInitialized) return
        
        // Clear previous state
        sensorDataBuffer.clear()
        _sensorData.value = emptyMap()
        
        // Check sensor availability
        val availableSensors = mutableListOf<Sensor>()
        val sensorStateMap = mutableMapOf<Int, Boolean>()
        
        for (type in sensorTypes) {
            val sensor = sensorManager.getDefaultSensor(type)
            if (sensor != null) {
                availableSensors.add(sensor)
                sensorStateMap[type] = true
                sensorDataBuffer[type] = FloatArray(sensor.maximumRange.toInt())
                Log.d(tag, "Sensor available: ${getSensorName(type)}")
            } else {
                sensorStateMap[type] = false
                Log.w(tag, "Sensor not available: ${getSensorName(type)}")
            }
        }
        
        sensors = availableSensors
        _sensorState.value = sensorStateMap
        isInitialized = true
        
        Log.d(tag, "Initialized ${sensors.size} out of ${sensorTypes.size} requested sensors")
    }
    
    /**
     * Starts listening to sensor events.
     */
    fun start() {
        if (!isInitialized) {
            initializeSensors()
        }
        
        // Register listeners for all available sensors
        sensors.forEach { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                samplingPeriodUs
            )
        }
        
        Log.d(tag, "Started listening to ${sensors.size} sensors")
    }
    
    /**
     * Stops listening to sensor events.
     */
    fun stop() {
        sensorManager.unregisterListener(this)
        Log.d(tag, "Stopped listening to sensors")
    }
    
    /**
     * Restarts sensor listeners with current configuration.
     */
    private fun restartSensors() {
        if (isInitialized) {
            stop()
            start()
        }
    }
    
    /**
     * Gets the human-readable name of a sensor type.
     */
    private fun getSensorName(type: Int): String {
        return SENSOR_NAMES[type] ?: "Unknown Sensor ($type)"
    }
    
    /**
     * Called when sensor accuracy changes.
     */
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Handle accuracy changes if needed
        Log.d(tag, "${sensor.name} accuracy changed: $accuracy")
    }
    
    /**
     * Called when sensor data is available.
     */
    override fun onSensorChanged(event: SensorEvent) {
        val type = event.sensor.type
        val values = event.values.copyOf()
        
        // Update the buffer with new sensor data
        sensorDataBuffer[type] = values
        
        // Emit the updated sensor data
        _sensorData.value = sensorDataBuffer.toMap()
    }
    
    /**
     * Gets the current sensor values for a specific sensor type.
     * 
     * @param type The sensor type (e.g., Sensor.TYPE_ACCELEROMETER)
     * @return The current sensor values or null if not available
     */
    fun getSensorValues(type: Int): FloatArray? {
        return sensorDataBuffer[type]
    }
    
    /**
     * Calculates the magnitude of a 3D vector.
     */
    private fun calculateMagnitude(x: Float, y: Float, z: Float): Float {
        return sqrt(x * x + y * y + z * z)
    }
    
    /**
     * Gets the current device orientation angles.
     * 
     * @return FloatArray containing [azimuth, pitch, roll] in radians, or null if not available
     */
    fun getOrientation(): FloatArray? {
        val accelerometer = getSensorValues(Sensor.TYPE_ACCELEROMETER) ?: return null
        val magnetometer = getSensorValues(Sensor.TYPE_MAGNETIC_FIELD) ?: return null
        
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        
        // Get rotation matrix from accelerometer and magnetometer
        if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometer, magnetometer)) {
            // Get orientation angles (azimuth, pitch, roll)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            return orientationAngles
        }
        
        return null
    }
    
    /**
     * Releases all resources used by the sensor manager.
     */
    fun release() {
        stop()
        sensorDataBuffer.clear()
        isInitialized = false
    }
}
