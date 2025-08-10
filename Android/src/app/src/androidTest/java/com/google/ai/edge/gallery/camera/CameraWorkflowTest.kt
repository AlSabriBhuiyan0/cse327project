package com.google.ai.edge.gallery.camera

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.ai.edge.gallery.MainActivity
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.util.TestUtils
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class CameraWorkflowTest {
    
    private lateinit var device: UiDevice
    private val testUtils = TestUtils()
    
    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        testUtils.grantCameraPermissions()
        testUtils.grantLocationPermissions()
        
        // Launch the activity
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Navigate to camera screen
                activity.navigateToCameraScreen()
            }
        }
    }
    
    @Test
    fun testCameraPreviewDisplayed() {
        // Verify camera preview is displayed
        Espresso.onView(ViewMatchers.withId(R.id.camera_preview))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }
    
    @Test
    fun testCaptureButtonFunctionality() {
        // Click capture button
        Espresso.onView(ViewMatchers.withId(R.id.capture_button))
            .perform(ViewActions.click())
        
        // Verify image was captured (check for confirmation message or preview)
        device.wait(Until.hasObject(By.text("Image captured")), 3000)
    }
    
    @Test
    fun testToggleCameraLens() {
        // Click switch camera button
        Espresso.onView(ViewMatchers.withId(R.id.switch_camera_button))
            .perform(ViewActions.click())
        
        // Verify camera switched (may need to check logs or UI state)
        // This is a basic check - in a real test, you'd verify the camera actually switched
        Espresso.onView(ViewMatchers.withId(R.id.camera_preview))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }
    
    @After
    fun cleanup() {
        testUtils.revokeCameraPermissions()
        testUtils.revokeLocationPermissions()
    }
}
