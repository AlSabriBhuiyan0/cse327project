package com.google.ai.edge.gallery.util

import com.google.ai.edge.gallery.data.model.User
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Date

class AuthUtilsTest {

    @Test
    fun `isValidEmail returns true for valid emails`() {
        // Valid email formats
        assertThat("test@example.com".isValidEmail()).isTrue()
        assertThat("firstname.lastname@example.com".isValidEmail()).isTrue()
        assertThat("email@subdomain.example.com".isValidEmail()).isTrue()
        assertThat("firstname+lastname@example.com".isValidEmail()).isTrue()
        assertThat("1234567890@example.com".isValidEmail()).isTrue()
        assertThat("email@example-one.com".isValidEmail()).isTrue()
        assertThat("_______@example.com".isValidEmail()).isTrue()
        assertThat("email@example.name".isValidEmail()).isTrue()
        assertThat("email@example.museum".isValidEmail()).isTrue()
        assertThat("firstname-lastname@example.com".isValidEmail()).isTrue()
    }

    @Test
    fun `isValidEmail returns false for invalid emails`() {
        // Invalid email formats
        assertThat("plainaddress".isValidEmail()).isFalse()
        assertThat("@missingusername.com".isValidEmail()).isFalse()
        assertThat("username@.com".isValidEmail()).isFalse()
        assertThat(".username@example.com".isValidEmail()).isFalse()
        assertThat("username@example..com".isValidEmail()).isFalse()
        assertThat("username@example.com.".isValidEmail()).isFalse()
        assertThat("username@.example.com".isValidEmail()).isFalse()
        assertThat("username@-example.com".isValidEmail()).isFalse()
        assertThat("username@example.c".isValidEmail()).isFalse()
        assertThat("😀@example.com".isValidEmail()).isFalse() // Emoji in email
    }

    @Test
    fun `isValidPassword returns true for valid passwords`() {
        // Valid passwords (minimum 6 characters)
        assertThat("123456".isValidPassword()).isTrue()
        assertThat("password".isValidPassword()).isTrue()
        assertThat("P@ssw0rd".isValidPassword()).isTrue()
        assertThat("ThisIsAVeryLongPassword123!".isValidPassword()).isTrue()
    }

    @Test
    fun `isValidPassword returns false for invalid passwords`() {
        // Invalid passwords (less than 6 characters)
        assertThat("".isValidPassword()).isFalse()
        assertThat("12345".isValidPassword()).isFalse()
        assertThat("short".isValidPassword()).isFalse()
        assertThat("     ".isValidPassword()).isFalse()
    }

    @Test
    fun `isValidName returns true for valid names`() {
        // Valid names
        assertThat("John".isValidName()).isTrue()
        assertThat("John Doe".isValidName()).isTrue()
        assertThat("O'Reilly".isValidName()).isTrue()
        assertThat("Jean-Luc Picard".isValidName()).isTrue()
        assertThat("Dr. John Doe Jr.".isValidName()).isTrue()
    }

    @Test
    fun `isValidName returns false for invalid names`() {
        // Invalid names
        assertThat("".isValidName()).isFalse()
        assertThat(" ".isValidName()).isFalse()
        assertThat("   ".isValidName()).isFalse()
        assertThat("@#$".isValidName()).isFalse()
        assertThat("John123".isValidName()).isFalse()
        assertThat("John@Doe".isValidName()).isFalse()
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
    fun `formatAuthError returns correct error messages`() {
        // Test common Firebase Auth error messages
        assertThat(formatAuthError("ERROR_INVALID_EMAIL"))
            .isEqualTo("The email address is badly formatted.")
        
        assertThat(formatAuthError("ERROR_WRONG_PASSWORD"))
            .isEqualTo("The password is invalid or the user does not have a password.")
        
        assertThat(formatAuthError("ERROR_USER_NOT_FOUND"))
            .isEqualTo("There is no user record corresponding to this identifier.")
        
        assertThat(formatAuthError("ERROR_USER_DISABLED"))
            .isEqualTo("The user account has been disabled by an administrator.")
        
        assertThat(formatAuthError("ERROR_TOO_MANY_REQUESTS"))
            .isEqualTo("Too many unsuccessful login attempts. Please try again later.")
        
        assertThat(formatAuthError("ERROR_OPERATION_NOT_ALLOWED"))
            .isEqualTo("This operation is not allowed.")
        
        assertThat(formatAuthError("ERROR_EMAIL_ALREADY_IN_USE"))
            .isEqualTo("The email address is already in use by another account.")
        
        // Test unknown error
        assertThat(formatAuthError("SOME_UNKNOWN_ERROR"))
            .isEqualTo("An unknown error occurred.")
    }

    @Test
    fun `getUserInitials returns correct initials`() {
        // Test with full name
        val user1 = User("123", "test@example.com", "John Doe")
        assertThat(user1.getInitials()).isEqualTo("JD")
        
        // Test with single name
        val user2 = User("123", "test@example.com", "John")
        assertThat(user2.getInitials()).isEqualTo("J")
        
        // Test with empty name
        val user3 = User("123", "test@example.com", "")
        assertThat(user3.getInitials()).isEqualTo("")
        
        // Test with multiple spaces
        val user4 = User("123", "test@example.com", "  John  Middle  Doe  ")
        assertThat(user4.getInitials()).isEqualTo("JD")
        
        // Test with special characters
        val user5 = User("123", "test@example.com", "Jöhn Döe")
        assertThat(user5.getInitials()).isEqualTo("JD")
    }

    @Test
    fun `isTokenExpired returns correct value`() {
        // Test with expired token (1 hour ago)
        val expiredTime = System.currentTimeMillis() - (61 * 60 * 1000) // 61 minutes ago
        assertThat(isTokenExpired(expiredTime)).isTrue()
        
        // Test with valid token (30 minutes ago)
        val validTime = System.currentTimeMillis() - (30 * 60 * 1000) // 30 minutes ago
        assertThat(isTokenExpired(validTime)).isFalse()
        
        // Test with future time (should not be expired)
        val futureTime = System.currentTimeMillis() + (30 * 60 * 1000) // 30 minutes in the future
        assertThat(isTokenExpired(futureTime)).isFalse()
    }

    @Test
    fun `formatDisplayName returns correct format`() {
        // Test with first and last name
        assertThat(formatDisplayName("John", "Doe")).isEqualTo("John Doe")
        
        // Test with only first name
        assertThat(formatDisplayName("John", "")).isEqualTo("John")
        
        // Test with only last name
        assertThat(formatDisplayName("", "Doe")).isEqualTo("Doe")
        
        // Test with empty names
        assertThat(formatDisplayName("", "")).isEqualTo("")
        
        // Test with extra spaces
        assertThat(formatDisplayName("  John  ", "  Doe  ")).isEqualTo("John Doe")
    }
}
