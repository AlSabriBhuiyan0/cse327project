package com.google.ai.edge.gallery.bridge.gmail

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.bridge.BridgeMessage
import com.google.ai.edge.gallery.bridge.MessageBridgeRepository
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.googleapis.auth.oauth2.GoogleAccountCredential
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for handling Gmail OAuth 2.0 authentication and token management.
 *
 * This service manages the Google Sign-In flow, token refresh, and Gmail API client initialization.
 * It provides a reactive state flow for authentication state changes.
 */
@Singleton
class GmailAuthService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val errorHandler: GmailErrorHandler
) {
    companion object {
        private const val TAG = "GmailAuthService"
        private const val TOKEN_REFRESH_THRESHOLD_MINUTES = 5L
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
    }
    
    // Scopes required for Gmail API access
    val SCOPES = listOf(
        GmailScopes.GMAIL_READONLY,
        GmailScopes.GMAIL_SEND,
        GmailScopes.GMAIL_LABELS,
        GmailScopes.GMAIL_MODIFY
    )

    private val gson = GsonFactory.getDefaultInstance()
    private val httpTransport = NetHttpTransport()
    
    private val _authState = MutableStateFlow<GmailAuthState>(GmailAuthState.SignedOut)
    val authState: StateFlow<GmailAuthState> = _authState
    
    private var lastTokenRefreshTime: Long = 0
    private var refreshToken: String? = null
    private var accessToken: String? = null
    private var tokenExpiryTime: Long = 0

    private val googleSignInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, getGoogleSignInOptions())
    }
    
    @Volatile
    private var gmailService: Gmail? = null

    private var authCallback: ((Result<GmailAuthResult>) -> Unit)? = null
    
    /**
     * Gets a GoogleSignInOptions object with the required scopes
     */
    fun getGoogleSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(GmailScopes.GMAIL_READONLY))
            .requestScopes(Scope(GmailScopes.GMAIL_SEND))
            .requestScopes(Scope(GmailScopes.GMAIL_LABELS))
            .requestScopes(Scope(GmailScopes.GMAIL_MODIFY))
            .requestServerAuthCode(context.getString(R.string.default_web_client_id))
            .build()
    }
    
    /**
     * Initializes the Gmail service with the provided Google account.
     *
     * @param account The Google account to use for authentication
     * @param forceRefresh If true, forces a new token refresh even if current token is valid
     * @return The initialized Gmail service
     * @throws GmailAuthException If authentication or initialization fails
     */
    @Throws(GmailAuthException::class)
    suspend fun initializeGmailService(account: GoogleSignInAccount, forceRefresh: Boolean = false): Gmail {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("Initializing Gmail service for account: ${account.email}")
                
                // Check if we need to refresh the token
                val now = System.currentTimeMillis()
                val tokenNeedsRefresh = forceRefresh || 
                    now >= tokenExpiryTime - TimeUnit.MINUTES.toMillis(TOKEN_REFRESH_THRESHOLD_MINUTES)
                
                if (tokenNeedsRefresh) {
                    Timber.d("Access token needs refresh, refreshing...")
                    refreshAccessToken(account)
                }
                
                val credential = try {
                    GoogleAccountCredential.usingOAuth2(
                        context,
                        SCOPES
                    ).setBackOff(ExponentialBackOff()).apply {
                        selectedAccount = account.account!!
                    }
                } catch (e: Exception) {
                    val error = GmailAuthException(
                        "Failed to create credentials: ${e.message}",
                        GmailAuthError.CREDENTIALS_ERROR,
                        e
                    )
                    Timber.e(error, "Error creating credentials")
                    throw error
                }
                
                try {
                    // Build the Gmail service
                    val service = Gmail.Builder(httpTransport, gson, credential)
                        .setApplicationName(context.getString(R.string.app_name))
                        .build()
                    
                    // Test the connection with a lightweight API call
                    service.users().getProfile("me").execute()
                    
                    gmailService = service
                    _authState.value = GmailAuthState.SignedIn(account, service)
                    lastTokenRefreshTime = System.currentTimeMillis()
                    
                    Timber.d("Gmail service initialized successfully for ${account.email}")
                    service
                } catch (e: GoogleJsonResponseException) {
                    val error = errorHandler.handleError(e, "Failed to initialize Gmail service")
                    val authError = GmailAuthException(
                        error.message,
                        GmailAuthError.API_ERROR,
                        e
                    )
                    _authState.value = GmailAuthState.Error(authError)
                    throw authError
                } catch (e: IOException) {
                    val error = errorHandler.handleError(e, "Network error initializing Gmail service")
                    val authError = GmailAuthException(
                        error.message,
                        GmailAuthError.NETWORK_ERROR,
                        e
                    )
                    _authState.value = GmailAuthState.Error(authError)
                    throw authError
                } catch (e: Exception) {
                    val error = errorHandler.handleError(e, "Failed to initialize Gmail service")
                    val authError = GmailAuthException(
                        error.message,
                        GmailAuthError.UNKNOWN,
                        e
                    )
                    _authState.value = GmailAuthState.Error(authError)
                    throw authError
                }
            } catch (e: GmailAuthException) {
                // Re-throw GmailAuthException as is
                _authState.value = GmailAuthState.Error(e)
                throw e
            } catch (e: Exception) {
                // Wrap other exceptions in GmailAuthException
                val error = errorHandler.handleError(e, "Unexpected error initializing Gmail service")
                val authError = GmailAuthException(
                    error.message,
                    GmailAuthError.UNKNOWN,
                    e
                )
                _authState.value = GmailAuthState.Error(authError)
                throw authError
            }
        }
    }
    
    /**
     * Refreshes the access token for the current account
     * @param account The Google account to refresh the token for
     * @throws GmailAuthException If token refresh fails
     */
    @Throws(GmailAuthException::class)
    private suspend fun refreshAccessToken(account: GoogleSignInAccount) {
        var retryCount = 0
        var lastError: Exception? = null
        
        while (retryCount < MAX_RETRY_ATTEMPTS) {
            try {
                Timber.d("Refreshing access token (attempt ${retryCount + 1}/$MAX_RETRY_ATTEMPTS)")
                
                val authCode = withContext(Dispatchers.Main) {
                    try {
                        GoogleAuthUtil.getToken(
                            context,
                            account.account!!,
                            "oauth2:${SCOPES.joinToString(" ")}",
                            Bundle()
                        )
                    } catch (e: Exception) {
                        throw when (e) {
                            is GoogleAuthException -> GmailAuthException(
                                "Authentication error: ${e.message}",
                                GmailAuthError.AUTH_ERROR,
                                e
                            )
                            is IOException -> GmailAuthException(
                                "Network error: ${e.message}",
                                GmailAuthError.NETWORK_ERROR,
                                e
                            )
                            else -> GmailAuthException(
                                "Failed to get auth token: ${e.message}",
                                GmailAuthError.UNKNOWN,
                                e
                            )
                        }
                    }
                }
                
                // Parse the token response
                val tokenInfo = authCode.split('.')
                if (tokenInfo.size >= 2) {
                    accessToken = tokenInfo[0]
                    refreshToken = tokenInfo.getOrNull(1) ?: refreshToken
                    tokenExpiryTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1) // Default 1 hour
                    
                    Timber.d("Access token refreshed successfully")
                    return
                } else {
                    throw GmailAuthException(
                        "Invalid token format received",
                        GmailAuthError.INVALID_TOKEN
                    )
                }
            } catch (e: GmailAuthException) {
                lastError = e
                retryCount++
                
                if (retryCount < MAX_RETRY_ATTEMPTS) {
                    val delay = RETRY_DELAY_MS * (1 shl (retryCount - 1)) // Exponential backoff
                    Timber.w("Token refresh failed (attempt $retryCount), retrying in ${delay}ms: ${e.message}")
                    delay(delay)
                } else {
                    // Convert to service error for consistent error handling
                    val serviceError = errorHandler.handleError(e, "Failed to refresh access token")
                    throw GmailAuthException(
                        serviceError.message,
                        GmailAuthError.TOKEN_REFRESH_FAILED,
                        e
                    )
                }
            } catch (e: Exception) {
                lastError = e
                retryCount++
                
                if (retryCount < MAX_RETRY_ATTEMPTS) {
                    val delay = RETRY_DELAY_MS * (1 shl (retryCount - 1))
                    Timber.w("Token refresh failed (attempt $retryCount), retrying in ${delay}ms: ${e.message}")
                    delay(delay)
                } else {
                    val serviceError = errorHandler.handleError(e, "Failed to refresh access token")
                    throw GmailAuthException(
                        serviceError.message,
                        GmailAuthError.TOKEN_REFRESH_FAILED,
                        e
                    )
                }
            }
        }
        
        val error = GmailAuthException(
            "Failed to refresh access token after $MAX_RETRY_ATTEMPTS attempts: ${lastError?.message}",
            GmailAuthError.TOKEN_REFRESH_FAILED,
            lastError
        )
        Timber.e(error, "Token refresh failed after $MAX_RETRY_ATTEMPTS attempts")
        _authState.value = GmailAuthState.Error(error)
        throw error
    }
    
    /**
     * Gets the authenticated Gmail service instance.
     *
     * @return The authenticated Gmail service
     * @throws GmailAuthException If the service is not initialized, not authenticated, or token is invalid
     */
    @Throws(GmailAuthException::class)
    fun getGmailService(): Gmail {
        return try {
            gmailService?.takeIf { isTokenValid() } 
                ?: throw GmailAuthException(
                    "Gmail service not initialized or token expired. Call signIn first.",
                    GmailAuthError.NOT_AUTHENTICATED
                )
        } catch (e: Exception) {
            val error = errorHandler.handleError(e, "Failed to get Gmail service")
            throw GmailAuthException(
                error.message,
                GmailAuthError.SERVICE_ERROR,
                e
            )
        }
    }
    
    /**
     * Checks if the current access token is valid.
     * A token is considered valid if it exists and hasn't expired (with a safety margin).
     *
     * @return true if the token is valid, false otherwise
     */
    private fun isTokenValid(): Boolean {
        return try {
            val isValid = accessToken != null && 
                System.currentTimeMillis() < tokenExpiryTime - TimeUnit.MINUTES.toMillis(TOKEN_REFRESH_THRESHOLD_MINUTES)
            
            if (!isValid) {
                Timber.d("Token check: ${if (accessToken == null) "No token" else "Token expired"}")
            }
            
            isValid
        } catch (e: Exception) {
            Timber.e(e, "Error checking token validity")
            false
        }
    }
    
    /**
     * Checks if the user is currently signed in and has a valid session.
     * This checks both the presence of a Gmail service instance and token validity.
     *
     * @return true if the user is signed in with a valid session, false otherwise
     */
    fun isSignedIn(): Boolean {
        return try {
            val isSignedIn = gmailService != null && isTokenValid()
            Timber.v("isSignedIn check: $isSignedIn")
            isSignedIn
        } catch (e: Exception) {
            Timber.e(e, "Error checking sign-in status")
            false
        }
    }
    
    /**
     * Get the current access token, refreshing if necessary
     */
    suspend fun getValidAccessToken(forceRefresh: Boolean = false): String {
        return try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
                ?: throw GmailAuthException(
                    "No signed in account", 
                    GmailAuthError.NOT_AUTHENTICATED
                )
            
            if (forceRefresh || !isTokenValid()) {
                Timber.d("Access token needs refresh (force=$forceRefresh)")
                refreshAccessToken(account)
            }
            
            accessToken ?: throw GmailAuthException(
                "No access token available after refresh",
                GmailAuthError.TOKEN_ERROR
            )
        } catch (e: GmailAuthException) {
            // Re-throw GmailAuthException as is
            Timber.e(e, "Failed to get valid access token")
            _authState.value = GmailAuthState.Error(e)
            throw e
        } catch (e: Exception) {
            // Wrap other exceptions
            val error = errorHandler.handleError(e, "Failed to get valid access token")
            val authError = GmailAuthException(
                error.message,
                GmailAuthError.TOKEN_ERROR,
                e
            )
            Timber.e(authError, "Unexpected error getting access token")
            _authState.value = GmailAuthState.Error(authError)
            throw authError
        }
    }

    /**
     * Start the Google Sign In flow
     */
    fun signIn(activity: FragmentActivity, callback: (Result<GmailAuthResult>) -> Unit) {
        authCallback = callback
        val signInIntent = googleSignInClient.signInIntent
        activity.startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    /**
     * Handle the sign-in result
     */
    suspend fun handleSignInResult(data: Intent?): GmailAuthResult {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
                ?: throw IllegalStateException("No account found")

            // Get the authorization code
            val authCode = account.serverAuthCode
                ?: throw IllegalStateException("No authorization code")

            // Exchange the authorization code for tokens
            val tokens = exchangeAuthCode(authCode)
            
            // Save the tokens
            saveTokens(account.email, tokens)
            
            // Update the repository
            MessageBridgeRepository.updateGmailConnection(true)
            
            GmailAuthResult.Success(account.email ?: "")
        } catch (e: Exception) {
            Timber.e(e, "Sign in failed")
            GmailAuthResult.Error(e.message ?: "Authentication failed")
        }
    }

    /**
     * Exchange the authorization code for access and refresh tokens
     */
    private suspend fun exchangeAuthCode(authCode: String): GmailTokens {
        return withContext(Dispatchers.IO) {
            try {
                // Load client secrets
                val clientSecrets = loadClientSecrets()
                
                // Exchange the authorization code for tokens
                val response = GoogleAuthorizationCodeTokenRequest(
                    httpTransport,
                    gson,
                    clientSecrets.installed.clientId,
                    clientSecrets.installed.clientSecret,
                    authCode,
                    ""
                )
                    .setScopes(SCOPES)
                    .execute()

                GmailTokens(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    expiresIn = response.expiresInSeconds?.toLong() ?: 3600
                )
            } catch (e: Exception) {
                Timber.e(e, "Error exchanging auth code for tokens")
                throw e
            }
        }
    }

    /**
     * Load client secrets from the raw resources
     */
    private fun loadClientSecrets(): GoogleClientSecrets {
        return context.resources.openRawResource(R.raw.credentials).use { `is` ->
            GoogleClientSecrets.load(
                gson,
                InputStreamReader(`is`)
            )
        }
    }

    /**
     * Save the tokens securely
     */
    private fun saveTokens(accountName: String, tokens: GmailTokens) {
        // Store tokens in AccountManager or EncryptedSharedPreferences
        // For demo purposes, we'll just log them
        Timber.d("Saving tokens for account: $accountName")
        Timber.d("Access token: ${tokens.accessToken.take(10)}...")
        Timber.d("Refresh token: ${tokens.refreshToken?.take(10)}...")
        
        // In a production app, you would store these tokens securely
        // using EncryptedSharedPreferences or AccountManager
    }

    /**
     * Sign out from Gmail
     */
    fun signOut() {
        googleSignInClient.signOut()
            .addOnCompleteListener {
                // Clear any stored tokens
                clearTokens()
                MessageBridgeRepository.updateGmailConnection(false)
            }
    }

    /**
     * Clear stored tokens
     */
    private fun clearTokens() {
        // Clear tokens from secure storage
    }

    /**
     * Get the current account if signed in
     */
    fun getCurrentAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Check if the user is signed in
     */
    fun isSignedIn(): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null
    }

    /**
     * Get an access token, refreshing if necessary
     */
    suspend fun getAccessToken(accountName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // In a real app, you would get the stored tokens and refresh if needed
                // For now, we'll just get a new token each time
                GoogleAuthUtil.getToken(
                    context,
                    accountName,
                    "oauth2:${SCOPES.joinToString(" ")}"
                )
            } catch (e: GoogleAuthException) {
                Timber.e(e, "Failed to get access token")
                null
            } catch (e: IOException) {
                Timber.e(e, "Failed to get access token")
                null
            }
        }
    }

    companion object {
        private const val RC_SIGN_IN = 9001
    }
}

