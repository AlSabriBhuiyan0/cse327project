

package com.google.ai.edge.gallery

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.ai.edge.gallery.ui.navigation.GalleryNavHost

/** Top level composable representing the main screen of the application. */
@Composable
fun GalleryApp(navController: NavHostController = rememberNavController()) {
  GalleryNavHost(navController = navController)
}
