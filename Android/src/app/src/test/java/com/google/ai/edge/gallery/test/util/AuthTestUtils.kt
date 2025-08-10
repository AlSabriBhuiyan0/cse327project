package com.google.ai.edge.gallery.test.util

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput

/**
 * Utility functions for testing authentication flows.
 */
object AuthTestUtils {

    /**
     * Fills out the login form with the provided credentials.
     */
    fun SemanticsNodeInteractionsProvider.fillLoginForm(
        email: String,
        password: String
    ) {
        onNodeWithText("Email").performTextInput(email)
        onNodeWithText("Password").performTextInput(password)
    }

    /**
     * Fills out the sign-up form with the provided user details.
     */
    fun SemanticsNodeInteractionsProvider.fillSignUpForm(
        name: String,
        email: String,
        password: String,
        confirmPassword: String = password
    ) {
        onNodeWithText("Full Name").performTextInput(name)
        onNodeWithText("Email").performTextInput(email)
        onNodeWithText("Password").performTextInput(password)
        onNodeWithText("Confirm Password").performTextInput(confirmPassword)
    }

    /**
     * Fills out the forgot password form with the provided email.
     */
    fun SemanticsNodeInteractionsProvider.fillForgotPasswordForm(
        email: String
    ) {
        onNodeWithText("Email").performTextInput(email)
    }

    /**
     * Clicks the sign-in button.
     */
    fun SemanticsNodeInteractionsProvider.clickSignInButton() {
        onNodeWithText("SIGN IN").performClick()
    }

    /**
     * Clicks the sign-up button.
     */
    fun SemanticsNodeInteractionsProvider.clickSignUpButton() {
        onNodeWithText("SIGN UP").performClick()
    }

    /**
     * Clicks the send reset link button.
     */
    fun SemanticsNodeInteractionsProvider.clickSendResetLinkButton() {
        onNodeWithText("SEND RESET LINK").performClick()
    }

    /**
     * Navigates to the sign-up screen from the login screen.
     */
    fun SemanticsNodeInteractionsProvider.navigateToSignUp() {
        onNodeWithText("Sign up").performClick()
    }

    /**
     * Navigates to the forgot password screen from the login screen.
     */
    fun SemanticsNodeInteractionsProvider.navigateToForgotPassword() {
        onNodeWithText("Forgot password?").performClick()
    }

    /**
     * Navigates to the login screen from the sign-up screen.
     */
    fun SemanticsNodeInteractionsProvider.navigateToLoginFromSignUp() {
        onNodeWithText("Sign in").performClick()
    }

    /**
     * Navigates back using the back button.
     */
    fun SemanticsNodeInteractionsProvider.navigateBack() {
        onNodeWithText("Back").performClick()
    }

    /**
     * Asserts that the login screen is displayed.
     */
    fun SemanticsNodeInteractionsProvider.assertLoginScreenIsDisplayed() {
        onNodeWithText("Sign In").assertExists()
        onNodeWithText("Email").assertExists()
        onNodeWithText("Password").assertExists()
    }

    /**
     * Asserts that the sign-up screen is displayed.
     */
    fun SemanticsNodeInteractionsProvider.assertSignUpScreenIsDisplayed() {
        onNodeWithText("Create Account").assertExists()
        onNodeWithText("Full Name").assertExists()
        onNodeWithText("Email").assertExists()
        onNodeWithText("Password").assertExists()
        onNodeWithText("Confirm Password").assertExists()
    }

    /**
     * Asserts that the forgot password screen is displayed.
     */
    fun SemanticsNodeInteractionsProvider.assertForgotPasswordScreenIsDisplayed() {
        onNodeWithText("Reset Password").assertExists()
        onNodeWithText("Email").assertExists()
    }

    /**
     * Asserts that the success state is shown after a successful operation.
     */
    fun SemanticsNodeInteractionsProvider.assertSuccessStateIsDisplayed() {
        onNodeWithText("Success").assertExists()
    }

    /**
     * Asserts that an error message is displayed.
     */
    fun SemanticsNodeInteractionsProvider.assertErrorMessageIsDisplayed(message: String) {
        onNodeWithText(message).assertExists()
    }

    /**
     * Asserts that the loading indicator is displayed.
     */
    fun SemanticsNodeInteractionsProvider.assertLoadingIsDisplayed() {
        onNodeWithText("Loading").assertExists()
    }
}
