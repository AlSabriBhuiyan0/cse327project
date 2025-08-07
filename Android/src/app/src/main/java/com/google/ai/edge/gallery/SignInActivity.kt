package com.google.ai.edge.gallery

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SignInActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val viewModel: AuthViewModel by viewModels()

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                firebaseAuthWithGoogle(account.idToken!!)
            } else {
                Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            Log.w("SignIn", "Google sign-in failed", e)
            Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            // Collect the sign-in state
            val signInState by viewModel.signInState.collectAsState()

            // Render the sign-in screen
            SignInScreen(
                onGoogleSignInClick = {
                    val signInIntent = googleSignInClient.signInIntent
                    signInLauncher.launch(signInIntent)
                },
                onEmailSignInClick = { email, password ->
                    viewModel.signInWithEmailAndPassword(email, password)
                },
                onSignUpClick = {
                    // Navigate to the sign-up screen
                    startActivity(Intent(this, SignUpActivity::class.java))
                }
            )

            // Handle the sign-in state
            when (signInState) {
                is AuthState.Success -> {
                    Toast.makeText(this, (signInState as AuthState.Success).message, Toast.LENGTH_SHORT).show()
                    // Navigate to MainActivity after successful sign-in
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    // Reset the state to avoid showing the success message again on configuration change
                    viewModel.resetSignInState()
                }
                is AuthState.Error -> {
                    Toast.makeText(this, (signInState as AuthState.Error).message, Toast.LENGTH_LONG).show()
                    // Reset the error state
                    viewModel.resetSignInState()
                }
                else -> {
                    // No action needed for Idle or Loading states
                }
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Log.w("SignIn", "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        super.onBackPressed()
        // Optional: Prevent going back to Splash screen or exit the app
        moveTaskToBack(true)
    }
}
