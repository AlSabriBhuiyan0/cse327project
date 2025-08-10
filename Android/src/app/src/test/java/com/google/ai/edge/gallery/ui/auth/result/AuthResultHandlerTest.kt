package com.google.ai.edge.gallery.ui.auth.result

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.ai.edge.gallery.data.model.AuthResult
import com.google.ai.edge.gallery.data.model.User
import com.google.ai.edge.gallery.ui.auth.AuthViewModel
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test

class AuthResultHandlerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `handleAuthResult with Success calls onSuccess`() {
        // Given
        val testUser = User("test123", "test@example.com", "Test User")
        val successResult = AuthResult.Success(testUser)
        var successCalled = false
        
        // When
        handleAuthResult(
            result = successResult,
            onSuccess = { successCalled = true },
            onError = {},
            onLoading = {}
        )
        
        // Then
        assertThat(successCalled).isTrue()
    }

    @Test
    fun `handleAuthResult with Error calls onError`() {
        // Given
        val errorMessage = "Authentication failed"
        val errorResult = AuthResult.Error<Unit>(errorMessage)
        var errorMessageReceived = ""
        
        // When
        handleAuthResult(
            result = errorResult,
            onSuccess = {},
            onError = { message -> errorMessageReceived = message },
            onLoading = {}
        )
        
        // Then
        assertThat(errorMessageReceived).isEqualTo(errorMessage)
    }

    @Test
    fun `handleAuthResult with Loading calls onLoading`() {
        // Given
        val loadingResult = AuthResult.Loading<Unit>()
        var loadingCalled = false
        
        // When
        handleAuthResult(
            result = loadingResult,
            onSuccess = {},
            onError = {},
            onLoading = { loadingCalled = true }
        )
        
        // Then
        assertThat(loadingCalled).isTrue()
    }

    @Test
    fun `AuthResultHandler composable shows loading state`() {
        // Given
        val loadingResult = AuthResult.Loading<Unit>()
        
        // When
        composeTestRule.setContent {
            AuthResultHandler(
                result = loadingResult,
                onSuccess = {},
                onError = {}
            )
        }
        
        // Then
        composeTestRule.onNodeWithText("Loading").assertIsDisplayed()
    }

    @Test
    fun `AuthResultHandler composable shows error state`() {
        // Given
        val errorMessage = "Authentication failed"
        val errorResult = AuthResult.Error<Unit>(errorMessage)
        
        // When
        composeTestRule.setContent {
            AuthResultHandler(
                result = errorResult,
                onSuccess = {},
                onError = {}
            )
        }
        
        // Then
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    @Test
    fun `AuthResultHandler composable calls onSuccess when result is success`() {
        // Given
        val testUser = User("test123", "test@example.com", "Test User")
        val successResult = AuthResult.Success(testUser)
        var successCalled = false
        
        // When
        composeTestRule.setContent {
            AuthResultHandler(
                result = successResult,
                onSuccess = { successCalled = true },
                onError = {}
            )
        }
        
        // Then
        assertThat(successCalled).isTrue()
    }

    @Test
    fun `observeAuthResults handles state changes correctly`() {
        // Given
        val testUser = User("test123", "test@example.com", "Test User")
        val authStateFlow = MutableStateFlow<AuthResult<User>>(AuthResult.Loading())
        val mockViewModel: AuthViewModel = mockk(relaxed = true) {
            every { authState } returns authStateFlow
        }
        
        var loadingState = false
        var successState = false
        var errorState: String? = null
        
        // When - Initial loading state
        observeAuthResults(
            viewModel = mockViewModel,
            onLoading = { loadingState = true },
            onSuccess = { successState = true },
            onError = { errorState = it }
        )
        
        // Then - Verify loading state
        assertThat(loadingState).isTrue()
        
        // When - Update to success state
        authStateFlow.value = AuthResult.Success(testUser)
        
        // Then - Verify success state
        assertThat(successState).isTrue()
        
        // When - Update to error state
        val errorMessage = "Test error"
        authStateFlow.value = AuthResult.Error(errorMessage)
        
        // Then - Verify error state
        assertThat(errorState).isEqualTo(errorMessage)
    }
}
