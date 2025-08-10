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

class ForgotPasswordScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockViewModel: AuthViewModel = mockk(relaxed = true)
    private val mockOnBack: () -> Unit = mockk(relaxed = true)
    private val mockOnEmailSent: () -> Unit = mockk(relaxed = true)

    @Test
    fun forgotPasswordScreen_displaysCorrectTitle() {
        // When
        setForgotPasswordScreen()

        // Then
        composeTestRule.onNodeWithText("Reset Password").assertExists()
        composeTestRule.onNodeWithText("Forgot your password?").assertExists()
    }

    @Test
    fun forgotPasswordScreen_hasEmailField() {
        // When
        setForgotPasswordScreen()

        // Then
        composeTestRule.onNodeWithText("Email").assertExists()
    }

    @Test
    fun forgotPasswordScreen_hasSendResetLinkButtonDisabledByDefault() {
        // When
        setForgotPasswordScreen()

        // Then
        composeTestRule.onNodeWithText("SEND RESET LINK").assertIsNotEnabled()
    }

    @Test
    fun forgotPasswordScreen_enablesButtonWhenEmailIsValid() {
        // When
        setForgotPasswordScreen()
        
        // Enter valid email
        composeTestRule.onNodeWithText("Email").performTextInput("test@example.com")

        // Then
        composeTestRule.onNodeWithText("SEND RESET LINK").assertIsEnabled()
    }

    @Test
    fun forgotPasswordScreen_showsErrorForInvalidEmail() {
        // When
        setForgotPasswordScreen()
        
        // Enter invalid email
        composeTestRule.onNodeWithText("Email").performTextInput("invalid-email")

        // Then
        composeTestRule.onNodeWithText("Invalid email format").assertExists()
    }

    @Test
    fun forgotPasswordScreen_showsBackButton() {
        // When
        setForgotPasswordScreen()

        // Then
        composeTestRule.onNodeWithContentDescription("Back").assertExists()
    }

    @Test
    fun forgotPasswordScreen_callsOnBackWhenBackButtonClicked() {
        // When
        setForgotPasswordScreen()
        
        // Click back button
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // Then
        verify { mockOnBack() }
    }

    @Test
    fun forgotPasswordScreen_showsLoadingStateWhenSendingEmail() {
        // Given
        setForgotPasswordScreen(showLoading = true)

        // Then
        // Verify loading indicator is shown
        composeTestRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }

    @Test
    fun forgotPasswordScreen_showsSuccessStateWhenEmailSent() {
        // Given
        val testEmail = "test@example.com"
        setForgotPasswordScreen(emailSent = true, email = testEmail)

        // Then
        composeTestRule.onNodeWithText("Check your email").assertIsDisplayed()
        composeTestRule.onNodeWithText(testEmail).assertIsDisplayed()
        composeTestRule.onNodeWithText("Back to login").assertIsDisplayed()
    }

    @Test
    fun forgotPasswordScreen_callsOnBackWhenBackToLoginClicked() {
        // Given
        setForgotPasswordScreen(emailSent = true)

        // When
        composeTestRule.onNodeWithText("Back to login").performClick()

        // Then
        verify { mockOnBack() }
    }

    @Test
    fun forgotPasswordScreen_showsErrorWhenSendingFails() {
        // Given
        val errorMessage = "Failed to send reset email"
        setForgotPasswordScreen(authError = errorMessage)

        // Then
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    private fun setForgotPasswordScreen(
        showLoading: Boolean = false,
        emailSent: Boolean = false,
        email: String = "",
        authError: String? = null
    ) {
        // TODO: Uncomment and implement when ForgotPasswordScreen is available
        // composeTestRule.setContent {
        //     ForgotPasswordScreen(
        //         viewModel = mockViewModel,
        //         onBack = mockOnBack,
        //         onEmailSent = mockOnEmailSent
        //     )
        // }
    }
}
