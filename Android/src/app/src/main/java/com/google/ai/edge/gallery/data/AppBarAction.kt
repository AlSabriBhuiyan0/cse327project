package com.google.ai.edge.gallery.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable

/** Possible action for app bar. */
enum class AppBarActionType {
  NO_ACTION,
  APP_SETTING,
  SIGN_OUT,
  DOWNLOAD_MANAGER,
  MODEL_SELECTOR,
  NAVIGATE_UP,
  REFRESH_MODELS,
  REFRESHING_MODELS,
}

// Change here: actionFn is NOT @Composable
class AppBarAction(val actionType: AppBarActionType, val actionFn: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryTopAppBar(
  title: String,
  rightActions: List<AppBarAction>,
  scrollBehavior: TopAppBarScrollBehavior? = null,
) {
  CenterAlignedTopAppBar(
    title = { Text(title) },
    actions = {
      rightActions.forEach { action ->
        IconButton(onClick = action.actionFn) {
          when (action.actionType) {
            AppBarActionType.APP_SETTING -> Icon(Icons.Filled.Settings, contentDescription = "Settings")
            AppBarActionType.SIGN_OUT -> Icon(Icons.Filled.Logout, contentDescription = "Sign Out")
            AppBarActionType.NO_ACTION -> {} // No-op or replace with real UI
            AppBarActionType.DOWNLOAD_MANAGER -> {} // Implement as needed
            AppBarActionType.MODEL_SELECTOR -> {}    // Implement as needed
            AppBarActionType.NAVIGATE_UP -> {}       // Implement as needed
            AppBarActionType.REFRESH_MODELS -> {}    // Implement as needed
            AppBarActionType.REFRESHING_MODELS -> {} // Implement as needed
          }
        }
      }
    },
    scrollBehavior = scrollBehavior
  )
}
