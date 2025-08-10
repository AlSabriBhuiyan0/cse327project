package com.google.ai.edge.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {

    // State holders for authentication operations
    private val _signInState = MutableStateFlow<AuthState>(AuthState.Idle)
    val signInState: StateFlow<AuthState> = _signInState

    private val _signUpState = MutableStateFlow<AuthState>(AuthState.Idle)
    val signUpState: StateFlow<AuthState> = _signUpState

    /**
     * Creates a new user with the given email and password
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

                // Attempt to create user
                auth.createUserWithEmailAndPassword(email, password).await()

                _signUpState.value = AuthState.Success("Account created successfully")
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is FirebaseAuthWeakPasswordException -> "Password is too weak."
                    is FirebaseAuthInvalidCredentialsException -> "Invalid email format."
                    is FirebaseAuthUserCollisionException -> "This email is already in use."
                    else -> "Sign up failed: ${e.message}"
                }
                _signUpState.value = AuthState.Error(errorMessage)
            }
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
     * Signs in a user with Google by using the ID token obtained from Google Sign-In
     *
     * @param idToken ID token obtained from Google Sign-In
     */
    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            try {
                _signInState.value = AuthState.Loading
                
                // Create a GoogleAuthProvider credential with the token
                val credential = GoogleAuthProvider.getCredential(idToken, null)

                // Sign in to Firebase with the Google credential
                auth.signInWithCredential(credential).await()

                _signInState.value = AuthState.Success("Signed in successfully with Google")
            } catch (e: Exception) {
                val message = when (e) {
                    is FirebaseAuthInvalidCredentialsException -> "Invalid Google credentials. Please try again."
                    is FirebaseAuthInvalidUserException -> "Your account could not be found."
                    else -> "Google sign in failed: ${e.message ?: "Unknown error"}"
                }
                _signInState.value = AuthState.Error(message)
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

/**
 * Represents the various states of an authentication operation
 */
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val message: String) : AuthState()
    data class Error(val message: String) : AuthState()
    data class Info(val message: String) : AuthState()
}
