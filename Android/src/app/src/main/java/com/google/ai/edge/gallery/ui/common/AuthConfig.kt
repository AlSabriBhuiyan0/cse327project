

package com.google.ai.edge.gallery.ui.common

import androidx.core.net.toUri
import net.openid.appauth.AuthorizationServiceConfiguration

object AuthConfig {
  // Hugging Face Client ID.
  const val clientId = "88a0ac25-fcf4-467b-b8cd-ebcc2aec9bd0"
  // Registered redirect URI.
  //
  // The scheme needs to match the
  // "android.defaultConfig.manifestPlaceholders["appAuthRedirectScheme"]" field in
  // "build.gradle.kts".
  const val redirectUri = "com.google.ai.edge.gallery.oauth://oauthredirect"

  // OAuth 2.0 Endpoints (Authorization + Token Exchange)
  private const val authEndpoint = "https://huggingface.co/oauth/authorize"
  private const val tokenEndpoint = "https://huggingface.co/oauth/token"

  // OAuth service configuration (AppAuth library requires this)
  val authServiceConfig =
    AuthorizationServiceConfiguration(
      authEndpoint.toUri(), // Authorization endpoint
      tokenEndpoint.toUri(), // Token exchange endpoint
    )
}
