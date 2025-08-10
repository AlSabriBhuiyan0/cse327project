package com.google.ai.edge.gallery.bridge.gmail

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized error handler for Gmail bridge operations.
 * Handles different types of exceptions and converts them to standardized [GmailServiceError]s.
 */
@Singleton
class GmailErrorHandler @Inject constructor() {
    /**
     * Handles an exception and returns a standardized [GmailServiceError].
     *
     * @param throwable The exception to handle
     * @param defaultMessage Default error message if no specific handler is found
     * @return A [GmailServiceError] with appropriate error details
     */
    fun handleError(throwable: Throwable, defaultMessage: String): GmailServiceError {
        return when (throwable) {
            is GoogleJsonResponseException -> handleGoogleJsonError(throwable)
            is HttpException -> handleHttpError(throwable)
            is SocketTimeoutException, is UnknownHostException -> GmailServiceError(
                message = "Network error. Please check your internet connection.",
                code = GmailErrorCode.NETWORK_ERROR,
                exception = throwable
            )
            is IOException -> GmailServiceError(
                message = "I/O error: ${throwable.message}",
                code = GmailErrorCode.IO_ERROR,
                exception = throwable
            )
            is GmailServiceError -> throwable // Already handled
            else -> GmailServiceError(
                message = defaultMessage,
                code = GmailErrorCode.UNKNOWN,
                exception = throwable
            )
        }
    }

    /**
     * Handles Google JSON API errors.
     */
    private fun handleGoogleJsonError(exception: GoogleJsonResponseException): GmailServiceError {
        return when (exception.statusCode) {
            401, 403 -> GmailServiceError(
                message = "Authentication error. Please sign in again.",
                code = GmailErrorCode.AUTH_ERROR,
                exception = exception
            )
            404 -> GmailServiceError(
                message = "Resource not found.",
                code = GmailErrorCode.NOT_FOUND,
                exception = exception
            )
            429 -> GmailServiceError(
                message = "Rate limit exceeded. Please try again later.",
                code = GmailErrorCode.RATE_LIMIT,
                exception = exception
            )
            in 500..599 -> GmailServiceError(
                message = "Server error occurred. Please try again later.",
                code = GmailErrorCode.SERVER_ERROR,
                exception = exception
            )
            else -> GmailServiceError(
                message = "API error: ${exception.message}",
                code = GmailErrorCode.API_ERROR,
                exception = exception
            )
        }
    }

    /**
     * Handles HTTP errors from Retrofit.
     */
    private fun handleHttpError(exception: HttpException): GmailServiceError {
        return when (exception.code()) {
            401, 403 -> GmailServiceError(
                message = "Authentication error. Please sign in again.",
                code = GmailErrorCode.AUTH_ERROR,
                exception = exception
            )
            in 400..499 -> GmailServiceError(
                message = "Client error: ${exception.message()}",
                code = GmailErrorCode.CLIENT_ERROR,
                exception = exception
            )
            in 500..599 -> GmailServiceError(
                message = "Server error. Please try again later.",
                code = GmailErrorCode.SERVER_ERROR,
                exception = exception
            )
            else -> GmailServiceError(
                message = "HTTP error: ${exception.message()}",
                code = GmailErrorCode.UNKNOWN,
                exception = exception
            )
        }
    }
}

/**
 * Error codes for Gmail bridge operations.
 */
enum class GmailErrorCode {
    /** Network connectivity issues */
    NETWORK_ERROR,
    
    /** Authentication/authorization failures */
    AUTH_ERROR,
    
    /** Rate limiting */
    RATE_LIMIT,
    
    /** Quota exceeded */
    QUOTA_EXCEEDED,
    
    /** Resource not found */
    NOT_FOUND,
    
    /** Server-side errors */
    SERVER_ERROR,
    
    /** Client-side errors */
    CLIENT_ERROR,
    
    /** I/O operation failures */
    IO_ERROR,
    
    /** Gmail API errors */
    API_ERROR,
    
    /** Unknown/unhandled errors */
    UNKNOWN
}

/**
 * Standardized error class for Gmail bridge operations.
 *
 * @property message User-friendly error message
 * @property code Error code categorizing the type of error
 * @property exception Original exception (if any)
 */
data class GmailServiceError(
    val message: String,
    val code: GmailErrorCode,
    val exception: Throwable? = null
) : Exception(message, exception)
