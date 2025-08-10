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

class SignUpScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockViewModel: AuthViewModel = mockk(relaxed = true)
    private val mockOnNavigateToLogin: () -> Unit = mockk(relaxed = true)
    private val mockOnSignUpSuccess: () -> Unit = mockk(relaxed = true)
    private val mockOnBack: () -> Unit = mockk(relaxed = true)

    @Test
    fun signUpScreen_displaysCorrectTitle() {
        // When
        setSignUpScreen()

        // Then
        composeTestRule.onNodeWithText("Create Account").assertExists()
    }

    @Test
    fun signUpScreen_hasAllRequiredFields() {
        // When
        setSignUpScreen()

        // Then
        composeTestRule.onNodeWithText("Full Name").assertExists()
        composeTestRule.onNodeWithText("Email").assertExists()
        composeTestRule.onNodeWithText("Password").assertExists()
        composeTestRule.onNodeWithText("Confirm Password").assertExists()
    }

    @Test
    fun signUpScreen_hasSignUpButtonDisabledByDefault() {
        // When
        setSignUpScreen()

        // Then
        composeTestRule.onNodeWithText("SIGN UP").assertIsNotEnabled()
    }

    @Test
    fun signUpScreen_enablesSignUpButtonWhenFormIsValid() {
        // When
        setSignUpScreen()
        
        // Enter valid form data
        composeTestRule.onNodeWithText("Full Name").performTextInput("Test User")
        composeTestRule.onNodeWithText("Email").performTextInput("test@example.com")
        composeTestRule.onNodeWithText("Password").performTextInput("password123")
        composeTestRule.onNodeWithText("Confirm Password").performTextInput("password123")

        // Then
        composeTestRule.onNodeWithText("SIGN UP").assertIsEnabled()
    }

    @Test
    fun signUpScreen_showsErrorForInvalidEmail() {
        // When
        setSignUpScreen()
        
        // Enter invalid email
        composeTestRule.onNodeWithText("Email").performTextInput("invalid-email")

        // Then
        composeTestRule.onNodeWithText("Invalid email format").assertExists()
    }

    @Test
    fun signUpScreen_showsErrorForMismatchedPasswords() {
        // When
        setSignUpScreen()
        
        // Enter mismatched passwords
        composeTestRule.onNodeWithText("Password").performTextInput("password123")
        composeTestRule.onNodeWithText("Confirm Password").performTextInput("differentpassword")

        // Then
        composeTestRule.onNodeWithText("Passwords do not match").assertExists()
    }

    @Test
    fun signUpScreen_showsErrorForShortPassword() {
        // When
        setSignUpScreen()
        
        // Enter short password
        composeTestRule.onNodeWithText("Password").performTextInput("12345")

        // Then
        composeTestRule.onNodeWithText("Password must be at least 6 characters").assertExists()
    }

    @Test
    fun signUpScreen_navigatesToLoginWhenSignInLinkClicked() {
        // When
        setSignUpScreen()
        
        // Click on sign in link
        composeTestRule.onNodeWithText("Already have an account?").assertExists()
        composeTestRule.onNodeWithText("Sign in").performClick()

        // Then
        verify { mockOnNavigateToLogin() }
    }

    @Test
    fun signUpScreen_showsBackButton() {
        // When
        setSignUpScreen()

        // Then
        composeTestRule.onNodeWithContentDescription("Back").assertExists()
    }

    @Test
    fun signUpScreen_callsOnBackWhenBackButtonClicked() {
        // When
        setSignUpScreen()
        
        // Click back button
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // Then
        verify { mockOnBack() }
    }

    @Test
    fun signUpScreen_showsLoadingStateWhenSigningUp() {
        // Given
        setSignUpScreen(showLoading = true)

        // Then
        // Verify loading indicator is shown
        composeTestRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }

    @Test
    fun signUpScreen_showsErrorWhenRegistrationFails() {
        // Given
        val errorMessage = "Registration failed"
        setSignUpScreen(authError = errorMessage)

        // Then
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    private fun setSignUpScreen(
        showLoading: Boolean = false,
        authError: String? = null
    ) {
        // TODO: Uncomment and implement when SignUpScreen is available
        // composeTestRule.setContent {
        //     SignUpScreen(
        //         viewModel = mockViewModel,
        //         onNavigateToLogin = mockOnNavigateToLogin,
        //         onSignUpSuccess = mockOnSignUpSuccess,
        //         onBack = mockOnBack
        //     )
        // }
    }
}
