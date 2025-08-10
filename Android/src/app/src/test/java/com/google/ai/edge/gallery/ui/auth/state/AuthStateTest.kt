package com.google.ai.edge.gallery.ui.auth.state

import com.google.ai.edge.gallery.data.model.AuthResult
import com.google.ai.edge.gallery.data.model.User
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AuthStateTest {

    @Test
    fun `AuthState initial state is correct`() {
        // When
        val state = AuthState()

        // Then
        assertThat(state.isAuthenticated).isFalse()
        assertThat(state.user).isNull()
        assertThat(state.error).isNull()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `AuthState copy with user sets authenticated to true`() {
        // Given
        val testUser = User("test123", "test@example.com", "Test User")
        
        // When
        val state = AuthState(user = testUser)

        // Then
        assertThat(state.isAuthenticated).isTrue()
        assertThat(state.user).isEqualTo(testUser)
    }

    @Test
    fun `AuthState copy with error sets error message`() {
        // Given
        val errorMessage = "Authentication failed"
        
        // When
        val state = AuthState(error = errorMessage)

        // Then
        assertThat(state.error).isEqualTo(errorMessage)
    }

    @Test
    fun `AuthState copy with loading sets loading to true`() {
        // When
        val state = AuthState(isLoading = true)

        // Then
        assertThat(state.isLoading).isTrue()
    }

    @Test
    fun `AuthResult Success contains correct data`() {
        // Given
        val testData = "test data"
        
        // When
        val result = AuthResult.Success(testData)

        // Then
        assertThat(result.data).isEqualTo(testData)
        assertThat(result.message).isNull()
    }

    @Test
    fun `AuthResult Error contains correct error message`() {
        // Given
        val errorMessage = "Test error"
        
        // When
        val result = AuthResult.Error<Unit>(errorMessage)

        // Then
        assertThat(result.message).isEqualTo(errorMessage)
    }

    @Test
    fun `AuthResult Loading contains loading state`() {
        // When
        val result = AuthResult.Loading<Unit>()

        // Then
        assertThat(result.isLoading).isTrue()
    }

    @Test
    fun `AuthResult fromResult creates correct result types`() {
        // Test Success
        val successResult = AuthResult.fromResult(Result.success("success"))
        assertThat(successResult).isInstanceOf(AuthResult.Success::class.java)
        assertThat((successResult as AuthResult.Success).data).isEqualTo("success")
        
        // Test Error
        val exception = Exception("test error")
        val errorResult = AuthResult.fromResult(Result.failure<String>(exception))
        assertThat(errorResult).isInstanceOf(AuthResult.Error::class.java)
        assertThat((errorResult as AuthResult.Error).message).isEqualTo("test error")
    }

    @Test
    fun `AuthResult fold handles success and error cases`() {
        // Test Success case
        val successResult = AuthResult.Success("success")
        val successOutput = successResult.fold(
            onSuccess = { "Success: $it" },
            onError = { "Error: $it" },
            onLoading = { "Loading" }
        )
        assertThat(successOutput).isEqualTo("Success: success")
        
        // Test Error case
        val errorResult = AuthResult.Error<String>("test error")
        val errorOutput = errorResult.fold(
            onSuccess = { "Success: $it" },
            onError = { "Error: $it" },
            onLoading = { "Loading" }
        )
        assertThat(errorOutput).isEqualTo("Error: test error")
        
        // Test Loading case
        val loadingResult = AuthResult.Loading<String>()
        val loadingOutput = loadingResult.fold(
            onSuccess = { "Success: $it" },
            onError = { "Error: $it" },
            onLoading = { "Loading" }
        )
        assertThat(loadingOutput).isEqualTo("Loading")
    }
}
