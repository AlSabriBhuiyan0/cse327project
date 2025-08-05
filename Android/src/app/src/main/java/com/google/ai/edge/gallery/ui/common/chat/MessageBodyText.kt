

package com.google.ai.edge.gallery.ui.common.chat

// import com.google.ai.edge.gallery.ui.theme.GalleryTheme
// import androidx.compose.ui.tooling.preview.Preview

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.common.MarkdownText

/** Composable function to display the text content of a ChatMessageText. */
@Composable
fun MessageBodyText(message: ChatMessageText) {
  if (message.side == ChatSide.USER) {
    Text(
      message.content,
      style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
      color = Color.White,
      modifier = Modifier.padding(12.dp),
    )
  } else if (message.side == ChatSide.AGENT) {
    if (message.isMarkdown) {
      MarkdownText(text = message.content, modifier = Modifier.padding(12.dp))
    } else {
      Text(
        message.content,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(12.dp),
      )
    }
  }
}

// @Preview(showBackground = true)
// @Composable
// fun MessageBodyTextPreview() {
//   GalleryTheme {
//     Column {
//       Row(modifier = Modifier.padding(16.dp).background(MaterialTheme.colorScheme.primary)) {
//         MessageBodyText(ChatMessageText(content = "Hello world", side = ChatSide.USER))
//       }
//       Row(
//         modifier = Modifier.padding(16.dp).background(MaterialTheme.colorScheme.surfaceContainer)
//       ) {
//         MessageBodyText(ChatMessageText(content = "yes hello world", side = ChatSide.AGENT))
//       }
//     }
//   }
// }
