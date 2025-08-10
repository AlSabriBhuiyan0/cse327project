package com.google.ai.edge.gallery.di

import com.google.ai.edge.gallery.bridge.gmail.GmailErrorHandler
import com.google.ai.edge.gallery.bridge.gmail.GmailErrorType
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module that provides dependencies for the bridge components.
 */
@Module
@InstallIn(SingletonComponent::class)
object BridgeModule {
    
    /**
     * Provides a singleton instance of [GmailErrorHandler].
     * 
     * @return Configured instance of [GmailErrorHandler]
     */
    @Provides
    @Singleton
    fun provideGmailErrorHandler(): GmailErrorHandler {
        return GmailErrorHandler(
            defaultUserMessage = "An unexpected error occurred. Please try again.",
            defaultErrorType = GmailErrorType.UNKNOWN
        )
    }
    
    // Note: GmailService and GmailAuthService are already annotated with @Inject constructor()
    // and @Singleton, so they don't need explicit provider methods here.
}
