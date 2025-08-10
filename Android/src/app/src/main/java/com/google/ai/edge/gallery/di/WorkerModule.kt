package com.google.ai.edge.gallery.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.google.ai.edge.gallery.api.HuggingFaceApiHelper
import com.google.ai.edge.gallery.worker.DownloadWorker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Hilt module that provides worker-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {
    
    /**
     * Provides a map of worker class names to their factory providers.
     * This is used by the [HiltWorkerFactory] to create worker instances with dependencies.
     */
    @Provides
    @Singleton
    fun provideWorkerFactory(
        workerFactories: Map<Class<out ListenableWorker>, @JvmSuppressWildcards Provider<ChildWorkerFactory>>
    ): WorkerFactory {
        return HiltWorkerFactory(workerFactories)
    }
}

/**
 * Interface for worker factory that can create workers with dependencies.
 */
interface ChildWorkerFactory {
    fun create(
        context: Context,
        workerParams: WorkerParameters
    ): ListenableWorker
}

/**
 * Factory for creating [DownloadWorker] instances with dependencies.
 */
class DownloadWorkerFactory @Inject constructor(
    private val apiHelper: HuggingFaceApiHelper
) : ChildWorkerFactory {
    
    override fun create(
        context: Context,
        workerParams: WorkerParameters
    ): ListenableWorker {
        return DownloadWorker(
            context = context,
            workerParams = workerParams,
            huggingFaceApiHelper = apiHelper
        )
    }
}

/**
 * Module that binds worker factories.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkerBindingModule {
    
    @Binds
    @IntoMap
    @WorkerKey(DownloadWorker::class)
    abstract fun bindDownloadWorkerFactory(factory: DownloadWorkerFactory): ChildWorkerFactory
}

/**
 * Annotation for mapping worker classes to their factories.
 */
@MapKey
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class WorkerKey(val value: KClass<out ListenableWorker>)

/**
 * Custom worker factory that uses Hilt to create workers with dependencies.
 */
class HiltWorkerFactory @Inject constructor(
    private val workerFactories: Map<Class<out ListenableWorker>, @JvmSuppressWildcards Provider<ChildWorkerFactory>>
) : WorkerFactory() {
    
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        val foundEntry = workerFactories.entries.find { 
            Class.forName(workerClassName).isAssignableFrom(it.key) 
        }
        
        val factoryProvider = foundEntry?.value
            ?: throw IllegalArgumentException("Unknown worker class name: $workerClassName")
            
        return factoryProvider.get().create(appContext, workerParameters)
    }
}
