package com.google.ai.edge.gallery.ui.auth

import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.gallery.MainActivity
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.auth.AuthState
import com.google.ai.edge.gallery.auth.GoogleAuthManager
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SignInActivityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<SignInActivity>()

    @Inject
    lateinit var auth: FirebaseAuth

    @Inject
    lateinit var googleAuthManager: GoogleAuthManager

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        hiltRule.inject()
        FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testScope.cleanupTestCoroutines()
        unmockkAll()
    }

    @Test
    fun whenClickGoogleSignInButton_shouldLaunchGoogleSignIn() = testScope.runTest {
        // Given
        val mockSignInClient = mockk<SignInClient>(relaxed = true)
        val mockBeginSignInRequest = mockk<BeginSignInRequest>(relaxed = true)
        
        // Mock the Google Sign-In client
        mockkObject(googleAuthManager)
        coEvery { googleAuthManager.signIn(any(), any()) } coAnswers {
            // Simulate successful sign-in
            composeTestRule.activity.runOnUiThread {
                (composeTestRule.activity as? SignInActivity)?.let { activity ->
                    activity.viewModel.handleGoogleSignInResult(Result.success(mockk(relaxed = true)))
                }
            }
        }

        // When - Click the Google Sign-In button
        composeTestRule.onNodeWithText("Sign in with Google").performClick()

        // Then - Verify the sign-in flow was triggered
        coVerify { googleAuthManager.signIn(any(), any()) }
    }

    @Test
    fun whenSignInFails_shouldShowErrorMessage() = testScope.runTest {
        // Given
        val errorMessage = "Sign in failed. Please try again."
        
        // Mock the Google Sign-In to fail
        mockkObject(googleAuthManager)
        coEvery { googleAuthManager.signIn(any(), any()) } coAnswers {
            // Simulate failed sign-in
            composeTestRule.activity.runOnUiThread {
                (composeTestRule.activity as? SignInActivity)?.let { activity ->
                    activity.viewModel.handleGoogleSignInResult(
                        Result.failure(Exception(errorMessage))
                    )
                }
            }
        }

        // When - Click the Google Sign-In button
        composeTestRule.onNodeWithText("Sign in with Google").performClick()

        // Then - Verify error message is displayed
        composeTestRule.onNodeWithText(errorMessage, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun whenAlreadySignedIn_shouldNavigateToMain() = testScope.runTest {
        // Given - Mock that user is already signed in
        mockkObject(googleAuthManager)
        coEvery { googleAuthManager.isUserSignedIn() } returns true
        
        // Create a new activity scenario
        val scenario = ActivityScenario.launch(SignInActivity::class.java)
        
        // Then - Verify we navigate to MainActivity
        scenario.onActivity { activity ->
            assert(activity.isFinishing)
        }
        
        scenario.close()
    }

    @Test
    fun whenBackPressed_shouldNotFinishActivity() = testScope.runTest {
        // When - Press back
        composeTestRule.activity.onBackPressed()
        
        // Then - Activity should not finish (moved to back)
        assert(!composeTestRule.activity.isFinishing)
    }

    @Test
    fun whenSignInSuccess_shouldNavigateToMain() = testScope.runTest {
        // Given - Mock successful sign-in
        val mockAccount = mockk<com.google.android.gms.auth.api.signin.GoogleSignInAccount>(relaxed = true)
        mockkObject(googleAuthManager)
        coEvery { googleAuthManager.signIn(any(), any()) } coAnswers {
            // Simulate successful sign-in
            composeTestRule.activity.runOnUiThread {
                (composeTestRule.activity as? SignInActivity)?.let { activity ->
                    activity.viewModel.handleGoogleSignInResult(Result.success(mockAccount))
                }
            }
        }

        // When - Click the Google Sign-In button
        composeTestRule.onNodeWithText("Sign in with Google").performClick()
        
        // Then - Verify we navigate to MainActivity
        assert(composeTestRule.activity.isFinishing)
    }
}
