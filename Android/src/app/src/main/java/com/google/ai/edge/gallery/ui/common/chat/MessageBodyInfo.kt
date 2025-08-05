

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
import com.google.ai.edge.gallery.ui.theme.customColors

/**
 * Composable function to display informational message content within a chat.
 *
 * Supports markdown.
 */
@Composable
fun MessageBodyInfo(message: ChatMessageInfo, smallFontSize: Boolean = true) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
    Box(
      modifier =
        Modifier.clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.customColors.agentBubbleBgColor)
    ) {
      MarkdownText(
        text = message.content,
        modifier = Modifier.padding(12.dp),
        smallFontSize = smallFontSize,
      )
    }
  }
}

// @Preview(showBackground = true)
// @Composable
// fun MessageBodyInfoPreview() {
//   GalleryTheme {
//     Row(modifier = Modifier.padding(16.dp)) {
//       MessageBodyInfo(message = ChatMessageInfo(content = "This is a model"))
//     }
//   }
// }
