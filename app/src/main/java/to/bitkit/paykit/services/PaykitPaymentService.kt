package to.bitkit.paykit.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import to.bitkit.paykit.PaykitException
import to.bitkit.paykit.PaykitIntegrationHelper
import to.bitkit.paykit.PaykitManager
import to.bitkit.repositories.LightningRepo
import to.bitkit.utils.Logger
import uniffi.paykit_mobile.PaymentCandidate
import uniffi.paykit_mobile.SelectionPreferences
import uniffi.paykit_mobile.SelectionStrategy
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for executing payments through Paykit.
 *
 * Provides high-level methods for:
 * - Payment discovery (finding recipient payment methods)
 * - Payment execution (Lightning and onchain)
 * - Receipt generation and storage
 * - Payment status tracking
 *
 * ## Usage
 *
 * Prefer dependency injection:
 * ```kotlin
 * @Inject constructor(private val paymentService: PaykitPaymentService)
 * val result = paymentService.pay(lightningRepo, "lnbc...", amountSats = 10000uL)
 * ```
 *
 * Legacy `getInstance()` is deprecated and will be removed in a future release.
 */
@Singleton
class PaykitPaymentService @Inject constructor(
    private val receiptStore: PaykitReceiptStore,
    private val spendingLimitManager: SpendingLimitManager,
    private val directoryService: DirectoryService,
    private val paykitManager: PaykitManager,
) {

    companion object {
        private const val TAG = "PaykitPaymentService"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Payment timeout in milliseconds */
    var paymentTimeoutMs: Long = 60_000L

    /** Whether to automatically store receipts */
    var autoStoreReceipts: Boolean = true

    private val _paymentState = MutableStateFlow<PaykitPaymentState>(PaykitPaymentState.Idle)
    val paymentState: StateFlow<PaykitPaymentState> = _paymentState.asStateFlow()

    // MARK: - Payment Discovery

    /**
     * Discover available payment methods for a recipient.
     *
     * @param recipient Address, invoice, or Paykit URI
     * @return Available payment methods
     */
    fun discoverPaymentMethods(recipient: String): List<PaymentMethod> {
        val paymentType = detectPaymentType(recipient)

        return when (paymentType) {
            DetectedPaymentType.LIGHTNING -> listOf(PaymentMethod.Lightning(recipient))
            DetectedPaymentType.ONCHAIN -> listOf(PaymentMethod.Onchain(recipient))
            DetectedPaymentType.PAYKIT -> listOf(PaymentMethod.Paykit(recipient))
            DetectedPaymentType.UNKNOWN -> emptyList()
        }
    }

    /**
     * Detect payment type from a string.
     */
    private fun detectPaymentType(input: String): DetectedPaymentType {
        val lowercased = input.lowercase()

        return when {
            lowercased.startsWith("lnbc") ||
                lowercased.startsWith("lntb") ||
                lowercased.startsWith("lnbcrt") -> DetectedPaymentType.LIGHTNING

            lowercased.startsWith("bc1") ||
                lowercased.startsWith("tb1") ||
                lowercased.startsWith("bcrt1") -> DetectedPaymentType.ONCHAIN

            lowercased.startsWith("1") ||
                lowercased.startsWith("3") ||
                lowercased.startsWith("m") ||
                lowercased.startsWith("n") ||
                lowercased.startsWith("2") -> DetectedPaymentType.ONCHAIN

            lowercased.startsWith("paykit:") ||
                lowercased.startsWith("pip:") -> DetectedPaymentType.PAYKIT

            else -> DetectedPaymentType.UNKNOWN
        }
    }

    // MARK: - Payment Execution

    /**
     * Execute a payment to a recipient.
     *
     * Automatically detects payment type and routes accordingly.
     *
     * @param lightningRepo LightningRepo instance for payment execution
     * @param recipient Address, invoice, or Paykit URI
     * @param amountSats Amount in satoshis (required for onchain, optional for invoices)
     * @param feeRate Fee rate for onchain payments (sat/vB)
     * @param peerPubkey Optional peer pubkey for spending limit enforcement
     * @return Payment result with receipt
     */
    suspend fun pay(
        lightningRepo: LightningRepo,
        recipient: String,
        amountSats: ULong? = null,
        feeRate: Double? = null,
        peerPubkey: String? = null,
    ): PaykitPaymentResult {
        if (!PaykitIntegrationHelper.isReady) {
            return PaykitPaymentResult(
                success = false,
                receipt = createFailedReceipt(recipient, amountSats ?: 0uL, PaykitReceiptType.LIGHTNING),
                error = PaykitPaymentError.NotInitialized
            )
        }

        val paymentType = detectPaymentType(recipient)

        // If peer pubkey is provided and spending limit manager is initialized, use atomic spending
        if (peerPubkey != null && amountSats != null && spendingLimitManager.isInitialized) {
            return payWithSpendingLimit(
                lightningRepo = lightningRepo,
                recipient = recipient,
                amountSats = amountSats,
                feeRate = feeRate,
                peerPubkey = peerPubkey,
                paymentType = paymentType,
            )
        }

        return when (paymentType) {
            DetectedPaymentType.LIGHTNING -> payLightning(lightningRepo, recipient, amountSats)
            DetectedPaymentType.ONCHAIN -> {
                if (amountSats == null) {
                    return PaykitPaymentResult(
                        success = false,
                        receipt = createFailedReceipt(recipient, 0uL, PaykitReceiptType.ONCHAIN),
                        error = PaykitPaymentError.AmountRequired
                    )
                }
                payOnchain(lightningRepo, recipient, amountSats, feeRate)
            }
            DetectedPaymentType.PAYKIT -> payPaykitUri(recipient, amountSats)
            DetectedPaymentType.UNKNOWN -> PaykitPaymentResult(
                success = false,
                receipt = createFailedReceipt(recipient, amountSats ?: 0uL, PaykitReceiptType.LIGHTNING),
                error = PaykitPaymentError.InvalidRecipient(recipient)
            )
        }
    }

    // MARK: - Smart Method Selection

    /**
     * Select optimal payment method for a recipient based on strategy.
     *
     * @param recipientPubkey Recipient's pubkey
     * @param amountSats Payment amount
     * @param strategy Selection strategy (default: BALANCED)
     * @return Selected payment method or null if none available
     */
    suspend fun selectOptimalMethod(
        recipientPubkey: String,
        amountSats: ULong,
        strategy: SelectionStrategy = SelectionStrategy.BALANCED,
    ): uniffi.paykit_mobile.PaymentMethod? {
        val client = runCatching { paykitManager.getClient() }.getOrNull() ?: return null

        val methods = directoryService.discoverPaymentMethods(recipientPubkey)
        if (methods.isEmpty()) return null

        return runCatching {
            val preferences = SelectionPreferences(
                strategy = strategy,
                excludedMethods = emptyList(),
                maxFeeSats = null,
                maxConfirmationTimeSecs = null,
            )
            val result = client.selectMethod(methods, amountSats, preferences)
            // Try primary method first, then fallback methods
            methods.find { it.methodId == result.primaryMethod }
                ?: result.fallbackMethods.firstNotNullOfOrNull { fallback ->
                    methods.find { it.methodId == fallback }
                }
        }.getOrElse { e ->
            Logger.warn("Method selection failed, using first available: ${e.message}", context = TAG)
            methods.firstOrNull()
        }
    }

    /**
     * Build ordered payment methods for fallback execution.
     *
     * Returns primary method first, followed by fallback methods.
     *
     * @param recipientPubkey Recipient's pubkey
     * @param primaryMethod The primary selected method
     * @return Ordered list of payment methods
     */
    private suspend fun buildOrderedPaymentMethods(
        recipientPubkey: String,
        primaryMethod: uniffi.paykit_mobile.PaymentMethod,
    ): List<uniffi.paykit_mobile.PaymentMethod> {
        val client = runCatching { paykitManager.getClient() }.getOrNull()
            ?: return listOf(primaryMethod)

        val methods = directoryService.discoverPaymentMethods(recipientPubkey)

        // Get selection result to determine fallback order
        val selectionResult = runCatching {
            val preferences = SelectionPreferences(
                strategy = SelectionStrategy.BALANCED,
                excludedMethods = emptyList(),
                maxFeeSats = null,
                maxConfirmationTimeSecs = null,
            )
            client.selectMethod(methods, 0uL, preferences)
        }.getOrNull()

        // Build ordered list: primary first, then fallbacks
        val orderedMethods = mutableListOf<uniffi.paykit_mobile.PaymentMethod>()

        // Add primary method
        orderedMethods.add(primaryMethod)

        // Add fallback methods (if available)
        if (selectionResult != null) {
            for (fallbackMethodId in selectionResult.fallbackMethods) {
                val fallbackMethod = methods.find { it.methodId == fallbackMethodId }
                if (fallbackMethod != null && fallbackMethod.methodId != primaryMethod.methodId) {
                    orderedMethods.add(fallbackMethod)
                }
            }
        }

        return orderedMethods
    }

    /**
     * Execute a Paykit URI payment.
     *
     * Discovers payment methods for the recipient and executes payment using smart selection.
     *
     * @param uri The Paykit URI (e.g., "paykit:pubkey" or "pip:pubkey")
     * @param amountSats Amount in satoshis
     * @return Payment result
     */
    private suspend fun payPaykitUri(
        uri: String,
        amountSats: ULong?,
    ): PaykitPaymentResult {
        Logger.info("Executing Paykit URI payment: $uri", context = TAG)
        _paymentState.value = PaykitPaymentState.Processing

        val pubkey = extractPubkeyFromUri(uri)
        val amount = amountSats ?: 0uL

        // Use smart method selection
        val selectedMethod = selectOptimalMethod(pubkey, amount)
        if (selectedMethod == null) {
            Logger.warn("No payment methods found for $pubkey", context = TAG)
            val receipt = createFailedReceipt(uri, amount, PaykitReceiptType.LIGHTNING)
            _paymentState.value = PaykitPaymentState.Failed(PaykitPaymentError.InvalidRecipient("No payment methods found for $pubkey"))
            return PaykitPaymentResult(
                success = false,
                receipt = receipt,
                error = PaykitPaymentError.InvalidRecipient("No payment methods found for $pubkey"),
            )
        }

        Logger.info("Selected payment method: ${selectedMethod.methodId}", context = TAG)

        return try {
            val client = paykitManager.getClient()

            // Build ordered methods: primary first, then fallbacks
            val orderedMethods = buildOrderedPaymentMethods(pubkey, selectedMethod)
            val candidates = orderedMethods.map { method ->
                PaymentCandidate(
                    methodId = method.methodId,
                    endpoint = method.endpoint,
                )
            }

            val execution = client.executeWithFallbacks(
                candidates = candidates,
                amountSats = amount,
                metadataJson = null,
            )

            val attemptedMethods = execution.attempts.map { it.methodId }

            val successResult = execution.successfulExecution
            if (!execution.success || successResult == null || !successResult.success) {
                Logger.warn(execution.summary, context = TAG)
                val receipt = createFailedReceipt(uri, amount, PaykitReceiptType.LIGHTNING)
                val errorMsg = execution.attempts.lastOrNull()?.error ?: execution.summary
                _paymentState.value = PaykitPaymentState.Failed(mapError(Exception(errorMsg)))
                return PaykitPaymentResult(
                    success = false,
                    receipt = receipt,
                    error = mapError(Exception(errorMsg)),
                )
            }

            // Determine receipt type based on successful method
            val receiptType = when {
                successResult.methodId.contains("onchain", ignoreCase = true) ||
                    successResult.methodId.contains("bitcoin", ignoreCase = true) -> PaykitReceiptType.ONCHAIN
                else -> PaykitReceiptType.LIGHTNING
            }

            val receipt = PaykitReceipt(
                id = UUID.randomUUID().toString(),
                type = receiptType,
                recipient = pubkey,
                amountSats = amount,
                feeSats = 0uL,
                paymentHash = successResult.executionId,
                preimage = null,
                txid = successResult.executionId,
                timestamp = Date(),
                status = PaykitReceiptStatus.SUCCEEDED,
            )

            if (autoStoreReceipts) {
                receiptStore.store(receipt)
            }

            // Log fallback attempts if any
            if (attemptedMethods.size > 1) {
                val attemptSummary = attemptedMethods.joinToString(" → ")
                Logger.info(
                    "Paykit payment succeeded after ${attemptedMethods.size} attempts: $attemptSummary",
                    context = TAG
                )
            } else {
                Logger.info("Paykit URI payment succeeded: ${successResult.executionId}", context = TAG)
            }
            _paymentState.value = PaykitPaymentState.Succeeded(receipt)

            PaykitPaymentResult(
                success = true,
                receipt = receipt,
                error = null,
            )
        } catch (e: Exception) {
            Logger.error("Paykit URI payment failed", e, context = TAG)

            val receipt = createFailedReceipt(uri, amount, PaykitReceiptType.LIGHTNING)
            if (autoStoreReceipts) {
                receiptStore.store(receipt)
            }

            _paymentState.value = PaykitPaymentState.Failed(mapError(e))

            PaykitPaymentResult(
                success = false,
                receipt = receipt,
                error = mapError(e),
            )
        }
    }

    private fun extractPubkeyFromUri(uri: String): String {
        return uri
            .removePrefix("paykit:")
            .removePrefix("pip:")
            .trim()
    }

    /**
     * Execute a payment with atomic spending limit enforcement.
     *
     * Uses reserve/commit/rollback pattern to prevent race conditions.
     */
    private suspend fun payWithSpendingLimit(
        lightningRepo: LightningRepo,
        recipient: String,
        amountSats: ULong,
        feeRate: Double?,
        peerPubkey: String,
        paymentType: DetectedPaymentType,
    ): PaykitPaymentResult {
        return runCatching {
            spendingLimitManager.executeWithSpendingLimit(
                peerPubkey = peerPubkey,
                amountSats = amountSats.toLong(),
            ) {
                when (paymentType) {
                    DetectedPaymentType.LIGHTNING -> payLightning(lightningRepo, recipient, amountSats)
                    DetectedPaymentType.ONCHAIN -> payOnchain(lightningRepo, recipient, amountSats, feeRate)
                    else -> throw PaykitPaymentError.UnsupportedPaymentType
                }
            }
        }.getOrElse { error ->
            Logger.error("Payment with spending limit failed", error as? Exception, context = TAG)
            when (error) {
                is SpendingLimitException.WouldExceedLimit -> PaykitPaymentResult(
                    success = false,
                    receipt = createFailedReceipt(recipient, amountSats, PaykitReceiptType.LIGHTNING),
                    error = PaykitPaymentError.SpendingLimitExceeded(error.remaining)
                )
                else -> PaykitPaymentResult(
                    success = false,
                    receipt = createFailedReceipt(recipient, amountSats, PaykitReceiptType.LIGHTNING),
                    error = mapError(error as? Exception ?: Exception(error.message))
                )
            }
        }
    }

    /**
     * Execute a Lightning payment.
     */
    suspend fun payLightning(
        lightningRepo: LightningRepo,
        invoice: String,
        amountSats: ULong? = null,
    ): PaykitPaymentResult {
        Logger.info("Executing Lightning payment", context = TAG)
        _paymentState.value = PaykitPaymentState.Processing

        val startTime = System.currentTimeMillis()

        return try {
            val lightningResult = PaykitIntegrationHelper.payLightning(lightningRepo, invoice, amountSats)

            val receipt = PaykitReceipt(
                id = UUID.randomUUID().toString(),
                type = PaykitReceiptType.LIGHTNING,
                recipient = invoice,
                amountSats = amountSats ?: 0uL,
                feeSats = lightningResult.feeMsat / 1000uL,
                paymentHash = lightningResult.paymentHash,
                preimage = lightningResult.preimage,
                txid = null,
                timestamp = Date(),
                status = PaykitReceiptStatus.SUCCEEDED
            )

            if (autoStoreReceipts) {
                receiptStore.store(receipt)
            }

            val duration = System.currentTimeMillis() - startTime
            Logger.info("Lightning payment succeeded in ${duration}ms", context = TAG)

            _paymentState.value = PaykitPaymentState.Succeeded(receipt)

            PaykitPaymentResult(
                success = true,
                receipt = receipt,
                error = null
            )
        } catch (e: Exception) {
            Logger.error("Lightning payment failed", e, context = TAG)

            val receipt = createFailedReceipt(invoice, amountSats ?: 0uL, PaykitReceiptType.LIGHTNING)

            if (autoStoreReceipts) {
                receiptStore.store(receipt)
            }

            _paymentState.value = PaykitPaymentState.Failed(mapError(e))

            PaykitPaymentResult(
                success = false,
                receipt = receipt,
                error = mapError(e)
            )
        }
    }

    /**
     * Execute an onchain payment.
     */
    suspend fun payOnchain(
        lightningRepo: LightningRepo,
        address: String,
        amountSats: ULong,
        feeRate: Double? = null,
    ): PaykitPaymentResult {
        Logger.info("Executing onchain payment", context = TAG)
        _paymentState.value = PaykitPaymentState.Processing

        val startTime = System.currentTimeMillis()

        return try {
            val txResult = PaykitIntegrationHelper.payOnchain(lightningRepo, address, amountSats, feeRate)

            val receipt = PaykitReceipt(
                id = UUID.randomUUID().toString(),
                type = PaykitReceiptType.ONCHAIN,
                recipient = address,
                amountSats = amountSats,
                feeSats = txResult.feeSats,
                paymentHash = null,
                preimage = null,
                txid = txResult.txid,
                timestamp = Date(),
                status = PaykitReceiptStatus.PENDING
            )

            if (autoStoreReceipts) {
                receiptStore.store(receipt)
            }

            val duration = System.currentTimeMillis() - startTime
            Logger.info("Onchain payment broadcast in ${duration}ms, txid: ${txResult.txid}", context = TAG)

            _paymentState.value = PaykitPaymentState.Succeeded(receipt)

            PaykitPaymentResult(
                success = true,
                receipt = receipt,
                error = null
            )
        } catch (e: Exception) {
            Logger.error("Onchain payment failed", e, context = TAG)

            val receipt = createFailedReceipt(address, amountSats, PaykitReceiptType.ONCHAIN)

            if (autoStoreReceipts) {
                receiptStore.store(receipt)
            }

            _paymentState.value = PaykitPaymentState.Failed(mapError(e))

            PaykitPaymentResult(
                success = false,
                receipt = receipt,
                error = mapError(e)
            )
        }
    }

    // MARK: - Receipt Management

    /** Get all stored receipts. */
    fun getReceipts(): List<PaykitReceipt> = receiptStore.getAll()

    /** Get receipt by ID. */
    fun getReceipt(id: String): PaykitReceipt? = receiptStore.get(id)

    /** Clear all receipts. */
    suspend fun clearReceipts() = receiptStore.clear()

    // MARK: - Helpers

    private fun createFailedReceipt(
        recipient: String,
        amountSats: ULong,
        type: PaykitReceiptType,
    ): PaykitReceipt {
        return PaykitReceipt(
            id = UUID.randomUUID().toString(),
            type = type,
            recipient = recipient,
            amountSats = amountSats,
            feeSats = 0uL,
            paymentHash = null,
            preimage = null,
            txid = null,
            timestamp = Date(),
            status = PaykitReceiptStatus.FAILED
        )
    }

    private fun mapError(error: Exception): PaykitPaymentError {
        return when (error) {
            is PaykitException.NotInitialized -> PaykitPaymentError.NotInitialized
            is PaykitException.Timeout -> PaykitPaymentError.Timeout
            is PaykitException.PaymentFailed -> PaykitPaymentError.PaymentFailed(error.reason)
            else -> PaykitPaymentError.Unknown(error.message ?: error.toString())
        }
    }

    /**
     * Determine if an error is retryable (should try next fallback method).
     *
     * Non-retryable errors stop the fallback loop to avoid double-spend risks.
     */
    private fun isRetryableError(errorMessage: String?): Boolean {
        if (errorMessage == null) return true

        val msg = errorMessage.lowercase()

        // Non-retryable patterns (could cause double-spend or are permanent)
        val nonRetryablePatterns = listOf(
            "already paid",
            "duplicate payment",
            "duplicate invoice",
            "insufficient balance",
            "insufficient funds",
            "invoice expired",
            "payment hash already exists",
            "invoice already paid",
            "amount too low",
            "amount below minimum",
            "permanently failed",
        )

        return nonRetryablePatterns.none { msg.contains(it) }
    }

    /** Reset payment state to idle. */
    fun resetState() {
        _paymentState.value = PaykitPaymentState.Idle
    }
}

// MARK: - Supporting Types

private enum class DetectedPaymentType {
    LIGHTNING,
    ONCHAIN,
    PAYKIT,
    UNKNOWN
}

/** Available payment method for a recipient. */
sealed class PaymentMethod {
    data class Lightning(val invoice: String) : PaymentMethod()
    data class Onchain(val address: String) : PaymentMethod()
    data class Paykit(val uri: String) : PaymentMethod()
}

/** Payment state for UI observation. */
sealed class PaykitPaymentState {
    object Idle : PaykitPaymentState()
    object Processing : PaykitPaymentState()
    data class Succeeded(val receipt: PaykitReceipt) : PaykitPaymentState()
    data class Failed(val error: PaykitPaymentError) : PaykitPaymentState()
}

/** Result of a payment operation. */
data class PaykitPaymentResult(
    val success: Boolean,
    val receipt: PaykitReceipt,
    val error: PaykitPaymentError?,
)

/** Payment receipt for record keeping. */
data class PaykitReceipt(
    val id: String,
    val type: PaykitReceiptType,
    val recipient: String,
    val amountSats: ULong,
    val feeSats: ULong,
    val paymentHash: String?,
    val preimage: String?,
    val txid: String?,
    val timestamp: Date,
    var status: PaykitReceiptStatus,
)

enum class PaykitReceiptType {
    LIGHTNING,
    ONCHAIN
}

enum class PaykitReceiptStatus {
    PENDING,
    SUCCEEDED,
    FAILED
}

/** Errors specific to payment operations. */
sealed class PaykitPaymentError(override val message: String) : Exception(message) {
    object NotInitialized : PaykitPaymentError("Payment service not initialized")
    data class InvalidRecipient(val recipient: String) : PaykitPaymentError("Invalid recipient: $recipient")
    object AmountRequired : PaykitPaymentError("Amount is required for this payment type")
    object InsufficientFunds : PaykitPaymentError("Insufficient funds for payment")
    data class PaymentFailed(override val message: String) : PaykitPaymentError(message)
    object Timeout : PaykitPaymentError("Payment timed out")
    object UnsupportedPaymentType : PaykitPaymentError("Unsupported payment type")
    data class SpendingLimitExceeded(val remainingSats: Long) : PaykitPaymentError(
        "Spending limit exceeded ($remainingSats sats remaining)"
    )
    data class Unknown(override val message: String) : PaykitPaymentError(message)

    /** User-friendly message for display. */
    val userMessage: String
        get() = when (this) {
            is NotInitialized -> "Please wait for the app to initialize"
            is InvalidRecipient -> "Please check the payment address or invoice"
            is AmountRequired -> "Please enter an amount"
            is InsufficientFunds -> "You don't have enough funds for this payment"
            is PaymentFailed -> "Payment could not be completed. Please try again."
            is Timeout -> "Payment is taking longer than expected"
            is UnsupportedPaymentType -> "This payment type is not supported yet"
            is SpendingLimitExceeded -> "This payment would exceed your spending limit"
            is Unknown -> "An unexpected error occurred"
        }
}

// MARK: - Receipt Store
// Note: PaykitReceiptStore is defined in PaykitReceiptStore.kt
