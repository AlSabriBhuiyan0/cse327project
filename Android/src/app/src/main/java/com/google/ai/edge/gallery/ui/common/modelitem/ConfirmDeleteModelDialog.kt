

package com.google.ai.edge.gallery.ui.common.modelitem

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.Model

/** Composable function to display a confirmation dialog for deleting a model. */
@Composable
fun ConfirmDeleteModelDialog(model: Model, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.confirm_delete_model_dialog_title)) },
    text = {
      Text(stringResource(R.string.confirm_delete_model_dialog_content).format(model.name))
    },
    confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.ok)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}
