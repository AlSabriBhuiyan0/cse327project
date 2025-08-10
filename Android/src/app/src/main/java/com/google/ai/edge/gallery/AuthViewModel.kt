package com.google.ai.edge.gallery

import android.app.Activity
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.auth.GoogleAuthManager
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * ViewModel responsible for handling authentication logic and state management.
 * Works with both email/password and Google authentication flows.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val googleAuthManager: GoogleAuthManager
) : ViewModel() {

    // State holders for authentication operations
    private val _signInState = MutableStateFlow<AuthState>(AuthState.Idle)
    val signInState = _signInState.asStateFlow()

    private val _signUpState = MutableStateFlow<AuthState>(AuthState.Idle)
    val signUpState = _signUpState.asStateFlow()
    
    // State for Google Sign-In
    private val _googleSignInState = MutableStateFlow<GoogleSignInState>(GoogleSignInState.Idle)
    val googleSignInState = _googleSignInState.asStateFlow()

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
     * Initiates the Google Sign-In flow
     *
     * @param activity The activity that will handle the sign-in result
     * @param launcher The launcher that will handle the sign-in intent
     */
    fun signInWithGoogle(
        activity: Activity,
        launcher: (IntentSenderRequest) -> Unit
    ) {
        viewModelScope.launch {
            try {
                _signInState.value = AuthState.Loading
                _googleSignInState.value = GoogleSignInState.Loading
                
                googleAuthManager.signIn(activity) { intentSender ->
                    launcher(IntentSenderRequest.Builder(intentSender).build())
                }
                
            } catch (e: Exception) {
                val message = when (e) {
                    is FirebaseAuthInvalidCredentialsException -> "Invalid Google credentials. Please try again."
                    is FirebaseAuthInvalidUserException -> "Your account could not be found."
                    else -> "Google sign in failed: ${e.message ?: "Unknown error"}"
                }
                _signInState.value = AuthState.Error(message)
                _googleSignInState.value = GoogleSignInState.Error(message)
            }
        }
    }
    
    /**
     * Handles the result of a Google Sign-In attempt
     * 
     * @param result The result from the Google Sign-In flow
     */
    fun handleGoogleSignInResult(result: Result<GoogleSignInAccount>) {
        viewModelScope.launch {
            try {
                _signInState.value = AuthState.Loading
                _googleSignInState.value = GoogleSignInState.Loading
                
                result.onSuccess { account ->
                    // Sign in with Firebase using the Google account
                    val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                    auth.signInWithCredential(credential).await()
                    
                    _signInState.value = AuthState.Success("Signed in successfully with Google")
                    _googleSignInState.value = GoogleSignInState.Success(account)
                }.onFailure { exception ->
                    val message = when (exception) {
                        is FirebaseAuthInvalidCredentialsException -> "Invalid Google credentials. Please try again."
                        is FirebaseAuthInvalidUserException -> "Your account could not be found."
                        else -> "Google sign in failed: ${exception.message ?: "Unknown error"}"
                    }
                    _signInState.value = AuthState.Error(message)
                    _googleSignInState.value = GoogleSignInState.Error(message)
                }
            } catch (e: Exception) {
                val message = "Error during Google Sign-In: ${e.message ?: "Unknown error"}"
                _signInState.value = AuthState.Error(message)
                _googleSignInState.value = GoogleSignInState.Error(message)
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

/**
 * Represents the state of a Google Sign-In operation
 */
sealed class GoogleSignInState {
    object Idle : GoogleSignInState()
    object Loading : GoogleSignInState()
    data class Success(val account: GoogleSignInAccount) : GoogleSignInState()
    data class Error(val message: String) : GoogleSignInState()
}
