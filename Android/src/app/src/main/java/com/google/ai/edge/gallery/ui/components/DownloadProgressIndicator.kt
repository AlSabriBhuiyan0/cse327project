package com.google.ai.edge.gallery.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.ModelDownloadStatus
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.util.formatFileSize
import kotlin.math.roundToInt

/**
 * A composable that shows download progress for a model.
 *
 * @param status The current download status.
 * @param onCancel Callback when the download is cancelled.
 * @param modifier Optional modifier for the container.
 */
@Composable
fun DownloadProgressIndicator(
    status: ModelDownloadStatus,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val progressText = remember(status) {
        when (status.status) {
            ModelDownloadStatusType.IN_PROGRESS -> {
                buildAnnotatedString {
                    // Show progress percentage
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("${status.progressPercent}%")
                    }
                    
                    // Show download speed if available
                    if (status.formattedSpeed.isNotEmpty()) {
                        append(" • ${status.formattedSpeed}")
                    }
                    
                    // Show remaining time if available
                    if (status.formattedRemainingTime.isNotEmpty()) {
                        append(" • ${status.formattedRemainingTime}")
                    }
                }
            }
            ModelDownloadStatusType.UNZIPPING -> {
                buildAnnotatedString {
                    append(context.getString(R.string.unzipping_model))
                }
            }
            ModelDownloadStatusType.SUCCEEDED -> {
                buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(context.getString(R.string.download_completed))
                    }
                }
            }
            ModelDownloadStatusType.FAILED -> {
                buildAnnotatedString {
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.error)) {
                        append(status.errorMessage.ifEmpty {
                            context.getString(R.string.download_failed)
                        })
                    }
                    
                    // Show retry info if applicable
                    if (status.hasRetryInfo) {
                        append("\n")
                        withStyle(style = SpanStyle(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize
                        )) {
                            val remainingRetries = (status.maxRetries - status.retryCount).coerceAtLeast(0)
                            if (remainingRetries > 0) {
                                val retryIn = (status.nextRetryDelayMs / 1000).coerceAtLeast(1)
                                append(context.getString(
                                    R.string.retry_attempt_info,
                                    status.retryCount,
                                    remainingRetries,
                                    retryIn
                                ))
                            } else {
                                append(context.getString(R.string.no_more_retries))
                            }
                        }
                    }
                }
            }
            else -> buildAnnotatedString { append("") }
        }
    }

    val showProgress = status.status == ModelDownloadStatusType.IN_PROGRESS ||
            status.status == ModelDownloadStatusType.UNZIPPING

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when (status.status) {
                        ModelDownloadStatusType.IN_PROGRESS -> stringResource(R.string.downloading_model)
                        ModelDownloadStatusType.UNZIPPING -> stringResource(R.string.unzipping_model)
                        ModelDownloadStatusType.SUCCEEDED -> stringResource(R.string.download_completed)
                        ModelDownloadStatusType.FAILED -> stringResource(R.string.download_failed)
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (onCancel != null && status.status == ModelDownloadStatusType.IN_PROGRESS) {
                    TextButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }

            if (showProgress) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = if (status.totalBytes > 0) {
                        (status.receivedBytes.toFloat() / status.totalBytes).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
            }

            if (progressText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.status == ModelDownloadStatusType.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        LocalContentColor.current
                    },
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                if (showProgress) {
                    LinearProgressIndicator(
                        progress = status.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = status.formattedReceived,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        if (status.totalBytes > 0) {
                            Text(
                                text = status.formattedTotal,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
