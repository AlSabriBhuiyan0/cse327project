

package com.google.ai.edge.gallery.ui.common.chat

// import androidx.compose.ui.tooling.preview.Preview
// import com.google.ai.edge.gallery.ui.theme.GalleryTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Composable function to display benchmark LLM results within a chat message.
 *
 * This function renders benchmark statistics (e.g., various token speed) in data cards
 */
@Composable
fun MessageBodyBenchmarkLlm(message: ChatMessageBenchmarkLlmResult, modifier: Modifier = Modifier) {
  Column(modifier = modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    // Data cards.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      for (stat in message.orderedStats) {
        DataCard(label = stat.label, unit = stat.unit, value = message.statValues[stat.id])
      }
    }
  }
}

// @Preview(showBackground = true)
// @Composable
// fun MessageBodyBenchmarkLlmPreview() {
//   GalleryTheme {
//     MessageBodyBenchmarkLlm(
//       message = ChatMessageBenchmarkLlmResult(
//         orderedStats = listOf(
//           Stat(id = "stat1", label = "Stat1", unit = "tokens/s"),
//           Stat(id = "stat2", label = "Stat2", unit = "tokens/s")
//         ),
//         statValues = mutableMapOf(
//           "stat1" to 0.3f,
//           "stat2" to 0.4f,
//         ),
//         running = false,
//       )
//     )
//   }
// }
