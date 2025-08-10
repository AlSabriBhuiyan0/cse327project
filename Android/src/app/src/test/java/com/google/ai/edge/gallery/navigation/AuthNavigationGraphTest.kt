package com.google.ai.edge.gallery.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import com.google.ai.edge.gallery.ui.auth.AuthViewModel
import com.google.ai.edge.gallery.ui.auth.screens.AuthScreen
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

class AuthNavigationGraphTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockViewModel: AuthViewModel = mockk(relaxed = true)
    private val mockOnAuthSuccess: () -> Unit = {}
    private val mockOnBack: () -> Unit = {}

    @Test
    fun authNavigationGraph_startsWithLoginScreen() {
        // When
        setAuthNavigationGraph()

        // Then
        composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
    }

    @Test
    fun authNavigationGraph_navigatesToSignUpScreen() {
        // Given
        setAuthNavigationGraph()

        // When
        composeTestRule.onNodeWithText("Sign up").performClick()

        // Then
        composeTestRule.onNodeWithText("Create Account").assertIsDisplayed()
    }

    @Test
    fun authNavigationGraph_navigatesToForgotPasswordScreen() {
        // Given
        setAuthNavigationGraph()

        // When
        composeTestRule.onNodeWithText("Forgot password?").performClick()

        // Then
        composeTestRule.onNodeWithText("Reset Password").assertIsDisplayed()
    }

    @Test
    fun authNavigationGraph_navigatesBackFromSignUpToLogin() {
        // Given
        setAuthNavigationGraph()
        composeTestRule.onNodeWithText("Sign up").performClick()
        
        // When
        composeTestRule.onNodeWithText("Back").performClick()

        // Then
        composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
    }

    @Test
    fun authNavigationGraph_navigatesBackFromForgotPasswordToLogin() {
        // Given
        setAuthNavigationGraph()
        composeTestRule.onNodeWithText("Forgot password?").performClick()
        
        // When
        composeTestRule.onNodeWithText("Back").performClick()

        // Then
        composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
    }

    @Test
    fun authNavigationGraph_showsLoadingState() {
        // Given
        setAuthNavigationGraph(showLoading = true)

        // Then
        // Verify loading indicator is shown
        composeTestRule.onNodeWithText("Loading").assertIsDisplayed()
    }

    @Test
    fun authNavigationGraph_showsErrorState() {
        // Given
        val errorMessage = "Authentication failed"
        setAuthNavigationGraph(authError = errorMessage)

        // Then
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    private fun setAuthNavigationGraph(
        startDestination: String = AuthScreen.Login.route,
        showLoading: Boolean = false,
        authError: String? = null
    ) {
        // TODO: Uncomment and implement when AuthNavigationGraph is available
        // composeTestRule.setContent {
        //     val navController = rememberNavController()
        //     AuthNavigationGraph(
        //         navController = navController,
        //         viewModel = mockViewModel,
        //         onAuthSuccess = mockOnAuthSuccess,
        //         onBack = mockOnBack,
        //         startDestination = startDestination,
        //         showLoading = showLoading,
        //         authError = authError
        //     )
        // }
    }
}
