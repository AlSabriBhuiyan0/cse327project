package com.google.ai.edge.gallery.ui.auth

import com.google.ai.edge.gallery.data.model.AuthResult
import com.google.ai.edge.gallery.data.model.User
import com.google.ai.edge.gallery.data.repository.AuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: AuthViewModel
    private val mockRepository: AuthRepository = mockk(relaxed = true)
    private val testUser = User(
        id = "test123",
        email = "test@example.com",
        displayName = "Test User",
        photoUrl = "http://example.com/photo.jpg",
        isEmailVerified = true
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AuthViewModel(mockRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onLoginChange with valid email and password updates login state`() {
        // Given
        val email = "test@example.com"
        val password = "password123"

        // When
        viewModel.onLoginChange(email, password)

        // Then
        val state = viewModel.loginState.value
        assertThat(state.email).isEqualTo(email)
        assertThat(state.password).isEqualTo(password)
        assertThat(state.emailError).isNull()
        assertThat(state.passwordError).isNull()
        assertThat(state.isFormValid).isTrue()
    }

    @Test
    fun `onLoginChange with invalid email shows error`() {
        // Given
        val email = "invalid-email"
        val password = "password123"

        // When
        viewModel.onLoginChange(email, password)

        // Then
        val state = viewModel.loginState.value
        assertThat(state.emailError).isNotNull()
        assertThat(state.isFormValid).isFalse()
    }

    @Test
    fun `signInWithEmailAndPassword with valid credentials updates auth state`() = runTest {
        // Given
        val email = "test@example.com"
        val password = "password123"
        
        coEvery { mockRepository.signInWithEmailAndPassword(email, password) } returns
            flowOf(AuthResult.Success(Unit))
        coEvery { mockRepository.getCurrentUser() } returns testUser

        // When
        viewModel.signInWithEmailAndPassword(email, password)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val state = viewModel.authState.value
        assertThat(state).isInstanceOf(AuthResult.Success::class.java)
        assertThat((state as AuthResult.Success).data).isEqualTo(testUser)
        
        coVerify { mockRepository.signInWithEmailAndPassword(email, password) }
    }

    @Test
    fun `signInWithGoogle with valid account updates auth state`() = runTest {
        // Given
        val mockAccount: GoogleSignInAccount = mockk()
        every { mockAccount.idToken } returns "test-token"
        
        coEvery { mockRepository.signInWithGoogle(mockAccount) } returns
            flowOf(AuthResult.Success(Unit))
        coEvery { mockRepository.getCurrentUser() } returns testUser

        // When
        viewModel.signInWithGoogle(mockAccount)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val state = viewModel.authState.value
        assertThat(state).isInstanceOf(AuthResult.Success::class.java)
        assertThat((state as AuthResult.Success).data).isEqualTo(testUser)
        
        coVerify { mockRepository.signInWithGoogle(mockAccount) }
    }

    @Test
    fun `signOut calls repository and updates auth state`() = runTest {
        // Given
        coEvery { mockRepository.getCurrentUser() } returns null

        // When
        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val state = viewModel.authState.value
        assertThat(state).isInstanceOf(AuthResult.Error::class.java)
        
        coVerify { mockRepository.signOut() }
    }

    @Test
    fun `sendPasswordResetEmail with valid email updates auth state`() = runTest {
        // Given
        val email = "test@example.com"
        coEvery { mockRepository.sendPasswordResetEmail(email) } returns
            flowOf(AuthResult.Success(Unit))

        // When
        viewModel.sendPasswordResetEmail(email)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val state = viewModel.authState.value
        assertThat(state).isInstanceOf(AuthResult.Success::class.java)
        assertThat((state as AuthResult.Success).data.email).isEqualTo(email)
        
        coVerify { mockRepository.sendPasswordResetEmail(email) }
    }
}
