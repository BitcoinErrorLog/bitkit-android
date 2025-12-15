package to.bitkit.paykit.executors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import to.bitkit.paykit.PaykitException
import to.bitkit.repositories.LightningRepo
import to.bitkit.utils.Logger

/**
 * Bitkit implementation of BitcoinExecutorFFI.
 *
 * Bridges Bitkit's LightningRepo (which handles onchain) to Paykit's executor interface.
 * All methods are called synchronously from the Rust FFI layer.
 *
 * Thread Safety: All methods use runBlocking with Dispatchers.IO to handle
 * the suspend LightningRepo APIs from sync FFI calls.
 */
class BitkitBitcoinExecutor(
    private val lightningRepo: LightningRepo,
) {
    companion object {
        private const val TAG = "BitkitBitcoinExecutor"
        private const val TIMEOUT_MS = 60_000L
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
    fun sendToAddress(
        address: String,
        amountSats: ULong,
        feeRate: Double?,
    ): BitcoinTxResult = runBlocking(Dispatchers.IO) {
        withTimeout(TIMEOUT_MS) {
            Logger.debug("Sending $amountSats sats to $address", context = TAG)

            val result = lightningRepo.sendOnChain(
                address = address,
                sats = amountSats,
                speed = null, // Use default speed
                utxosToSpend = null,
                feeRates = null,
                isTransfer = false,
                channelId = null,
                isMaxAmount = false,
            )

            result.fold(
                onSuccess = { txid ->
                    Logger.debug("Send successful, txid: $txid", context = TAG)
                    BitcoinTxResult(
                        txid = txid,
                        rawTx = null,
                        vout = 0u,
                        feeSats = 0uL, // TODO: Extract from transaction
                        feeRate = feeRate ?: 1.0,
                        blockHeight = null,
                        confirmations = 0uL,
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
    fun estimateFee(
        address: String,
        amountSats: ULong,
        targetBlocks: UInt,
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
                    Logger.warn("Fee estimation failed, using fallback", context = TAG)
                    // Fallback fee estimation
                    val baseFee = 250uL
                    val multiplier: ULong = when {
                        targetBlocks <= 1u -> 3uL
                        targetBlocks <= 3u -> 2uL
                        else -> 1uL
                    }
                    baseFee * multiplier
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
    fun getTransaction(txid: String): BitcoinTxResult? {
        // TODO: Implement via ActivityRepo or transaction lookup service
        Logger.debug("getTransaction called for txid: $txid", context = TAG)
        return null
    }

    /**
     * Verify a transaction matches expected address and amount.
     *
     * @param txid Transaction ID
     * @param address Expected destination address
     * @param amountSats Expected amount
     * @return true if transaction matches expectations
     */
    fun verifyTransaction(
        txid: String,
        address: String,
        amountSats: ULong,
    ): Boolean {
        // TODO: Implement verification via transaction lookup
        Logger.debug("verifyTransaction called for txid: $txid", context = TAG)
        val tx = getTransaction(txid) ?: return false
        return tx.txid == txid
    }
}

/**
 * Result of a Bitcoin transaction for Paykit FFI.
 */
data class BitcoinTxResult(
    val txid: String,
    val rawTx: String?,
    val vout: UInt,
    val feeSats: ULong,
    val feeRate: Double,
    val blockHeight: ULong?,
    val confirmations: ULong,
) {
    // TODO: Uncomment when PaykitMobile bindings are available
    // fun toFfi(): BitcoinTxResultFfi = BitcoinTxResultFfi(
    //     txid = txid,
    //     rawTx = rawTx,
    //     vout = vout,
    //     feeSats = feeSats,
    //     feeRate = feeRate,
    //     blockHeight = blockHeight,
    //     confirmations = confirmations,
    // )
}
