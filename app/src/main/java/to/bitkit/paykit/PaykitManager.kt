package to.bitkit.paykit

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.lightningdevkit.ldknode.Network
import to.bitkit.env.Env
import to.bitkit.paykit.executors.BitkitBitcoinExecutor
import to.bitkit.paykit.executors.BitkitLightningExecutor
import to.bitkit.repositories.LightningRepo
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages PaykitClient lifecycle and executor registration for Bitkit Android.
 *
 * Usage:
 * ```kotlin
 * val manager = PaykitManager.getInstance()
 * manager.initialize()
 * manager.registerExecutors(lightningRepo)
 * ```
 *
 * Note: PaykitMobile bindings must be generated and linked before this class
 * is fully functional. See INTEGRATION_DISCOVERY.md for setup instructions.
 */
@Singleton
class PaykitManager @Inject constructor() {

    companion object {
        private const val TAG = "PaykitManager"

        @Volatile
        private var instance: PaykitManager? = null

        fun getInstance(): PaykitManager {
            return instance ?: synchronized(this) {
                instance ?: PaykitManager().also { instance = it }
            }
        }
    }

    // The underlying Paykit client (null until initialized)
    // Type: PaykitClient from PaykitMobile bindings
    private var client: Any? = null

    // Bitcoin executor instance (stored to prevent garbage collection)
    private var bitcoinExecutor: BitkitBitcoinExecutor? = null

    // Lightning executor instance (stored to prevent garbage collection)
    private var lightningExecutor: BitkitLightningExecutor? = null

    // Mutex for thread-safe initialization
    private val mutex = Mutex()

    // Whether the manager has been initialized
    var isInitialized: Boolean = false
        private set

    // Whether executors have been registered
    var hasExecutors: Boolean = false
        private set

    // Bitcoin network configuration
    val bitcoinNetwork: BitcoinNetworkConfig = mapNetwork(Env.network).first

    // Lightning network configuration
    val lightningNetwork: LightningNetworkConfig = mapNetwork(Env.network).second

    /**
     * Maps LDK Network to Paykit network configs.
     */
    private fun mapNetwork(network: Network): Pair<BitcoinNetworkConfig, LightningNetworkConfig> {
        return when (network) {
            Network.BITCOIN -> BitcoinNetworkConfig.MAINNET to LightningNetworkConfig.MAINNET
            Network.TESTNET -> BitcoinNetworkConfig.TESTNET to LightningNetworkConfig.TESTNET
            Network.REGTEST -> BitcoinNetworkConfig.REGTEST to LightningNetworkConfig.REGTEST
            Network.SIGNET -> BitcoinNetworkConfig.TESTNET to LightningNetworkConfig.TESTNET
        }
    }

    /**
     * Initialize the Paykit client with network configuration.
     *
     * Call this during app startup, after the wallet is ready.
     *
     * @throws PaykitException if initialization fails
     */
    suspend fun initialize() = mutex.withLock {
        if (isInitialized) {
            Logger.debug("PaykitManager already initialized", context = TAG)
            return@withLock
        }

        Logger.info("Initializing PaykitManager with network: $bitcoinNetwork", context = TAG)

        // TODO: Uncomment when PaykitMobile bindings are available
        // client = PaykitClient.newWithNetwork(
        //     bitcoinNetwork = bitcoinNetwork.toFfi(),
        //     lightningNetwork = lightningNetwork.toFfi()
        // )

        isInitialized = true
        Logger.info("PaykitManager initialized successfully", context = TAG)
    }

    /**
     * Register Bitcoin and Lightning executors with the Paykit client.
     *
     * This connects Bitkit's LightningRepo to Paykit for payment execution.
     * Must be called after [initialize].
     *
     * @param lightningRepo The LightningRepo instance for payment operations
     * @throws PaykitException if registration fails or client not initialized
     */
    suspend fun registerExecutors(lightningRepo: LightningRepo) = mutex.withLock {
        if (!isInitialized) {
            throw PaykitException.NotInitialized
        }

        if (hasExecutors) {
            Logger.debug("Executors already registered", context = TAG)
            return@withLock
        }

        Logger.info("Registering Paykit executors", context = TAG)

        // Create executor instances
        bitcoinExecutor = BitkitBitcoinExecutor(lightningRepo)
        lightningExecutor = BitkitLightningExecutor(lightningRepo)

        // TODO: Uncomment when PaykitMobile bindings are available
        // val paykitClient = client as? PaykitClient
        //     ?: throw PaykitException.NotInitialized
        //
        // paykitClient.registerBitcoinExecutor(bitcoinExecutor!!)
        // paykitClient.registerLightningExecutor(lightningExecutor!!)

        hasExecutors = true
        Logger.info("Paykit executors registered successfully", context = TAG)
    }

    /**
     * Reset the manager state (for testing or logout scenarios).
     */
    fun reset() {
        client = null
        bitcoinExecutor = null
        lightningExecutor = null
        isInitialized = false
        hasExecutors = false
        Logger.info("PaykitManager reset", context = TAG)
    }
}

/**
 * Bitcoin network configuration for Paykit.
 */
enum class BitcoinNetworkConfig {
    MAINNET,
    TESTNET,
    REGTEST;

    // TODO: Uncomment when PaykitMobile bindings are available
    // fun toFfi(): BitcoinNetworkFfi = when (this) {
    //     MAINNET -> BitcoinNetworkFfi.MAINNET
    //     TESTNET -> BitcoinNetworkFfi.TESTNET
    //     REGTEST -> BitcoinNetworkFfi.REGTEST
    // }
}

/**
 * Lightning network configuration for Paykit.
 */
enum class LightningNetworkConfig {
    MAINNET,
    TESTNET,
    REGTEST;

    // TODO: Uncomment when PaykitMobile bindings are available
    // fun toFfi(): LightningNetworkFfi = when (this) {
    //     MAINNET -> LightningNetworkFfi.MAINNET
    //     TESTNET -> LightningNetworkFfi.TESTNET
    //     REGTEST -> LightningNetworkFfi.REGTEST
    // }
}

/**
 * Errors that can occur during Paykit operations.
 */
sealed class PaykitException(message: String) : Exception(message) {
    object NotInitialized : PaykitException("PaykitManager has not been initialized")
    data class ExecutorRegistrationFailed(val reason: String) : PaykitException("Failed to register executor: $reason")
    data class PaymentFailed(val reason: String) : PaykitException("Payment failed: $reason")
    object Timeout : PaykitException("Operation timed out")
    data class Unknown(val reason: String) : PaykitException("Unknown error: $reason")
}
