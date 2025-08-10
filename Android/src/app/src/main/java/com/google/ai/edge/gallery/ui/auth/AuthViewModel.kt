package com.google.ai.edge.gallery.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.model.AuthResult
import com.google.ai.edge.gallery.data.model.LoginFormState
import com.google.ai.edge.gallery.data.model.SignUpFormState
import com.google.ai.edge.gallery.data.model.User
import com.google.ai.edge.gallery.data.repository.AuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * ViewModel handling authentication state and business logic.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _loginState = MutableStateFlow(LoginFormState())
    val loginState: StateFlow<LoginFormState> = _loginState.asStateFlow()

    private val _signUpState = MutableStateFlow(SignUpFormState())
    val signUpState: StateFlow<SignUpFormState> = _signUpState.asStateFlow()

    private val _authState = MutableStateFlow<AuthResult<User>>(AuthResult.Loading())
    val authState: StateFlow<AuthResult<User>> = _authState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        checkCurrentUser()
    }

    /**
     * Check if a user is currently logged in.
     */
    private fun checkCurrentUser() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                authRepository.getCurrentUser()?.let { user ->
                    _authState.value = AuthResult.Success(user)
                } ?: run {
                    _authState.value = AuthResult.Error("Not logged in")
                }
            } catch (e: Exception) {
                _authState.value = AuthResult.Error("Error checking authentication status")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Update login form state when input fields change.
     */
    fun onLoginChange(email: String, password: String) {
        val emailError = if (email.isBlank()) "Email is required" 
            else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) "Invalid email format" 
            else null

        val passwordError = if (password.isBlank()) "Password is required"
            else if (password.length < 6) "Password must be at least 6 characters"
            else null

        _loginState.value = LoginFormState(
            email = email,
            password = password,
            emailError = emailError,
            passwordError = passwordError,
            isFormValid = emailError == null && passwordError == null
        )
    }

    /**
     * Update signup form state when input fields change.
     */
    fun onSignUpChange(
        email: String,
        password: String,
        confirmPassword: String,
        name: String
    ) {
        val emailError = if (email.isBlank()) "Email is required" 
            else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) "Invalid email format" 
            else null

        val passwordError = if (password.isBlank()) "Password is required"
            else if (password.length < 6) "Password must be at least 6 characters"
            else null

        val confirmPasswordError = if (confirmPassword != password) "Passwords do not match"
            else null

        val nameError = if (name.isBlank()) "Name is required"
            else null

        _signUpState.value = SignUpFormState(
            email = email,
            password = password,
            confirmPassword = confirmPassword,
            name = name,
            emailError = emailError,
            passwordError = passwordError,
            confirmPasswordError = confirmPasswordError,
            nameError = nameError,
            isFormValid = emailError == null && passwordError == null && 
                         confirmPasswordError == null && nameError == null
        )
    }

    /**
     * Sign in with email and password.
     */
    fun signInWithEmailAndPassword(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                authRepository.signInWithEmailAndPassword(email, password)
                    .collect { result ->
                        when (result) {
                            is AuthResult.Success -> {
                                authRepository.getCurrentUser()?.let { user ->
                                    _authState.value = AuthResult.Success(user)
                                } ?: run {
                                    _authState.value = AuthResult.Error("User not found")
                                }
                            }
                            is AuthResult.Error -> {
                                _authState.value = AuthResult.Error(result.message)
                            }
                            is AuthResult.Loading -> {
                                // Loading state handled by _isLoading
                            }
                        }
                    }
            } catch (e: Exception) {
                _authState.value = AuthResult.Error(e.message ?: "Sign in failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Sign up with email and password.
     */
    fun signUpWithEmailAndPassword(email: String, password: String, name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                authRepository.signUpWithEmailAndPassword(email, password, name)
                    .collect { result ->
                        when (result) {
                            is AuthResult.Success -> {
                                authRepository.getCurrentUser()?.let { user ->
                                    _authState.value = AuthResult.Success(user)
                                } ?: run {
                                    _authState.value = AuthResult.Error("Registration failed")
                                }
                            }
                            is AuthResult.Error -> {
                                _authState.value = AuthResult.Error(result.message)
                            }
                            is AuthResult.Loading -> {
                                // Loading state handled by _isLoading
                            }
                        }
                    }
            } catch (e: Exception) {
                _authState.value = AuthResult.Error(e.message ?: "Registration failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Sign in with Google account.
     */
    fun signInWithGoogle(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                authRepository.signInWithGoogle(account).collect { result ->
                    when (result) {
                        is AuthResult.Success -> {
                            authRepository.getCurrentUser()?.let { user ->
                                _authState.value = AuthResult.Success(user)
                            } ?: run {
                                _authState.value = AuthResult.Error("Google sign in failed")
                            }
                        }
                        is AuthResult.Error -> {
                            _authState.value = AuthResult.Error(result.message)
                        }
                        is AuthResult.Loading -> {
                            // Loading state handled by _isLoading
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Google sign in error", e)
                _authState.value = AuthResult.Error(e.message ?: "Google sign in failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Handle Google Sign-In result.
     */
    fun handleGoogleSignInResult(task: com.google.android.gms.tasks.Task<GoogleSignInAccount>) {
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                signInWithGoogle(account)
            } else {
                _authState.value = AuthResult.Error("Google sign in failed: No account")
            }
        } catch (e: ApiException) {
            Log.e("AuthViewModel", "Google sign in failed", e)
            _authState.value = AuthResult.Error("Google sign in failed: ${e.statusCode}")
        }
    }

    /**
     * Send password reset email.
     */
    fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                authRepository.sendPasswordResetEmail(email).collect { result ->
                    when (result) {
                        is AuthResult.Success -> {
                            _authState.value = AuthResult.Success(
                                User("", email = email)
                            )
                        }
                        is AuthResult.Error -> {
                            _authState.value = AuthResult.Error(result.message)
                        }
                        is AuthResult.Loading -> {
                            // Loading state handled by _isLoading
                        }
                    }
                }
            } catch (e: Exception) {
                _authState.value = AuthResult.Error(e.message ?: "Failed to send reset email")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Sign out the current user.
     */
    fun signOut() {
        viewModelScope.launch {
            try {
                authRepository.signOut()
                _authState.value = AuthResult.Error("Signed out")
            } catch (e: Exception) {
                _authState.value = AuthResult.Error("Failed to sign out")
            }
        }
    }

    /**
     * Reset the authentication state.
     */
    fun resetAuthState() {
        _authState.value = AuthResult.Loading()
        checkCurrentUser()
    }
}