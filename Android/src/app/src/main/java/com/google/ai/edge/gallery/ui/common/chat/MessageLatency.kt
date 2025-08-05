

package com.google.ai.edge.gallery.ui.common.chat

// import androidx.compose.ui.tooling.preview.Preview
// import com.google.ai.edge.gallery.ui.theme.GalleryTheme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.google.ai.edge.gallery.ui.common.humanReadableDuration

/** Composable function to display the latency of a chat message, if available. */
@Composable
fun LatencyText(message: ChatMessage) {
  if (message.latencyMs >= 0) {
    Text(
      message.latencyMs.humanReadableDuration(),
      modifier = Modifier.alpha(0.5f),
      style = MaterialTheme.typography.labelSmall,
    )
  }
}

// @Preview(showBackground = true)
// @Composable
// fun LatencyTextPreview() {
//   GalleryTheme {
//     Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp))
// {
//       for (latencyMs in listOf(123f, 1234f, 123456f, 7234567f)) {
//         LatencyText(
//           message =
//             ChatMessage(latencyMs = latencyMs, type = ChatMessageType.TEXT, side =
// ChatSide.AGENT)
//         )
//       }
//     }
//   }
// }
