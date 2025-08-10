package com.google.ai.edge.gallery.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setue
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.ai.edge.gallery.R

/**
 * A sealed class that represents different states of a UI operation.
 */
sealed class UiState<out T> {
    object Initial : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<out T>(val data: T) : UiState<T>()
    data class Error(
        val message: String? = null,
        val errorCode: Int? = null,
        val retryAction: (() -> Unit)? = null
    ) : UiState<Nothing>()

    /**
     * Returns the current data if available, or null if not.
     */
    fun getOrNull(): T? = (this as? Success)?.data

    /**
     * Returns the current data or throws an exception if not available.
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw IllegalStateException(message ?: "Error state encountered")
        is Loading -> throw IllegalStateException("Data is still loading")
        is Initial -> throw IllegalStateException("No data available")
    }

    /**
     * Returns true if the current state is Success.
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * Returns true if the current state is Loading.
     */
    fun isLoading(): Boolean = this is Loading

    /**
     * Returns true if the current state is Error.
     */
    fun isError(): Boolean = this is Error

    /**
     * Executes the given [action] if the current state is Success.
     */
    fun onSuccess(action: (T) -> Unit): UiState<T> {
        if (this is Success) {
            action(data)
        }
        return this
    }

    /**
     * Executes the given [action] if the current state is Error.
     */
    fun onError(action: (Error) -> Unit): UiState<T> {
        if (this is Error) {
            action(this)
        }
        return this
    }

    /**
     * Executes the given [action] if the current state is Loading.
     */
    fun onLoading(action: () -> Unit): UiState<T> {
        if (this is Loading) {
            action()
        }
        return this
    }
}

/**
 * A wrapper class that holds the current UI state and provides methods to update it.
 */
@Stable
class UiStateHolder<T>(
    initialState: UiState<T> = UiState.Initial
) {
    private val _state = mutableStateOf(initialState)
    var state: UiState<T> by _state
        private set

    /**
     * Updates the state to Loading.
     */
    fun setLoading() {
        state = UiState.Loading
    }

    /**
     * Updates the state to Success with the given data.
     */
    fun setSuccess(data: T) {
        state = UiState.Success(data)
    }

    /**
     * Updates the state to Error with the given message and optional retry action.
     */
    fun setError(
        message: String? = null,
        errorCode: Int? = null,
        retryAction: (() -> Unit)? = null
    ) {
        state = UiState.Error(message, errorCode, retryAction)
    }

    /**
     * Updates the state to the result of the given action.
     * If the action throws an exception, the state will be updated to Error.
     */
    fun <R> withState(
        onLoading: @Composable () -> Unit,
        onError: @Composable (UiState.Error) -> Unit,
        onSuccess: @Composable (T) -> R
    ): R? {
        return when (val currentState = state) {
            is UiState.Initial -> null
            is UiState.Loading -> onLoading()
            is UiState.Error -> {
                onError(currentState)
                null
            }
            is UiState.Success -> onSuccess(currentState.data)
        }
    }

    companion object {
        /**
         * Creates a [Saver] for [UiStateHolder].
         */
        fun <T> saver() = listSaver<UiStateHolder<T>, Any?>(
            save = { listOf(it.state) },
            restore = { UiStateHolder(it[0] as UiState<T>) }
        )
    }
}

/**
 * Data class representing a user message to be displayed in the UI.
 */
data class UserMessage(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val type: MessageType,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val duration: Long = 3000L
) {
    /**
     * The type of user message, which determines its appearance.
     */
    enum class MessageType(
        val icon: ImageVector? = null,
        val containerColor: Long = 0,
        val contentColor: Long = 0,
        val actionColor: Long = 0
    ) {
        INFO(
            icon = Icons.Default.Info,
            containerColor = R.attr.colorPrimaryContainer,
            contentColor = R.attr.colorOnPrimaryContainer,
            actionColor = R.attr.colorPrimary
        ),
        SUCCESS(
            icon = Icons.Default.CheckCircle,
            containerColor = R.attr.colorSuccessContainer,
            contentColor = R.attr.colorOnSuccessContainer,
            actionColor = R.attr.colorSuccess
        ),
        WARNING(
            icon = Icons.Default.Warning,
            containerColor = R.attr.colorWarningContainer,
            contentColor = R.attr.colorOnWarningContainer,
            actionColor = R.attr.colorWarning
        ),
        ERROR(
            icon = Icons.Default.Error,
            containerColor = R.attr.colorErrorContainer,
            contentColor = R.attr.colorOnErrorContainer,
            actionColor = R.attr.colorError
        )
    }
}

/**
 * A simple wrapper for handling loading, error, and success states in Compose.
 */
@Composable
fun <T> UiState<T>.Handle(
    onLoading: @Composable () -> Unit = { /* Default loading indicator */ },
    onError: @Composable (UiState.Error) -> Unit = { /* Default error UI */ },
    onSuccess: @Composable (T) -> Unit
) {
    when (this) {
        is UiState.Initial -> { /* Do nothing */ }
        is UiState.Loading -> onLoading()
        is UiState.Error -> onError(this)
        is UiState.Success -> onSuccess(data)
    }
}
