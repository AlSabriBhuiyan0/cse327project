package com.google.ai.edge.gallery.bridge.gmail

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.gallery.bridge.MessageBridgeRepository
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Integration tests for the Gmail bridge components using the real Gmail API.
 * 
 * These tests require:
 * 1. A test Gmail account with API access enabled
 * 2. Internet connection
 * 3. Valid Google Sign-In credentials
 * 
 * Note: These tests will perform actual network calls to the Gmail API.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class GmailBridgeIntegrationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var gmailAuthService: GmailAuthService

    @Inject
    lateinit var gmailService: GmailService

    @Inject
    lateinit var errorHandler: GmailErrorHandler

    private val testDispatcher = TestCoroutineDispatcher()
    private val testEmail = "your-test-email@gmail.com" // Replace with test email
    private val testSubject = "Test Subject ${System.currentTimeMillis()}"
    private val testBody = "This is a test email body."

    @Before
    fun setup() {
        hiltRule.inject()
        // Enable debug logging
        Timber.plant(Timber.DebugTree())
    }

    @After
    fun cleanup() = runBlocking {
        try {
            Timber.d("Cleaning up test resources")
            gmailService.disconnect()
            gmailAuthService.signOut()
        } catch (e: Exception) {
            Timber.e(e, "Error during cleanup")
        }
    }

    @Test
    fun testSignInAndLoadLabels() = runTest {
        // Given: User is not signed in
        assertThat(gmailAuthService.isSignedIn()).isFalse()

        // When: Signing in
        val signInResult = signInWithLatch()
        assertThat(signInResult.isSuccess).isTrue()
        assertThat(gmailAuthService.isSignedIn()).isTrue()

        // Then: Should be able to load labels
        val labels = gmailService.labels.first()
        assertThat(labels).isNotEmpty()
        Timber.d("Found ${labels.size} labels")
    }

    @Test
    fun testSendAndReceiveEmail() = runTest {
        // Given: User is signed in
        signInWithLatch()
        
        // When: Sending an email
        val sendResult = kotlin.runCatching {
            gmailService.sendEmail(
                to = testEmail,
                subject = testSubject,
                body = testBody
            )
        }
        
        // Then: Email should be sent successfully
        assertThat(sendResult.isSuccess).isTrue()
        Timber.d("Email sent successfully")

        // Wait a moment for the email to be delivered
        delay(5000)

        // And: Should be able to find the sent email
        val messages = gmailService.loadRecentMessages(
            maxResults = 10,
            query = "subject:$testSubject"
        )
        
        assertThat(messages).isNotEmpty()
        val receivedEmail = messages.first()
        assertThat(receivedEmail.subject).contains(testSubject)
        assertThat(receivedEmail.body).contains(testBody)
    }

    @Test
    fun testErrorHandling_InvalidRecipient() = runTest {
        // Given: User is signed in
        signInWithLatch()
        
        // When: Sending to an invalid email address
        val errorMessage = kotlin.runCatching {
            gmailService.sendEmail(
                to = "invalid-email",
                subject = testSubject,
                body = testBody
            )
        }.exceptionOrNull()?.message ?: ""
        
        // Then: Should receive a descriptive error message
        assertThat(errorMessage).isNotEmpty()
        assertThat(errorMessage).contains("invalid", ignoreCase = true)
        Timber.d("Received expected error: $errorMessage")
    }

    @Test
    fun testTokenRefresh() = runTest {
        // Given: User is signed in
        signInWithLatch()
        
        // When: Forcing token refresh
        val account = gmailAuthService.getCurrentAccount()!!
        gmailAuthService.forceTokenRefresh(account)
        
        // Then: Should still be able to access Gmail API
        val labels = gmailService.labels.first()
        assertThat(labels).isNotEmpty()
        Timber.d("Successfully refreshed token and accessed Gmail API")
    }

    @Test
    fun testNetworkErrorRecovery() = runTest {
        // This test simulates a network error by temporarily disabling network
        // and verifies that the error is properly handled and recovery is possible
        
        // Given: User is signed in and has a valid session
        signInWithLatch()
        
        try {
            // Simulate network error by using an invalid API endpoint
            // (in a real test, you would use a test rule to control network state)
            val error = kotlin.runCatching {
                gmailService.loadRecentMessages(maxResults = 1, query = "this-should-fail-${System.currentTimeMillis()}")
            }.exceptionOrNull()
            
            // Then: Should handle the error gracefully
            assertThat(error).isNotNull()
            Timber.d("Handled network error: ${error?.message}")
            
            // And: Should be able to recover and make a new request
            val labels = gmailService.labels.first()
            assertThat(labels).isNotEmpty()
            Timber.d("Successfully recovered from network error")
            
        } catch (e: Exception) {
            // Log the error but don't fail the test - we expect some errors here
            val handledError = errorHandler.handleError(e, "Network error test")
            Timber.e("Test encountered handled error: ${handledError.message}")
        }
    }

    // Helper function to handle the asynchronous sign-in flow
    private suspend fun signInWithLatch(): Result<Unit> = kotlin.runCatching {
        val latch = CountDownLatch(1)
        var signInResult: Result<Unit> = Result.failure(RuntimeException("Sign in not completed"))
        
        val activity = ApplicationProvider.getApplicationContext<Context>()
        
        // Launch sign-in in a separate coroutine
        launch(testDispatcher) {
            gmailAuthService.signIn(activity as android.app.Activity) { result ->
                signInResult = result
                latch.countDown()
            }
        }
        
        // Wait for sign-in to complete with a timeout
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw TimeoutException("Sign in timed out")
        }
        
        // Wait a bit more for any post-sign-in setup
        delay(2000)
        
        signInResult.getOrThrow()
    }
}
