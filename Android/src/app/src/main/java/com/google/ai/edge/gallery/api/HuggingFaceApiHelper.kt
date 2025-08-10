package com.google.ai.edge.gallery.api

import android.content.Context
import android.util.Log
import com.google.ai.edge.gallery.BuildConfig
import com.google.ai.edge.gallery.data.Model
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for interacting with the Hugging Face API for model downloads.
 */
@Singleton
class HuggingFaceApiHelper @Inject constructor(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "HuggingFaceApiHelper"
        private const val HF_API_BASE_URL = "https://huggingface.co/api/"
        private const val HF_DOWNLOAD_BASE_URL = "https://huggingface.co/"
        private const val HF_AUTH_HEADER = "Authorization"
        private const val HF_BEARER_PREFIX = "Bearer "
        
        // Default timeout for API calls in seconds
        private const val DEFAULT_TIMEOUT = 30L
    }

    /**
     * Downloads a model file from Hugging Face with authentication.
     *
     * @param model The model to download.
     * @param outputFile The file to save the downloaded content to.
     * @param progressCallback Callback to report download progress.
     * @return true if the download was successful, false otherwise.
     */
    suspend fun downloadModelFile(
        model: Model,
        outputFile: File,
        progressCallback: (bytesDownloaded: Long, contentLength: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val downloadUrl = model.downloadUrl ?: return@withContext false
            val request = Request.Builder()
                .url(downloadUrl)
                .apply {
                    // Add authentication header if token is available
                    model.accessToken?.takeIf { it.isNotBlank() }?.let { token ->
                        header(HF_AUTH_HEADER, "$HF_BEARER_PREFIX$token")
                    }
                    // Add user agent to identify the app
                    header("User-Agent", "HappyChatAI/${BuildConfig.VERSION_NAME}")
                }
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to download model: ${response.code} - ${response.message}")
                    return@withContext false
                }

                val contentLength = response.body?.contentLength() ?: -1L
                response.body?.byteStream()?.use { inputStream ->
                    FileOutputStream(outputFile).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            
                            // Report progress if content length is known
                            if (contentLength > 0) {
                                progressCallback(totalBytesRead, contentLength)
                            }
                        }
                        
                        // Final progress update
                        if (contentLength > 0 && totalBytesRead < contentLength) {
                            progressCallback(contentLength, contentLength)
                        }
                        
                        return@withContext true
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model file: ${e.message}", e)
            false
        }
    }

    /**
     * Validates a Hugging Face access token.
     *
     * @param token The token to validate.
     * @return true if the token is valid, false otherwise.
     */
    suspend fun validateToken(token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${HF_API_BASE_URL}whoami-v2")
                .header(HF_AUTH_HEADER, "$HF_BEARER_PREFIX$token")
                .header("User-Agent", "HappyChatAI/${BuildConfig.VERSION_NAME}")
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating token: ${e.message}", e)
            false
        }
    }

    /**
     * Gets the download URL for a model file from Hugging Face.
     *
     * @param modelName The name of the model (e.g., "google/gemma-3b-it")
     * @param fileName The name of the file to download (e.g., "model.safetensors")
     * @param token Optional Hugging Face access token for private models
     * @return The direct download URL or null if not found
     */
    suspend fun getModelFileDownloadUrl(
        modelName: String,
        fileName: String,
        token: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val url = "${HF_DOWNLOAD_BASE_URL}$modelName/resolve/main/$fileName"
            val request = Request.Builder()
                .url("$url")
                .apply {
                    token?.takeIf { it.isNotBlank() }?.let {
                        header(HF_AUTH_HEADER, "$HF_BEARER_PREFIX$it")
                    }
                    header("User-Agent", "HappyChatAI/${BuildConfig.VERSION_NAME}")
                }
                .build()

            // Make a HEAD request to check if the file exists and get the final URL
            client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
                .newCall(request)
                .execute()
                .use { response ->
                    when (response.code) {
                        HttpURLConnection.HTTP_MOVED_PERM,
                        HttpURLConnection.HTTP_MOVED_TEMP,
                        HttpURLConnection.HTTP_SEE_OTHER -> {
                            response.header("Location")
                        }
                        HttpURLConnection.HTTP_OK -> url
                        else -> null
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting download URL: ${e.message}", e)
            null
        }
    }
}
