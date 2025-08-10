package com.google.ai.edge.gallery.ui.auth.form

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AuthFormStateTest {

    @Test
    fun `LoginFormState validation with valid input`() {
        // Given
        val formState = LoginFormState(
            email = "test@example.com",
            password = "password123"
        )

        // Then
        assertThat(formState.isFormValid).isTrue()
        assertThat(formState.emailError).isNull()
        assertThat(formState.passwordError).isNull()
    }

    @Test
    fun `LoginFormState validation with invalid email`() {
        // Given
        val formState = LoginFormState(
            email = "invalid-email",
            password = "password123"
        )

        // Then
        assertThat(formState.isFormValid).isFalse()
        assertThat(formState.emailError).isNotNull()
        assertThat(formState.passwordError).isNull()
    }

    @Test
    fun `LoginFormState validation with short password`() {
        // Given
        val formState = LoginFormState(
            email = "test@example.com",
            password = "12345"
        )

        // Then
        assertThat(formState.isFormValid).isFalse()
        assertThat(formState.emailError).isNull()
        assertThat(formState.passwordError).isNotNull()
    }

    @Test
    fun `SignUpFormState validation with valid input`() {
        // Given
        val formState = SignUpFormState(
            name = "Test User",
            email = "test@example.com",
            password = "password123",
            confirmPassword = "password123"
        )

        // Then
        assertThat(formState.isFormValid).isTrue()
        assertThat(formState.nameError).isNull()
        assertThat(formState.emailError).isNull()
        assertThat(formState.passwordError).isNull()
        assertThat(formState.confirmPasswordError).isNull()
    }

    @Test
    fun `SignUpFormState validation with empty name`() {
        // Given
        val formState = SignUpFormState(
            name = "",
            email = "test@example.com",
            password = "password123",
            confirmPassword = "password123"
        )

        // Then
        assertThat(formState.isFormValid).isFalse()
        assertThat(formState.nameError).isNotNull()
    }

    @Test
    fun `SignUpFormState validation with mismatched passwords`() {
        // Given
        val formState = SignUpFormState(
            name = "Test User",
            email = "test@example.com",
            password = "password123",
            confirmPassword = "differentpassword"
        )

        // Then
        assertThat(formState.isFormValid).isFalse()
        assertThat(formState.confirmPasswordError).isNotNull()
    }

    @Test
    fun `ForgotPasswordFormState validation with valid email`() {
        // Given
        val formState = ForgotPasswordFormState(
            email = "test@example.com"
        )

        // Then
        assertThat(formState.isFormValid).isTrue()
        assertThat(formState.emailError).isNull()
    }

    @Test
    fun `ForgotPasswordFormState validation with invalid email`() {
        // Given
        val formState = ForgotPasswordFormState(
            email = "invalid-email"
        )

        // Then
        assertThat(formState.isFormValid).isFalse()
        assertThat(formState.emailError).isNotNull()
    }

    @Test
    fun `AuthFormState copy methods work correctly`() {
        // Test LoginFormState copy
        val loginState = LoginFormState(
            email = "test@example.com",
            password = "password123"
        )
        val updatedLoginState = loginState.copy(
            email = "new@example.com",
            emailError = "Invalid email"
        )
        assertThat(updatedLoginState.email).isEqualTo("new@example.com")
        assertThat(updatedLoginState.emailError).isEqualTo("Invalid email")
        assertThat(updatedLoginState.password).isEqualTo("password123")

        // Test SignUpFormState copy
        val signUpState = SignUpFormState(
            name = "Test User",
            email = "test@example.com",
            password = "password123",
            confirmPassword = "password123"
        )
        val updatedSignUpState = signUpState.copy(
            name = "New User",
            emailError = "Email already in use"
        )
        assertThat(updatedSignUpState.name).isEqualTo("New User")
        assertThat(updatedSignUpState.emailError).isEqualTo("Email already in use")
        assertThat(updatedSignUpState.password).isEqualTo("password123")

        // Test ForgotPasswordFormState copy
        val forgotPasswordState = ForgotPasswordFormState(
            email = "test@example.com"
        )
        val updatedForgotPasswordState = forgotPasswordState.copy(
            email = "new@example.com",
            emailError = "Email not found"
        )
        assertThat(updatedForgotPasswordState.email).isEqualTo("new@example.com")
        assertThat(updatedForgotPasswordState.emailError).isEqualTo("Email not found")
    }
}
