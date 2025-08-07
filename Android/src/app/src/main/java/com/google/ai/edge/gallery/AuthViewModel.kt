        }
    }

    /**
     * Signs in a user with the given email and password
     *
     * @param email User's email address
     * @param password User's password
     */
    fun signInWithEmailAndPassword(email: String, password: String) {
        viewModelScope.launch {
            try {
                _signInState.value = AuthState.Loading

                // Validate inputs
                if (email.isBlank() || password.isBlank()) {
                    _signInState.value = AuthState.Error("Email and password cannot be empty")
                    return@launch
                }

                // Attempt to sign in
                auth.signInWithEmailAndPassword(email, password).await()

                _signInState.value = AuthState.Success("Signed in successfully")
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is FirebaseAuthInvalidUserException -> "User does not exist."
                    is FirebaseAuthInvalidCredentialsException -> "Invalid email or password."
                    else -> "Sign in failed: ${e.message}"
                }
                _signInState.value = AuthState.Error(errorMessage)
            }
        }
    }

    /**
     * Resets the sign-in state to idle
     */
    fun resetSignInState() {
        _signInState.value = AuthState.Idle
    }

    /**
     * Resets the sign-up state to idle
     */
    fun resetSignUpState() {
        _signUpState.value = AuthState.Idle
    }
}
package com.google.ai.edge.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Authentication state representing the current UI state for auth operations
 */
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val message: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * ViewModel that handles authentication operations including email/password and Google sign-in
 */
@HiltViewModel
class AuthViewModel @Inject constructor() : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // State flows for sign-in and sign-up operations
    private val _signInState = MutableStateFlow<AuthState>(AuthState.Idle)
    val signInState: StateFlow<AuthState> = _signInState.asStateFlow()

    private val _signUpState = MutableStateFlow<AuthState>(AuthState.Idle)
    val signUpState: StateFlow<AuthState> = _signUpState.asStateFlow()

    /**
     * Creates a new user account with the given email and password
     *
     * @param email User's email address
     * @param password User's password
     */
    fun createUserWithEmailAndPassword(email: String, password: String) {
        viewModelScope.launch {
            try {
                _signUpState.value = AuthState.Loading

                // Validate inputs
                if (email.isBlank() || password.isBlank()) {
                    _signUpState.value = AuthState.Error("Email and password cannot be empty")
                    return@launch
                }

                // Attempt to create the user
                auth.createUserWithEmailAndPassword(email, password).await()

                _signUpState.value = AuthState.Success("Account created successfully")
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is FirebaseAuthWeakPasswordException -> "Password is too weak. Please use a stronger password."
                    is FirebaseAuthInvalidCredentialsException -> "Invalid email format."
                    is FirebaseAuthUserCollisionException -> "This email is already in use."
                    else -> "Sign up failed: ${e.message}"
                }
                _signUpState.value = AuthState.Error(errorMessage)
            }
