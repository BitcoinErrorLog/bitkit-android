package to.bitkit.paykit.utils

import kotlinx.coroutines.delay
import to.bitkit.paykit.services.PubkyRingException
import to.bitkit.paykit.services.PubkySDKException
import to.bitkit.utils.Logger
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.min

/**
 * Helper for retrying async operations with exponential backoff
 */
object RetryHelper {

    /**
     * Retry configuration
     */
    data class Config(
        val maxAttempts: Int,
        val initialDelayMs: Long,
        val maxDelayMs: Long,
        val multiplier: Double
    ) {
        companion object {
            val DEFAULT = Config(
                maxAttempts = 3,
                initialDelayMs = 1000,
                maxDelayMs = 10000,
                multiplier = 2.0
            )

            val AGGRESSIVE = Config(
                maxAttempts = 5,
                initialDelayMs = 500,
                maxDelayMs = 30000,
                multiplier = 2.0
            )
        }
    }

    /**
     * Retry an async operation with exponential backoff
     */
    suspend fun <T> retry(
        config: Config = Config.DEFAULT,
        shouldRetry: (Throwable) -> Boolean = { isRetryable(it) },
        operation: suspend () -> T
    ): T {
        var lastError: Throwable? = null
        var delayMs = config.initialDelayMs

        for (attempt in 1..config.maxAttempts) {
            try {
                return operation()
            } catch (e: Throwable) {
                lastError = e

                // Check if we should retry
                if (attempt >= config.maxAttempts || !shouldRetry(e)) {
                    throw e
                }

                Logger.warn(
                    "Operation failed (attempt $attempt/${config.maxAttempts}), retrying in ${delayMs}ms: ${e.message}",
                    context = "RetryHelper"
                )

                // Wait before retry
                delay(delayMs)

                // Increase delay for next attempt (exponential backoff)
                delayMs = min((delayMs * config.multiplier).toLong(), config.maxDelayMs)
            }
        }

        throw lastError ?: Exception("All retry attempts failed")
    }

    /**
     * Check if an error is retryable (network errors, timeouts, etc.)
     */
    fun isRetryable(error: Throwable): Boolean {
        return when (error) {
            is SocketTimeoutException,
            is UnknownHostException,
            is IOException -> true
            is PubkySDKException.FetchFailed,
            is PubkySDKException.WriteFailed -> true
            is PubkySDKException.NotFound,
            is PubkySDKException.InvalidData,
            is PubkySDKException.InvalidUri,
            is PubkySDKException.NotConfigured,
            is PubkySDKException.NoSession -> false
            else -> false
        }
    }
}

/**
 * Extension to get user-friendly error messages
 */
fun Throwable.userFriendlyMessage(): String {
    return when (this) {
        // Network errors
        is SocketTimeoutException -> "Request timed out. Please try again."
        is UnknownHostException -> "Cannot connect to server. Please check your internet connection."
        is IOException -> "Network error occurred. Please try again."

        // Pubky SDK errors
        is PubkySDKException.NotConfigured -> "Service not configured. Please restart the app."
        is PubkySDKException.NoSession -> "Please reconnect to Pubky-ring."
        is PubkySDKException.FetchFailed -> "Failed to fetch data: ${this.message}"
        is PubkySDKException.WriteFailed -> "Failed to save data: ${this.message}"
        is PubkySDKException.NotFound -> "Not found: ${this.message}"
        is PubkySDKException.InvalidData -> "Invalid data: ${this.message}"
        is PubkySDKException.InvalidUri -> "Invalid URL format."

        // Pubky Ring errors
        is PubkyRingException.AppNotInstalled ->
            "Pubky-ring app is not installed. Please install it to use this feature."
        is PubkyRingException.Timeout -> "Request timed out. Please try again."
        is PubkyRingException.Cancelled -> "Request was cancelled."
        is PubkyRingException.InvalidCallback, is PubkyRingException.MissingParameters ->
            "Received invalid response. Please try again."
        is PubkyRingException.FailedToOpenApp ->
            "Failed to open Pubky-ring app. Please try again."
        is PubkyRingException.CrossDeviceFailed ->
            "Cross-device authentication failed: ${this.message}"

        // Generic fallback
        else -> message ?: "An error occurred. Please try again."
    }
}

