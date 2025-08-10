package com.google.ai.edge.gallery.util

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.worker.DownloadWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class to handle model downloads with WorkManager.
 */
@Singleton
class ModelDownloadHelper @Inject constructor(
    private val context: Context,
    private val workManager: WorkManager
) {
    
    companion object {
        private const val TAG = "ModelDownloadHelper"
        private const val DOWNLOAD_WORK_TAG = "model_download"
        private const val PAUSE_WORK_TAG = "download_pause_"
        
        // WorkData keys
        const val KEY_IS_PAUSED = "is_paused"
        const val KEY_RESUME_OFFSET = "resume_offset"
        const val KEY_RETRY_COUNT = "retry_count"
        const val KEY_LAST_ERROR = "last_error"
        
        // Retry configuration
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 5000L // 5 seconds
        private const val MAX_RETRY_DELAY_MS = 60000L // 1 minute
    }
    
    private val pausedDownloads = mutableSetOf<String>()
    
    /**
     * Starts or resumes a download for the given model.
     *
     * @param model The model to download.
     * @param onProgress Callback for download progress updates (0-100).
     * @param resumeOffset The byte offset to resume the download from. If null, starts from beginning.
     * @param retryCount The number of retry attempts made so far.
     * @param lastError The last error message if this is a retry attempt.
     * @return The WorkRequest ID for the download operation.
     */
    fun startDownload(
        model: Model,
        onProgress: (Int) -> Unit = {},
        resumeOffset: Long? = null,
        retryCount: Int = 0,
        lastError: String? = null
    ): String {
        // Cancel any existing downloads for this model
        cancelDownload(model)
        
        // Create input data for the worker
        val inputData = workDataOf(
            DownloadWorker.KEY_MODEL_NAME to model.name,
            DownloadWorker.KEY_MODEL_URL to model.downloadUrl,
            DownloadWorker.KEY_MODEL_FILE_NAME to model.downloadFileName,
            DownloadWorker.KEY_MODEL_FILE_SIZE to model.fileSizeBytes,
            DownloadWorker.KEY_IS_ZIP to model.isZipFile,
            DownloadWorker.KEY_EXTRACT_TO_DIR to model.extractToDir,
            DownloadWorker.KEY_MODEL_DOWNLOAD_ACCESS_TOKEN to model.accessToken,
            KEY_RESUME_OFFSET to (resumeOffset ?: 0L),
            KEY_RETRY_COUNT to retryCount,
            KEY_LAST_ERROR to (lastError ?: "")
        )
        
        // Calculate backoff delay for retries
        val backoffDelay = calculateBackoffDelay(retryCount)
        
        // Create constraints for the download
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        
        // Create the work request
        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag(DOWNLOAD_WORK_TAG)
            .addTag("model_${model.name}")
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                backoffDelay,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        // Enqueue the work
        workManager.enqueue(downloadRequest)
        
        // Observe progress
        workManager.getWorkInfoByIdLiveData(downloadRequest.id)
            .observeForever { workInfo ->
                when (workInfo.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getInt(DownloadWorker.KEY_PROGRESS, 0)
                        onProgress(progress)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        onProgress(100)
                    }
                    WorkInfo.State.FAILED -> {
                        val errorMessage = workInfo.outputData.getString(DownloadWorker.KEY_ERROR_MESSAGE)
                        Log.e(TAG, "Download failed: $errorMessage")
                    }
                    else -> { /* Do nothing */ }
                }
            }
        
        return downloadRequest.id.toString()
    }
    
    /**
     * Pauses an ongoing download for the given model.
     *
     * @param model The model whose download should be paused.
     * @param currentProgress The current download progress (0-100).
     * @return true if the pause request was successful, false otherwise.
     */
    fun pauseDownload(model: Model, currentProgress: Int): Boolean {
        val workInfo = getDownloadStatus(model) ?: return false
        
        if (workInfo.state == WorkInfo.State.RUNNING) {
            // Mark as paused in our local state
            pausedDownloads.add(model.name)
            
            // Create a one-time work request to handle the pause
            val pauseRequest = OneTimeWorkRequestBuilder<PauseWorker>()
                .setInputData(workDataOf(
                    "model_name" to model.name,
                    "progress" to currentProgress
                ))
                .addTag("${PAUSE_WORK_TAG}${model.name}")
                .build()
            
            workManager.beginWith(pauseRequest).enqueue()
            return true
        }
        return false
    }
    
    /**
     * Resumes a paused download for the given model.
     *
     * @param model The model whose download should be resumed.
     * @param currentProgress The current download progress (0-100).
     * @return The new WorkRequest ID if resuming was successful, null otherwise.
     */
    fun resumeDownload(model: Model, currentProgress: Int): String? {
        if (!isDownloadPaused(model)) return null
        
        // Calculate the resume offset based on progress
        val fileSize = model.fileSizeBytes
        val resumeOffset = (fileSize * currentProgress) / 100L
        
        // Clear the paused state
        pausedDownloads.remove(model.name)
        
        // Start a new download from the resume offset
        return startDownload(model, resumeOffset = resumeOffset)
    }
    
    /**
     * Handles a failed download attempt and schedules a retry if needed.
     *
     * @param model The model that failed to download.
     * @param errorMessage The error message from the failed attempt.
     * @param currentRetryCount The number of retry attempts made so far.
     * @param onRetry Callback when a retry is scheduled.
     * @param onFailure Callback when max retries are exhausted.
     * @return true if a retry was scheduled, false if max retries were reached.
     */
    fun handleDownloadFailure(
        model: Model,
        errorMessage: String,
        currentRetryCount: Int,
        onRetry: (attempt: Int, delayMs: Long) -> Unit = { _, _ -> },
        onFailure: (String) -> Unit = {}
    ): Boolean {
        val nextRetryCount = currentRetryCount + 1
        
        if (nextRetryCount > MAX_RETRY_ATTEMPTS) {
            // Max retries reached, notify failure
            val failureMessage = "Download failed after $MAX_RETRY_ATTEMPTS attempts: $errorMessage"
            Log.e(TAG, failureMessage)
            onFailure(failureMessage)
            return false
        }
        
        // Calculate delay using exponential backoff
        val delayMs = calculateBackoffDelay(nextRetryCount)
        
        // Schedule retry
        Log.w(TAG, "Scheduling retry $nextRetryCount for ${model.name} in ${delayMs}ms")
        
        // Notify UI about the retry
        onRetry(nextRetryCount, delayMs)
        
        // Start a new download with retry count
        startDownload(
            model = model,
            retryCount = nextRetryCount,
            lastError = errorMessage.take(100) // Truncate error message
        )
        
        return true
    }
    
    /**
     * Calculates the backoff delay for retry attempts.
     *
     * @param attempt The current retry attempt (1-based).
     * @return The delay in milliseconds.
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        return minOf(
            INITIAL_RETRY_DELAY_MS * (1L shl (attempt - 1)),
            MAX_RETRY_DELAY_MS
        )
    }
    
    /**
     * Cancels any ongoing downloads for the given model.
     *
     * @param model The model whose downloads should be cancelled.
     * @param clearPausedState If true, also clears any paused state for this model.
     * @param clearRetryState If true, also clears any retry state for this model.
     */
    fun cancelDownload(
        model: Model, 
        clearPausedState: Boolean = true,
        clearRetryState: Boolean = true
    ) {
        workManager.cancelAllWorkByTag("model_${model.name}")
        if (clearPausedState) {
            pausedDownloads.remove(model.name)
        }
        if (clearRetryState) {
            // Clear any retry state in shared preferences
            val prefs = context.getSharedPreferences("download_states", Context.MODE_PRIVATE)
            prefs.edit()
                .remove("${model.name}_retry_count")
                .remove("${model.name}_last_error")
                .apply()
        }
    }
    
    /**
     * Cancels all ongoing downloads.
     */
    fun cancelAllDownloads() {
        workManager.cancelAllWorkByTag(DOWNLOAD_WORK_TAG)
    }
    
    /**
     * Gets the download status for a specific model.
     *
     * @param model The model to check.
     * @return The current download status or null if no download is in progress.
     */
    fun getDownloadStatus(model: Model): WorkInfo? {
        val workInfos = workManager.getWorkInfosByTag("model_${model.name}").get()
        return workInfos.firstOrNull { workInfo ->
            workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED
        }
    }
    
    /**
     * Checks if a download is in progress for the given model.
     *
     * @param model The model to check.
     * @return true if a download is in progress, false otherwise.
     */
    fun isDownloadInProgress(model: Model): Boolean {
        return getDownloadStatus(model) != null
    }
    
    /**
     * Checks if a download is currently paused for the given model.
     *
     * @param model The model to check.
     * @return true if the download is paused, false otherwise.
     */
    fun isDownloadPaused(model: Model): Boolean {
        return model.name in pausedDownloads
    }
    
    /**
     * Gets the current download state for a model.
     *
     * @param model The model to check.
     * @return The current download state (RUNNING, PAUSED, or NONE).
     */
    fun getDownloadState(model: Model): DownloadState {
        return when {
            isDownloadPaused(model) -> DownloadState.PAUSED
            isDownloadInProgress(model) -> DownloadState.RUNNING
            else -> DownloadState.NONE
        }
    }
    
    /**
     * Enum representing the possible download states.
     */
    enum class DownloadState {
        NONE,       // No download in progress
        RUNNING,    // Download is in progress
        PAUSED      // Download is paused
    }
}
