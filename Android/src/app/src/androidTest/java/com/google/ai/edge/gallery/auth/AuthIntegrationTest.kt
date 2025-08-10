package com.google.ai.edge.gallery.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.gallery.MainActivity
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.repository.AuthRepository
import com.google.ai.edge.gallery.di.AppModule
import com.google.ai.edge.gallery.ui.auth.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@UninstallModules(AppModule::class)
class AuthIntegrationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var auth: FirebaseAuth

    @Inject
    lateinit var authRepository: AuthRepository

    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        hiltRule.inject()
        viewModel = AuthViewModel(authRepository)
        // Sign out before each test
        runBlocking {
            auth.signOut()
        }
    }

    @After
    fun tearDown() {
        // Clean up after each test
        runBlocking {
            try {
                auth.currentUser?.delete()?.await()
            } catch (e: Exception) {
                // User not logged in or already deleted
            }
        }
    }

    @Test
    fun testSuccessfulLoginFlow() {
        // Given - Test credentials
        val testEmail = "test@example.com"
        val testPassword = "password123"

        // When - Navigate to login screen (if not already there)
        composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
        
        // Enter credentials
        composeTestRule.onNodeWithText("Email").performTextInput(testEmail)
        composeTestRule.onNodeWithText("Password").performTextInput(testPassword)
        
        // Click login button
        composeTestRule.onNodeWithText("SIGN IN").performClick()
        
        // Then - Verify successful login (redirect to main screen or show success message)
        // Note: This will depend on your app's navigation after successful login
        // For example, if your main screen has a specific element:
        // composeTestRule.onNodeWithText("Welcome").assertIsDisplayed()
    }

    @Test
    fun testFailedLoginFlow() {
        // Given - Invalid credentials
        val invalidEmail = "nonexistent@example.com"
        val invalidPassword = "wrongpassword"

        // When - Attempt to login with invalid credentials
        composeTestRule.onNodeWithText("Email").performTextInput(invalidEmail)
        composeTestRule.onNodeWithText("Password").performTextInput(invalidPassword)
        composeTestRule.onNodeWithText("SIGN IN").performClick()
        
        // Then - Verify error message is shown
        composeTestRule.onNodeWithText("Authentication failed").assertIsDisplayed()
    }

    @Test
    fun testSignUpNavigation() {
        // When - Click on sign up link
        composeTestRule.onNodeWithText("Sign up").performClick()
        
        // Then - Verify sign up screen is displayed
        composeTestRule.onNodeWithText("Create Account").assertIsDisplayed()
    }

    @Test
    fun testForgotPasswordNavigation() {
        // When - Click on forgot password link
        composeTestRule.onNodeWithText("Forgot password?").performClick()
        
        // Then - Verify forgot password screen is displayed
        composeTestRule.onNodeWithText("Reset Password").assertIsDisplayed()
    }

    @Test
    fun testFormValidation() {
        // Test empty email
        composeTestRule.onNodeWithText("SIGN IN").performClick()
        composeTestRule.onNodeWithText("Email is required").assertIsDisplayed()
        
        // Test invalid email format
        composeTestRule.onNodeWithText("Email").performTextInput("invalid-email")
        composeTestRule.onNodeWithText("Invalid email format").assertIsDisplayed()
        
        // Test short password
        composeTestRule.onNodeWithText("Password").performTextInput("12345")
        composeTestRule.onNodeWithText("Password must be at least 6 characters").assertIsDisplayed()
    }

    @Test
    fun testSignUpFormValidation() {
        // Navigate to sign up screen
        composeTestRule.onNodeWithText("Sign up").performClick()
        
        // Test empty name
        composeTestRule.onNodeWithText("SIGN UP").performClick()
        composeTestRule.onNodeWithText("Name is required").assertIsDisplayed()
        
        // Test password mismatch
        composeTestRule.onNodeWithText("Full Name").performTextInput("Test User")
        composeTestRule.onNodeWithText("Email").performTextInput("test@example.com")
        composeTestRule.onNodeWithText("Password").performTextInput("password123")
        composeTestRule.onNodeWithText("Confirm Password").performTextInput("differentpassword")
        composeTestRule.onNodeWithText("Passwords do not match").assertIsDisplayed()
    }

    @Test
    fun testBackNavigation() {
        // Navigate to sign up screen
        composeTestRule.onNodeWithText("Sign up").performClick()
        
        // When - Press back
        composeTestRule.onNodeWithText("Back").performClick()
        
        // Then - Should be back at login screen
        composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
    }
}
