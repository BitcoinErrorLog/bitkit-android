package to.bitkit.paykit.executors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.lightningdevkit.ldknode.PaymentDetails
import org.lightningdevkit.ldknode.PaymentStatus
import to.bitkit.paykit.PaykitException
import to.bitkit.repositories.LightningRepo
import to.bitkit.utils.Logger
import java.security.MessageDigest

/**
 * Bitkit implementation of LightningExecutorFFI.
 *
 * Bridges Bitkit's LightningRepo to Paykit's executor interface.
 * Handles coroutine-to-sync bridging and payment completion polling.
 */
class BitkitLightningExecutor(
    private val lightningRepo: LightningRepo,
) {
    companion object {
        private const val TAG = "BitkitLightningExecutor"
        private const val TIMEOUT_MS = 60_000L
        private const val POLLING_INTERVAL_MS = 500L
    }

    /**
     * Pay a BOLT11 invoice.
     *
     * Initiates payment and polls for completion to get preimage.
     *
     * @param invoice BOLT11 invoice string
     * @param amountMsat Amount in millisatoshis (for zero-amount invoices)
     * @param maxFeeMsat Maximum fee willing to pay
     * @return Payment result with preimage proof
     * @throws PaykitException on failure
     */
    fun payInvoice(
        invoice: String,
        amountMsat: ULong?,
        maxFeeMsat: ULong?,
    ): LightningPaymentResult = runBlocking(Dispatchers.IO) {
        withTimeout(TIMEOUT_MS) {
            Logger.debug("Paying invoice: ${invoice.take(20)}...", context = TAG)

            val sats = amountMsat?.let { it / 1000uL }

            val result = lightningRepo.payInvoice(
                bolt11 = invoice,
                sats = sats,
            )

            result.fold(
                onSuccess = { paymentId ->
                    Logger.debug("Payment initiated, id: $paymentId", context = TAG)
                    // Poll for payment completion to get preimage
                    pollForPaymentCompletion(paymentId.toString())
                },
                onFailure = { error ->
                    Logger.error("Payment failed", error, context = TAG)
                    throw PaykitException.PaymentFailed(error.message ?: "Unknown error")
                }
            )
        }
    }

    /**
     * Poll for payment completion to extract preimage.
     */
    private suspend fun pollForPaymentCompletion(paymentHash: String): LightningPaymentResult {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < TIMEOUT_MS) {
            val paymentsResult = lightningRepo.getPayments()

            paymentsResult.fold(
                onSuccess = { payments ->
                    val payment = payments.find { it.id.toString() == paymentHash }
                    if (payment != null) {
                        when (payment.status) {
                            PaymentStatus.SUCCEEDED -> {
                                Logger.debug("Payment succeeded", context = TAG)
                                return extractPaymentResult(payment, LightningPaymentStatus.SUCCEEDED)
                            }
                            PaymentStatus.FAILED -> {
                                Logger.error("Payment failed", context = TAG)
                                throw PaykitException.PaymentFailed("Payment failed")
                            }
                            else -> {
                                // Still pending, continue polling
                            }
                        }
                    }
                },
                onFailure = { error ->
                    Logger.warn("Failed to get payments: ${error.message}", context = TAG)
                }
            )

            delay(POLLING_INTERVAL_MS)
        }

        throw PaykitException.Timeout
    }

    /**
     * Extract payment result from PaymentDetails.
     */
    private fun extractPaymentResult(
        payment: PaymentDetails,
        status: LightningPaymentStatus,
    ): LightningPaymentResult {
        // TODO: Extract actual preimage from payment details
        // The preimage field location depends on LDK-node PaymentDetails structure
        return LightningPaymentResult(
            preimage = "", // TODO: Extract from payment.preimage
            paymentHash = payment.id.toString(),
            amountMsat = 0uL, // TODO: Extract from payment
            feeMsat = 0uL, // TODO: Extract from payment
            hops = 0u,
            status = status,
        )
    }

    /**
     * Decode a BOLT11 invoice.
     *
     * @param invoice BOLT11 invoice string
     * @return Decoded invoice details
     */
    fun decodeInvoice(invoice: String): DecodedInvoice {
        // TODO: Use BitkitCore.decode() when available
        Logger.debug("decodeInvoice called for: ${invoice.take(20)}...", context = TAG)
        return DecodedInvoice(
            paymentHash = "",
            amountMsat = null,
            description = null,
            descriptionHash = null,
            payee = "",
            expiry = 3600uL,
            timestamp = (System.currentTimeMillis() / 1000).toULong(),
            expired = false,
        )
    }

    /**
     * Estimate routing fee for an invoice.
     *
     * @param invoice BOLT11 invoice
     * @return Estimated fee in millisatoshis
     */
    fun estimateFee(invoice: String): ULong = runBlocking(Dispatchers.IO) {
        withTimeout(TIMEOUT_MS) {
            Logger.debug("Estimating routing fee for: ${invoice.take(20)}...", context = TAG)

            val result = lightningRepo.estimateRoutingFees(invoice)

            result.fold(
                onSuccess = { fee ->
                    Logger.debug("Estimated routing fee: $fee msat", context = TAG)
                    fee * 1000uL // Convert to msat
                },
                onFailure = { error ->
                    Logger.warn("Fee estimation failed: ${error.message}", context = TAG)
                    // Default routing fee estimate (1% base)
                    1000uL
                }
            )
        }
    }

    /**
     * Get payment status by payment hash.
     *
     * @param paymentHash Payment hash (hex-encoded)
     * @return Payment result if found, null otherwise
     */
    fun getPayment(paymentHash: String): LightningPaymentResult? = runBlocking(Dispatchers.IO) {
        withTimeout(TIMEOUT_MS) {
            Logger.debug("getPayment called for: $paymentHash", context = TAG)

            val result = lightningRepo.getPayments()

            result.fold(
                onSuccess = { payments ->
                    val payment = payments.find { it.id.toString() == paymentHash }
                    if (payment != null) {
                        val status = when (payment.status) {
                            PaymentStatus.SUCCEEDED -> LightningPaymentStatus.SUCCEEDED
                            PaymentStatus.FAILED -> LightningPaymentStatus.FAILED
                            else -> LightningPaymentStatus.PENDING
                        }
                        extractPaymentResult(payment, status)
                    } else {
                        null
                    }
                },
                onFailure = { null }
            )
        }
    }

    /**
     * Verify preimage matches payment hash.
     *
     * @param preimage Payment preimage (hex-encoded)
     * @param paymentHash Payment hash (hex-encoded)
     * @return true if preimage hashes to payment hash
     */
    fun verifyPreimage(preimage: String, paymentHash: String): Boolean {
        return try {
            val preimageBytes = preimage.hexToByteArray()
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(preimageBytes)
            val computedHash = hash.toHexString()

            computedHash.equals(paymentHash, ignoreCase = true)
        } catch (e: Exception) {
            Logger.error("Preimage verification failed", e, context = TAG)
            false
        }
    }

    /**
     * Convert hex string to byte array.
     */
    private fun String.hexToByteArray(): ByteArray {
        val hex = if (startsWith("0x")) substring(2) else this
        require(hex.length % 2 == 0) { "Hex string must have even length" }

        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    /**
     * Convert byte array to hex string.
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}

/**
 * Result of a Lightning payment for Paykit FFI.
 */
data class LightningPaymentResult(
    val preimage: String,
    val paymentHash: String,
    val amountMsat: ULong,
    val feeMsat: ULong,
    val hops: UInt,
    val status: LightningPaymentStatus,
) {
    // TODO: Uncomment when PaykitMobile bindings are available
    // fun toFfi(): LightningPaymentResultFfi = LightningPaymentResultFfi(
    //     preimage = preimage,
    //     paymentHash = paymentHash,
    //     amountMsat = amountMsat,
    //     feeMsat = feeMsat,
    //     hops = hops,
    //     status = status.toFfi(),
    // )
}

/**
 * Lightning payment status.
 */
enum class LightningPaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED;

    // TODO: Uncomment when PaykitMobile bindings are available
    // fun toFfi(): LightningPaymentStatusFfi = when (this) {
    //     PENDING -> LightningPaymentStatusFfi.PENDING
    //     SUCCEEDED -> LightningPaymentStatusFfi.SUCCEEDED
    //     FAILED -> LightningPaymentStatusFfi.FAILED
    // }
}

/**
 * Decoded BOLT11 invoice.
 */
data class DecodedInvoice(
    val paymentHash: String,
    val amountMsat: ULong?,
    val description: String?,
    val descriptionHash: String?,
    val payee: String,
    val expiry: ULong,
    val timestamp: ULong,
    val expired: Boolean,
)
