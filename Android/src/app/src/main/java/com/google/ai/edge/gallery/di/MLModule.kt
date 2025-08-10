package com.google.ai.edge.gallery.di

import android.content.Context
import com.google.ai.edge.gallery.ml.ObjectDetector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module that provides ML-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object MLModule {
    
    /**
     * Provides an instance of [ObjectDetector].
     */
    @Provides
    @Singleton
    fun provideObjectDetector(
        @ApplicationContext context: Context
    ): ObjectDetector {
        return ObjectDetector(context)
    }
}
