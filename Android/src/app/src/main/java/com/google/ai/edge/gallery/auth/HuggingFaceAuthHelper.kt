package com.google.ai.edge.gallery.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class to manage Hugging Face authentication tokens.
 * Stores and retrieves the access token securely using SharedPreferences.
 */
@Singleton
class HuggingFaceAuthHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("hf_auth", Context.MODE_PRIVATE)
    }

    private var _accessToken: String? = null

    /**
     * Get or set the Hugging Face access token.
     * When setting a new token, it's persisted to SharedPreferences.
     */
    var accessToken: String?
        get() = _accessToken ?: prefs.getString("hf_access_token", null)
        set(value) {
            _accessToken = value
            prefs.edit { putString("hf_access_token", value) }
        }

    /**
     * Check if the user is authenticated with Hugging Face.
     * @return true if a valid access token exists, false otherwise.
     */
    val isAuthenticated: Boolean
        get() = !accessToken.isNullOrBlank()

    /**
     * Clear the stored authentication token.
     */
    fun clearAuth() {
        _accessToken = null
        prefs.edit { remove("hf_access_token") }
    }

    /**
     * Check if authentication is required for a model.
     * @param modelName The name of the model to check.
     * @return true if the model requires authentication, false otherwise.
     */
    fun isAuthRequired(modelName: String): Boolean {
        // For now, all Gemma models require authentication
        return modelName.startsWith("Gemma", ignoreCase = true)
    }
}
