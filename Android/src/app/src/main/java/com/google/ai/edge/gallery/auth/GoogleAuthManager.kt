package com.google.ai.edge.gallery.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.RequiresApi
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.ai.edge.gallery.R
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A unified manager for handling Google authentication that supports both:
 * 1. The new Credential Manager API (preferred, API 23+)
 * 2. The legacy Google Sign-In API (fallback for older devices)
 */
@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        // Logging tag with package name for better filtering
        private const val TAG = "HappyChat:GoogleAuthManager"
        private const val RC_SIGN_IN = 9001
        
        // Error messages
        private const val ERROR_INVALID_CREDENTIAL = "Invalid or missing Google credential"
        private const val ERROR_SIGN_IN_FAILED = "Google Sign-In failed"
        private const val ERROR_SIGN_OUT_FAILED = "Sign out failed"
        private const val ERROR_TOKEN_RETRIEVAL = "Failed to retrieve authentication token"
        private const val ERROR_ACCOUNT_RETRIEVAL = "Failed to retrieve Google account"
        
        // Log messages
        private const val LOG_SIGN_IN_STARTED = "Starting Google Sign-In flow"
        private const val LOG_SIGN_IN_SUCCESS = "Google Sign-In successful"
        private const val LOG_SIGN_OUT_STARTED = "Starting sign out"
        private const val LOG_SIGN_OUT_SUCCESS = "Sign out successful"
        private const val LOG_TOKEN_RETRIEVED = "Successfully retrieved authentication token"
    }

    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    // Legacy Google Sign-In client (for older devices)
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .requestProfile()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    // New Credential Manager (for API 23+)
    private val credentialManager = CredentialManager.create(context)
    private val oneTapClient = Identity.getSignInClient(context)

    /**
     * Sign in with Google using the appropriate method based on API level
     */
    /**
     * Initiates the Google Sign-In flow using the appropriate method based on API level.
     * 
     * @param activity The activity that will handle the sign-in result
     * @param launcher The launcher that will handle the sign-in intent
     * @return A [Result] containing the authenticated [GoogleSignInAccount] or an exception
     */
    suspend fun signIn(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>? = null
    ): Result<GoogleSignInAccount> = withContext(ioDispatcher) {
        Log.d(TAG, LOG_SIGN_IN_STARTED)
        _authState.postValue(AuthState.Loading)
        
        return@withContext try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                signInWithCredentialManager(activity, launcher)
            } else {
                signInWithLegacy(activity, launcher)
            }
            
            result.onSuccess { 
                Log.i(TAG, "$LOG_SIGN_IN_SUCCESS - User: ${it.email}")
                _authState.postValue(AuthState.Success(it))
            }.onFailure { 
                Log.e(TAG, "$ERROR_SIGN_IN_FAILED: ${it.message}", it)
                _authState.postValue(AuthState.Error("$ERROR_SIGN_IN_FAILED: ${it.message}"))
            }
            
            result
        } catch (e: Exception) {
            val errorMsg = "$ERROR_SIGN_IN_FAILED: ${e.message ?: "Unknown error"}"
            Log.e(TAG, errorMsg, e)
            _authState.postValue(AuthState.Error(errorMsg))
            Result.failure(e)
        }
    }

    /**
     * Sign in using the new Credential Manager (API 23+)
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private suspend fun signInWithCredentialManager(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>?
    ): Result<GoogleSignInAccount> = withContext(ioDispatcher) {
        return@withContext try {
            Log.d(TAG, "Starting Credential Manager sign-in flow")
            
            val signInRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(
                    BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                        .setSupported(true)
                        .setServerClientId(context.getString(R.string.default_web_client_id))
                        .setFilterByAuthorizedAccounts(false)
                        .build()
                )
                .setAutoSelectEnabled(true)
                .build()

            Log.d(TAG, "Initiating Credential Manager sign-in")
            val beginSignInResult = try {
                oneTapClient.beginSignIn(signInRequest).await()
            } catch (e: Exception) {
                val errorMsg = "Failed to start Credential Manager sign-in: ${e.message}"
                Log.e(TAG, errorMsg, e)
                return@withContext Result.failure(IllegalStateException(errorMsg, e))
            }
            
            launcher?.launch(
                IntentSenderRequest.Builder(beginSignInResult.pendingIntent.intentSender).build()
            )
            
            Log.d(TAG, "Credential Manager sign-in intent launched")
            // Return a pending result since we need to wait for the activity result
            Result.failure(IllegalStateException("Waiting for activity result"))
        } catch (e: Exception) {
            val errorMsg = "Credential Manager sign-in failed: ${e.message}"
            Log.e(TAG, errorMsg, e)
            Result.failure(IllegalStateException(errorMsg, e))
        }
    }

    /**
     * Handle the sign-in result from Credential Manager
     */
    /**
     * Handles the result from the Credential Manager sign-in flow.
     * This method processes the credential response and signs in the user with Firebase.
     *
     * @param result The credential response from the Credential Manager
     * @return A [Result] containing the authenticated [GoogleSignInAccount] or an exception
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    suspend fun handleCredentialManagerResult(result: GetCredentialResponse): Result<GoogleSignInAccount> {
        return withContext(ioDispatcher) {
            try {
                val credential = result.credential
                Log.d(TAG, "Received credential type: ${credential::class.simpleName}")

                when {
                    // Handle Google credential
                    credential is CustomCredential && credential.type == "com.google.android.libraries.identity.googleid.GOOGLE_ID_TOKEN_CREDENTIAL" -> {
                        val googleIdToken = credential.data.getString("id_token")
                        Log.d(TAG, "Google ID token received: ${googleIdToken?.take(10)}...")

                        if (googleIdToken.isNullOrEmpty()) {
                            throw IllegalStateException("Google ID token is null or empty")
                        }

                        // Sign in with Firebase using the Google credential
                        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                        val authResult = firebaseAuth.signInWithCredential(authCredential).await()
                        
                        // Get the Google account details
                        val account = GoogleSignIn.getLastSignedInAccount(context)
                            ?: throw IllegalStateException("Failed to retrieve Google account")

                        Log.d(TAG, "Successfully authenticated user: ${account.email}")
                        _authState.postValue(AuthState.Success(account))
                        Result.success(account)
                    }
                    
                    // Handle password credential (if needed in the future)
                    credential is CustomCredential && credential.type == "androidx.credentials.TYPE_PASSWORD_CREDENTIAL" -> {
                        Log.w(TAG, "Password credentials are not supported")
                        Result.failure(UnsupportedOperationException("Password credentials not supported"))
                    }
                    
                    // Handle unsupported credential types
                    else -> {
                        val errorMsg = "Unsupported credential type: ${credential::class.simpleName}"
                        Log.e(TAG, errorMsg)
                        Result.failure(UnsupportedOperationException(errorMsg))
                    }
                }
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is ApiException -> {
                        val statusCode = e.statusCode
                        val statusMessage = e.status?.statusMessage ?: "No status message"
                        "Google API error ($statusCode): $statusMessage"
                    }
                    is FirebaseAuthException -> {
                        "Firebase authentication error: ${e.message}"
                    }
                    else -> {
                        "Authentication failed: ${e.message ?: "Unknown error"}"
                    }
                }
                
                Log.e(TAG, errorMessage, e)
                _authState.postValue(AuthState.Error(errorMessage))
                Result.failure(e)
            }
        }
    }

    /**
     * Fallback sign-in using legacy Google Sign-In API
     */
    private suspend fun signInWithLegacy(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>?
    ): Result<GoogleSignInAccount> = withContext(ioDispatcher) {
        Log.d(TAG, "Starting legacy Google Sign-In flow")
        
        return@withContext try {
            val signInIntent = googleSignInClient.signInIntent
            
            Log.d(TAG, "Launching legacy Google Sign-In intent")
            launcher?.launch(IntentSenderRequest.Builder(signInIntent).build())
            
            // Return a pending result since we need to wait for the activity result
            Result.failure(IllegalStateException("Waiting for activity result"))
        } catch (e: Exception) {
            val errorMsg = "Legacy Google Sign-In failed: ${e.message}"
            Log.e(TAG, errorMsg, e)
            Result.failure(IllegalStateException(errorMsg, e))
        }
    }

    /**
     * Handle the sign-in result from legacy Google Sign-In
     */
    suspend fun handleLegacySignInResult(data: Intent?): Result<GoogleSignInAccount> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val token = account.idToken
            
            if (token != null) {
                val credential = GoogleAuthProvider.getCredential(token, null)
                val authResult = firebaseAuth.signInWithCredential(credential).await()
                _authState.postValue(AuthState.Success(account))
                Result.success(account)
            } else {
                throw IllegalStateException("ID token is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle legacy sign-in result", e)
            _authState.postValue(AuthState.Error(e.message ?: "Authentication failed"))
            Result.failure(e)
        }
        try {
            val credential = result.credential
            Log.d(TAG, "Received credential type: ${credential::class.simpleName}")

            when {
                // Handle Google credential
                credential is CustomCredential && credential.type == "com.google.android.libraries.identity.googleid.GOOGLE_ID_TOKEN_CREDENTIAL" -> {
                    val googleIdToken = credential.data.getString("id_token")
                    Log.d(TAG, "Google ID token received: ${googleIdToken?.take(10)}...")

                    if (googleIdToken.isNullOrEmpty()) {
                        throw IllegalStateException("Google ID token is null or empty")
                    }

                    // Sign in with Firebase using the Google credential
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = firebaseAuth.signInWithCredential(authCredential).await()
                    
                    // Get the Google account details
                    val account = GoogleSignIn.getLastSignedInAccount(context)
                        ?: throw IllegalStateException("Failed to retrieve Google account")

                    Log.d(TAG, "Successfully authenticated user: ${account.email}")
                    _authState.postValue(AuthState.Success(account))
                    Result.success(account)
                }
                
                // Handle password credential (if needed in the future)
                credential is CustomCredential && credential.type == "androidx.credentials.TYPE_PASSWORD_CREDENTIAL" -> {
                    Log.w(TAG, "Password credentials are not supported")
                    Result.failure(UnsupportedOperationException("Password credentials not supported"))
                }
                
                // Handle unsupported credential types
                else -> {
                    val errorMsg = "Unsupported credential type: ${credential::class.simpleName}"
                    Log.e(TAG, errorMsg)
                    Result.failure(UnsupportedOperationException(errorMsg))
                }
            }
        } catch (e: Exception) {
            val errorMessage = when (e) {
                is ApiException -> {
                    val statusCode = e.statusCode
                    val statusMessage = e.status?.statusMessage ?: "No status message"
                    "Google API error ($statusCode): $statusMessage"
                }
                is FirebaseAuthException -> {
                    "Firebase authentication error: ${e.message}"
                }
                else -> {
                    "Authentication failed: ${e.message ?: "Unknown error"}"
                }
            }
            
            Log.e(TAG, errorMessage, e)
            _authState.postValue(AuthState.Error(errorMessage))
            Result.failure(e)
        }
    }
}

