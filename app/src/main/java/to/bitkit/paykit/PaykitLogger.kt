// PaykitLogger.kt
// Bitkit Android - Paykit Integration
//
// Structured logging utility for Paykit integration operations.
// Phase 6: Production Hardening

package to.bitkit.paykit

import android.util.Log

/**
 * Structured logger for Paykit integration operations.
 *
 * Provides consistent logging across all Paykit components with:
 * - Log level filtering
 * - Performance metrics
 * - Error context tracking
 * - Privacy-safe logging
 */
object PaykitLogger {

    private const val TAG_PREFIX = "Paykit"

    // MARK: - Logging Methods

    /** Log a debug message. */
    fun debug(
        message: String,
        category: String = "general",
        context: Map<String, Any>? = null
    ) {
        log(message, PaykitLogLevel.DEBUG, category, context)
    }

    /** Log an info message. */
    fun info(
        message: String,
        category: String = "general",
        context: Map<String, Any>? = null
    ) {
        log(message, PaykitLogLevel.INFO, category, context)
    }

    /** Log a warning message. */
    fun warning(
        message: String,
        category: String = "general",
        context: Map<String, Any>? = null
    ) {
        log(message, PaykitLogLevel.WARNING, category, context)
    }

    /** Log an error message. */
    fun error(
        message: String,
        category: String = "general",
        error: Throwable? = null,
        context: Map<String, Any>? = null
    ) {
        val fullContext = context?.toMutableMap() ?: mutableMapOf()
        error?.let { err ->
            fullContext["error"] = err.message ?: "Unknown error"
            fullContext["error_type"] = err.javaClass.simpleName
            fullContext["stack_trace"] = err.stackTraceToString().take(500)
        }

        log(message, PaykitLogLevel.ERROR, category, fullContext)

        // Report to error monitoring
        error?.let { err ->
            PaykitConfigManager.reportError(err, fullContext)
        }
    }

    /** Log a payment flow event. */
    fun logPaymentFlow(
        event: String,
        paymentMethod: String,
        amount: ULong? = null,
        durationMs: Long? = null
    ) {
        if (!PaykitConfigManager.logPaymentDetails) {
            info("Payment flow: $event", category = "payment")
            return
        }

        val context = mutableMapOf<String, Any>("payment_method" to paymentMethod)
        amount?.let { context["amount_msat"] = it }
        durationMs?.let { context["duration_ms"] = it }

        info("Payment flow: $event", category = "payment", context = context)
    }

    /** Log a performance metric. */
    fun logPerformance(
        operation: String,
        durationMs: Long,
        success: Boolean,
        context: Map<String, Any>? = null
    ) {
        val fullContext = context?.toMutableMap() ?: mutableMapOf()
        fullContext["operation"] = operation
        fullContext["duration_ms"] = durationMs
        fullContext["success"] = success

        val level = if (success) PaykitLogLevel.INFO else PaykitLogLevel.WARNING
        log(
            "Performance: $operation (${durationMs}ms)",
            level,
            "performance",
            fullContext
        )
    }

    // MARK: - Private Helpers

    private fun log(
        message: String,
        level: PaykitLogLevel,
        category: String,
        context: Map<String, Any>?
    ) {
        if (compareLogLevels(level, PaykitConfigManager.logLevel) < 0) {
            return
        }

        val contextString = context?.let { ctx ->
            val pairs = ctx.entries.joinToString(", ") { "${it.key}=${it.value}" }
            " [$pairs]"
        } ?: ""

        val tag = "$TAG_PREFIX:$category"
        val fullMessage = "[${level.prefix}] $message$contextString"

        when (level) {
            PaykitLogLevel.DEBUG -> Log.d(tag, fullMessage)
            PaykitLogLevel.INFO -> Log.i(tag, fullMessage)
            PaykitLogLevel.WARNING -> Log.w(tag, fullMessage)
            PaykitLogLevel.ERROR -> Log.e(tag, fullMessage)
            PaykitLogLevel.NONE -> {}
        }
    }

    private val PaykitLogLevel.prefix: String
        get() = when (this) {
            PaykitLogLevel.DEBUG -> "DEBUG"
            PaykitLogLevel.INFO -> "INFO"
            PaykitLogLevel.WARNING -> "WARN"
            PaykitLogLevel.ERROR -> "ERROR"
            PaykitLogLevel.NONE -> ""
        }

    private fun compareLogLevels(a: PaykitLogLevel, b: PaykitLogLevel): Int {
        return a.ordinal.compareTo(b.ordinal)
    }
}

// MARK: - Convenience Logging Functions

/** Log a debug message to Paykit logger. */
fun paykitDebug(message: String, category: String = "general", context: Map<String, Any>? = null) {
    PaykitLogger.debug(message, category, context)
}

/** Log an info message to Paykit logger. */
fun paykitInfo(message: String, category: String = "general", context: Map<String, Any>? = null) {
    PaykitLogger.info(message, category, context)
}

/** Log a warning message to Paykit logger. */
fun paykitWarning(message: String, category: String = "general", context: Map<String, Any>? = null) {
    PaykitLogger.warning(message, category, context)
}

/** Log an error message to Paykit logger. */
fun paykitError(
    message: String,
    category: String = "general",
    error: Throwable? = null,
    context: Map<String, Any>? = null
) {
    PaykitLogger.error(message, category, error, context)
}
