package com.google.ai.edge.gallery.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.ai.edge.gallery.ui.auth.screens.ForgotPasswordScreen
import com.google.ai.edge.gallery.ui.auth.screens.LoginScreen
import com.google.ai.edge.gallery.ui.auth.screens.SignUpScreen

/**
 * Sealed class representing the authentication screens in the app.
 */
sealed class AuthScreen(val route: String) {
    object Login : AuthScreen("login")
    object SignUp : AuthScreen("signup")
    object ForgotPassword : AuthScreen("forgot_password")
}

/**
 * Composable that handles the navigation between authentication screens.
 */
@Composable
fun AuthNavigation(
    onAuthSuccess: () -> Unit,
    onBack: () -> Unit,
    navController: NavHostController = rememberNavController(),
    startDestination: String = AuthScreen.Login.route
) {
    val viewModel: AuthViewModel = hiltViewModel()
    
    // Handle navigation based on authentication state
    LaunchedEffect(Unit) {
        viewModel.authState.collect { result ->
            if (result is AuthResult.Success) {
                onAuthSuccess()
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Login Screen
        composable(AuthScreen.Login.route) {
            LoginScreen(
                viewModel = viewModel,
                onNavigateToSignUp = {
                    navController.navigate(AuthScreen.SignUp.route) {
                        // Prevent multiple copies of the same destination in the back stack
                        launchSingleTop = true
                    }
                },
                onNavigateToForgotPassword = {
                    navController.navigate(AuthScreen.ForgotPassword.route) {
                        launchSingleTop = true
                    }
                },
                onSignInSuccess = onAuthSuccess,
                onBack = onBack
            )
        }

        // Sign Up Screen
        composable(AuthScreen.SignUp.route) {
            SignUpScreen(
                viewModel = viewModel,
                onNavigateToLogin = {
                    // Pop up to the login screen to avoid back stack buildup
                    navController.popBackStack(AuthScreen.Login.route, false)
                },
                onSignUpSuccess = onAuthSuccess,
                onBack = { navController.navigateUp() }
            )
        }

        // Forgot Password Screen
        composable(AuthScreen.ForgotPassword.route) {
            ForgotPasswordScreen(
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
                onEmailSent = {
                    // Navigate back to login with a success message
                    navController.popBackStack()
                    // Show a snackbar or toast in the login screen
                }
            )
        }
    }
}
