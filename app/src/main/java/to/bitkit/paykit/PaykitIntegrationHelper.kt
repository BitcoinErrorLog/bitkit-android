package to.bitkit.paykit

import uniffi.paykit_mobile.BitcoinTxResultFfi
import uniffi.paykit_mobile.LightningPaymentResultFfi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import to.bitkit.paykit.executors.BitkitBitcoinExecutor
import to.bitkit.paykit.executors.BitkitLightningExecutor
import to.bitkit.repositories.LightningRepo
import to.bitkit.utils.Logger

/**
 * Helper object for setting up and managing Paykit integration.
 *
 * Provides convenience methods for common integration tasks.
 */
object PaykitIntegrationHelper {

    private const val TAG = "PaykitIntegrationHelper"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Set up Paykit with Bitkit's Lightning repository.
     *
     * Call this during app startup after the wallet is ready.
     *
     * @param paykitManager The PaykitManager instance
     * @param lightningRepo The LightningRepo instance for payment operations
     * @throws PaykitException if setup fails
     */
    suspend fun setup(paykitManager: PaykitManager, lightningRepo: LightningRepo) {
        paykitManager.initialize()
        paykitManager.registerExecutors(lightningRepo)

        Logger.info("Paykit integration setup complete", context = TAG)
    }

    /**
     * Set up Paykit asynchronously.
     *
     * @param paykitManager The PaykitManager instance
     * @param lightningRepo The LightningRepo instance
     * @param onComplete Callback with success/failure result
     */
    fun setupAsync(
        paykitManager: PaykitManager,
        lightningRepo: LightningRepo,
        onComplete: (Result<Unit>) -> Unit = {},
    ) {
        scope.launch {
            try {
                setup(paykitManager, lightningRepo)
                onComplete(Result.success(Unit))
            } catch (e: Exception) {
                Logger.error("Paykit setup failed", e, context = TAG)
                onComplete(Result.failure(e))
            }
        }
    }

    /**
     * Check if Paykit is ready for use.
     */
    fun isReady(paykitManager: PaykitManager): Boolean =
        paykitManager.isInitialized && paykitManager.hasExecutors

    /**
     * Get the current network configuration.
     */
    fun networkInfo(paykitManager: PaykitManager): Pair<BitcoinNetworkConfig, LightningNetworkConfig> =
        paykitManager.bitcoinNetwork to paykitManager.lightningNetwork

    /**
     * Execute a Lightning payment via Paykit.
     *
     * @param paykitManager The PaykitManager instance
     * @param lightningRepo The LightningRepo instance
     * @param invoice BOLT11 invoice
     * @param amountSats Amount in satoshis (for zero-amount invoices)
     * @return Payment result
     * @throws PaykitException on failure
     */
    suspend fun payLightning(
        paykitManager: PaykitManager,
        lightningRepo: LightningRepo,
        invoice: String,
        amountSats: ULong?,
    ): LightningPaymentResultFfi {
        if (!isReady(paykitManager)) {
            throw PaykitException.NotInitialized
        }

        val executor = BitkitLightningExecutor(lightningRepo)
        val amountMsat = amountSats?.let { it * 1000uL }

        return executor.`payInvoice`(
            `invoice` = invoice,
            `amountMsat` = amountMsat,
            `maxFeeMsat` = null,
        )
    }

    /**
     * Execute an onchain payment via Paykit.
     *
     * @param paykitManager The PaykitManager instance
     * @param lightningRepo The LightningRepo instance
     * @param address Bitcoin address
     * @param amountSats Amount in satoshis
     * @param feeRate Fee rate in sat/vB
     * @return Transaction result
     * @throws PaykitException on failure
     */
    suspend fun payOnchain(
        paykitManager: PaykitManager,
        lightningRepo: LightningRepo,
        address: String,
        amountSats: ULong,
        feeRate: Double?,
    ): BitcoinTxResultFfi {
        if (!isReady(paykitManager)) {
            throw PaykitException.NotInitialized
        }

        val executor = BitkitBitcoinExecutor(lightningRepo)

        return executor.`sendToAddress`(
            `address` = address,
            `amountSats` = amountSats,
            `feeRate` = feeRate,
        )
    }

    /**
     * Reset Paykit integration state.
     *
     * Call this during logout or wallet reset.
     */
    fun reset(paykitManager: PaykitManager) {
        paykitManager.reset()
        Logger.info("Paykit integration reset", context = TAG)
    }
}