/**
 * Result of a Gmail authentication attempt
 */
data class GmailAuthResult(
    val success: Boolean,
    val message: String? = null,
    val exception: Exception? = null
)

/**
 * Represents Gmail authentication tokens
 */
data class GmailTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiryTime: Long
)

/**
 * Sealed class representing the authentication state
 */
sealed class GmailAuthState {
    object SignedOut : GmailAuthState()
    data class SignedIn(val account: GoogleSignInAccount, val gmail: Gmail) : GmailAuthState()
    data class Error(val exception: Throwable) : GmailAuthState()
}

/**
 * Custom exception class for Gmail authentication errors
 */
class GmailAuthException(
    message: String,
    val errorCode: GmailAuthError = GmailAuthError.UNKNOWN,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Enum representing different Gmail authentication error types.
 */
enum class GmailAuthError {
    /** Authentication is required */
    NOT_AUTHENTICATED,
    
    /** Network connectivity issue */
    NETWORK_ERROR,
    
    /** Server returned an error */
    SERVER_ERROR,
    
    /** Invalid request parameters */
    INVALID_REQUEST,
    
    /** Invalid or malformed token */
    INVALID_TOKEN,
    
    /** Token refresh failed */
    TOKEN_REFRESH_FAILED,
    
    /** Error with credentials */
    CREDENTIALS_ERROR,
    
    /** Error with token */
    TOKEN_ERROR,
    
    /** Authentication error (invalid credentials, revoked access, etc.) */
    AUTH_ERROR,
    
    /** API error from Google services */
    API_ERROR,
    
    /** Sign-in was cancelled by the user */
    SIGN_IN_CANCELLED,
    
    /** Sign-in failed */
    SIGN_IN_FAILED,
    
    /** Error with Gmail service */
    SERVICE_ERROR,
    
    /** Unknown or unhandled error */
    UNKNOWN
}
