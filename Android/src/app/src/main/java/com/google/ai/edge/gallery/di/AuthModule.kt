package com.google.ai.edge.gallery.di

import android.content.Context
import com.google.ai.edge.gallery.auth.GoogleAuthManager
import com.google.ai.edge.gallery.auth.HuggingFaceAuthHelper
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/**
 * Hilt module that provides authentication-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    
    /**
     * Provides a singleton instance of [HuggingFaceAuthHelper].
     */
    @Provides
    @Singleton
    fun provideHuggingFaceAuthHelper(appContext: android.app.Application): HuggingFaceAuthHelper {
        return HuggingFaceAuthHelper(appContext)
    }
    
    /**
     * Provides a singleton instance of [GoogleAuthManager].
     */
    @Provides
    @Singleton
    fun provideGoogleAuthManager(
        @ApplicationContext context: Context,
        firebaseAuth: FirebaseAuth
    ): GoogleAuthManager {
        return GoogleAuthManager(context, firebaseAuth, Dispatchers.IO)
    }
}
