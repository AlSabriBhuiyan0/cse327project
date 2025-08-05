

package com.google.ai.edge.gallery.ui.common.modelitem

// import androidx.compose.ui.tooling.preview.Preview
// import com.google.ai.edge.gallery.ui.theme.GalleryTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.ModelDownloadStatus
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.ui.theme.customColors

private val SIZE = 18.dp

/** Composable function to display an icon representing the download status of a model. */
@Composable
fun StatusIcon(downloadStatus: ModelDownloadStatus?, modifier: Modifier = Modifier) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
    modifier = modifier,
  ) {
    when (downloadStatus?.status) {
      ModelDownloadStatusType.NOT_DOWNLOADED ->
        Icon(
          Icons.AutoMirrored.Outlined.HelpOutline,
          tint = Color(0xFFCCCCCC),
          contentDescription = "",
          modifier = Modifier.size(SIZE),
        )

      ModelDownloadStatusType.SUCCEEDED -> {
        Icon(
          Icons.Filled.DownloadForOffline,
          tint = MaterialTheme.customColors.successColor,
          contentDescription = "",
          modifier = Modifier.size(SIZE),
        )
      }

      ModelDownloadStatusType.FAILED ->
        Icon(
          Icons.Rounded.Error,
          tint = Color(0xFFAA0000),
          contentDescription = "",
          modifier = Modifier.size(SIZE),
        )

      ModelDownloadStatusType.IN_PROGRESS ->
        Icon(Icons.Rounded.Downloading, contentDescription = "", modifier = Modifier.size(SIZE))

      else -> {}
    }
  }
}

// @Preview(showBackground = true)
// @Composable
// fun StatusIconPreview() {
//   GalleryTheme {
//     Column {
//       for (downloadStatus in
//         listOf(
//           ModelDownloadStatus(status = ModelDownloadStatusType.NOT_DOWNLOADED),
//           ModelDownloadStatus(status = ModelDownloadStatusType.IN_PROGRESS),
//           ModelDownloadStatus(status = ModelDownloadStatusType.SUCCEEDED),
//           ModelDownloadStatus(status = ModelDownloadStatusType.FAILED),
//           ModelDownloadStatus(status = ModelDownloadStatusType.UNZIPPING),
//           ModelDownloadStatus(status = ModelDownloadStatusType.PARTIALLY_DOWNLOADED),
//         )) {
//         StatusIcon(downloadStatus = downloadStatus)
//       }
//     }
//   }
// }
