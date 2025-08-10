

package com.google.ai.edge.gallery.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.google.ai.edge.gallery.AppLifecycleProvider
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.util.ModelDownloadHelper
import com.google.ai.edge.gallery.worker.DownloadWorker
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DownloadRepository"
private const val MODEL_NAME_TAG = "modelName"
private const val NOTIFICATION_CHANNEL_ID = "model_downloads"
private const val NOTIFICATION_ID = 1001

/**
 * Information about a work item for a model download.
 */
data class AGWorkInfo(val modelName: String, val workId: String)

/**
 * Repository for managing model downloads.
 */
interface DownloadRepository {
    /**
     * Starts a download for the specified model.
     *
     * @param model The model to download.
     * @param onStatusUpdated Callback for status updates during the download.
     */
    fun downloadModel(
        model: Model,
        onStatusUpdated: (model: Model, status: ModelDownloadStatus) -> Unit,
    )

    /**
     * Cancels the download for the specified model.
     *
     * @param model The model whose download should be cancelled.
     */
    fun cancelDownloadModel(model: Model)

    /**
     * Cancels all downloads.
     *
     * @param models The list of models whose downloads should be cancelled.
     * @param onComplete Callback when all cancellations are complete.
     */
    fun cancelAll(models: List<Model>, onComplete: () -> Unit)

    /**
     * Gets information about enqueued or running downloads.
     *
     * @return A list of [AGWorkInfo] objects representing the active downloads.
     */
    fun getEnqueuedOrRunningWorkInfos(): List<AGWorkInfo>
}

/**
 * Default implementation of [DownloadRepository] that uses WorkManager for background downloads.
 *
 * @property context The application context.
 * @property lifecycleProvider Provider for app lifecycle events.
 * @property modelDownloadHelper Helper for managing model downloads.
 */
@Singleton
class DefaultDownloadRepository @Inject constructor(
    private val context: Context,
    private val lifecycleProvider: AppLifecycleProvider,
    private val modelDownloadHelper: ModelDownloadHelper
) : DownloadRepository {

    private val workManager = WorkManager.getInstance(context)
    
    init {
        createNotificationChannel()
    }

    override fun downloadModel(
        model: Model,
        onStatusUpdated: (model: Model, status: ModelDownloadStatus) -> Unit,
    ) {
        Log.d(TAG, "Starting download for model: ${model.name}")
        
        // Start the download using ModelDownloadHelper
        modelDownloadHelper.startDownload(
            model = model,
            onProgress = { progress ->
                // Map progress to ModelDownloadStatus
                val status = when (progress) {
                    100 -> ModelDownloadStatus(status = ModelDownloadStatusType.SUCCEEDED)
                    else -> ModelDownloadStatus(
                        status = ModelDownloadStatusType.IN_PROGRESS,
                        totalBytes = model.fileSizeBytes,
                        receivedBytes = (model.fileSizeBytes * progress) / 100,
                        bytesPerSecond = 0, // Not available directly from ModelDownloadHelper
                        remainingMs = 0 // Not available directly from ModelDownloadHelper
                    )
                }
                onStatusUpdated(model, status)
                
                // Show notification for download progress
                if (progress > 0 && progress < 100) {
                    showDownloadProgressNotification(model.name, progress)
                } else if (progress == 100) {
                    showDownloadCompleteNotification(model.name)
                }
            }
        )
    }

    override fun cancelDownloadModel(model: Model) {
        Log.d(TAG, "Cancelling download for model: ${model.name}")
        modelDownloadHelper.cancelDownload(model)
        
        // Show cancellation notification
        showDownloadCancelledNotification(model.name)
        
        // Remove the progress notification
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    override fun cancelAll(models: List<Model>, onComplete: () -> Unit) {
        Log.d(TAG, "Cancelling all downloads")
        models.forEach { modelDownloadHelper.cancelDownload(it) }
        onComplete()
    }

    override fun getEnqueuedOrRunningWorkInfos(): List<AGWorkInfo> {
        // This is a simplified implementation that returns an empty list
        // since ModelDownloadHelper doesn't expose this information directly
        return emptyList()
    }

          WorkInfo.State.FAILED,
          WorkInfo.State.CANCELLED -> {
            var status = ModelDownloadStatusType.FAILED
            val errorMessage = workInfo.outputData.getString(KEY_MODEL_DOWNLOAD_ERROR_MESSAGE) ?: ""
            Log.d(
              "repo",
              "worker %s FAILED or CANCELLED: %s".format(workerId.toString(), errorMessage),
            )
            if (workInfo.state == WorkInfo.State.CANCELLED) {
              status = ModelDownloadStatusType.NOT_DOWNLOADED
            } else {
              sendNotification(
                title = context.getString(R.string.notification_title_fail),
                text = context.getString(R.string.notification_content_success).format(model.name),
                modelName = "",
              )
            }
            onStatusUpdated(
              model,
              ModelDownloadStatus(status = status, errorMessage = errorMessage),
            )
          }

          else -> {}
        }
      }
    }
  }

  /**
   * Retrieves a list of AGWorkInfo objects representing WorkManager work items that are either
   * enqueued or currently running.
   */
  override fun getEnqueuedOrRunningWorkInfos(): List<AGWorkInfo> {
    val workQuery =
      WorkQuery.Builder.fromStates(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING)).build()

    return workManager.getWorkInfos(workQuery).get().map { info ->
      val tags = info.tags
      var modelName = ""
      Log.d(TAG, "work: ${info.id}, tags: $tags")
      for (tag in tags) {
        if (tag.startsWith("$MODEL_NAME_TAG:")) {
          val index = tag.indexOf(':')
          if (index >= 0) {
            modelName = tag.substring(index + 1)
            break
          }
    }

    /**
     * Creates the notification channel for download progress notifications.
     */
    /**
     * Shows a notification when a download is cancelled.
     *
     * @param modelName The name of the model whose download was cancelled.
     */
    private fun showDownloadCancelledNotification(modelName: String) {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Download Cancelled")
            .setContentText("Download for $modelName was cancelled")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID + 1, notification)
    }
    
    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "Model Downloads"
            val descriptionText = "Shows progress for model downloads"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
