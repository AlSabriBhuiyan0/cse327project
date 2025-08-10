package com.google.ai.edge.gallery.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.ai.edge.gallery.api.HuggingFaceApiHelper
import com.google.ai.edge.gallery.data.Model
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DownloadWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters

    @Mock
    private lateinit var mockApiHelper: HuggingFaceApiHelper

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()
        workerParams = createWorkerParams()
    }
    
    private fun createWorkerParams(
        modelName: String = "test-model",
        modelUrl: String = "https://huggingface.co/test/model",
        fileName: String = "model.bin",
        fileSize: Long = 1024L,
        isZip: Boolean = false,
        extractToDir: String = "",
        accessToken: String? = null
    ): WorkerParameters {
        val inputData = Data.Builder()
            .putString("model_name", modelName)
            .putString("model_url", modelUrl)
            .putString("file_name", fileName)
            .putLong("file_size", fileSize)
            .putBoolean("is_zip", isZip)
            .putString("extract_to_dir", extractToDir)
            .apply {
                if (accessToken != null) {
                    putString("access_token", accessToken)
                }
            }
            .build()
            
        return WorkerParameters(
            UUID.randomUUID(),
            inputData,
            emptyList(),
            WorkerParameters.RuntimeExtras(),
            1,
            0,
            mock(),
            mock(),
            mock(),
            mock(),
            mock()
        )
    }

    @Test
    fun `doWork returns success when download is successful`() = runTest {
        // Given
        workerParams = createWorkerParams()
        
        `when`(mockApiHelper.downloadModelFile(
            modelUrl = anyString(),
            outputFile = any(File::class.java),
            accessToken = any(),
            onProgress = any()
        )).thenReturn(Result.success())

        val worker = TestDownloadWorker(context, workerParams, mockApiHelper)

        // When
        val result = worker.doWork()

        // Then
        assertThat(result).isEqualTo(Result.success())
        verify(mockApiHelper).downloadModelFile(
            modelUrl = "https://huggingface.co/test/model",
            outputFile = any(File::class.java),
            accessToken = null,
            onProgress = any()
        )
    }

    @Test
    fun `doWork returns retry when download fails`() = runTest {
        // Given
        val testModel = Model(
            name = "test-model",
            downloadUrl = "https://huggingface.co/test/model",
            downloadFileName = "model.bin",
            fileSizeBytes = 1024L
        )

        val inputData = androidx.work.workDataOf(
            "model_name" to testModel.name,
            "model_url" to testModel.downloadUrl,
            "file_name" to testModel.downloadFileName,
            "file_size" to testModel.fileSizeBytes,
            "is_zip" to false,
            "extract_to_dir" to "",
            "access_token" to null
        )

        workerParams = WorkerParameters(
            UUID.randomUUID(),
            inputData,
            emptyList(),
            WorkerParameters.RuntimeExtras(),
            1,
            0,
            mock(),
            mock(),
            mock(),
            mock(),
            mock()
        )

        `when`(mockApiHelper.downloadModelFile(
            modelUrl = testModel.downloadUrl,
            outputFile = any(File::class.java),
            accessToken = null,
            onProgress = any()
        )).thenReturn(Result.retry())

        val worker = TestDownloadWorker(
            context,
            workerParams,
            mockApiHelper
        )

        // When
        val result = worker.doWork()

        // Then
        assertThat(result).isEqualTo(Result.retry())
    }

    @Test
    fun `doWork returns failure when download fails`() = runTest {
        // Given
        workerParams = createWorkerParams()
        
        `when`(mockApiHelper.downloadModelFile(
            modelUrl = anyString(),
            outputFile = any(File::class.java),
            accessToken = any(),
            onProgress = any()
        )).thenReturn(Result.failure())

        val worker = TestDownloadWorker(context, workerParams, mockApiHelper)

        // When
        val result = worker.doWork()

        // Then
        assertThat(result).isEqualTo(Result.failure())
    }
    
    @Test
    fun `doWork handles IOException during download`() = runTest {
        // Given
        workerParams = createWorkerParams()
        
        `when`(mockApiHelper.downloadModelFile(
            modelUrl = anyString(),
            outputFile = any(File::class.java),
            accessToken = any(),
            onProgress = any()
        )).thenThrow(IOException("Network error"))

        val worker = TestDownloadWorker(context, workerParams, mockApiHelper)

        // When
        val result = worker.doWork()

        // Then
        assertThat(result).isEqualTo(Result.failure())
    }
    
    @Test
    fun `doWork handles HttpException`() = runTest {
        // Given
        workerParams = createWorkerParams()
        val errorResponse = Response.error<ResponseBody>(
            403, 
            ResponseBody.create(null, "Forbidden")
        )
        
        `when`(mockApiHelper.downloadModelFile(
            modelUrl = anyString(),
            outputFile = any(File::class.java),
            accessToken = any(),
            onProgress = any()
        )).thenThrow(HttpException(errorResponse))

        val worker = TestDownloadWorker(context, workerParams, mockApiHelper)

        // When
        val result = worker.doWork()

        // Then
        assertThat(result).isEqualTo(Result.failure())
    }
    
    @Test
    fun `doWork handles unauthorized error with valid token`() = runTest {
        // Given
        val testToken = "test_token_123"
        workerParams = createWorkerParams(accessToken = testToken)
        
        `when`(mockApiHelper.downloadModelFile(
            modelUrl = anyString(),
            outputFile = any(File::class.java),
            accessToken = testToken,
            onProgress = any()
        )).thenReturn(Result.success())

        val worker = TestDownloadWorker(context, workerParams, mockApiHelper)

        // When
        val result = worker.doWork()

        // Then
        assertThat(result).isEqualTo(Result.success())
    }
    
    @Test
    fun `doWork handles zip extraction`() = runTest {
        // Given
        workerParams = createWorkerParams(
            isZip = true,
            extractToDir = "extracted_models"
        )
        
        `when`(mockApiHelper.downloadModelFile(
            modelUrl = anyString(),
            outputFile = any(File::class.java),
            accessToken = any(),
            onProgress = any()
        )).thenReturn(Result.success())

        val worker = TestDownloadWorker(context, workerParams, mockApiHelper)

        // When
        val result = worker.doWork()

        // Then
        assertThat(result).isEqualTo(Result.success())
        // Additional verification for zip extraction would go here
    }
    
    @Test
    fun `doWork handles cancellation`() = runTest {
        // Given
        workerParams = createWorkerParams()
        
        `when`(mockApiHelper.downloadModelFile(
            modelUrl = anyString(),
            outputFile = any(File::class.java),
            accessToken = any(),
            onProgress = any()
        )).thenAnswer { 
            // Simulate cancellation during download
            throw InterruptedException("Download cancelled")
        }

        val worker = TestDownloadWorker(context, workerParams, mockApiHelper)

        // When
        val result = worker.doWork()

        // Then
        assertThat(result).isEqualTo(Result.retry())
    }
    
    internal class TestDownloadWorker(
        context: Context,
        workerParams: WorkerParameters,
        private val apiHelper: HuggingFaceApiHelper
    ) : DownloadWorker(context, workerParams) {
        override fun createApiHelper(): HuggingFaceApiHelper = apiHelper
    }

    // Helper function to match any object of a given type
    private fun <T> any(type: Class<T>): T = org.mockito.ArgumentMatchers.any(type)
    private inline fun <reified T> any(): T = any(T::class.java)
}