/**
 * Fallback sign-in using legacy Google Sign-In API
 */
private suspend fun signInWithLegacy(
    activity: Activity,
    launcher: ActivityResultLauncher<IntentSenderRequest>?
): Result<GoogleSignInAccount> = withContext(ioDispatcher) {
    Log.d(TAG, "Starting legacy Google Sign-In flow")
    
    return@withContext try {
        val signInIntent = googleSignInClient.signInIntent
        
        Log.d(TAG, "Launching legacy Google Sign-In intent")
        launcher?.launch(IntentSenderRequest.Builder(signInIntent).build())
        
        // Return a pending result since we need to wait for the activity result
        Result.failure(IllegalStateException("Waiting for activity result"))
    } catch (e: Exception) {
        val errorMsg = "Legacy Google Sign-In failed: ${e.message}"
        Log.e(TAG, errorMsg, e)
        Result.failure(IllegalStateException(errorMsg, e))
    }
}

/**
 * Handle the sign-in result from legacy Google Sign-In
 */
suspend fun handleLegacySignInResult(data: Intent?): Result<GoogleSignInAccount> {
    return try {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        val account = task.getResult(ApiException::class.java)
        val token = account.idToken
        
        if (token != null) {
            val credential = GoogleAuthProvider.getCredential(token, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            _authState.postValue(AuthState.Success(account))
            Result.success(account)
        } else {
            throw IllegalStateException("ID token is null")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to handle legacy sign-in result", e)
        _authState.postValue(AuthState.Error(e.message ?: "Authentication failed"))
        Result.failure(e)
    }
}

/**
 * Sign out the current user
 */
/**
 * Signs out the current user from both Firebase and Google Sign-In.
 * Handles any errors that occur during the sign-out process.
 */
suspend fun signOut() {
    Log.d(TAG, LOG_SIGN_OUT_STARTED)
    
    return try {
        // Sign out from Firebase
        firebaseAuth.signOut()
        
        // Sign out from Google Sign-In
        try {
            googleSignInClient.signOut().await()
            Log.i(TAG, LOG_SIGN_OUT_SUCCESS)
            _authState.postValue(AuthState.SignedOut)
        } catch (e: Exception) {
            Log.e(TAG, "Error during Google Sign-Out: ${e.message}", e)
            // Continue even if Google Sign-Out fails
            _authState.postValue(AuthState.SignedOut)
            throw e
        }
    } catch (e: Exception) {
        val errorMsg = "$ERROR_SIGN_OUT_FAILED: ${e.message}"
        Log.e(TAG, errorMsg, e)
        _authState.postValue(AuthState.Error(errorMsg))
        throw e
    }
     * Check if a user is currently signed in
     */
    /**
     * Checks if a user is currently signed in.
     * 
     * @return true if a user is signed in, false otherwise
     */
    fun isUserSignedIn(): Boolean {
        val isSignedIn = firebaseAuth.currentUser != null
        Log.d(TAG, "User signed in: $isSignedIn")
        return isSignedIn

    /**
     * Authentication state sealed class
     */
    sealed class AuthState {
        object Initial : AuthState()
        object Loading : AuthState()
        object SignedOut : AuthState()
        data class Success(val account: GoogleSignInAccount) : AuthState()
        data class Error(val message: String) : AuthState()
    }
}
