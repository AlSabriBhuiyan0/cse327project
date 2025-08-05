

package com.google.ai.edge.gallery.ui.theme

import androidx.compose.runtime.mutableStateOf
import com.google.ai.edge.gallery.proto.Theme

object ThemeSettings {
  val themeOverride = mutableStateOf<Theme>(Theme.THEME_AUTO)
}
