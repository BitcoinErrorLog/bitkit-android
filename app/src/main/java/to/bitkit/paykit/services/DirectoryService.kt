package to.bitkit.paykit.services

import android.content.Context
import com.paykit.mobile.*
import to.bitkit.paykit.KeyManager
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for interacting with the Pubky directory
 * Uses PaykitClient FFI methods for directory operations
 */
@Singleton
class DirectoryService @Inject constructor(
    private val context: Context,
    private val keyManager: KeyManager,
    private val pubkyStorage: PubkyStorageAdapter
) {
    companion object {
        private const val TAG = "DirectoryService"
        private const val PAYKIT_PATH_PREFIX = "/pub/paykit.app/v0/"
    }

    private var paykitClient: PaykitClient? = null
    private var unauthenticatedTransport: UnauthenticatedTransportFfi? = null
    private var authenticatedTransport: AuthenticatedTransportFfi? = null
    private var homeserverBaseURL: String? = null

    /**
     * Initialize with PaykitClient
     */
    fun initialize(client: PaykitClient) {
        this.paykitClient = client
    }

    /**
     * Configure Pubky transport for directory operations
     */
    fun configurePubkyTransport(homeserverBaseURL: String? = null) {
        this.homeserverBaseURL = homeserverBaseURL
        val adapter = PubkyUnauthenticatedStorageAdapter(homeserverBaseURL)
        unauthenticatedTransport = UnauthenticatedTransportFfi.fromCallback(adapter)
    }

    /**
     * Configure authenticated transport with session
     */
    fun configureAuthenticatedTransport(sessionId: String, ownerPubkey: String, homeserverBaseURL: String? = null) {
        this.homeserverBaseURL = homeserverBaseURL
        val adapter = PubkyAuthenticatedStorageAdapter(sessionId, homeserverBaseURL)
        authenticatedTransport = AuthenticatedTransportFfi.fromCallback(adapter, ownerPubkey)
    }

    /**
     * Discover noise endpoint for a recipient
     */
    suspend fun discoverNoiseEndpoint(forRecipient recipientPubkey: String): NoiseEndpointInfo? {
        val client = paykitClient ?: run {
            Logger.error("DirectoryService: PaykitClient not initialized", null, context = TAG)
            return null
        }

        val transport = unauthenticatedTransport ?: run {
            // Create default transport if not configured
            val adapter = PubkyUnauthenticatedStorageAdapter(homeserverBaseURL)
            UnauthenticatedTransportFfi.fromCallback(adapter).also {
                unauthenticatedTransport = it
            }
        }

        return try {
            discoverNoiseEndpoint(transport, recipientPubkey)
        } catch (e: Exception) {
            Logger.error("Failed to discover Noise endpoint for $recipientPubkey", e, context = TAG)
            null
        }
    }

    /**
     * Publish our noise endpoint to the directory
     */
    suspend fun publishNoiseEndpoint(
        host: String,
        port: Int,
        noisePubkey: String,
        metadata: String? = null
    ) {
        val client = paykitClient ?: throw DirectoryError.NotConfigured
        val transport = authenticatedTransport ?: throw DirectoryError.NotConfigured

        try {
            publishNoiseEndpoint(transport, host, port.toUShort(), noisePubkey, metadata)
            Logger.info("Published Noise endpoint: $host:$port", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to publish Noise endpoint", e, context = TAG)
            throw DirectoryError.PublishFailed(e.message ?: "Unknown error")
        }
    }

    /**
     * Remove noise endpoint from directory
     */
    suspend fun removeNoiseEndpoint() {
        val client = paykitClient ?: throw DirectoryError.NotConfigured
        val transport = authenticatedTransport ?: throw DirectoryError.NotConfigured

        try {
            removeNoiseEndpoint(transport)
            Logger.info("Removed Noise endpoint", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to remove Noise endpoint", e, context = TAG)
            throw DirectoryError.PublishFailed(e.message ?: "Unknown error")
        }
    }

    /**
     * Discover payment methods for a recipient
     */
    suspend fun discoverPaymentMethods(forPubkey pubkey: String): List<PaymentMethod> {
        val client = paykitClient ?: run {
            Logger.error("DirectoryService: PaykitClient not initialized", null, context = TAG)
            return emptyList()
        }

        val transport = unauthenticatedTransport ?: run {
            val adapter = PubkyUnauthenticatedStorageAdapter(homeserverBaseURL)
            UnauthenticatedTransportFfi.fromCallback(adapter).also {
                unauthenticatedTransport = it
            }
        }

        return try {
            fetchSupportedPayments(transport, pubkey)
        } catch (e: Exception) {
            Logger.error("Failed to discover payment methods for $pubkey", e, context = TAG)
            emptyList()
        }
    }

    /**
     * Publish a payment method to the directory
     */
    suspend fun publishPaymentMethod(methodId: String, endpoint: String) {
        val client = paykitClient ?: throw DirectoryError.NotConfigured
        val transport = authenticatedTransport ?: throw DirectoryError.NotConfigured

        try {
            publishPaymentEndpoint(transport, methodId, endpoint)
            Logger.info("Published payment method: $methodId", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to publish payment method $methodId", e, context = TAG)
            throw DirectoryError.PublishFailed(e.message ?: "Unknown error")
        }
    }

    /**
     * Remove a payment method from the directory
     */
    suspend fun removePaymentMethod(methodId: String) {
        val client = paykitClient ?: throw DirectoryError.NotConfigured
        val transport = authenticatedTransport ?: throw DirectoryError.NotConfigured

        try {
            removePaymentEndpointFromDirectory(transport, methodId)
            Logger.info("Removed payment method: $methodId", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to remove payment method $methodId", e, context = TAG)
            throw DirectoryError.PublishFailed(e.message ?: "Unknown error")
        }
    }

    /**
     * Discover contacts from Pubky follows directory
     */
    suspend fun discoverContactsFromFollows(): List<DiscoveredContact> {
        val ownerPubkey = keyManager.getCurrentPublicKeyZ32() ?: return emptyList()

        // Create unauthenticated adapter for reading follows
        val unauthAdapter = pubkyStorage.createUnauthenticatedAdapter(homeserverBaseURL)

        // Fetch follows list from Pubky
        val followsPath = "/pub/pubky.app/follows/"
        val followsList = pubkyStorage.listDirectory(followsPath, unauthAdapter, ownerPubkey)

        val discovered = mutableListOf<DiscoveredContact>()

        for (followPubkey in followsList) {
            // Check if this follow has payment methods
            val paymentMethods = discoverPaymentMethods(followPubkey)
            if (paymentMethods.isNotEmpty()) {
                discovered.add(
                    DiscoveredContact(
                        pubkey = followPubkey,
                        name = null, // Could fetch from Pubky profile
                        hasPaymentMethods = true,
                        supportedMethods = paymentMethods.map { it.methodId }
                    )
                )
            }
        }

        return discovered
    }
}

/**
 * Discovered contact from directory
 */
data class DiscoveredContact(
    val pubkey: String,
    val name: String? = null,
    val hasPaymentMethods: Boolean = false,
    val supportedMethods: List<String> = emptyList()
)

/**
 * Directory service errors
 */
sealed class DirectoryError(message: String) : Exception(message) {
    object NotConfigured : DirectoryError("Directory service not configured")
    class NetworkError(msg: String) : DirectoryError("Network error: $msg")
    class ParseError(msg: String) : DirectoryError("Parse error: $msg")
    class NotFound(resource: String) : DirectoryError("Not found: $resource")
    class PublishFailed(msg: String) : DirectoryError("Publish failed: $msg")
}

