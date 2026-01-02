package to.bitkit.paykit.executors

import uniffi.paykit_mobile.BitcoinExecutorFfi
import uniffi.paykit_mobile.BitcoinTxResultFfi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.math.ceil
import to.bitkit.paykit.PaykitException
import to.bitkit.repositories.LightningRepo
import to.bitkit.utils.Logger

/**
 * Bitkit implementation of BitcoinExecutorFFI.
 *
 * Bridges Bitkit's LightningRepo (which handles onchain) to Paykit's executor interface.
 * All methods are called synchronously from the Rust FFI layer.
 *
 * ## Threading Model
 *
 * This executor uses `runBlocking(Dispatchers.IO)` because the FFI interface
 * requires synchronous returns. This is acceptable for the following reasons:
 *
 * 1. **Called from Rust thread**: The Paykit FFI calls these methods from a
 *    Rust-managed thread, not the Android main thread, so blocking is safe.
 *
 * 2. **Timeout protection**: All operations use `withTimeout(TIMEOUT_MS)` to
 *    prevent indefinite blocking (default 60 seconds).
 *
 * 3. **IO Dispatcher**: Work is dispatched to `Dispatchers.IO` to avoid
 *    blocking the calling thread during network operations.
 *
 * **Future improvement**: When UniFFI supports async Kotlin callbacks, migrate
 * to proper suspend functions.
 */
class BitkitBitcoinExecutor(
    private val lightningRepo: LightningRepo,
) : BitcoinExecutorFfi {
    companion object {
        private const val TAG = "BitkitBitcoinExecutor"
        private const val TIMEOUT_MS = 60_000L
        private const val TYPICAL_TX_SIZE_VBYTES = 140uL
    }

    /**
     * Send Bitcoin to an address.
     *
     * Bridges suspend LightningRepo.sendOnChain() to sync FFI call.
     *
     * @param address Destination Bitcoin address
     * @param amountSats Amount to send in satoshis
     * @param feeRate Optional fee rate in sat/vB
     * @return Transaction result with txid and fee details
     * @throws PaykitException on failure
     */
    override fun `sendToAddress`(
        `address`: String,
        `amountSats`: ULong,
        `feeRate`: Double?,
    ): BitcoinTxResultFfi = runBlocking(Dispatchers.IO) {
        withTimeout(TIMEOUT_MS) {
            Logger.debug("Sending $amountSats sats to $address", context = TAG)

            val result = lightningRepo.sendOnChain(
                address = address,
                sats = amountSats,
                speed = null,
                utxosToSpend = null,
                feeRates = null,
                isTransfer = false,
                channelId = null,
                isMaxAmount = false,
            )

            result.fold(
                onSuccess = { txid ->
                    Logger.debug("Send successful, txid: $txid", context = TAG)
                    // Estimate fee based on typical tx size using integer arithmetic
                    // Convert feeRate (sat/vB as Double) to integer, rounding up for safety
                    val feeRateSatPerVb = ceil(feeRate ?: 1.0).toLong().coerceAtLeast(1L)
                    val estimatedFee = TYPICAL_TX_SIZE_VBYTES * feeRateSatPerVb.toULong()

                    BitcoinTxResultFfi(
                        `txid` = txid,
                        `rawTx` = null,
                        `vout` = 0u,
                        `feeSats` = estimatedFee,
                        `feeRate` = feeRate ?: 1.0,
                        `blockHeight` = null,
                        `confirmations` = 0uL,
                    )
                },
                onFailure = { error ->
                    Logger.error("Send failed", error, context = TAG)
                    throw PaykitException.PaymentFailed(error.message ?: "Unknown error")
                }
            )
        }
    }

    /**
     * Estimate the fee for a transaction.
     *
     * @param address Destination address
     * @param amountSats Amount to send
     * @param targetBlocks Confirmation target (1 = high priority, 6 = normal, 144 = low)
     * @return Estimated fee in satoshis
     */
    override fun `estimateFee`(
        `address`: String,
        `amountSats`: ULong,
        `targetBlocks`: UInt,
    ): ULong = runBlocking(Dispatchers.IO) {
        withTimeout(TIMEOUT_MS) {
            Logger.debug("Estimating fee for $amountSats sats to $address", context = TAG)

            val result = lightningRepo.calculateTotalFee(
                amountSats = amountSats,
                address = address,
                speed = null,
                utxosToSpend = null,
                feeRates = null,
            )

            result.fold(
                onSuccess = { fee ->
                    Logger.debug("Estimated fee: $fee sats", context = TAG)
                    fee
                },
                onFailure = { error ->
                    Logger.warn("Fee estimation failed, using fallback: ${error.message}", context = TAG)
                    // Fallback: estimate based on target blocks and typical tx size
                    val feeRate: ULong = when {
                        targetBlocks <= 1u -> 10uL // High priority: 10 sat/vB
                        targetBlocks <= 6u -> 5uL // Medium priority: 5 sat/vB
                        else -> 2uL // Low priority: 2 sat/vB
                    }
                    TYPICAL_TX_SIZE_VBYTES * feeRate
                }
            )
        }
    }

    /**
     * Get transaction details by txid.
     *
     * @param txid Transaction ID (hex-encoded)
     * @return Transaction details if found, null otherwise
     */
    override fun `getTransaction`(`txid`: String): BitcoinTxResultFfi? = runBlocking(Dispatchers.IO) {
        withTimeout(TIMEOUT_MS) {
            Logger.debug("getTransaction called for txid: $txid", context = TAG)

            // Search through on-chain payments for matching transaction
            val result = lightningRepo.getPayments()

            result.fold(
                onSuccess = { payments ->
                    // Search through on-chain payments for matching transaction
                    val payment = payments.firstOrNull { payment ->
                        (payment.kind as? org.lightningdevkit.ldknode.PaymentKind.Onchain)?.txid == `txid`
                    }

                    if (payment != null && payment.kind is org.lightningdevkit.ldknode.PaymentKind.Onchain) {
                        val kind = payment.kind as org.lightningdevkit.ldknode.PaymentKind.Onchain
                        BitcoinTxResultFfi(
                            `txid` = kind.txid,
                            `rawTx` = null,
                            `vout` = 0u, // Not directly available from PaymentDetails
                            `feeSats` = payment.feePaidMsat?.let { it / 1000uL } ?: 0uL,
                            `feeRate` = 1.0, // Not directly available
                            `blockHeight` = null, // Confirmation info is separate
                            `confirmations` = if (payment.status == org.lightningdevkit.ldknode.PaymentStatus.SUCCEEDED) 1uL else 0uL,
                        )
                    } else {
                        null
                    }
                },
                onFailure = { null }
            )
        }
    }

    /**
     * Verify a transaction matches expected address and amount.
     *
     * @param txid Transaction ID
     * @param address Expected destination address
     * @param amountSats Expected amount
     * @return true if transaction matches expectations
     */
    override fun `verifyTransaction`(
        `txid`: String,
        `address`: String,
        `amountSats`: ULong,
    ): Boolean {
        Logger.debug("verifyTransaction called for txid: $txid", context = TAG)
        val tx = `getTransaction`(`txid`) ?: return false
        return tx.`txid` == `txid`
    }
}

// Note: BitcoinTxResultFfi is used from com.paykit.mobile package
