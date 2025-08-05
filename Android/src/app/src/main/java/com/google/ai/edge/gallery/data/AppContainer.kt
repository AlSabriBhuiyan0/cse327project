

package com.google.ai.edge.gallery.data

import android.content.Context
import androidx.datastore.core.DataStore
import com.google.ai.edge.gallery.AppLifecycleProvider
import com.google.ai.edge.gallery.GalleryLifecycleProvider
import com.google.ai.edge.gallery.proto.Settings

/**
 * App container for Dependency injection.
 *
 * This interface defines the dependencies required by the application.
 */
interface AppContainer {
  val context: Context
  val lifecycleProvider: AppLifecycleProvider
  val dataStoreRepository: DataStoreRepository
  val downloadRepository: DownloadRepository
}

/**
 * Default implementation of the AppContainer interface.
 *
 * This class provides concrete implementations for the application's dependencies,
 */
class DefaultAppContainer(ctx: Context, dataStore: DataStore<Settings>) : AppContainer {
  override val context = ctx
  override val lifecycleProvider = GalleryLifecycleProvider()
  override val dataStoreRepository = DefaultDataStoreRepository(dataStore)
  override val downloadRepository = DefaultDownloadRepository(ctx, lifecycleProvider)
}
