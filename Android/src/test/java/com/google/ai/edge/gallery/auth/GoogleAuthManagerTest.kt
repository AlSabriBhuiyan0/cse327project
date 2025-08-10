package com.google.ai.edge.gallery.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialResponse
import androidx.lifecycle.LiveData
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.gallery.R
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@OptIn(ExperimentalCoroutinesApi::class)
class GoogleAuthManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    
    private lateinit var context: Context
    private lateinit var authManager: GoogleAuthManager
    
    // Mocks
    private lateinit var mockFirebaseAuth: FirebaseAuth
    private lateinit var mockGoogleSignInClient: GoogleSignInClient
    private lateinit var mockSignInClient: SignInClient
    private lateinit var mockActivity: Activity
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        FirebaseApp.initializeApp(context)
        
        // Setup mocks
        mockFirebaseAuth = mockk(relaxed = true)
        mockGoogleSignInClient = mockk(relaxed = true)
        mockSignInClient = mockk(relaxed = true)
        mockActivity = mockk(relaxed = true)
        
        // Mock GoogleSignIn
        mockkStatic(GoogleSignIn::class)
        every { GoogleSignIn.getLastSignedInAccount(any()) } returns mockk(relaxed = true)
        
        // Create the auth manager with test dispatcher
        authManager = GoogleAuthManager(
            context = context,
            firebaseAuth = mockFirebaseAuth,
            ioDispatcher = testDispatcher
        )
        
        // Inject mocks using reflection (for testing purposes)
        val signInClientField = GoogleAuthManager::class.java.getDeclaredField("oneTapClient")
        signInClientField.isAccessible = true
        signInClientField.set(authManager, mockSignInClient)
        
        val googleSignInClientField = GoogleAuthManager::class.java.getDeclaredField("googleSignInClient")
        googleSignInClientField.isAccessible = true
        googleSignInClientField.set(authManager, mockGoogleSignInClient)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }
    
    @Test
    fun `signIn with Credential Manager success`() = testScope.runTest {
        // Given
        val mockCredential: Credential = mockk(relaxed = true)
        val mockResponse = GetCredentialResponse(mockCredential)
        
        coEvery { mockSignInClient.beginSignIn(any<BeginSignInRequest>()) } returns mockk {
            coAwait().let { beginResult ->
                // Simulate successful credential response
                authManager.handleCredentialManagerResult(mockResponse)
                beginResult
            }
        }
        
        // When
        val result = authManager.signIn(mockActivity, null)
        
        // Then
        assertThat(result.isSuccess).isTrue()
        coVerify { mockSignInClient.beginSignIn(any<BeginSignInRequest>()) }
    }
    
    @Test
    fun `handleCredentialManagerResult with valid Google credential signs in successfully`() = testScope.runTest {
        // Given
        val testEmail = "test@example.com"
        val testToken = "test_token"
        
        val mockCredential: Credential = mockk {
            every { type } returns "com.google.android.libraries.identity.googleid.GOOGLE_ID_TOKEN_CREDENTIAL"
            every { data } returns mapOf("id_token" to testToken)
        }
        
        val mockResponse = GetCredentialResponse(mockCredential)
        val mockFirebaseUser: FirebaseUser = mockk(relaxed = true)
        val mockAuthResult: com.google.firebase.auth.AuthResult = mockk {
            every { user } returns mockFirebaseUser
        }
        
        coEvery { mockFirebaseAuth.signInWithCredential(any()) } returns mockk {
            coAwait().let { mockAuthResult }
        }
        
        // When
        val result = authManager.handleCredentialManagerResult(mockResponse)
        
        // Then
        assertThat(result.isSuccess).isTrue()
        coVerify { mockFirebaseAuth.signInWithCredential(any()) }
    }
    
    @Test
    fun `handleCredentialManagerResult with invalid credential type returns failure`() = testScope.runTest {
        // Given
        val mockCredential: Credential = mockk {
            every { type } returns "invalid_credential_type"
        }
        val mockResponse = GetCredentialResponse(mockCredential)
        
        // When
        val result = authManager.handleCredentialManagerResult(mockResponse)
        
        // Then
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull() is UnsupportedOperationException).isTrue()
    }
    
    @Test
    fun `signOut signs out from both Firebase and Google`() = testScope.runTest {
        // Given
        coEvery { mockGoogleSignInClient.signOut() } returns mockk {
            coAwait()
            null
        }
        
        // When
        authManager.signOut()
        
        // Then
        coVerify { mockFirebaseAuth.signOut() }
        coVerify { mockGoogleSignInClient.signOut() }
    }
    
    @Test
    fun `isUserSignedIn returns true when Firebase has current user`() {
        // Given
        every { mockFirebaseAuth.currentUser } returns mockk(relaxed = true)
        
        // When
        val isSignedIn = authManager.isUserSignedIn()
        
        // Then
        assertThat(isSignedIn).isTrue()
    }
    
    @Test
    fun `isUserSignedIn returns false when Firebase has no current user`() {
        // Given
        every { mockFirebaseAuth.currentUser } returns null
        
        // When
        val isSignedIn = authManager.isUserSignedIn()
        
        // Then
        assertThat(isSignedIn).isFalse()
    }
}
