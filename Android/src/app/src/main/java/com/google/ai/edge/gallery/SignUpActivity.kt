package com.google.ai.edge.gallery

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SignUpActivity : AppCompatActivity() {

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // Collect the sign-up state
            val signUpState by viewModel.signUpState.collectAsState()

            // Render the sign-up screen
            SignUpScreen(
                onSignUpClick = { email, password ->
                    viewModel.createUserWithEmailAndPassword(email, password)
                },
                onSignInClick = {
                    // Navigate back to the sign-in screen
                    finish()
                }
            )

            // Handle the sign-up state
            when (signUpState) {
                is AuthState.Success -> {
                    Toast.makeText(this, (signUpState as AuthState.Success).message, Toast.LENGTH_SHORT).show()
                    // Navigate to MainActivity after successful sign-up
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    // Reset the state to avoid showing the success message again on configuration change
                    viewModel.resetSignUpState()
                }
                is AuthState.Error -> {
                    Toast.makeText(this, (signUpState as AuthState.Error).message, Toast.LENGTH_LONG).show()
                    // Reset the error state
                    viewModel.resetSignUpState()
                }
                else -> {
                    // No action needed for Idle or Loading states
                }
            }
        }
    }
}
