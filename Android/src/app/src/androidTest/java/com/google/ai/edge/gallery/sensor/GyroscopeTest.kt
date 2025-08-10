package com.google.ai.edge.gallery.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.gallery.util.TestUtils
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class GyroscopeTest {
    
    private lateinit var sensorManager: SensorManager
    private lateinit var gyroscopeManager: GyroscopeManager
    private lateinit var testUtils: TestUtils
    private val testDispatcher = TestCoroutineDispatcher()
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscopeManager = GyroscopeManager(context, testDispatcher)
        testUtils = TestUtils()
    }
    
    @Test
    fun testGyroscopeDataFlow() = runBlockingTest {
        // Start collecting gyroscope data
        val job = launch {
            gyroscopeManager.rotationData.collect { rotationData ->
                // Verify rotation data is within valid ranges
                assertThat(rotationData.x).isIn(-360f..360f)
                assertThat(rotationData.y).isIn(-360f..360f)
                assertThat(rotationData.z).isIn(-360f..360f)
            }
        }
        
        // Simulate some sensor events
        simulateGyroscopeEvent(0.1f, 0.2f, 0.3f)
        
        // Verify sensor state is ACTIVE
        val sensorState = gyroscopeManager.sensorState.first { it == SensorState.ACTIVE }
        assertThat(sensorState).isEqualTo(SensorState.ACTIVE)
        
        // Cleanup
        job.cancel()
    }
    
    @Test
    fun testGyroscopeCalibration() = runBlockingTest {
        // Start gyroscope
        gyroscopeManager.start()
        
        // Wait for initial calibration
        delay(1000)
        
        // Get initial rotation data
        val initialRotation = gyroscopeManager.rotationData.first()
        
        // Simulate device rotation
        simulateGyroscopeEvent(1.0f, 0f, 0f)  // Rotate around X-axis
        
        // Get new rotation data
        delay(100)
        val newRotation = gyroscopeManager.rotationData.first()
        
        // Verify rotation values have changed
        assertThat(newRotation.x).isNotEqualTo(initialRotation.x)
        
        // Cleanup
        gyroscopeManager.stop()
    }
    
    private fun simulateGyroscopeEvent(x: Float, y: Float, z: Float) {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val event = createSensorEvent(sensor, floatArrayOf(x, y, z))
        
        // Get the listener from GyroscopeManager using reflection
        try {
            val field = gyroscopeManager.javaClass.getDeclaredField("sensorListener")
            field.isAccessible = true
            val listener = field.get(gyroscopeManager) as SensorEventListener
            
            // Simulate sensor event
            listener.onSensorChanged(event)
        } catch (e: Exception) {
            // Skip if reflection fails in test
        }
    }
    
    private fun createSensorEvent(sensor: Sensor, values: FloatArray): SensorEvent {
        return SensorEvent(3).apply {
            this.sensor = sensor
            this.values = values
            this.timestamp = System.nanoTime()
        }
    }
    
    @After
    fun cleanup() {
        gyroscopeManager.stop()
        testDispatcher.cleanupTestCoroutines()
    }
}
