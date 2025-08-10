package com.google.ai.edge.gallery.ui.auth.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.ui.auth.AuthViewModel
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onSignInSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val loginState = viewModel.loginState.collectAsState()
    val authState = viewModel.authState.collectAsState()
    val isLoading = viewModel.isLoading.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Handle auth state changes
    LaunchedEffect(authState.value) {
        when (val result = authState.value) {
            is AuthResult.Success -> {
                onSignInSuccess()
            }
            is AuthResult.Error -> {
                // Error is handled in the UI
            }
            is AuthResult.Loading -> {
                // Loading state is handled by the button
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sign_in)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo or App Name
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Email Field
            OutlinedTextField(
                value = loginState.value.email,
                onValueChange = { email ->
                    viewModel.onLoginChange(
                        email = email,
                        password = loginState.value.password
                    )
                },
                label = { Text(stringResource(R.string.email)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email
                ),
                isError = loginState.value.emailError != null,
                supportingText = {
                    if (loginState.value.emailError != null) {
                        Text(loginState.value.emailError ?: "")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            // Password Field
            OutlinedTextField(
                value = loginState.value.password,
                onValueChange = { password ->
                    viewModel.onLoginChange(
                        email = loginState.value.email,
                        password = password
                    )
                },
                label = { Text(stringResource(R.string.password)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    val image = if (passwordVisible) {
                        Icons.Filled.Visibility
                    } else {
                        Icons.Filled.VisibilityOff
                    }
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = image,
                            contentDescription = if (passwordVisible) {
                                stringResource(R.string.hide_password)
                            } else {
                                stringResource(R.string.show_password)
                            }
                        )
                    }
                },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                isError = loginState.value.passwordError != null,
                supportingText = {
                    if (loginState.value.passwordError != null) {
                        Text(loginState.value.passwordError ?: "")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            // Forgot Password Link
            TextButton(
                onClick = onNavigateToForgotPassword,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(bottom = 16.dp)
            ) {
                Text(stringResource(R.string.forgot_password))
            }

            // Sign In Button
            Button(
                onClick = {
                    viewModel.signInWithEmailAndPassword(
                        loginState.value.email,
                        loginState.value.password
                    )
                },
                enabled = loginState.value.isFormValid && !isLoading.value,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isLoading.value) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.sign_in))
                }
            }

            // Or Divider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Divider(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Text(
                    text = stringResource(R.string.or_continue_with).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Divider(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
            }

            // Google Sign In Button
            OutlinedButton(
                onClick = {
                    // Handle Google Sign In
                    // This will be implemented in the parent composable
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                // Google icon would go here
                Text(stringResource(R.string.continue_with_google))
            }

            // Sign Up Link
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.dont_have_an_account))
                TextButton(
                    onClick = onNavigateToSignUp
                ) {
                    Text(stringResource(R.string.sign_up))
                }
            }
        }

        // Show error dialog if there's an error
        authState.value.let { result ->
            if (result is AuthResult.Error && result.message.isNotBlank()) {
                AlertDialog(
                    onDismissRequest = {
                        // Clear the error
                        viewModel.resetAuthState()
                    },
                    title = { Text(stringResource(R.string.error)) },
                    text = { Text(result.message) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.resetAuthState()
                            }
                        ) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                )
            }
        }
    }
}
