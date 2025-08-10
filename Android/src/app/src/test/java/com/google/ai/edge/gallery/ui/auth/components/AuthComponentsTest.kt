package com.google.ai.edge.gallery.ui.auth.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.ui.theme.HappyChatAITheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class AuthComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun authButton_displaysCorrectText() {
        // Given
        val buttonText = "Sign In"
        
        // When
        composeTestRule.setContent {
            HappyChatAITheme {
                AuthButton(
                    text = buttonText,
                    onClick = {},
                    modifier = Modifier.testTag("authButton")
                )
            }
        }
        
        // Then
        composeTestRule.onNodeWithText(buttonText).assertIsDisplayed()
    }

    @Test
    fun authButton_isDisabledWhenLoading() {
        // When
        composeTestRule.setContent {
            HappyChatAITheme {
                AuthButton(
                    text = "Sign In",
                    onClick = {},
                    isLoading = true,
                    modifier = Modifier.testTag("authButton")
                )
            }
        }
        
        // Then
        composeTestRule.onNodeWithTag("authButton").assertIsNotEnabled()
    }

    @Test
    fun authButton_showsLoadingWhenLoading() {
        // When
        composeTestRule.setContent {
            HappyChatAITheme {
                AuthButton(
                    text = "Sign In",
                    onClick = {},
                    isLoading = true,
                    modifier = Modifier.testTag("authButton")
                )
            }
        }
        
        // Then
        composeTestRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }

    @Test
    fun authButton_callsOnClickWhenClicked() {
        // Given
        var onClickCalled = false
        
        // When
        composeTestRule.setContent {
            HappyChatAITheme {
                AuthButton(
                    text = "Sign In",
                    onClick = { onClickCalled = true },
                    modifier = Modifier.testTag("authButton")
                )
            }
        }
        
        // When
        composeTestRule.onNodeWithTag("authButton").performClick()
        
        // Then
        assertThat(onClickCalled).isTrue()
    }

    @Test
    fun authTextField_displaysLabelAndHint() {
        // Given
        val label = "Email"
        val hint = "Enter your email"
        
        // When
        composeTestRule.setContent {
            HappyChatAITheme {
                AuthTextField(
                    value = "",
                    onValueChange = {},
                    label = label,
                    placeholder = hint,
                    modifier = Modifier.testTag("authTextField")
                )
            }
        }
        
        // Then
        composeTestRule.onNodeWithText(label).assertIsDisplayed()
        composeTestRule.onNodeWithText(hint).assertIsDisplayed()
    }

    @Test
    fun authTextField_showsErrorWhenErrorIsPresent() {
        // Given
        val errorMessage = "Invalid email"
        
        // When
        composeTestRule.setContent {
            HappyChatAITheme {
                AuthTextField(
                    value = "",
                    onValueChange = {},
                    label = "Email",
                    error = errorMessage,
                    modifier = Modifier.testTag("authTextField")
                )
            }
        }
        
        // Then
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    @Test
    fun authTextField_callsOnValueChangeWhenTextChanges() {
        // Given
        var inputText = ""
        val testText = "test@example.com"
        
        // When
        composeTestRule.setContent {
            HappyChatAITheme {
                AuthTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = "Email",
                    modifier = Modifier.testTag("authTextField")
                )
            }
        }
        
        // When
        composeTestRule.onNodeWithTag("authTextField").performTextInput(testText)
        
        // Then
        assertThat(inputText).isEqualTo(testText)
    }

    @Test
    fun authLinkText_displaysTextAndLink() {
        // Given
        val prefix = "Don't have an account?"
        val linkText = "Sign up"
        
        // When
        composeTestRule.setContent {
            HappyChatAITheme {
                AuthLinkText(
                    prefix = prefix,
                    linkText = linkText,
                    onClick = {}
                )
            }
        }
        
        // Then
        composeTestRule.onNodeWithText(prefix).assertIsDisplayed()
        composeTestRule.onNodeWithText(linkText).assertIsDisplayed()
    }

    @Test
    fun authLinkText_callsOnClickWhenLinkIsClicked() {
        // Given
        var onClickCalled = false
        
        // When
        composeTestRule.setContent {
            HappyChatAITheme {
                AuthLinkText(
                    prefix = "Don't have an account?",
                    linkText = "Sign up",
                    onClick = { onClickCalled = true }
                )
            }
        }
        
        // When
        composeTestRule.onNodeWithText("Sign up").performClick()
        
        // Then
        assertThat(onClickCalled).isTrue()
    }

    @Test
    fun authHeader_displaysTitleAndSubtitle() {
        // Given
        val title = "Welcome Back"
        val subtitle = "Sign in to continue"
        
        // When
        composeTestRule.setContent {
            HappyChatAITheme {
                AuthHeader(
                    title = title,
                    subtitle = subtitle
                )
            }
        }
        
        // Then
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithText(subtitle).assertIsDisplayed()
    }

    @Test
    fun authErrorText_displaysErrorWhenErrorIsPresent() {
        // Given
        val errorMessage = "Authentication failed"
        
        // When
        composeTestRule.setContent {
            HappyChatAITheme {
                AuthErrorText(
                    error = errorMessage,
                    modifier = Modifier.testTag("errorText")
                )
            }
        }
        
        // Then
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    @Test
    fun authErrorText_isNotDisplayedWhenErrorIsNull() {
        // When
        composeTestRule.setContent {
            HappyChatAITheme {
                AuthErrorText(
                    error = null,
                    modifier = Modifier.testTag("errorText")
                )
            }
        }
        
        // Then
        composeTestRule.onNodeWithTag("errorText").assertDoesNotExist()
    }

    @Test
    fun authLoadingIndicator_isDisplayedWhenLoadingIsTrue() {
        // When
        composeTestRule.setContent {
            HappyChatAITheme {
                AuthLoadingIndicator(
                    isLoading = true,
                    modifier = Modifier.testTag("loadingIndicator")
                )
            }
        }
        
        // Then
        composeTestRule.onNodeWithTag("loadingIndicator").assertIsDisplayed()
    }

    @Test
    fun authLoadingIndicator_isNotDisplayedWhenLoadingIsFalse() {
        // When
        composeTestRule.setContent {
            HappyChatAITheme {
                AuthLoadingIndicator(
                    isLoading = false,
                    modifier = Modifier.testTag("loadingIndicator")
                )
            }
        }
        
        // Then
        composeTestRule.onNodeWithTag("loadingIndicator").assertDoesNotExist()
    }
}
