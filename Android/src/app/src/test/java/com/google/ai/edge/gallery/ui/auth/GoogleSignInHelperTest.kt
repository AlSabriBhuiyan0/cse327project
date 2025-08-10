package com.google.ai.edge.gallery.ui.auth

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GoogleSignInHelperTest {

    private lateinit var context: Context
    private lateinit var googleSignInHelper: GoogleSignInHelper
    private lateinit var mockGoogleSignInClient: GoogleSignInClient
    private lateinit var mockGoogleSignInAccount: GoogleSignInAccount

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // Mock GoogleSignIn static methods
        mockkStatic(GoogleSignIn::class)
        
        // Create mock GoogleSignInClient
        mockGoogleSignInClient = mockk(relaxed = true)
        every { GoogleSignIn.getClient(any(), any()) } returns mockGoogleSignInClient
        
        // Create mock GoogleSignInAccount
        mockGoogleSignInAccount = mockk()
        
        // Initialize the helper
        googleSignInHelper = GoogleSignInHelper(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getSignInIntent returns intent from GoogleSignInClient`() {
        // Given
        val testIntent = Intent("TEST_ACTION")
        every { mockGoogleSignInClient.signInIntent } returns testIntent
        
        // When
        val result = googleSignInHelper.getSignInIntent()
        
        // Then
        assertThat(result).isSameInstanceAs(testIntent)
        verify { mockGoogleSignInClient.signInIntent }
    }

    @Test
    fun `getLastSignedInAccount returns account when available`() {
        // Given
        mockkStatic(GoogleSignIn::class)
        every { GoogleSignIn.getLastSignedInAccount(any()) } returns mockGoogleSignInAccount
        
        // When
        val result = googleSignInHelper.getLastSignedInAccount()
        
        // Then
        assertThat(result).isSameInstanceAs(mockGoogleSignInAccount)
        verify { GoogleSignIn.getLastSignedInAccount(any()) }
    }

    @Test
    fun `getLastSignedInAccount returns null when no account`() {
        // Given
        mockkStatic(GoogleSignIn::class)
        every { GoogleSignIn.getLastSignedInAccount(any()) } returns null
        
        // When
        val result = googleSignInHelper.getLastSignedInAccount()
        
        // Then
        assertThat(result).isNull()
        verify { GoogleSignIn.getLastSignedInAccount(any()) }
    }

    @Test
    fun `signOut calls signOut on GoogleSignInClient`() {
        // When
        googleSignInHelper.signOut()
        
        // Then
        verify { mockGoogleSignInClient.signOut() }
    }

    @Test
    fun `getSignedInAccountFromIntent returns account when successful`() {
        // Given
        val testIntent = Intent()
        val mockTask = mockk<com.google.android.gms.tasks.Task<GoogleSignInAccount>>()
        
        mockkStatic(GoogleSignIn::class)
        every { GoogleSignIn.getSignedInAccountFromIntent(testIntent) } returns mockTask
        every { mockTask.getResult(ApiException::class.java) } returns mockGoogleSignInAccount
        
        // When
        val result = googleSignInHelper.getSignedInAccountFromIntent(testIntent)
        
        // Then
        assertThat(result).isSameInstanceAs(mockGoogleSignInAccount)
    }

    @Test
    fun `getSignedInAccountFromIntent returns null on ApiException`() {
        // Given
        val testIntent = Intent()
        val mockTask = mockk<com.google.android.gms.tasks.Task<GoogleSignInAccount>>()
        
        mockkStatic(GoogleSignIn::class)
        every { GoogleSignIn.getSignedInAccountFromIntent(testIntent) } returns mockTask
        every { mockTask.getResult(ApiException::class.java) } throws ApiException(null)
        
        // When
        val result = googleSignInHelper.getSignedInAccountFromIntent(testIntent)
        
        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `GoogleSignInContract creates correct intent`() {
        // Given
        val contract = GoogleSignInHelper.GoogleSignInContract()
        val testIntent = Intent("TEST_ACTION")
        
        mockkStatic(GoogleSignIn::class)
        every { GoogleSignIn.getClient(any(), any()) } returns mockGoogleSignInClient
        every { mockGoogleSignInClient.signInIntent } returns testIntent
        
        // When
        val result = contract.createIntent(context, Unit)
        
        // Then
        assertThat(result).isSameInstanceAs(testIntent)
    }

    @Test
    fun `GoogleSignInContract parses result correctly`() {
        // Given
        val contract = GoogleSignInHelper.GoogleSignInContract()
        val testIntent = Intent()
        val mockTask = mockk<com.google.android.gms.tasks.Task<GoogleSignInAccount>>()
        
        mockkStatic(GoogleSignIn::class)
        every { GoogleSignIn.getSignedInAccountFromIntent(testIntent) } returns mockTask
        
        // When
        val result = contract.parseResult(android.app.Activity.RESULT_OK, testIntent)
        
        // Then
        assertThat(result).isSameInstanceAs(mockTask)
    }
}
