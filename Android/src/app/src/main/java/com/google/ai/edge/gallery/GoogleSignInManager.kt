@file:Suppress("DEPRECATION")
// TODO: Migrate Google Sign-In to Google Identity Services / Credential Manager.
// Using GoogleSignInOptions is deprecated in recent Play Services versions.
package com.google.ai.edge.gallery

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager class for handling Google Sign-In operations
 */
@Singleton
class GoogleSignInManager @Inject constructor() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private val TAG = "GoogleSignInManager"

    /**
     * Initializes the Google Sign-In client
     *
     * @param context Application context
     * @param clientId Web client ID from resources
     * @return GoogleSignInClient instance
     */
    fun init(context: Context, clientId: String): GoogleSignInClient {
        require(clientId.isNotBlank()) { "Web client ID is blank. Ensure default_web_client_id is configured from google-services.json" }

        // Configure Google Sign-In. Must use the Web client ID to get an ID token for Firebase.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .build()

        // Build a GoogleSignInClient with the options
        googleSignInClient = GoogleSignIn.getClient(context, gso)
        Log.d(TAG, "Google Sign-In client initialized with client ID: $clientId")
        return googleSignInClient
    }

    /**
     * Creates a sign-in intent for starting the Google Sign-In flow
     *
     * @param context Application context
     * @param clientId Web client ID from resources
     * @return Intent for starting Google Sign-In
     */
    fun getSignInIntent(context: Context, clientId: String): Intent {
        if (!::googleSignInClient.isInitialized) {
            Log.d(TAG, "GoogleSignInClient not initialized, initializing now")
            init(context, clientId)
        }
        return googleSignInClient.signInIntent
    }

    /**
     * Signs out the current Google account
     */
    fun signOut() {
        if (::googleSignInClient.isInitialized) {
            googleSignInClient.signOut()
        }
    }

    /**
     * Handles the result from Google Sign-In intent
     *
     * @param data Intent data from onActivityResult
     * @return ID token for Firebase authentication or null if sign-in failed
     */
    fun handleSignInResult(data: Intent?): String? {
        return try {
            // Get the sign-in account from the intent data
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            val token = account.idToken
            if (token.isNullOrBlank()) {
                Log.e(TAG, "Google Sign-In succeeded but ID token is null/blank for email=${account.email}")
                null
            } else {
                Log.d(TAG, "Google Sign-In successful, ID token obtained")
                token
            }
        } catch (e: ApiException) {
            // Handle sign-in failure
            Log.e(TAG, "Google Sign-In failed with error code: ${e.statusCode}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during Google Sign-In", e)
            null
        }
    }
}
