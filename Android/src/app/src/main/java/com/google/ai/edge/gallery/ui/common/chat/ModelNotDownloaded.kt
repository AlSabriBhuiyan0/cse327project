

package com.google.ai.edge.gallery.ui.common.chat

// import androidx.compose.ui.tooling.preview.Preview
// import com.google.ai.edge.gallery.ui.theme.GalleryTheme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Composable function to display a button to download model if the model has not been downloaded.
 */
@Composable
fun ModelNotDownloaded(modifier: Modifier = Modifier, onClicked: () -> Unit) {
  Column(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Button(onClick = onClicked) { Text("Download & Try it", maxLines = 1) }
  }
}

// @Preview(showBackground = true)
// @Composable
// fun Preview() {
//   GalleryTheme { ModelNotDownloaded(onClicked = {}) }
// }
