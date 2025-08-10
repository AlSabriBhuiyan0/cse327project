

package com.google.ai.edge.gallery.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.google.ai.edge.gallery.common.readLaunchInfo
import com.google.ai.edge.gallery.data.KEY_MODEL_DOWNLOAD_ACCESS_TOKEN
import com.google.ai.edge.gallery.data.KEY_MODEL_DOWNLOAD_APP_TS
import com.google.ai.edge.gallery.data.KEY_MODEL_DOWNLOAD_ERROR_MESSAGE
import com.google.ai.edge.gallery.data.KEY_MODEL_DOWNLOAD_FILE_NAME
import com.google.ai.edge.gallery.data.KEY_MODEL_DOWNLOAD_MODEL_DIR
import com.google.ai.edge.gallery.data.KEY_MODEL_DOWNLOAD_RATE
import com.google.ai.edge.gallery.data.KEY_MODEL_DOWNLOAD_RECEIVED_BYTES
import com.google.ai.edge.gallery.data.KEY_MODEL_DOWNLOAD_REMAINING_MS
import com.google.ai.edge.gallery.data.KEY_MODEL_EXTRA_DATA_DOWNLOAD_FILE_NAMES
import com.google.ai.edge.gallery.data.KEY_MODEL_EXTRA_DATA_URLS
import com.google.ai.edge.gallery.data.KEY_MODEL_IS_ZIP
import com.google.ai.edge.gallery.data.KEY_MODEL_NAME
import com.google.ai.edge.gallery.data.KEY_MODEL_START_UNZIPPING
import com.google.ai.edge.gallery.data.KEY_MODEL_TOTAL_BYTES
import com.google.ai.edge.gallery.data.KEY_MODEL_UNZIPPED_DIR
import com.google.ai.edge.gallery.data.KEY_MODEL_URL
import com.google.ai.edge.gallery.data.KEY_MODEL_VERSION
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AGDownloadWorker"

data class UrlAndFileName(val url: String, val fileName: String)

private const val FOREGROUND_NOTIFICATION_CHANNEL_ID = "model_download_channel_foreground"
private var channelCreated = false

