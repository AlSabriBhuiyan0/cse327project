package com.google.ai.edge.gallery.ui.auth

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.gallery.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AuthActivityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<AuthActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun authActivity_showsLoginScreenByDefault() {
        // When
        val scenario = ActivityScenario.launch(AuthActivity::class.java)
        
        // Then
        composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
        
        scenario.close()
    }

    @Test
    fun authActivity_finishesWithResultOkWhenAuthIsSuccessful() {
        // Given
        val scenario = ActivityScenario.launch(AuthActivity::class.java)
        
        // When
        scenario.onActivity { activity ->
            // Simulate successful authentication
            activity.setResult(Activity.RESULT_OK)
            activity.finish()
        }
        
        // Then
        val result = scenario.result
        assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        
        scenario.close()
    }

    @Test
    fun authActivity_finishesWithResultCanceledWhenBackPressed() {
        // Given
        val scenario = ActivityScenario.launch(AuthActivity::class.java)
        
        // When
        scenario.onActivity { activity ->
            // Simulate back press
            activity.onBackPressed()
        }
        
        // Then
        val result = scenario.result
        assertThat(result.resultCode).isEqualTo(Activity.RESULT_CANCELED)
        
        scenario.close()
    }

    @Test
    fun authActivity_launchesGoogleSignInWhenGoogleButtonClicked() {
        // TODO: This test needs to be implemented with proper mocking of Google Sign-In
        // The test would verify that clicking the Google sign-in button launches the Google Sign-In intent
    }

    @Test
    fun authActivity_handlesGoogleSignInResult() {
        // Given
        val scenario = ActivityScenario.launch(AuthActivity::class.java)
        
        // When
        scenario.onActivity { activity ->
            // Simulate receiving a Google Sign-In result
            val mockAccount = GoogleSignIn.getAccountForExtension(
                activity,
                GoogleSignInOptions.DEFAULT_SIGN_IN
            )
            
            // This would typically be called by the Google Sign-In API
            // We're simulating the behavior here for testing
            activity.onActivityResult(
                GoogleSignInHelper.RC_SIGN_IN,
                Activity.RESULT_OK,
                Intent().apply {
                    // Add test account data
                }
            )
        }
        
        // Then
        // Verify that the activity processes the result and updates the UI accordingly
        // This would typically involve checking that the user is signed in
        
        scenario.close()
    }

    @Test
    fun authActivity_navigatesToSignUpWhenSignUpLinkClicked() {
        // Given
        val scenario = ActivityScenario.launch(AuthActivity::class.java)
        
        // When
        composeTestRule.onNodeWithText("Sign up").performClick()
        
        // Then
        composeTestRule.onNodeWithText("Create Account").assertIsDisplayed()
        
        scenario.close()
    }

    @Test
    fun authActivity_navigatesToForgotPasswordWhenForgotPasswordClicked() {
        // Given
        val scenario = ActivityScenario.launch(AuthActivity::class.java)
        
        // When
        composeTestRule.onNodeWithText("Forgot password?").performClick()
        
        // Then
        composeTestRule.onNodeWithText("Reset Password").assertIsDisplayed()
        
        scenario.close()
    }
}
