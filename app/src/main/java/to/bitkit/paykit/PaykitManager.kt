package to.bitkit.paykit

import android.content.Context
import com.paykit.mobile.BitcoinNetworkFfi
import com.paykit.mobile.LightningNetworkFfi
import com.paykit.mobile.PaykitClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.lightningdevkit.ldknode.Network
import to.bitkit.env.Env
import to.bitkit.paykit.executors.BitkitBitcoinExecutor
import to.bitkit.paykit.executors.BitkitLightningExecutor
import to.bitkit.paykit.services.PaykitPaymentService
import to.bitkit.paykit.services.PubkyRingBridge
import to.bitkit.paykit.services.PubkySession
import to.bitkit.repositories.LightningRepo
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages PaykitClient lifecycle and executor registration for Bitkit Android.
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

    private var client: PaykitClient? = null
    private var bitcoinExecutor: BitkitBitcoinExecutor? = null
    private var lightningExecutor: BitkitLightningExecutor? = null
    private val mutex = Mutex()

    var isInitialized: Boolean = false
        private set

    var hasExecutors: Boolean = false
        private set

    /**
     * Owner public key for Paykit operations.
     * This is set during initialization from the wallet's identity key.
     */
    var ownerPubkey: String? = null
        private set

    /**
     * Payment service instance for executing payments.
     */
    var paymentService: PaykitPaymentService? = null
        private set

    val bitcoinNetwork: BitcoinNetworkConfig = mapNetwork(Env.network).first
    val lightningNetwork: LightningNetworkConfig = mapNetwork(Env.network).second

    private fun mapNetwork(network: Network): Pair<BitcoinNetworkConfig, LightningNetworkConfig> {
        return when (network) {
            Network.BITCOIN -> BitcoinNetworkConfig.MAINNET to LightningNetworkConfig.MAINNET
            Network.TESTNET -> BitcoinNetworkConfig.TESTNET to LightningNetworkConfig.TESTNET
            Network.REGTEST -> BitcoinNetworkConfig.REGTEST to LightningNetworkConfig.REGTEST
            Network.SIGNET -> BitcoinNetworkConfig.TESTNET to LightningNetworkConfig.TESTNET
        }
    }

    suspend fun initialize() = mutex.withLock {
        if (isInitialized) {
            Logger.debug("PaykitManager already initialized", context = TAG)
            return@withLock
        }

        Logger.info("Initializing PaykitManager with network: $bitcoinNetwork", context = TAG)

        client = PaykitClient.newWithNetwork(
            bitcoinNetwork = bitcoinNetwork.toFfi(),
            lightningNetwork = lightningNetwork.toFfi()
        )

        isInitialized = true
        Logger.info("PaykitManager initialized successfully", context = TAG)
    }

    suspend fun registerExecutors(lightningRepo: LightningRepo) = mutex.withLock {
        if (!isInitialized) {
            throw PaykitException.NotInitialized
        }

        if (hasExecutors) {
            Logger.debug("Executors already registered", context = TAG)
            return@withLock
        }

        Logger.info("Registering Paykit executors", context = TAG)

        bitcoinExecutor = BitkitBitcoinExecutor(lightningRepo)
        lightningExecutor = BitkitLightningExecutor(lightningRepo)

        val paykitClient = client ?: throw PaykitException.NotInitialized

        paykitClient.`registerBitcoinExecutor`(bitcoinExecutor!! as com.paykit.mobile.BitcoinExecutorFfi)
        paykitClient.`registerLightningExecutor`(lightningExecutor!! as com.paykit.mobile.LightningExecutorFfi)

        hasExecutors = true
        Logger.info("Paykit executors registered successfully", context = TAG)
    }

    fun reset() {
        client = null
        bitcoinExecutor = null
        lightningExecutor = null
        isInitialized = false
        hasExecutors = false
        Logger.info("PaykitManager reset", context = TAG)
    }

    /**
     * Get the PaykitClient instance
     * @throws PaykitException.NotInitialized if not initialized
     */
    fun getClient(): PaykitClient {
        return client ?: throw PaykitException.NotInitialized
    }

    // MARK: - Pubky-Ring Integration

    /**
     * Request a session from Pubky-ring app.
     * This opens Pubky-ring to authenticate and returns a session with credentials.
     */
    suspend fun requestPubkySession(context: Context): PubkySession {
        if (PubkyRingBridge.getInstance().isPubkyRingInstalled(context)) {
            Logger.info("Requesting session from Pubky-ring", context = TAG)
            return PubkyRingBridge.getInstance().requestSession(context)
        }
        throw PaykitException.PubkyRingNotInstalled
    }

    /**
     * Get a cached session or request a new one from Pubky-ring.
     */
    suspend fun getOrRequestSession(context: Context, pubkey: String? = null): PubkySession {
        // Check cache first
        if (pubkey != null) {
            PubkyRingBridge.getInstance().getCachedSession(pubkey)?.let { cached ->
                return cached
            }
        }

        // Request new session from Pubky-ring
        return requestPubkySession(context)
    }

    /**
     * Check if Pubky-ring is installed and available
     */
    fun isPubkyRingAvailable(context: Context): Boolean =
        PubkyRingBridge.getInstance().isPubkyRingInstalled(context)
}

/**
 * Extension function to get PaykitClient from PaykitManager
 */
fun PaykitManager.getClient(): PaykitClient = getClient()

enum class BitcoinNetworkConfig {
    MAINNET,
    TESTNET,
    REGTEST;

    fun toFfi(): BitcoinNetworkFfi = when (this) {
        MAINNET -> BitcoinNetworkFfi.MAINNET
        TESTNET -> BitcoinNetworkFfi.TESTNET
        REGTEST -> BitcoinNetworkFfi.REGTEST
    }
}

enum class LightningNetworkConfig {
    MAINNET,
    TESTNET,
    REGTEST;

    fun toFfi(): LightningNetworkFfi = when (this) {
        MAINNET -> LightningNetworkFfi.MAINNET
        TESTNET -> LightningNetworkFfi.TESTNET
        REGTEST -> LightningNetworkFfi.REGTEST
    }
}

sealed class PaykitException(message: String) : Exception(message) {
    object NotInitialized : PaykitException("PaykitManager has not been initialized")
    data class ExecutorRegistrationFailed(val reason: String) : PaykitException("Failed to register executor: $reason")
    data class PaymentFailed(val reason: String) : PaykitException("Payment failed: $reason")
    object Timeout : PaykitException("Operation timed out")
    object PubkyRingNotInstalled : PaykitException(
        "Pubky-ring app is not installed. Please install Pubky-ring to use this feature."
    )
    object SessionRequired : PaykitException("A Pubky session is required for this operation")
    data class Unknown(val reason: String) : PaykitException("Unknown error: $reason")
}