@RequiresApi(Build.VERSION_CODES.O)
class DownloadWorker(context: Context, params: WorkerParameters) :
  CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DownloadWorker"

        // Input data keys
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_MODEL_URL = "model_url"
        const val KEY_MODEL_FILE_NAME = "model_file_name"
        const val KEY_MODEL_FILE_SIZE = "model_file_size"
        const val KEY_IS_ZIP = "is_zip"
        const val KEY_EXTRACT_TO_DIR = "extract_to_dir"
        const val KEY_MODEL_DOWNLOAD_ACCESS_TOKEN = "model_download_access_token"
        const val KEY_RESUME_OFFSET = "resume_offset"
        const val KEY_RETRY_COUNT = "retry_count"
        const val KEY_LAST_ERROR = "last_error"

        // Output data keys
        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val KEY_BYTES_DOWNLOADED = "bytes_downloaded"
        const val KEY_SHOULD_RETRY = "should_retry"
        const val KEY_NEXT_RETRY_MS = "next_retry_ms"
        
        // Default retry configuration (can be overridden by ModelDownloadHelper)
        private const val DEFAULT_MAX_RETRIES = 3
        private const val DEFAULT_INITIAL_RETRY_DELAY_MS = 5000L // 5 seconds

        // Progress values
        private const val PROGRESS_START = 0
        private const val PROGRESS_DOWNLOADING = 1..98
        private const val PROGRESS_EXTRACTING = 99
        private const val PROGRESS_COMPLETE = 100
        
        // HTTP headers
        private const val HEADER_RANGE = "Range"
        private const val HEADER_ACCEPT_RANGES = "Accept-Ranges"
        private const val HEADER_CONTENT_RANGE = "Content-Range"
        private const val HEADER_AUTHORIZATION = "Authorization"
    }

    private val externalFilesDir = context.getExternalFilesDir(null)

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Unique notification id.
    private val notificationId: Int = workerParams.id.hashCode()

    init {
        if (!channelCreated) {
            // Create a notification channel for showing notifications for model downloading progress.
            val channel =
                NotificationChannel(
                    FOREGROUND_NOTIFICATION_CHANNEL_ID,
                    "Model Downloading",
                    // Make it silent.
                    NotificationManager.IMPORTANCE_LOW,
                )
                .apply { description = "Notifications for model downloading" }
            notificationManager.createNotificationChannel(channel)
            channelCreated = true
        }
    }

    override suspend fun doWork(): Result {
        val appTs = readLaunchInfo(context = applicationContext)?.ts ?: 0

        val modelName = inputData.getString(KEY_MODEL_NAME) ?: "Model"
        val version = inputData.getString(KEY_MODEL_VERSION)!!
        val fileName = inputData.getString(KEY_MODEL_DOWNLOAD_FILE_NAME)
        val modelDir = inputData.getString(KEY_MODEL_DOWNLOAD_MODEL_DIR)!!
        val isZip = inputData.getBoolean(KEY_MODEL_IS_ZIP, false)
        val unzippedDir = inputData.getString(KEY_MODEL_UNZIPPED_DIR)
        val extraDataFileUrls = inputData.getString(KEY_MODEL_EXTRA_DATA_URLS)?.split(",") ?: listOf()
        val extraDataFileNames =
            inputData.getString(KEY_MODEL_EXTRA_DATA_DOWNLOAD_FILE_NAMES)?.split(",") ?: listOf()
        val totalBytes = inputData.getLong(KEY_MODEL_TOTAL_BYTES, 0L)
        val accessToken = inputData.getString(KEY_MODEL_DOWNLOAD_ACCESS_TOKEN)
        val workerAppTs = inputData.getLong(KEY_MODEL_DOWNLOAD_APP_TS, 0L)
        val retryCount = inputData.getInt(KEY_RETRY_COUNT, 0)
        val lastError = inputData.getString(KEY_LAST_ERROR) ?: ""

        if (workerAppTs > 0 && appTs > 0 && workerAppTs != appTs) {
            Log.d(TAG, "Worker is from previous launch. Ignoring...")
            return Result.success()
        }
        
        // Log retry attempt if applicable
        if (retryCount > 0) {
            Log.d(TAG, "Retry attempt $retryCount for $modelName. Previous error: $lastError")
        }

        return withContext(Dispatchers.IO) {
            if (fileName == null) {
                Result.failure()
            } else {
                return@withContext try {
                    // Set the worker as a foreground service immediately.
                    setForeground(createForegroundInfo(progress = 0, modelName = modelName))

                    // Get resume offset if any
                    val resumeOffset = inputData.getLong(KEY_RESUME_OFFSET, 0L)
                    val isResuming = resumeOffset > 0L
                    
                    // Create the downloads directory if it doesn't exist
                    val downloadsDir = File(context.filesDir, "downloads")
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs()
                    }
                    
                    // Set up the output file
                    val outputFile = File(downloadsDir, fileName)
                    
                    // If resuming, verify the file exists and has the expected size
                    if (isResuming) {
                        if (!outputFile.exists() || outputFile.length() != resumeOffset) {
                            // If the file doesn't exist or has wrong size, start over
                            outputFile.delete()
                            return@withContext downloadFile(
                                modelName,
                                fileName,
                                modelUrl,
                                totalBytes,
                                isZip,
                                unzippedDir,
                                accessToken
                            )
                        }
                        Log.d(TAG, "Resuming download from offset: $resumeOffset")
                    }
                    
                    // Download the file
                    return@withContext downloadFile(
                        modelName,
                        fileName,
                        modelUrl,
                        totalBytes,
                        isZip,
                        unzippedDir,
                        accessToken,
                        resumeOffset
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error during download", e)
                    Result.failure(
                        Data.Builder()
                            .putString(KEY_ERROR_MESSAGE, "Download failed: ${e.message}")
                            .build()
                    )
                }
            }
        }
    }
    
    /**
     * Downloads a file with support for resuming interrupted downloads.
     *
     * @param modelName Name of the model being downloaded.
     * @param fileName Name of the file to save the download to.
     * @param fileUrl URL of the file to download.
     * @param totalSize Total size of the file in bytes, or 0 if unknown.
     * @param isZip Whether the file should be treated as a zip archive.
     * @param unzippedDir Directory to extract the zip file to, if isZip is true.
     * @param accessToken Optional access token for authentication.
     * @param startOffset Byte offset to start downloading from (for resuming).
     * @return A [Result] indicating success or failure.
     */
    private suspend fun downloadFile(
        modelName: String,
        fileName: String,
        fileUrl: String,
        totalSize: Long,
        isZip: Boolean,
        unzippedDir: String?,
        accessToken: String?,
        startOffset: Long = 0L
    ): Result {
        var connection: HttpURLConnection? = null
        var inputStream = null
        var outputStream = null
        var outputFile: File? = null
        
        return try {
            // Set up the output file
            val downloadsDir = File(context.filesDir, "downloads")
            outputFile = File(downloadsDir, fileName)
            
            // Open connection with resume support if needed
            val url = URL(fileUrl)
            connection = url.openConnection() as HttpURLConnection
            
            // Set up authentication if provided
            accessToken?.let { token ->
                connection.setRequestProperty(HEADER_AUTHORIZATION, "Bearer $token")
            }
            
            // Set range header for resuming
            if (startOffset > 0) {
                connection.setRequestProperty(HEADER_RANGE, "bytes=$startOffset-")
            }
            
            connection.connect()
            
            // Check if server supports range requests
            val contentLength = connection.contentLengthLong
            val acceptRanges = connection.getHeaderField(HEADER_ACCEPT_RANGES)
            val contentRange = connection.getHeaderField(HEADER_CONTENT_RANGE)
            
            val isResumeSupported = acceptRanges == "bytes" || contentRange != null
            
            if (startOffset > 0 && !isResumeSupported) {
                Log.w(TAG, "Server does not support range requests, starting from beginning")
                connection.disconnect()
                // Retry without resume
                return downloadFile(modelName, fileName, fileUrl, totalSize, isZip, unzippedDir, accessToken)
            }
            
            // Get input stream
            inputStream = connection.inputStream
            
            // Set up output stream (append if resuming)
            outputStream = if (startOffset > 0 && outputFile.exists()) {
                FileOutputStream(outputFile, true)
            } else {
                FileOutputStream(outputFile)
            }
            
            // Download the file
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var downloadedBytes = startOffset
            var lastProgressUpdate = 0L
            val startTime = System.currentTimeMillis()
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                
                // Update progress periodically
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastProgressUpdate > 1000) { // Update at most once per second
                    val progress = if (totalSize > 0) {
                        ((downloadedBytes * 100) / totalSize).toInt()
                    } else {
                        -1
                    }
                    
                    // Calculate download speed
                    val elapsedTime = (currentTime - startTime).coerceAtLeast(1) / 1000f // in seconds
                    val bytesPerSecond = (downloadedBytes / elapsedTime).toLong()
                    
                    // Estimate remaining time
                    val remainingBytes = (totalSize - downloadedBytes).coerceAtLeast(0)
                    val remainingSeconds = if (bytesPerSecond > 0) {
                        remainingBytes / bytesPerSecond
                    } else {
                        -1
                    }
                    
                    // Update progress
                    setProgressAsync(
                        Data.Builder()
                            .putInt(KEY_PROGRESS, progress)
                            .putLong(KEY_BYTES_DOWNLOADED, downloadedBytes)
                            .putLong("rate_bps", bytesPerSecond)
                            .putLong("remaining_seconds", remainingSeconds)
                            .build()
                    )
                    
                    // Update notification
                    setForegroundAsync(createForegroundInfo(progress, modelName))
                    
                    lastProgressUpdate = currentTime
                }
                
                // Check if the work has been cancelled
                if (isStopped) {
                    Log.d(TAG, "Download was stopped")
                    return Result.retry()
                }
            }
            
            // Download complete
            Log.d(TAG, "Download completed: $downloadedBytes bytes")
            
            // Unzip if needed
            if (isZip && unzippedDir != null) {
                setProgressAsync(Data.Builder().putBoolean(KEY_MODEL_START_UNZIPPING, true).build())
                unzipFile(outputFile, unzippedDir)
            }
            
            // Return success
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file", e)
            
            // If we were resuming and got an error, try from the beginning
            if (startOffset > 0 && e is IOException) {
                Log.d(TAG, "Resume failed, trying from beginning")
                // Delete the partial file and try again
                outputFile?.delete()
                return downloadFile(modelName, fileName, fileUrl, totalSize, isZip, unzippedDir, accessToken)
            }
            
            // Check if we should retry
            val errorMessage = e.message ?: "Unknown error"
            
            // Create a result that indicates a retry is needed
            val result = Result.retry(
                Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, errorMessage)
                    .putBoolean(KEY_SHOULD_RETRY, true)
                    .putInt(KEY_RETRY_COUNT, retryCount)
                    .putString(KEY_LAST_ERROR, errorMessage)
                    .build()
            )
            
            Log.w(TAG, "Download failed (attempt ${retryCount + 1}): $errorMessage")
            result
            
        } finally {
            // Clean up
            try {
                inputStream?.close()
                outputStream?.close()
                connection?.disconnect()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing streams", e)
                // Don't fail the worker for cleanup errors
            }
        }
    }
    
    /**
     * Unzips a file to the specified directory.
     *
     * @param zipFile The zip file to extract.
     * @param destDirName The destination directory name (relative to app's external files dir).
     */
    private fun unzipFile(zipFile: File, destDirName: String) {
        // Implementation of unzipFile remains the same
        // ...
    }

                setProgress(
                  Data.Builder()
                    .putLong(KEY_MODEL_DOWNLOAD_RECEIVED_BYTES, downloadedBytes)
                    .putLong(KEY_MODEL_DOWNLOAD_RATE, (bytesPerMs * 1000).toLong())
                    .putLong(KEY_MODEL_DOWNLOAD_REMAINING_MS, remainingMs.toLong())
                    .build()
                )
                setForeground(
                  createForegroundInfo(
                    progress = (downloadedBytes * 100 / totalBytes).toInt(),
                    modelName = modelName,
                  )
                )
                Log.d(TAG, "downloadedBytes: $downloadedBytes")
                lastSetProgressTs = curTs
              }
            }

            outputStream.close()
            inputStream.close()

            Log.d(TAG, "Download done")

            // Unzip if the downloaded file is a zip.
            if (isZip && unzippedDir != null) {
              setProgress(Data.Builder().putBoolean(KEY_MODEL_START_UNZIPPING, true).build())

              // Prepare target dir.
              val destDir =
                File(
                  externalFilesDir,
                  listOf(modelDir, version, unzippedDir).joinToString(File.separator),
                )
              if (!destDir.exists()) {
                destDir.mkdirs()
              }

              // Unzip.
              val unzipBuffer = ByteArray(4096)
              val zipFilePath =
                "${externalFilesDir}${File.separator}$modelDir${File.separator}$version${File.separator}${fileName}"
              val zipIn = ZipInputStream(BufferedInputStream(FileInputStream(zipFilePath)))
              var zipEntry: ZipEntry? = zipIn.nextEntry

              while (zipEntry != null) {
                val filePath = destDir.absolutePath + File.separator + zipEntry.name

                // Extract files.
                if (!zipEntry.isDirectory) {
                  // extract file
                  val bos = FileOutputStream(filePath)
                  bos.use { curBos ->
                    var len: Int
                    while (zipIn.read(unzipBuffer).also { len = it } > 0) {
                      curBos.write(unzipBuffer, 0, len)
                    }
                  }
                }
                // Create dir.
                else {
                  val dir = File(filePath)
                  dir.mkdirs()
                }

                zipIn.closeEntry()
                zipEntry = zipIn.nextEntry
              }
              zipIn.close()

              // Delete the original file.
              val zipFile = File(zipFilePath)
              zipFile.delete()
            }
          }
          Result.success()
        } catch (e: IOException) {
          Result.failure(
            Data.Builder().putString(KEY_MODEL_DOWNLOAD_ERROR_MESSAGE, e.message).build()
          )
        }
      }
    }
  }

  override suspend fun getForegroundInfo(): ForegroundInfo {
    // Initial progress is 0
    return createForegroundInfo(0)
  }

  /**
   * Creates a [ForegroundInfo] object for the download worker's ongoing notification. This
   * notification is used to keep the worker running in the foreground, indicating to the user that
   * an active download is in progress.
   */
  private fun createForegroundInfo(progress: Int, modelName: String? = null): ForegroundInfo {
    // Create a notification for the foreground service
    var title = "Downloading model"
    if (modelName != null) {
      title = "Downloading \"$modelName\""
    }
    val content = "Downloading in progress: $progress%"

    val notification =
      NotificationCompat.Builder(applicationContext, FOREGROUND_NOTIFICATION_CHANNEL_ID)
        .setContentTitle(title)
        .setContentText(content)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setOngoing(true) // Makes the notification non-dismissable
        .setProgress(100, progress, false) // Show progress
        .build()

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      ForegroundInfo(notificationId, notification)
    }
  }
}
