

package com.google.ai.edge.gallery.ui.common.chat

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * Custom Shape for creating message bubble outlines with configurable corner radii.
 *
 * This class defines a custom Shape that generates a rounded rectangle outline, suitable for
 * message bubbles. It allows specifying a uniform corner radius for most corners, but also provides
 * the option to have a hard (non-rounded) corner on either the left or right side.
 */
class MessageBubbleShape(
  private val radius: Dp,
  private val hardCornerAtLeftOrRight: Boolean = false,
) : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    val radiusPx = with(density) { radius.toPx() }
    val path =
      Path().apply {
        addRoundRect(
          RoundRect(
            left = 0f,
            top = 0f,
            right = size.width,
            bottom = size.height,
            topLeftCornerRadius =
              if (hardCornerAtLeftOrRight) CornerRadius(0f, 0f)
              else CornerRadius(radiusPx, radiusPx),
            topRightCornerRadius =
              if (hardCornerAtLeftOrRight) CornerRadius(radiusPx, radiusPx)
              else CornerRadius(0f, 0f), // No rounding here
            bottomLeftCornerRadius = CornerRadius(radiusPx, radiusPx),
            bottomRightCornerRadius = CornerRadius(radiusPx, radiusPx),
          )
        )
      }
    return Outline.Generic(path)
  }
}
