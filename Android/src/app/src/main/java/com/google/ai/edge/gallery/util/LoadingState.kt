package com.google.ai.edge.gallery.util

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R

/**
 * A sealed class representing different states of a data loading operation.
 */
sealed class LoadingState<out T> {
    /**
     * The initial state before any loading has occurred.
     */
    object Initial : LoadingState<Nothing>()

    /**
     * The loading state when data is being fetched.
     */
    object Loading : LoadingState<Nothing>()

    /**
     * The success state with the loaded data.
     */
    data class Success<out T>(val data: T) : LoadingState<T>()

    /**
     * The error state with an error message and optional retry action.
     */
    data class Error(
        val message: String? = null,
        val throwable: Throwable? = null,
        val retryAction: (() -> Unit)? = null
    ) : LoadingState<Nothing>()

    /**
     * Returns the data if the state is [Success], or null otherwise.
     */
    fun getOrNull(): T? = (this as? Success)?.data

    /**
     * Returns the data if the state is [Success], or throws an exception otherwise.
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw throwable ?: IllegalStateException(message ?: "An unknown error occurred")
        else -> throw IllegalStateException("No data available")
    }

    /**
     * Returns true if the state is [Success].
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * Returns true if the state is [Loading].
     */
    fun isLoading(): Boolean = this is Loading

    /**
     * Returns true if the state is [Error].
     */
    fun isError(): Boolean = this is Error

    /**
     * Executes the given [action] if the state is [Success].
     */
    fun onSuccess(action: (T) -> Unit): LoadingState<T> {
        if (this is Success) {
            action(data)
        }
        return this
    }

    /**
     * Executes the given [action] if the state is [Error].
     */
    fun onError(action: (Error) -> Unit): LoadingState<T> {
        if (this is Error) {
            action(this)
        }
        return this
    }

    /**
     * Executes the given [action] if the state is [Loading].
     */
    fun onLoading(action: () -> Unit): LoadingState<T> {
        if (this is Loading) {
            action()
        }
        return this
    }

    /**
     * Maps the data of a [Success] state using the given [transform] function.
     */
    fun <R> map(transform: (T) -> R): LoadingState<R> = when (this) {
        is Initial -> Initial
        is Loading -> Loading
        is Success -> Success(transform(data))
        is Error -> this
    }
}

/**
 * A composable that displays different UI based on the [LoadingState].
 */
@Composable
fun <T> LoadingStateHandler(
    state: LoadingState<T>,
    modifier: Modifier = Modifier,
    onLoading: @Composable (() -> Unit) = {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    },
    onError: @Composable (String?, (() -> Unit)?) -> Unit = { message, retryAction ->
        ErrorMessage(
            message = message ?: stringResource(R.string.error_unknown),
            retryAction = retryAction,
            modifier = modifier
        )
    },
    onSuccess: @Composable (T) -> Unit
) {
    when (state) {
        is LoadingState.Initial -> { /* Do nothing */ }
        is LoadingState.Loading -> onLoading()
        is LoadingState.Error -> onError(state.message, state.retryAction)
        is LoadingState.Success -> onSuccess(state.data)
    }
}

/**
 * A composable that displays an error message with an optional retry button.
 */
@Composable
fun ErrorMessage(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    retryAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        retryAction?.let { action ->
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = action) {
                Text(text = stringResource(R.string.retry))
            }
        }
    }
}

/**
 * A composable that displays a loading indicator with an optional message.
 */
@Composable
fun LoadingIndicator(
    message: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        
        if (!message.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
