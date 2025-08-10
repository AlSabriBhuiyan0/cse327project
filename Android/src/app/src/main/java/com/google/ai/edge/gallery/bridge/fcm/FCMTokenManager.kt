package com.google.ai.edge.gallery.bridge.fcm

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.ai.edge.gallery.BuildConfig
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manager for handling FCM token registration with the backend server
 */
class FCMTokenManager(private val context: Context) {
    companion object {
        private const val TAG = "FCMTokenManager"
        private const val PREFS_NAME = "fcm_token_prefs"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_TOKEN_SENT = "token_sent_to_server"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Gets the current FCM token and registers it with the backend if needed
     */
    suspend fun getAndRegisterFCMToken(): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Check if we already have a token
                val savedToken = prefs.getString(KEY_FCM_TOKEN, null)
                val tokenSent = prefs.getBoolean(KEY_TOKEN_SENT, false)
                
                if (savedToken != null && tokenSent) {
                    // Token already sent to server
                    return@withContext savedToken
                }
                
                // Get the current token from Firebase
                val token = FirebaseMessaging.getInstance().token.await()
                
                // Save token locally
                prefs.edit()
                    .putString(KEY_FCM_TOKEN, token)
                    .apply()
                
                // Simulate sending token to backend in debug mode
                // In a real app, this would send the token to your backend server
                val success = true // Always successful in this sample app

                if (success) {
                    // Mark as sent
                    prefs.edit()
                        .putBoolean(KEY_TOKEN_SENT, true)
                        .apply()
                    
                    Log.d(TAG, "FCM token registered: $token")
                }
                
                return@withContext token
            } catch (e: Exception) {
                Log.e(TAG, "Error getting FCM token", e)
                return@withContext null
            }
        }
    }
    
    /**
     * Gets a unique device ID for token registration
     */
    private fun getDeviceId(): String {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
        return androidId ?: "unknown_device"
    }
}
