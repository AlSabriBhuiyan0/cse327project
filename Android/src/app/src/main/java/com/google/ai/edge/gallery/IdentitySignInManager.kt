package com.google.ai.edge.gallery

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.libraries.identity.googleid.GetSignInIntentRequest
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.libraries.identity.googleid.Identity
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

/**
 * Experimental skeleton for Google Identity Services (One Tap) sign-in using the Credential Manager.
 * Not yet wired into UI; kept side-by-side with GoogleSignInManager pending full migration.
 */
@ActivityScoped
class IdentitySignInManager @Inject constructor() {
  private val tag = "IdentitySignInManager"

  /**
   * Builds a sign-in intent request for Google Identity Services.
   * clientId: Web client ID from Google Cloud console (same as used for Firebase auth).
   */
  fun buildSignInRequest(clientId: String): GetSignInIntentRequest {
    require(clientId.isNotBlank()) { "Web client ID is blank. Ensure default_web_client_id is configured." }
    return GetSignInIntentRequest.builder()
      .setServerClientId(clientId)
      .build()
  }

  /**
   * Launches the sign-in intent. You must call this from an Activity.
   * Returns a PendingIntent to be launched via startIntentSenderForResult or similar APIs.
   */
  fun getSignInPendingIntent(activity: Activity, request: GetSignInIntentRequest) =
    Identity.getSignInClient(activity).getSignInIntent(request)

  /**
   * Extracts ID token from the returned activity result intent.
   * Returns null if parsing fails.
   */
  fun extractIdToken(context: Context, data: android.content.Intent?): String? {
    return try {
      val credential = GoogleIdTokenCredential.createFrom(data)
      credential.idToken
    } catch (e: GoogleIdTokenParsingException) {
      Log.e(tag, "Failed to parse Google ID token", e)
      null
    } catch (e: Exception) {
      Log.e(tag, "Unexpected error extracting Google ID token", e)
      null
    }
  }
}
