package com.google.ai.edge.gallery.ui.auth.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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

@Composable
fun SignUpScreen(
    viewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit,
    onSignUpSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val signUpState = viewModel.signUpState.collectAsState()
    val authState = viewModel.authState.collectAsState()
    val isLoading = viewModel.isLoading.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    // Handle auth state changes
    LaunchedEffect(authState.value) {
        when (val result = authState.value) {
            is AuthResult.Success -> {
                onSignUpSuccess()
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
                title = { Text(stringResource(R.string.create_account)) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Name Field
            OutlinedTextField(
                value = signUpState.value.name,
                onValueChange = { name ->
                    viewModel.onSignUpChange(
                        email = signUpState.value.email,
                        password = signUpState.value.password,
                        confirmPassword = signUpState.value.confirmPassword,
                        name = name
                    )
                },
                label = { Text(stringResource(R.string.full_name)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null
                    )
                },
                isError = signUpState.value.nameError != null,
                supportingText = {
                    if (signUpState.value.nameError != null) {
                        Text(signUpState.value.nameError ?: "")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            // Email Field
            OutlinedTextField(
                value = signUpState.value.email,
                onValueChange = { email ->
                    viewModel.onSignUpChange(
                        email = email,
                        password = signUpState.value.password,
                        confirmPassword = signUpState.value.confirmPassword,
                        name = signUpState.value.name
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
                isError = signUpState.value.emailError != null,
                supportingText = {
                    if (signUpState.value.emailError != null) {
                        Text(signUpState.value.emailError ?: "")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            // Password Field
            OutlinedTextField(
                value = signUpState.value.password,
                onValueChange = { password ->
                    viewModel.onSignUpChange(
                        email = signUpState.value.email,
                        password = password,
                        confirmPassword = signUpState.value.confirmPassword,
                        name = signUpState.value.name
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
                isError = signUpState.value.passwordError != null,
                supportingText = {
                    if (signUpState.value.passwordError != null) {
                        Text(signUpState.value.passwordError ?: "")
                    } else {
                        Text(stringResource(R.string.password_hint))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            // Confirm Password Field
            OutlinedTextField(
                value = signUpState.value.confirmPassword,
                onValueChange = { confirmPassword ->
                    viewModel.onSignUpChange(
                        email = signUpState.value.email,
                        password = signUpState.value.password,
                        confirmPassword = confirmPassword,
                        name = signUpState.value.name
                    )
                },
                label = { Text(stringResource(R.string.confirm_password)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    val image = if (confirmPasswordVisible) {
                        Icons.Filled.Visibility
                    } else {
                        Icons.Filled.VisibilityOff
                    }
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector = image,
                            contentDescription = if (confirmPasswordVisible) {
                                stringResource(R.string.hide_password)
                            } else {
                                stringResource(R.string.show_password)
                            }
                        )
                    }
                },
                visualTransformation = if (confirmPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                isError = signUpState.value.confirmPasswordError != null,
                supportingText = {
                    if (signUpState.value.confirmPasswordError != null) {
                        Text(signUpState.value.confirmPasswordError ?: "")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )

            // Sign Up Button
            Button(
                onClick = {
                    viewModel.signUpWithEmailAndPassword(
                        signUpState.value.email,
                        signUpState.value.password,
                        signUpState.value.name
                    )
                },
                enabled = signUpState.value.isFormValid && !isLoading.value,
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
                    Text(stringResource(R.string.sign_up))
                }
            }

            // Already have an account? Sign In
            Row(
                modifier = Modifier.padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.already_have_an_account))
                TextButton(
                    onClick = onNavigateToLogin
                ) {
                    Text(stringResource(R.string.sign_in))
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
