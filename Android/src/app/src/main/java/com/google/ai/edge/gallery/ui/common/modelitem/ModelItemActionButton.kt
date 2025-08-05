

package com.google.ai.edge.gallery.ui.common.modelitem

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatus
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.getTaskIconColor
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

/**
 * Composable function to display action buttons for a model item, based on its download status.
 *
 * This function renders the appropriate action button (download, delete, cancel) based on the
 * provided ModelDownloadStatus. It also handles notification permission requests for downloading
 * and displays a confirmation dialog for deleting models.
 */
@Composable
fun ModelItemActionButton(
  context: Context,
  model: Model,
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  downloadStatus: ModelDownloadStatus?,
  onDownloadClicked: (Model) -> Unit,
  modifier: Modifier = Modifier,
  showDeleteButton: Boolean = true,
  showDownloadButton: Boolean = true,
) {
  var showConfirmDeleteDialog by remember { mutableStateOf(false) }

  Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
    when (downloadStatus?.status) {
      // Button to start the download.
      ModelDownloadStatusType.NOT_DOWNLOADED,
      ModelDownloadStatusType.FAILED ->
        if (showDownloadButton) {
          IconButton(onClick = { onDownloadClicked(model) }) {
            Icon(Icons.Rounded.FileDownload, contentDescription = "", tint = getTaskIconColor(task))
          }
        }

      // Button to delete the download.
      ModelDownloadStatusType.SUCCEEDED -> {
        if (showDeleteButton) {
          IconButton(onClick = { showConfirmDeleteDialog = true }) {
            Icon(Icons.Rounded.Delete, contentDescription = "", tint = getTaskIconColor(task))
          }
        }
      }

      // Show spinner when the model is partially downloaded because it might some time for
      // background task to be started by Android.
      ModelDownloadStatusType.PARTIALLY_DOWNLOADED -> {
        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp).size(24.dp))
      }

      // Button to cancel the download when it is in progress.
      ModelDownloadStatusType.IN_PROGRESS,
      ModelDownloadStatusType.UNZIPPING ->
        IconButton(
          onClick = { modelManagerViewModel.cancelDownloadModel(task = task, model = model) }
        ) {
          Icon(Icons.Rounded.Cancel, contentDescription = "", tint = getTaskIconColor(task))
        }

      else -> {}
    }
  }

  if (showConfirmDeleteDialog) {
    ConfirmDeleteModelDialog(
      model = model,
      onConfirm = {
        modelManagerViewModel.deleteModel(task = task, model = model)
        showConfirmDeleteDialog = false
      },
      onDismiss = { showConfirmDeleteDialog = false },
    )
  }
}
