

package com.google.ai.edge.gallery

interface AppLifecycleProvider {
  var isAppInForeground: Boolean
}

class GalleryLifecycleProvider : AppLifecycleProvider {
  private var _isAppInForeground = false

  override var isAppInForeground: Boolean
    get() = _isAppInForeground
    set(value) {
      _isAppInForeground = value
    }
}
