package com.google.ai.edge.gallery.ui.auth.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.ui.auth.AuthViewModel
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockViewModel: AuthViewModel = mockk(relaxed = true)
    private val mockOnNavigateToSignUp: () -> Unit = mockk(relaxed = true)
    private val mockOnNavigateToForgotPassword: () -> Unit = mockk(relaxed = true)
    private val mockOnSignInSuccess: () -> Unit = mockk(relaxed = true)
    private val mockOnBack: () -> Unit = mockk(relaxed = true)

    @Test
    fun loginScreen_displaysCorrectTitle() {
        // When
        setLoginScreen()

        // Then
        composeTestRule.onNodeWithText("Sign In").assertExists()
        composeTestRule.onNodeWithText("HappyChat AI").assertExists()
    }

    @Test
    fun loginScreen_hasEmailAndPasswordFields() {
        // When
        setLoginScreen()

        // Then
        composeTestRule.onNodeWithText("Email").assertExists()
        composeTestRule.onNodeWithText("Password").assertExists()
    }

    @Test
    fun loginScreen_hasSignInButtonDisabledByDefault() {
        // When
        setLoginScreen()

        // Then
        composeTestRule.onNodeWithText("SIGN IN").assertIsNotEnabled()
    }

    @Test
    fun loginScreen_enablesSignInButtonWhenFormIsValid() {
        // When
        setLoginScreen()
        
        // Enter valid email and password
        composeTestRule.onNodeWithText("Email").performTextInput("test@example.com")
        composeTestRule.onNodeWithText("Password").performTextInput("password123")

        // Then
        composeTestRule.onNodeWithText("SIGN IN").assertIsEnabled()
    }

    @Test
    fun loginScreen_showsErrorForInvalidEmail() {
        // When
        setLoginScreen()
        
        // Enter invalid email
        composeTestRule.onNodeWithText("Email").performTextInput("invalid-email")

        // Then
        composeTestRule.onNodeWithText("Invalid email format").assertExists()
    }

    @Test
    fun loginScreen_navigatesToSignUpWhenSignUpLinkClicked() {
        // When
        setLoginScreen()
        
        // Click on sign up link
        composeTestRule.onNodeWithText("Don't have an account?").assertExists()
        composeTestRule.onNodeWithText("Sign up").performClick()

        // Then
        verify { mockOnNavigateToSignUp() }
    }

    @Test
    fun loginScreen_navigatesToForgotPasswordWhenForgotPasswordClicked() {
        // When
        setLoginScreen()
        
        // Click on forgot password link
        composeTestRule.onNodeWithText("Forgot password?").performClick()

        // Then
        verify { mockOnNavigateToForgotPassword() }
    }

    @Test
    fun loginScreen_showsBackButton() {
        // When
        setLoginScreen()

        // Then
        composeTestRule.onNodeWithContentDescription("Back").assertExists()
    }

    @Test
    fun loginScreen_callsOnBackWhenBackButtonClicked() {
        // When
        setLoginScreen()
        
        // Click back button
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // Then
        verify { mockOnBack() }
    }

    @Test
    fun loginScreen_showsLoadingStateWhenSigningIn() {
        // Given
        setLoginScreen(showLoading = true)

        // Then
        // Verify loading indicator is shown
        composeTestRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }

    @Test
    fun loginScreen_showsErrorWhenAuthFails() {
        // Given
        val errorMessage = "Authentication failed"
        setLoginScreen(authError = errorMessage)

        // Then
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    private fun setLoginScreen(
        showLoading: Boolean = false,
        authError: String? = null
    ) {
        composeTestRule.setContent {//            LoginScreen(
//                viewModel = mockViewModel,
//                onNavigateToSignUp = mockOnNavigateToSignUp,
//                onNavigateToForgotPassword = mockOnNavigateToForgotPassword,
//                onSignInSuccess = mockOnSignInSuccess,
//                onBack = mockOnBack
//            )
        }
    }
}
