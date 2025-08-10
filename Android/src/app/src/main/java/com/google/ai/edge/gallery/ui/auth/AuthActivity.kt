package com.google.ai.edge.gallery.ui.auth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.google.ai.edge.gallery.ui.theme.HappyChatAITheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity that handles user authentication.
 * This activity hosts the authentication flow including login, sign up, and password reset.
 */
@AndroidEntryPoint
class AuthActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            HappyChatAITheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuthNavigation(
                        onAuthSuccess = {
                            // Authentication successful, finish this activity
                            setResult(RESULT_OK)
                            finish()
                        },
                        onBack = {
                            // User pressed back, finish the activity
                            setResult(RESULT_CANCELED)
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onBackPressed() {
        // Let the navigation handle the back press
        // If we're on the login screen, finish the activity
        if (supportFragmentManager.backStackEntryCount == 0) {
            setResult(RESULT_CANCELED)
            finish()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        /**
         * Request code for starting this activity for result.
         */
        const val AUTH_REQUEST_CODE = 1001
    }
}
