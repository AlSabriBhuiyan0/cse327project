package com.google.ai.edge.gallery.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import com.google.ai.edge.gallery.data.model.AuthResult
import com.google.ai.edge.gallery.data.model.User
import com.google.ai.edge.gallery.ui.auth.screens.AuthScreen
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

class AuthNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockOnAuthSuccess: () -> Unit = mockk(relaxed = true)
    private val mockOnBack: () -> Unit = mockk(relaxed = true)

    @Test
    fun authNavigation_showsLoginScreenByDefault() {
        // When
        setAuthNavigation()

        // Then
        composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
        composeTestRule.onNodeWithText("Don't have an account?").assertIsDisplayed()
    }

    @Test
    fun authNavigation_navigatesToSignUpWhenSignUpLinkClicked() {
        // Given
        setAuthNavigation()

        // When
        composeTestRule.onNodeWithText("Sign up").performClick()

        // Then
        composeTestRule.onNodeWithText("Create Account").assertIsDisplayed()
    }

    @Test
    fun authNavigation_navigatesToForgotPasswordWhenForgotPasswordClicked() {
        // Given
        setAuthNavigation()

        // When
        composeTestRule.onNodeWithText("Forgot password?").performClick()

        // Then
        composeTestRule.onNodeWithText("Reset Password").assertIsDisplayed()
    }

    @Test
    fun authNavigation_navigatesBackFromSignUpToLogin() {
        // Given
        setAuthNavigation(startDestination = AuthScreen.SignUp.route)
        
        // When back is pressed (simulated by clicking back button)
        composeTestRule.onNodeWithText("Back").performClick()

        // Then we should see the login screen
        composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
    }

    @Test
    fun authNavigation_callsOnAuthSuccessWhenAuthIsSuccessful() {
        // Given
        val testUser = User("test123", "test@example.com", "Test User")
        
        // Simulate successful authentication
        setAuthNavigation(
            authResult = AuthResult.Success(testUser)
        )

        // Then
        verify { mockOnAuthSuccess() }
    }

    @Test
    fun authNavigation_callsOnBackWhenBackButtonPressed() {
        // Given
        setAuthNavigation()
        
        // When back is pressed (simulated by clicking back button)
        composeTestRule.onNodeWithText("Back").performClick()

        // Then
        verify { mockOnBack() }
    }

    private fun setAuthNavigation(
        startDestination: String = AuthScreen.Login.route,
        authResult: AuthResult<User> = AuthResult.Loading()
    ) {
        // TODO: Uncomment and implement when AuthNavigation is available
        // composeTestRule.setContent {
        //     val navController = rememberNavController()
        //     AuthNavigation(
        //         onAuthSuccess = mockOnAuthSuccess,
        //         onBack = mockOnBack,
        //         navController = navController,
        //         startDestination = startDestination
        //     )
        // }
    }
}
