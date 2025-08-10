package com.google.ai.edge.gallery.util.validation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AuthValidationTest {

    @Test
    fun `validateEmail returns true for valid emails`() {
        // Valid email formats
        assertThat(validateEmail("test@example.com")).isTrue()
        assertThat(validateEmail("firstname.lastname@example.com")).isTrue()
        assertThat(validateEmail("email@subdomain.example.com")).isTrue()
        assertThat(validateEmail("firstname+lastname@example.com")).isTrue()
        assertThat(validateEmail("email@123.123.123.123")).isTrue()
        assertThat(validateEmail("1234567890@example.com")).isTrue()
        assertThat(validateEmail("email@example-one.com")).isTrue()
        assertThat(validateEmail("_______@example.com")).isTrue()
        assertThat(validateEmail("email@example.name")).isTrue()
        assertThat(validateEmail("email@example.museum")).isTrue()
        assertThat(validateEmail("firstname-lastname@example.com")).isTrue()
    }

    @Test
    fun `validateEmail returns false for invalid emails`() {
        // Invalid email formats
        assertThat(validateEmail("plainaddress")).isFalse()
        assertThat(validateEmail("@missingusername.com")).isFalse()
        assertThat(validateEmail("username@.com")).isFalse()
        assertThat(validateEmail(".username@example.com")).isFalse()
        assertThat(validateEmail("username@example..com")).isFalse()
        assertThat(validateEmail("username@example.com.")).isFalse()
        assertThat(validateEmail("username@.example.com")).isFalse()
        assertThat(validateEmail("username@-example.com")).isFalse()
        assertThat(validateEmail("username@example.c")).isFalse()
        assertThat(validateEmail("😀@example.com")).isFalse() // Emoji in email
    }

    @Test
    fun `validatePassword returns true for valid passwords`() {
        // Valid passwords (minimum 6 characters)
        assertThat(validatePassword("123456")).isTrue()
        assertThat(validatePassword("password")).isTrue()
        assertThat(validatePassword("P@ssw0rd")).isTrue()
        assertThat(validatePassword("ThisIsAVeryLongPassword123!")).isTrue()
    }

    @Test
    fun `validatePassword returns false for invalid passwords`() {
        // Invalid passwords (less than 6 characters)
        assertThat(validatePassword("")).isFalse()
        assertThat(validatePassword("12345")).isFalse()
        assertThat(validatePassword("short")).isFalse()
        assertThat(validatePassword("     ")).isFalse()
    }

    @Test
    fun `validateName returns true for valid names`() {
        // Valid names
        assertThat(validateName("John")).isTrue()
        assertThat(validateName("John Doe")).isTrue()
        assertThat(validateName("O'Reilly")).isTrue()
        assertThat(validateName("Jean-Luc Picard")).isTrue()
        assertThat(validateName("Dr. John Doe Jr.")).isTrue()
        assertThat(validateName("John 2")).isTrue()
    }

    @Test
    fun `validateName returns false for invalid names`() {
        // Invalid names
        assertThat(validateName("")).isFalse()
        assertThat(validateName(" ")).isFalse()
        assertThat(validateName("   ")).isFalse()
        assertThat(validateName("@#$")).isFalse()
        assertThat(validateName("John123")).isFalse()
        assertThat(validateName("John@Doe")).isFalse()
    }

    @Test
    fun `passwordsMatch returns true when passwords match`() {
        assertThat(passwordsMatch("password123", "password123")).isTrue()
        assertThat(passwordsMatch("", "")).isTrue()
        assertThat(passwordsMatch("123456", "123456")).isTrue()
        assertThat(passwordsMatch("!@#$%^", "!@#$%^")).isTrue()
    }

    @Test
    fun `passwordsMatch returns false when passwords do not match`() {
        assertThat(passwordsMatch("password", "password1")).isFalse()
        assertThat(passwordsMatch("password", "PASSWORD")).isFalse()
        assertThat(passwordsMatch(" ", "  ")).isFalse()
        assertThat(passwordsMatch("123456", "1234567")).isFalse()
    }

    @Test
    fun `getEmailError returns null for valid email`() {
        assertThat(getEmailError("test@example.com")).isNull()
        assertThat(getEmailError("user+tag@example.com")).isNull()
    }

    @Test
    fun `getEmailError returns error message for invalid email`() {
        assertThat(getEmailError("invalid-email")).isNotNull()
        assertThat(getEmailError("")).isEqualTo("Email is required")
        assertThat(getEmailError(" ")).isEqualTo("Email is required")
    }

    @Test
    fun `getPasswordError returns null for valid password`() {
        assertThat(getPasswordError("password123")).isNull()
        assertThat(getPasswordError("123456")).isNull()
    }

    @Test
    fun `getPasswordError returns error message for invalid password`() {
        assertThat(getPasswordError("12345")).isEqualTo("Password must be at least 6 characters")
        assertThat(getPasswordError("")).isEqualTo("Password is required")
        assertThat(getPasswordError(" ")).isEqualTo("Password is required")
    }

    @Test
    fun `getNameError returns null for valid name`() {
        assertThat(getNameError("John Doe")).isNull()
        assertThat(getNameError("John")).isNull()
    }

    @Test
    fun `getNameError returns error message for invalid name`() {
        assertThat(getNameError("")).isEqualTo("Name is required")
        assertThat(getNameError(" ")).isEqualTo("Name is required")
        assertThat(getNameError("John123")).isEqualTo("Name can only contain letters, spaces, hyphens, and apostrophes")
    }

    @Test
    fun `getConfirmPasswordError returns null when passwords match`() {
        assertThat(getConfirmPasswordError("password", "password")).isNull()
        assertThat(getConfirmPasswordError("123456", "123456")).isNull()
    }

    @Test
    fun `getConfirmPasswordError returns error message when passwords do not match`() {
        assertThat(getConfirmPasswordError("password1", "password2"))
            .isEqualTo("Passwords do not match")
        assertThat(getConfirmPasswordError("", "password"))
            .isEqualTo("Passwords do not match")
    }
}
