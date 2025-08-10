package com.google.ai.edge.gallery.util

import android.content.Context
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import java.util.*
import java.util.concurrent.ExecutionException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class ModelDownloadHelperTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockWorkManager: WorkManager

    @Mock
    private lateinit var mockApiHelper: HuggingFaceApiHelper

    private lateinit var modelDownloadHelper: ModelDownloadHelper

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        modelDownloadHelper = ModelDownloadHelper(mockContext, mockWorkManager, mockApiHelper)
    }

    @Test
    fun `startDownload returns work ID`() = runTest {
        // Given
        val testModel = createTestModel()
        val testWorkId = UUID.randomUUID()
        val testWorkInfo = workInfoOf(WorkInfo.State.ENQUEUED)
        
        `when`(mockWorkManager.enqueue(any())).thenReturn(
            Operation.Success(WorkInfo.State.ENQUEUED, listOf(testWorkInfo), listOf(testWorkId))
        )

        // When
        val workId = modelDownloadHelper.startDownload(testModel) { _, _ -> }

        // Then
        assertThat(workId).isNotEmpty()
        verify(mockWorkManager).enqueue(any())
    }

    @Test
    fun `cancelDownload cancels work for model`() = runTest {
        // Given
        val testModel = createTestModel()
        val testWorkId = UUID.randomUUID()
        
        `when`(mockWorkManager.cancelAllWorkByTag(anyString())).thenReturn(
            Operation.Success(WorkInfo.State.CANCELLED)
        )

        // When
        modelDownloadHelper.cancelDownload(testModel)

        // Then
        verify(mockWorkManager).cancelAllWorkByTag("download_${testModel.name}")
    }

    @Test
    fun `isDownloadInProgress returns false when no work is in progress`() = runTest {
        // Given
        val testModel = createTestModel()
        
        `when`(mockWorkManager.getWorkInfosForUniqueWork(anyString()))
            .thenReturn(workDataOf(emptyList<WorkInfo>()))

        // When
        val isInProgress = modelDownloadHelper.isDownloadInProgress(testModel)

        // Then
        assertThat(isInProgress).isFalse()
    }
    
    @Test
    fun `isDownloadInProgress returns true when work is in progress`() = runTest {
        // Given
        val testModel = createTestModel()
        val testWorkInfo = workInfoOf(WorkInfo.State.RUNNING)
        
        `when`(mockWorkManager.getWorkInfosForUniqueWork(anyString()))
            .thenReturn(workDataOf(listOf(testWorkInfo)))

        // When
        val isInProgress = modelDownloadHelper.isDownloadInProgress(testModel)

        // Then
        assertThat(isInProgress).isTrue()
    }
    
    @Test
    fun `getDownloadStatus returns correct status`() = runTest {
        // Given
        val testModel = createTestModel()
        val testWorkInfo = workInfoOf(WorkInfo.State.RUNNING)
        
        `when`(mockWorkManager.getWorkInfosForUniqueWork(anyString()))
            .thenReturn(workDataOf(listOf(testWorkInfo)))

        // When
        val status = modelDownloadHelper.getDownloadStatus(testModel)

        // Then
        assertThat(status).isNotNull()
        assertThat(status?.status).isEqualTo(ModelDownloadStatusType.IN_PROGRESS)
    }
    
    @Test
    fun `getDownloadStatus returns null when no work exists`() = runTest {
        // Given
        val testModel = createTestModel()
        
        `when`(mockWorkManager.getWorkInfosForUniqueWork(anyString()))
            .thenReturn(workDataOf(emptyList<WorkInfo>()))

        // When
        val status = modelDownloadHelper.getDownloadStatus(testModel)

        // Then
        assertThat(status).isNull()
    }
    
    private fun createTestModel() = Model(
        name = "test-model-${UUID.randomUUID()}",
        downloadUrl = "https://huggingface.co/test/model",
        downloadFileName = "model.bin",
        fileSizeBytes = 1024L
    )
    
    private fun workInfoOf(state: WorkInfo.State): WorkInfo {
        return WorkInfo(
            UUID.randomUUID(),
            state,
            workDataOf("progress" to 50),
            emptyList(),
            workDataOf("progress" to 50),
            1
        )
    }
    
    private fun workDataOf(workInfos: List<WorkInfo>): ListenableFuture<List<WorkInfo>> {
        return object : ListenableFuture<List<WorkInfo>> {
            override fun isDone() = true
            override fun get() = workInfos
            override fun get(timeout: Long, unit: java.util.concurrent.TimeUnit) = workInfos
            override fun addListener(listener: Runnable, executor: java.util.concurrent.Executor) {
                executor.execute(listener)
            }
            override fun cancel(mayInterruptIfRunning: Boolean) = false
            override fun isCancelled() = false
        }
    }
}
