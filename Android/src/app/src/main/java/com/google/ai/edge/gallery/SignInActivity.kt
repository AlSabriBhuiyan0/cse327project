package com.google.ai.edge.gallery

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SignInActivity : AppCompatActivity() {

    @Inject
    lateinit var auth: FirebaseAuth

    @Inject
    lateinit var googleSignInManager: GoogleSignInManager

    private val viewModel: AuthViewModel by viewModels()
    private val TAG = "SignInActivity"

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        Log.d(TAG, "Google sign-in result received")
        val idToken = googleSignInManager.handleSignInResult(data)
        if (idToken != null) {
            Log.d(TAG, "ID token obtained, signing in with Google")
            viewModel.signInWithGoogle(idToken)
        } else {
            Log.e(TAG, "Google sign-in failed, no ID token obtained")
            Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)

        val clientId = getString(R.string.default_web_client_id)
        Log.d(TAG, "Using client ID: $clientId")

        // Register back press callback (modern approach)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Optional: Prevent going back to Splash screen or exit the app
                moveTaskToBack(true)
            }
        })

        // Observe authentication state
        lifecycleScope.launch {
            viewModel.signInState.collect { state ->
                when (state) {
                    is AuthState.Success -> {
                        Log.d(TAG, "Sign-in successful: ${state.message}")
                        Toast.makeText(this@SignInActivity, state.message, Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@SignInActivity, MainActivity::class.java))
                        finish()
                    }
                    is AuthState.Error -> {
                        Log.e(TAG, "Sign-in error: ${state.message}")
                        Toast.makeText(this@SignInActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    is AuthState.Loading -> {
                        Log.d(TAG, "Sign-in loading...")
                        // You could show a loading indicator here
                    }
                    else -> {} // Handle other states if needed
                }
            }
        }

        // Manual Google Sign-In only. Auto-launch is disabled per requirements.
        // Keep the manual button as the entry point for Google authentication.
        findViewById<LinearLayout>(R.id.btnGoogleSignIn)?.setOnClickListener {
            Log.d(TAG, "Google Sign-In button clicked")
            try {
                if (clientId.isBlank()) {
                    Log.e(TAG, "default_web_client_id is blank. Check google-services.json configuration.")
                    Toast.makeText(this, "Configuration error: missing web client ID", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                // Ensure a clean session to avoid stale tokens from previous attempts
                googleSignInManager.signOut()
                val signInIntent = googleSignInManager.getSignInIntent(this, clientId)
                signInLauncher.launch(signInIntent)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid client ID: ${e.message}", e)
                Toast.makeText(this, "Invalid Google Sign-In configuration", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch Google Sign-In from button", e)
                Toast.makeText(this, "Unable to start Google Sign-In", Toast.LENGTH_LONG).show()
            }
        }
    }
}
