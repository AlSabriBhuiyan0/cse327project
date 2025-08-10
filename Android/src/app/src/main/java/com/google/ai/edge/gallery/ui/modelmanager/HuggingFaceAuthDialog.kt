package com.google.ai.edge.gallery.ui.modelmanager

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R

/**
 * A dialog for entering Hugging Face authentication token.
 *
 * @param onDismiss Callback when the dialog is dismissed
 * @param onAuthenticate Callback when authentication is successful, provides the token
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuggingFaceAuthDialog(
    onDismiss: () -> Unit,
    onAuthenticate: (String) -> Unit
) {
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hf_auth_title)) },
        text = {
            Column {
                Text(stringResource(R.string.hf_auth_message))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.hf_auth_token_hint)) },
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrect = false)
                )
                Text(
                    text = stringResource(R.string.hf_auth_token_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (token.isNotBlank()) {
                        onAuthenticate(token.trim())
                        onDismiss()
                    }
                },
                enabled = token.isNotBlank()
            ) {
                Text(stringResource(R.string.hf_auth_authenticate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.hf_auth_cancel))
            }
        },
        modifier = Modifier.padding(16.dp)
    )
}
