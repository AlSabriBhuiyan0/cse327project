

package com.google.ai.edge.gallery.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.google.ai.edge.gallery.ui.theme.customColors
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.CodeBlockStyle
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.string.RichTextStringStyle

/** Composable function to display Markdown-formatted text. */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, smallFontSize: Boolean = false) {
  val fontSize =
    if (smallFontSize) MaterialTheme.typography.bodyMedium.fontSize
    else MaterialTheme.typography.bodyLarge.fontSize
  CompositionLocalProvider {
    ProvideTextStyle(value = TextStyle(fontSize = fontSize, lineHeight = fontSize * 1.3)) {
      RichText(
        modifier = modifier,
        style =
          RichTextStyle(
            codeBlockStyle =
              CodeBlockStyle(
                textStyle =
                  TextStyle(
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    fontFamily = FontFamily.Monospace,
                  )
              ),
            stringStyle =
              RichTextStringStyle(
                linkStyle =
                  TextLinkStyles(style = SpanStyle(color = MaterialTheme.customColors.linkColor))
              ),
          ),
      ) {
        Markdown(content = text)
      }
    }
  }
}

// @Preview(showBackground = true)
// @Composable
// fun MarkdownTextPreview() {
//   GalleryTheme {
//     MarkdownText(text = "*Hello World*\n**Good morning!!**")
//   }
// }
