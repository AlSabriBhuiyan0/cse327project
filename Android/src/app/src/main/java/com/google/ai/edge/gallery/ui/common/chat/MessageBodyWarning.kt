

package com.google.ai.edge.gallery.ui.common.chat

// import androidx.compose.ui.tooling.preview.Preview
// import com.google.ai.edge.gallery.ui.theme.GalleryTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.common.MarkdownText

/**
 * Composable function to display warning message content within a chat.
 *
 * Supports markdown.
 */
@Composable
fun MessageBodyWarning(message: ChatMessageWarning) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
    Box(
      modifier =
        Modifier.clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.tertiaryContainer)
    ) {
      MarkdownText(
        text = message.content,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        smallFontSize = true,
      )
    }
  }
}

// @Preview(showBackground = true)
// @Composable
// fun MessageBodyWarningPreview() {
//   GalleryTheme {
//     Row(modifier = Modifier.padding(16.dp)) {
//       MessageBodyWarning(message = ChatMessageWarning(content = "This is a warning"))
//     }
//   }
// }
