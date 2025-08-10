package com.google.ai.edge.gallery.ui.auth.state

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.model.AuthResult
import com.google.ai.edge.gallery.data.model.User
import com.google.ai.edge.gallery.ui.auth.AuthViewModel
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AuthScreenStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = TestCoroutineDispatcher()
    private lateinit var viewModel: AuthViewModel
    private val mockAuthRepository: AuthRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AuthViewModel(mockAuthRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun `login state updates correctly on success`() = runTest {
        // Given
        val testUser = User("test123", "test@example.com", "Test User")
        every { mockAuthRepository.signInWithEmailAndPassword(any(), any()) } returns
            MutableStateFlow(AuthResult.Success(testUser))
        
        // When
        viewModel.signInWithEmailAndPassword("test@example.com", "password123")
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.authState.value
        assertThat(state).isInstanceOf(AuthResult.Success::class.java)
        assertThat((state as AuthResult.Success).data).isEqualTo(testUser)
    }

    @Test
    fun `sign up state updates correctly on success`() = runTest {
        // Given
        val testUser = User("test123", "test@example.com", "Test User")
        every { mockAuthRepository.signUpWithEmailAndPassword(any(), any(), any()) } returns
            MutableStateFlow(AuthResult.Success(testUser))
        
        // When
        viewModel.signUpWithEmailAndPassword("Test User", "test@example.com", "password123")
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.authState.value
        assertThat(state).isInstanceOf(AuthResult.Success::class.java)
        assertThat((state as AuthResult.Success).data).isEqualTo(testUser)
    }

    @Test
    fun `forgot password state updates correctly on success`() = runTest {
        // Given
        every { mockAuthRepository.sendPasswordResetEmail(any()) } returns
            MutableStateFlow(AuthResult.Success(Unit))
        
        // When
        viewModel.sendPasswordResetEmail("test@example.com")
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.authState.value
        assertThat(state).isInstanceOf(AuthResult.Success::class.java)
    }

    @Test
    fun `login state updates correctly on error`() = runTest {
        // Given
        val errorMessage = "Invalid credentials"
        every { mockAuthRepository.signInWithEmailAndPassword(any(), any()) } returns
            MutableStateFlow(AuthResult.Error<Unit>(errorMessage))
        
        // When
        viewModel.signInWithEmailAndPassword("wrong@example.com", "wrongpassword")
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.authState.value
        assertThat(state).isInstanceOf(AuthResult.Error::class.java)
        assertThat((state as AuthResult.Error).message).isEqualTo(errorMessage)
    }

    @Test
    fun `sign up form validation works correctly`() {
        // Test empty name
        viewModel.onSignUpChange(
            name = "",
            email = "test@example.com",
            password = "password123",
            confirmPassword = "password123"
        )
        assertThat(viewModel.signUpState.value.nameError).isNotNull()
        
        // Test invalid email
        viewModel.onSignUpChange(
            name = "Test User",
            email = "invalid-email",
            password = "password123",
            confirmPassword = "password123"
        )
        assertThat(viewModel.signUpState.value.emailError).isNotNull()
        
        // Test short password
        viewModel.onSignUpChange(
            name = "Test User",
            email = "test@example.com",
            password = "12345",
            confirmPassword = "12345"
        )
        assertThat(viewModel.signUpState.value.passwordError).isNotNull()
        
        // Test password mismatch
        viewModel.onSignUpChange(
            name = "Test User",
            email = "test@example.com",
            password = "password123",
            confirmPassword = "differentpassword"
        )
        assertThat(viewModel.signUpState.value.confirmPasswordError).isNotNull()
        
        // Test valid form
        viewModel.onSignUpChange(
            name = "Test User",
            email = "test@example.com",
            password = "password123",
            confirmPassword = "password123"
        )
        assertThat(viewModel.signUpState.value.isFormValid).isTrue()
    }

    @Test
    fun `login form validation works correctly`() {
        // Test invalid email
        viewModel.onLoginChange(
            email = "invalid-email",
            password = "password123"
        )
        assertThat(viewModel.loginState.value.emailError).isNotNull()
        
        // Test short password
        viewModel.onLoginChange(
            email = "test@example.com",
            password = "12345"
        )
        assertThat(viewModel.loginState.value.passwordError).isNotNull()
        
        // Test valid form
        viewModel.onLoginChange(
            email = "test@example.com",
            password = "password123"
        )
        assertThat(viewModel.loginState.value.isFormValid).isTrue()
    }

    @Test
    fun `forgot password form validation works correctly`() {
        // Test invalid email
        viewModel.onForgotPasswordChange("invalid-email")
        assertThat(viewModel.forgotPasswordState.value.emailError).isNotNull()
        
        // Test empty email
        viewModel.onForgotPasswordChange("")
        assertThat(viewModel.forgotPasswordState.value.emailError).isNotNull()
        
        // Test valid email
        viewModel.onForgotPasswordChange("test@example.com")
        assertThat(viewModel.forgotPasswordState.value.isFormValid).isTrue()
    }

    @Test
    fun `resetAuthState resets the authentication state`() {
        // Given
        viewModel.onLoginChange("test@example.com", "password123")
        
        // When
        viewModel.resetAuthState()
        
        // Then
        assertThat(viewModel.loginState.value.email).isEmpty()
        assertThat(viewModel.loginState.value.password).isEmpty()
        assertThat(viewModel.authState.value).isInstanceOf(AuthResult.Loading::class.java)
    }
}
