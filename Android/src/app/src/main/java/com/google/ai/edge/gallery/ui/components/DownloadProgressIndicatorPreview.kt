package com.google.ai.edge.gallery.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.google.ai.edge.gallery.data.ModelDownloadStatus
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.ui.theme.HappyChatAITheme

@Preview(showBackground = true)
@Composable
fun DownloadProgressIndicatorPreview() {
    HappyChatAITheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // In progress with progress
                DownloadProgressIndicator(
                    status = ModelDownloadStatus(
                        status = ModelDownloadStatusType.IN_PROGRESS,
                        receivedBytes = 25_165_824, // 24 MB
                        totalBytes = 104_857_600, // 100 MB
                        bytesPerSecond = 1_048_576, // 1 MB/s
                        remainingMs = 76_000 // 1 min 16s
                    ),
                    onCancel = {}
                )

                // In progress without speed/remaining
                DownloadProgressIndicator(
                    status = ModelDownloadStatus(
                        status = ModelDownloadStatusType.IN_PROGRESS,
                        receivedBytes = 75_497_472, // 72 MB
                        totalBytes = 104_857_600, // 100 MB
                        bytesPerSecond = 0,
                        remainingMs = 0
                    ),
                    onCancel = {}
                )

                // Unzipping
                DownloadProgressIndicator(
                    status = ModelDownloadStatus(
                        status = ModelDownloadStatusType.UNZIPPING,
                        receivedBytes = 104_857_600, // 100 MB
                        totalBytes = 104_857_600 // 100 MB
                    )
                )

                // Completed
                DownloadProgressIndicator(
                    status = ModelDownloadStatus(
                        status = ModelDownloadStatusType.SUCCEEDED,
                        receivedBytes = 104_857_600, // 100 MB
                        totalBytes = 104_857_600 // 100 MB
                    )
                )

                // Failed with error
                DownloadProgressIndicator(
                    status = ModelDownloadStatus(
                        status = ModelDownloadStatusType.FAILED,
                        errorMessage = "Network error: Connection timed out"
                    )
                )
            }
        }
    }
}
