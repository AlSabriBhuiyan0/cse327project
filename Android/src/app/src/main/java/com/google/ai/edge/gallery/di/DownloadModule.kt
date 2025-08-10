package com.google.ai.edge.gallery.di

import android.content.Context
import androidx.work.WorkManager
import com.google.ai.edge.gallery.api.HuggingFaceApiHelper
import com.google.ai.edge.gallery.data.DefaultDownloadRepository
import com.google.ai.edge.gallery.data.DownloadRepository
import com.google.ai.edge.gallery.util.ModelDownloadHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module that provides dependencies related to model downloads.
 */
@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    /**
     * Provides an OkHttpClient with logging for debugging purposes.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Provides the HuggingFaceApiHelper for interacting with the Hugging Face API.
     */
    @Provides
    @Singleton
    fun provideHuggingFaceApiHelper(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): HuggingFaceApiHelper {
        return HuggingFaceApiHelper(context, okHttpClient)
    }

    /**
     * Provides the ModelDownloadHelper for managing model downloads.
     */
    @Provides
    @Singleton
    fun provideModelDownloadHelper(
        @ApplicationContext context: Context,
        huggingFaceApiHelper: HuggingFaceApiHelper
    ): ModelDownloadHelper {
        return ModelDownloadHelper(
            context = context,
            workManager = WorkManager.getInstance(context),
            huggingFaceApiHelper = huggingFaceApiHelper
        )
    }

    /**
     * Provides the DownloadRepository implementation.
     */
    @Provides
    @Singleton
    fun provideDownloadRepository(
        @ApplicationContext context: Context,
        modelDownloadHelper: ModelDownloadHelper
    ): DownloadRepository {
        return DefaultDownloadRepository(
            context = context,
            lifecycleProvider = object : com.google.ai.edge.gallery.AppLifecycleProvider {
                override val isAppInForeground: Boolean
                    get() = true // Simplified for this example
            },
            modelDownloadHelper = modelDownloadHelper
        )
    }
}
