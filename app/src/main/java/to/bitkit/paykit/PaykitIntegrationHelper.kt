package to.bitkit.paykit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.paykit.mobile.BitcoinTxResultFfi
import com.paykit.mobile.LightningPaymentResultFfi
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
     * @param lightningRepo The LightningRepo instance for payment operations
     * @throws PaykitException if setup fails
     */
    suspend fun setup(lightningRepo: LightningRepo) {
        val manager = PaykitManager.getInstance()

        manager.initialize()
        manager.registerExecutors(lightningRepo)

        Logger.info("Paykit integration setup complete", context = TAG)
    }

    /**
     * Set up Paykit asynchronously.
     *
     * @param lightningRepo The LightningRepo instance
     * @param onComplete Callback with success/failure result
     */
    fun setupAsync(
        lightningRepo: LightningRepo,
        onComplete: (Result<Unit>) -> Unit = {},
    ) {
        scope.launch {
            try {
                setup(lightningRepo)
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
    val isReady: Boolean
        get() {
            val manager = PaykitManager.getInstance()
            return manager.isInitialized && manager.hasExecutors
        }

    /**
     * Get the current network configuration.
     */
    val networkInfo: Pair<BitcoinNetworkConfig, LightningNetworkConfig>
        get() {
            val manager = PaykitManager.getInstance()
            return manager.bitcoinNetwork to manager.lightningNetwork
        }

    /**
     * Execute a Lightning payment via Paykit.
     *
     * @param lightningRepo The LightningRepo instance
     * @param invoice BOLT11 invoice
     * @param amountSats Amount in satoshis (for zero-amount invoices)
     * @return Payment result
     * @throws PaykitException on failure
     */
    suspend fun payLightning(
        lightningRepo: LightningRepo,
        invoice: String,
        amountSats: ULong?,
    ): LightningPaymentResultFfi {
        if (!isReady) {
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
     * @param lightningRepo The LightningRepo instance
     * @param address Bitcoin address
     * @param amountSats Amount in satoshis
     * @param feeRate Fee rate in sat/vB
     * @return Transaction result
     * @throws PaykitException on failure
     */
    suspend fun payOnchain(
        lightningRepo: LightningRepo,
        address: String,
        amountSats: ULong,
        feeRate: Double?,
    ): BitcoinTxResultFfi {
        if (!isReady) {
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
    fun reset() {
        PaykitManager.getInstance().reset()
        Logger.info("Paykit integration reset", context = TAG)
    }
}
