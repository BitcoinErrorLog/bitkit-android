package to.bitkit.paykit.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.paykit.mobile.BitcoinTxResultFfi
import com.paykit.mobile.LightningPaymentResultFfi
import to.bitkit.paykit.PaykitException
import to.bitkit.paykit.PaykitIntegrationHelper
import to.bitkit.repositories.LightningRepo
import to.bitkit.utils.Logger
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
 * Usage:
 * ```kotlin
 * val service = PaykitPaymentService.getInstance()
 * val result = service.pay(to = "lnbc...", amountSats = 10000)
 * ```
 */
@Singleton
class PaykitPaymentService @Inject constructor() {

    companion object {
        private const val TAG = "PaykitPaymentService"

        @Volatile
        private var instance: PaykitPaymentService? = null

        fun getInstance(): PaykitPaymentService {
            return instance ?: synchronized(this) {
                instance ?: PaykitPaymentService().also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val receiptStore = PaykitReceiptStore()

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
        val spendingLimitManager = SpendingLimitManager.getInstance()
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
            DetectedPaymentType.PAYKIT -> PaykitPaymentResult(
                success = false,
                receipt = createFailedReceipt(recipient, amountSats ?: 0uL, PaykitReceiptType.LIGHTNING),
                error = PaykitPaymentError.UnsupportedPaymentType
            )
            DetectedPaymentType.UNKNOWN -> PaykitPaymentResult(
                success = false,
                receipt = createFailedReceipt(recipient, amountSats ?: 0uL, PaykitReceiptType.LIGHTNING),
                error = PaykitPaymentError.InvalidRecipient(recipient)
            )
        }
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
        val spendingLimitManager = SpendingLimitManager.getInstance()

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
    data class SpendingLimitExceeded(val remainingSats: Long) : PaykitPaymentError("Spending limit exceeded ($remainingSats sats remaining)")
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
