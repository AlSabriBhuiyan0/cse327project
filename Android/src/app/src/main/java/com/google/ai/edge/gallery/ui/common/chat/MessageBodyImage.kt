

package com.google.ai.edge.gallery.ui.common.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun MessageBodyImage(message: ChatMessageImage, modifier: Modifier = Modifier) {
  val bitmapWidth = message.bitmap.width
  val bitmapHeight = message.bitmap.height
  val imageWidth =
    if (bitmapWidth >= bitmapHeight) 200 else (200f / bitmapHeight * bitmapWidth).toInt()
  val imageHeight =
    if (bitmapHeight >= bitmapWidth) 200 else (200f / bitmapWidth * bitmapHeight).toInt()

  Image(
    bitmap = message.imageBitMap,
    contentDescription = "",
    modifier = modifier.height(imageHeight.dp).width(imageWidth.dp),
    contentScale = ContentScale.Fit,
  )
}
