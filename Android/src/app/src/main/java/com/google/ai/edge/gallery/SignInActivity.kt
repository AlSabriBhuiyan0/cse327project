package com.google.ai.edge.gallery

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.api.ApiException
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity responsible for handling user sign-in with email/password or Google authentication.
 */
@AndroidEntryPoint
class SignInActivity : AppCompatActivity() {

    private val viewModel: AuthViewModel by viewModels()
    private val TAG = "SignInActivity"

    // Launcher for Google Sign-In
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.handleGoogleSignInResult(Result.success(Unit))
        } else {
            val error = result.data?.let { data ->
                ApiException(data.getParcelableExtra("error")!!)
            } ?: Exception("Google sign-in failed with result code: $result")
            
            viewModel.handleGoogleSignInResult(Result.failure(error))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)

        // Register back press callback (modern approach)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Prevent going back to Splash screen or exit the app
                moveTaskToBack(true)
            }
        })

        // Observe authentication state
        observeAuthState()
        
        // Set up Google Sign-In button click listener
        setupGoogleSignInButton()
    }
    
    /**
     * Sets up the Google Sign-In button click listener
     */
    private fun setupGoogleSignInButton() {
        findViewById<LinearLayout>(R.id.btnGoogleSignIn)?.setOnClickListener {
            Log.d(TAG, "Google Sign-In button clicked")
            viewModel.signInWithGoogle(this, googleSignInLauncher::launch)
        }
    }
    
    /**
     * Observes the authentication state and handles UI updates accordingly
     */
    private fun observeAuthState() {
        lifecycleScope.launch {
            // Observe general sign-in state
            viewModel.signInState.collect { state ->
                when (state) {
                    is AuthState.Success -> {
                        Log.d(TAG, "Sign-in successful: ${state.message}")
                        Toast.makeText(this@SignInActivity, state.message, Toast.LENGTH_SHORT).show()
                        navigateToMain()
                    }
                    is AuthState.Error -> {
                        Log.e(TAG, "Sign-in error: ${state.message}")
                        if (state.message.isNotBlank()) {
                            Toast.makeText(this@SignInActivity, state.message, Toast.LENGTH_LONG).show()
                        }
                    }
                    is AuthState.Loading -> {
                        Log.d(TAG, "Sign-in loading...")
                        // Show loading indicator if needed
                    }
                    else -> {} // Handle other states if needed
                }
            }
        }
        
        // Observe Google Sign-In specific state if needed
        lifecycleScope.launch {
            viewModel.googleSignInState.collectLatest { state ->
                when (state) {
                    is GoogleSignInState.Loading -> {
                        // Show loading state for Google Sign-In
                    }
                    is GoogleSignInState.Error -> {
                        Log.e(TAG, "Google Sign-In error: ${state.message}")
                        if (state.message.isNotBlank()) {
                            Toast.makeText(this@SignInActivity, state.message, Toast.LENGTH_LONG).show()
                        }
                    }
                    is GoogleSignInState.Success -> {
                        Log.d(TAG, "Google Sign-In successful for: ${state.account.email}")
                    }
                    else -> {} // Handle other states
                }
            }
        }
    }

    /**
     * Navigates to the main activity and finishes the current one
     */
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
