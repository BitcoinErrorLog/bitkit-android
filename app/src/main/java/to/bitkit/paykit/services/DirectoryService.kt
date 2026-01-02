package to.bitkit.paykit.services

import android.content.Context
import uniffi.paykit_mobile.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import to.bitkit.paykit.KeyManager
import to.bitkit.paykit.protocol.PaykitV0Protocol
import to.bitkit.paykit.storage.PaykitKeychainStorage
import to.bitkit.paykit.types.HomeserverURL
import to.bitkit.paykit.types.HomeserverResolver
import to.bitkit.paykit.types.HomeserverDefaults
import to.bitkit.paykit.types.OwnerPubkey
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

// MARK: - Pubky Homeserver Configuration

/**
 * Configuration for Pubky homeserver connections.
 *
 * Uses [PaykitV0Protocol] for canonical path and AAD construction.
 */
object PubkyConfig {
    /** Production homeserver pubkey (Synonym mainnet) */
    const val PRODUCTION_HOMESERVER = "8um71us3fyw6h8wbcxb5ar3rwusy1a6u49956ikzojg3gcwd1dty"

    /** Staging homeserver pubkey (Synonym staging) */
    const val STAGING_HOMESERVER = "ufibwbmed6jeq9k4p583go95wofakh9fwpp4k734trq79pd9u1uy"

    /** Default homeserver to use */
    const val DEFAULT_HOMESERVER = PRODUCTION_HOMESERVER

    /** Pubky app URL for production */
    const val PRODUCTION_APP_URL = "https://pubky.app"

    /** Pubky app URL for staging */
    const val STAGING_APP_URL = "https://staging.pubky.app"

    /** Paykit storage paths - matching paykit-lib conventions */
    const val PAYKIT_PATH_PREFIX = PaykitV0Protocol.PAYKIT_V0_PREFIX + "/"

    /**
     * Get the homeserver base URL for directory operations
     */
    fun homeserverUrl(homeserver: String = DEFAULT_HOMESERVER): String {
        // The homeserver pubkey is used as the base for directory operations
        return homeserver
    }

    /**
     * Get the canonical path for a payment request addressed to a recipient.
     * Path format: /pub/paykit.app/v0/requests/{scope}/{requestId}
     * where scope = hex(sha256(normalized_recipient_pubkey))
     */
    fun paymentRequestPath(recipientPubkey: String, requestId: String): String =
        PaykitV0Protocol.paymentRequestPath(recipientPubkey, requestId)

    /**
     * Get the canonical directory path for listing payment requests to a recipient.
     */
    fun paymentRequestsDir(recipientPubkey: String): String =
        PaykitV0Protocol.paymentRequestsDir(recipientPubkey)

    /**
     * Get the canonical path for a subscription proposal addressed to a subscriber.
     */
    fun subscriptionProposalPath(subscriberPubkey: String, proposalId: String): String =
        PaykitV0Protocol.subscriptionProposalPath(subscriberPubkey, proposalId)

    /**
     * Get the canonical directory path for listing subscription proposals to a subscriber.
     */
    fun subscriptionProposalsDir(subscriberPubkey: String): String =
        PaykitV0Protocol.subscriptionProposalsDir(subscriberPubkey)
}

/**
 * Service for interacting with the Pubky directory
 * Uses PaykitClient FFI methods for directory operations
 *
 * Note: This class has many functions (22) because it consolidates all directory operations
 * in one place for simpler dependency injection. A future refactoring could split it into:
 * - DirectoryProfileService (profile operations)
 * - DirectoryFollowsService (follows/discovery operations)
 * - DirectoryPushService (push notification endpoints)
 * - DirectoryPaymentRequestService (payment request storage)
 */
@Suppress("TooManyFunctions") // Intentional consolidation of directory operations
@Singleton
class DirectoryService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: KeyManager,
    private val pubkyStorage: PubkyStorageAdapter,
    private val pubkySDKService: PubkySDKService,
    private val pubkyRingBridge: PubkyRingBridge,
    private val keychainStorage: PaykitKeychainStorage,
) {
    companion object {
        private const val TAG = "DirectoryService"
        private const val PAYKIT_PATH_PREFIX = "/pub/paykit.app/v0/"
        private const val KEY_PUBLIC = "pubky.identity.public"
        private const val KEY_SESSION_SECRET = "pubky.session.secret"
        private const val KEY_HOMESERVER_URL = "pubky.homeserver.url"
    }

    private var paykitClient: PaykitClient? = null
    private var unauthenticatedTransport: UnauthenticatedTransportFfi? = null
    private var authenticatedTransport: AuthenticatedTransportFfi? = null
    private var authenticatedAdapter: PubkyAuthenticatedStorageAdapter? = null
    private var homeserverURL: HomeserverURL? = null

    /**
     * Initialize with PaykitClient
     */
    fun initialize(client: PaykitClient) {
        this.paykitClient = client
    }

    /**
     * Configure Pubky transport for directory operations
     * @param homeserverURL The homeserver URL (defaults to resolved default homeserver)
     */
    fun configurePubkyTransport(homeserverURL: HomeserverURL? = null) {
        this.homeserverURL = homeserverURL ?: HomeserverDefaults.defaultHomeserverURL
        val adapter = pubkyStorage.createUnauthenticatedAdapter(this.homeserverURL)
        unauthenticatedTransport = UnauthenticatedTransportFfi.fromCallback(adapter)
    }

    /**
     * Configure authenticated transport with session
     * @param sessionId The session ID from Pubky-ring
     * @param ownerPubkey The owner's public key
     * @param homeserverURL The homeserver URL (defaults to resolved default homeserver)
     */
    fun configureAuthenticatedTransport(sessionId: String, ownerPubkey: OwnerPubkey, homeserverURL: HomeserverURL? = null) {
        this.homeserverURL = homeserverURL ?: HomeserverDefaults.defaultHomeserverURL
        val adapter = pubkyStorage.createAuthenticatedAdapter(sessionId, ownerPubkey.value, this.homeserverURL)
        authenticatedAdapter = adapter
        authenticatedTransport = AuthenticatedTransportFfi.fromCallback(adapter, ownerPubkey.value)
    }

    /**
     * Configure transport using a Pubky session from Pubky-ring
     * @param session The Pubky session with credentials
     * @param homeserver Optional homeserver URL. If null, uses the default homeserver.
     */
    fun configureWithPubkySession(session: PubkySession, homeserver: HomeserverURL? = null) {
        homeserverURL = homeserver ?: HomeserverDefaults.defaultHomeserverURL

        // Configure authenticated transport and adapter
        val adapter = pubkyStorage.createAuthenticatedAdapter(session.sessionSecret, session.pubkey, homeserverURL)
        authenticatedAdapter = adapter  // Save adapter for direct put/delete operations
        authenticatedTransport = AuthenticatedTransportFfi.fromCallback(adapter, session.pubkey)

        // Also configure unauthenticated transport
        val unauthAdapter = pubkyStorage.createUnauthenticatedAdapter(homeserverURL)
        unauthenticatedTransport = UnauthenticatedTransportFfi.fromCallback(unauthAdapter)

        Logger.info("Configured DirectoryService with Pubky session for ${session.pubkey}", context = TAG)
    }

    /**
     * Attempt to auto-configure from stored keychain credentials.
     * Returns true if successfully configured, false if no stored session exists.
     */
    suspend fun tryRestoreFromKeychain(): Boolean {
        if (isConfigured) return true

        val storedPubkey = keychainStorage.getString(KEY_PUBLIC) ?: return false
        val sessionSecret = keychainStorage.getString(KEY_SESSION_SECRET) ?: return false

        // Restore homeserver URL if stored
        val homeserver = keychainStorage.getString(KEY_HOMESERVER_URL)?.let { HomeserverURL(it) }

        configureWithPubkySession(
            PubkySession(
                pubkey = storedPubkey,
                sessionSecret = sessionSecret,
                capabilities = listOf("read", "write"),
                createdAt = java.util.Date(),
            ),
            homeserver,
        )

        // Also import session into SDK
        runCatching { pubkySDKService.importSession(storedPubkey, sessionSecret) }

        return true
    }

    /**
     * Check if the service is configured with an authenticated transport
     */
    val isConfigured: Boolean get() = authenticatedTransport != null

    /**
     * Discover noise endpoint for a recipient
     */
    suspend fun discoverNoiseEndpoint(recipientPubkey: String): NoiseEndpointInfo? {
        val transport = unauthenticatedTransport ?: run {
            // Create default transport if not configured
            val adapter = pubkyStorage.createUnauthenticatedAdapter(homeserverURL)
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
    suspend fun discoverPaymentMethods(pubkey: String): List<uniffi.paykit_mobile.PaymentMethod> {
        val client = paykitClient ?: run {
            Logger.error("DirectoryService: PaykitClient not initialized", null, context = TAG)
            return emptyList()
        }

        val transport = unauthenticatedTransport ?: run {
            val adapter = pubkyStorage.createUnauthenticatedAdapter(homeserverURL)
            UnauthenticatedTransportFfi.fromCallback(adapter).also {
                unauthenticatedTransport = it
            }
        }

        return try {
            client.`fetchSupportedPayments`(transport, pubkey)
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
            client.`publishPaymentEndpoint`(transport, methodId, endpoint)
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
            client.`removePaymentEndpointFromDirectory`(transport, methodId)
            Logger.info("Removed payment method: $methodId", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to remove payment method $methodId", e, context = TAG)
            throw DirectoryError.PublishFailed(e.message ?: "Unknown error")
        }
    }

    // MARK: - Cross-Device Payment Request Storage

    /**
     * Publish a payment request to Pubky storage for async retrieval.
     * Stores the ENCRYPTED request at the canonical v0 path:
     * `/pub/paykit.app/v0/requests/{recipient_scope}/{requestId}`
     * on the sender's homeserver so the recipient can poll contacts to fetch it.
     *
     * SECURITY: Requests are encrypted using Sealed Blob v1 to recipient's Noise public key.
     * Uses canonical AAD format: `paykit:v0:request:{path}:{requestId}`
     *
     * @param request The payment request to publish
     * @param recipientPubkey The pubkey of the recipient (who should process the request)
     * @throws DirectoryError.EncryptionFailed if encryption fails (e.g., recipient has no Noise endpoint)
     */
    suspend fun publishPaymentRequest(
        request: to.bitkit.paykit.models.PaymentRequest,
        recipientPubkey: String,
    ) {
        // Auto-restore from keychain if not configured
        if (!isConfigured) tryRestoreFromKeychain()
        val adapter = authenticatedAdapter ?: throw DirectoryError.NotConfigured

        val requestJson = kotlinx.serialization.json.Json.encodeToString(
            to.bitkit.paykit.models.PaymentRequest.serializer(),
            request
        )

        // Use canonical v0 path (scope-based)
        val path = PaykitV0Protocol.paymentRequestPath(recipientPubkey, request.id)

        // Discover recipient's Noise endpoint to get their public key for encryption
        val recipientNoiseEndpoint = discoverNoiseEndpoint(recipientPubkey)
            ?: throw DirectoryError.EncryptionFailed("Recipient has no Noise endpoint published")

        // Encrypt request using Sealed Blob v1 with canonical AAD
        val plaintextBytes = requestJson.toByteArray(Charsets.UTF_8)
        val recipientNoisePkBytes = PubkyRingBridge.hexStringToByteArray(recipientNoiseEndpoint.serverNoisePubkey)
        val aad = PaykitV0Protocol.paymentRequestAad(recipientPubkey, request.id)

        val encryptedEnvelope = try {
            com.pubky.noise.sealedBlobEncrypt(
                recipientNoisePkBytes,
                plaintextBytes,
                aad,
                PaykitV0Protocol.PURPOSE_REQUEST,
            )
        } catch (e: Exception) {
            Logger.error("Failed to encrypt payment request", e, context = TAG)
            throw DirectoryError.EncryptionFailed("Encryption failed: ${e.message}")
        }

        val result = adapter.put(path, encryptedEnvelope)
        if (!result.success) {
            Logger.error("Failed to publish payment request ${request.id}: ${result.error}", context = TAG)
            throw DirectoryError.PublishFailed(result.error ?: "Unknown error")
        }
        Logger.info("Published encrypted payment request: ${request.id} to $recipientPubkey", context = TAG)
    }

    /**
     * Fetch a payment request from a sender's Pubky storage.
     * Retrieves and decrypts from: `pubky://{senderPubkey}/pub/paykit.app/v0/requests/{scope}/{requestId}`
     *
     * SECURITY: Decrypts using our Noise secret key and canonical AAD.
     *
     * @param requestId The unique request ID
     * @param senderPubkey The pubkey of the sender (who published the request)
     * @param recipientPubkey The pubkey of the recipient (our pubkey, used for scope computation)
     */
    suspend fun fetchPaymentRequest(
        requestId: String,
        senderPubkey: String,
        recipientPubkey: String,
    ): to.bitkit.paykit.models.PaymentRequest? {
        // Use canonical v0 path (scope-based)
        val path = PaykitV0Protocol.paymentRequestPath(recipientPubkey, requestId)
        
        // Use the proper pubky:// URI which uses DHT/Pkarr resolution
        val pubkyUri = "pubky://$senderPubkey$path"
        Logger.debug("Fetching payment request from: $pubkyUri", context = TAG)

        return try {
            val envelopeBytes = pubkySDKService.getData(pubkyUri)
            if (envelopeBytes != null) {
                val envelopeJson = String(envelopeBytes)
                
                // Check if this is an encrypted sealed blob
                if (!com.pubky.noise.isSealedBlob(envelopeJson)) {
                    Logger.error("Payment request is not encrypted (sealed blob required)", context = TAG)
                    return null
                }
                
                // Get our Noise secret key for decryption
                val noiseKeypair = keyManager.getCachedNoiseKeypair()
                if (noiseKeypair == null) {
                    Logger.error("No Noise keypair available for decryption", context = TAG)
                    return null
                }
                
                val myNoiseSk = PubkyRingBridge.hexStringToByteArray(noiseKeypair.secretKeyHex)
                val aad = PaykitV0Protocol.paymentRequestAad(recipientPubkey, requestId)
                
                val plaintextBytes = try {
                    com.pubky.noise.sealedBlobDecrypt(myNoiseSk, envelopeJson, aad)
                } catch (e: Exception) {
                    Logger.error("Failed to decrypt payment request $requestId", e, context = TAG)
                    return null
                }
                
                val requestJson = String(plaintextBytes)
                val request = kotlinx.serialization.json.Json.decodeFromString(
                    to.bitkit.paykit.models.PaymentRequest.serializer(),
                    requestJson
                )
                Logger.info("Successfully fetched payment request $requestId from ${senderPubkey.take(12)}...", context = TAG)
                request
            } else {
                Logger.debug("Payment request $requestId not found at ${senderPubkey.take(12)}...", context = TAG)
                null
            }
        } catch (e: Exception) {
            Logger.error("Failed to fetch payment request $requestId from $senderPubkey", e, context = TAG)
            null
        }
    }

    /**
     * Remove a payment request from storage (after it's been processed).
     *
     * NOTE: This can only delete requests from OUR storage. In the sender-storage model,
     * only the sender can delete their stored requests.
     *
     * @param requestId The unique request ID
     * @param recipientPubkey The pubkey of the recipient (used for scope computation)
     */
    suspend fun removePaymentRequest(requestId: String, recipientPubkey: String) {
        // Auto-restore from keychain if not configured
        if (!isConfigured) tryRestoreFromKeychain()
        val adapter = authenticatedAdapter ?: throw DirectoryError.NotConfigured
        val path = PaykitV0Protocol.paymentRequestPath(recipientPubkey, requestId)

        val result = adapter.delete(path)
        if (!result.success) {
            Logger.error("Failed to remove payment request $requestId: ${result.error}", context = TAG)
            throw DirectoryError.PublishFailed(result.error ?: "Unknown error")
        }
        Logger.info("Removed payment request: $requestId", context = TAG)
    }

    /**
     * Discover pending payment requests from a peer's storage.
     *
     * In the v0 sender-storage model, recipients poll known peers and list
     * their `.../{my_scope}/` directory to discover pending requests.
     *
     * @param peerPubkey The pubkey of the peer whose storage to poll
     * @param myPubkey Our pubkey (used for scope computation)
     * @return List of discovered requests addressed to us
     */
    suspend fun discoverPendingRequestsFromPeer(
        peerPubkey: String,
        myPubkey: String,
    ): List<to.bitkit.paykit.workers.DiscoveredRequest> {
        val adapter = pubkyStorage.createUnauthenticatedAdapter(homeserverURL)
        val myScope = PaykitV0Protocol.recipientScope(myPubkey)
        val requestsPath = "${PaykitV0Protocol.PAYKIT_V0_PREFIX}/${PaykitV0Protocol.REQUESTS_SUBPATH}/$myScope/"

        return try {
            val requestFiles = pubkyStorage.listDirectory(requestsPath, adapter, peerPubkey)

            requestFiles.mapNotNull { requestId ->
                try {
                    val requestPath = "$requestsPath$requestId"
                    val envelopeBytes = pubkyStorage.retrieve(requestPath, adapter, peerPubkey)
                    val envelopeJson = envelopeBytes?.let { String(it) }
                    decryptAndParsePaymentRequest(requestId, envelopeJson, myPubkey)
                } catch (e: Exception) {
                    Logger.error("Failed to parse request $requestId", e, context = TAG)
                    null
                }
            }
        } catch (e: Exception) {
            Logger.error("Failed to discover requests from $peerPubkey", e, context = TAG)
            emptyList()
        }
    }

    /**
     * Discover pending payment requests from the directory.
     *
     * DEPRECATED: This method lists our own storage which is the wrong model.
     * Use [discoverPendingRequestsFromPeer] to poll each known contact's storage.
     *
     * For backwards compatibility, this method will still work if requests are
     * addressed to our raw pubkey (legacy format).
     */
    @Deprecated("Use discoverPendingRequestsFromPeer to poll known contacts")
    suspend fun discoverPendingRequests(ownerPubkey: String): List<to.bitkit.paykit.workers.DiscoveredRequest> {
        val adapter = pubkyStorage.createUnauthenticatedAdapter(homeserverURL)
        val scope = PaykitV0Protocol.recipientScope(ownerPubkey)
        val requestsPath = "${PaykitV0Protocol.PAYKIT_V0_PREFIX}/${PaykitV0Protocol.REQUESTS_SUBPATH}/$scope/"

        return try {
            val requestFiles = pubkyStorage.listDirectory(requestsPath, adapter, ownerPubkey)

            requestFiles.mapNotNull { requestId ->
                try {
                    val requestPath = "$requestsPath$requestId"
                    val envelopeBytes = pubkyStorage.retrieve(requestPath, adapter, ownerPubkey)
                    val envelopeJson = envelopeBytes?.let { String(it) }
                    decryptAndParsePaymentRequest(requestId, envelopeJson, ownerPubkey)
                } catch (e: Exception) {
                    Logger.error("Failed to parse request $requestId", e, context = TAG)
                    null
                }
            }
        } catch (e: Exception) {
            Logger.error("Failed to discover pending requests for $ownerPubkey", e, context = TAG)
            emptyList()
        }
    }

    /**
     * Discover subscription proposals from a peer's storage.
     *
     * In the v0 provider-storage model, subscribers poll known providers and list
     * their `.../{my_scope}/` directory to discover pending proposals.
     *
     * @param peerPubkey The pubkey of the peer (provider) whose storage to poll
     * @param myPubkey Our pubkey (used for scope computation)
     * @return List of discovered proposals addressed to us
     */
    suspend fun discoverSubscriptionProposalsFromPeer(
        peerPubkey: String,
        myPubkey: String,
    ): List<to.bitkit.paykit.workers.DiscoveredSubscriptionProposal> {
        val adapter = pubkyStorage.createUnauthenticatedAdapter(homeserverURL)
        val myScope = PaykitV0Protocol.subscriberScope(myPubkey)
        val proposalsPath =
            "${PaykitV0Protocol.PAYKIT_V0_PREFIX}/${PaykitV0Protocol.SUBSCRIPTION_PROPOSALS_SUBPATH}/$myScope/"

        return try {
            val proposalFiles = pubkyStorage.listDirectory(proposalsPath, adapter, peerPubkey)

            proposalFiles.mapNotNull { proposalId ->
                try {
                    val proposalPath = "$proposalsPath$proposalId"
                    val envelopeBytes = pubkyStorage.retrieve(proposalPath, adapter, peerPubkey)
                    val envelopeJson = envelopeBytes?.let { String(it) }
                    decryptAndParseSubscriptionProposal(proposalId, envelopeJson, myPubkey, peerPubkey)
                } catch (e: Exception) {
                    Logger.error("Failed to parse proposal $proposalId", e, context = TAG)
                    null
                }
            }
        } catch (e: Exception) {
            Logger.error("Failed to discover proposals from $peerPubkey", e, context = TAG)
            emptyList()
        }
    }

    /**
     * Discover subscription proposals from the directory.
     *
     * DEPRECATED: This method uses the wrong storage model.
     * Use [discoverSubscriptionProposalsFromPeer] to poll each known provider's storage.
     */
    @Deprecated("Use discoverSubscriptionProposalsFromPeer to poll known providers")
    suspend fun discoverSubscriptionProposals(
        ownerPubkey: String,
    ): List<to.bitkit.paykit.workers.DiscoveredSubscriptionProposal> {
        val adapter = pubkyStorage.createUnauthenticatedAdapter(homeserverURL)
        val scope = PaykitV0Protocol.subscriberScope(ownerPubkey)
        val proposalsPath =
            "${PaykitV0Protocol.PAYKIT_V0_PREFIX}/${PaykitV0Protocol.SUBSCRIPTION_PROPOSALS_SUBPATH}/$scope/"

        return try {
            val proposalFiles = pubkyStorage.listDirectory(proposalsPath, adapter, ownerPubkey)

            proposalFiles.mapNotNull { proposalId ->
                try {
                    val proposalPath = "$proposalsPath$proposalId"
                    val envelopeBytes = pubkyStorage.retrieve(proposalPath, adapter, ownerPubkey)
                    val envelopeJson = envelopeBytes?.let { String(it) }
                    // NOTE: This deprecated method cannot verify provider binding since we don't know who we're polling
                    decryptAndParseSubscriptionProposal(proposalId, envelopeJson, ownerPubkey, expectedProviderPubkey = null)
                } catch (e: Exception) {
                    Logger.error("Failed to parse proposal $proposalId", e, context = TAG)
                    null
                }
            }
        } catch (e: Exception) {
            Logger.error("Failed to discover subscription proposals for $ownerPubkey", e, context = TAG)
            emptyList()
        }
    }

    /**
     * Publish a subscription proposal to our storage for the subscriber to discover.
     * The proposal is stored ENCRYPTED at the canonical v0 path:
     * `/pub/paykit.app/v0/subscriptions/proposals/{subscriber_scope}/{proposalId}`
     *
     * SECURITY: Proposals are encrypted using Sealed Blob v1 to subscriber's Noise public key.
     * Uses canonical AAD format: `paykit:v0:subscription_proposal:{path}:{proposalId}`
     *
     * @param proposal The subscription proposal to publish
     * @param subscriberPubkey The z32 pubkey of the subscriber
     * @throws DirectoryError.NotConfigured if session is not configured
     * @throws DirectoryError.PublishFailed if the publish operation fails
     * @throws DirectoryError.EncryptionFailed if encryption fails (e.g., subscriber has no Noise endpoint)
     */
    suspend fun publishSubscriptionProposal(
        proposal: to.bitkit.paykit.models.SubscriptionProposal,
        subscriberPubkey: String,
    ) {
        // Auto-restore from keychain if not configured
        if (!isConfigured) tryRestoreFromKeychain()
        val adapter = authenticatedAdapter ?: throw DirectoryError.NotConfigured

        // Use canonical v0 path (scope-based)
        val proposalPath = PaykitV0Protocol.subscriptionProposalPath(subscriberPubkey, proposal.id)

        val proposalJson = org.json.JSONObject().apply {
            put("provider_pubkey", proposal.providerPubkey)
            proposal.providerName?.let { put("provider_name", it) }
            put("amount_sats", proposal.amountSats)
            put("currency", proposal.currency)
            put("frequency", proposal.frequency)
            proposal.description?.let { put("description", it) }
            put("method_id", proposal.methodId)
            put("created_at", proposal.createdAt)
        }

        // Discover subscriber's Noise endpoint to get their public key for encryption
        val subscriberNoiseEndpoint = discoverNoiseEndpoint(subscriberPubkey)
            ?: throw DirectoryError.EncryptionFailed("Subscriber has no Noise endpoint published")

        // Encrypt proposal using Sealed Blob v1 with canonical AAD
        val plaintextBytes = proposalJson.toString().toByteArray(Charsets.UTF_8)
        val subscriberNoisePkBytes = PubkyRingBridge.hexStringToByteArray(subscriberNoiseEndpoint.serverNoisePubkey)
        val aad = PaykitV0Protocol.subscriptionProposalAad(subscriberPubkey, proposal.id)

        val encryptedEnvelope = try {
            com.pubky.noise.sealedBlobEncrypt(
                subscriberNoisePkBytes,
                plaintextBytes,
                aad,
                PaykitV0Protocol.PURPOSE_SUBSCRIPTION_PROPOSAL,
            )
        } catch (e: Exception) {
            Logger.error("Failed to encrypt subscription proposal", e, context = TAG)
            throw DirectoryError.EncryptionFailed("Encryption failed: ${e.message}")
        }

        val result = adapter.put(proposalPath, encryptedEnvelope)
        if (!result.success) {
            Logger.error("Failed to publish subscription proposal: ${result.error}", context = TAG)
            throw DirectoryError.PublishFailed(result.error ?: "Unknown error")
        }
        Logger.info("Published encrypted subscription proposal ${proposal.id} to $subscriberPubkey", context = TAG)
    }

    /**
     * Fetch a specific subscription proposal by ID from a provider's storage.
     *
     * @param proposalId The proposal ID
     * @param providerPubkey The z32 pubkey of the provider who stored the proposal
     * @param subscriberPubkey Our pubkey (used for scope computation and decryption)
     * @return The discovered proposal or null if not found
     */
    suspend fun fetchSubscriptionProposal(
        proposalId: String,
        providerPubkey: String,
        subscriberPubkey: String,
    ): to.bitkit.paykit.workers.DiscoveredSubscriptionProposal? {
        val adapter = pubkyStorage.createUnauthenticatedAdapter(homeserverURL)
        val proposalPath = PaykitV0Protocol.subscriptionProposalPath(subscriberPubkey, proposalId)

        return try {
            val envelopeBytes = pubkyStorage.retrieve(proposalPath, adapter, providerPubkey)
            val envelopeJson = envelopeBytes?.let { String(it) }
            decryptAndParseSubscriptionProposal(proposalId, envelopeJson, subscriberPubkey, providerPubkey)
        } catch (e: Exception) {
            Logger.error("Failed to fetch subscription proposal $proposalId", e, context = TAG)
            null
        }
    }

    /**
     * Remove a subscription proposal from OUR storage.
     *
     * NOTE: In the v0 provider-storage model, proposals are stored on the **provider's** homeserver.
     * Only the provider can delete proposals from their own storage. Subscribers cannot delete.
     *
     * This method can only be called by the **provider** to remove proposals they published.
     *
     * @param proposalId The proposal ID to remove
     * @param subscriberPubkey The subscriber pubkey (used for scope computation)
     * @throws DirectoryError.NotConfigured if session is not configured
     * @throws DirectoryError.PublishFailed if the delete operation fails
     */
    @Deprecated(
        message = "Subscribers cannot delete proposals from provider storage. Use local deduplication instead.",
        level = DeprecationLevel.WARNING,
    )
    suspend fun removeSubscriptionProposal(
        proposalId: String,
        subscriberPubkey: String,
    ) {
        // Auto-restore from keychain if not configured
        if (!isConfigured) tryRestoreFromKeychain()
        val adapter = authenticatedAdapter ?: throw DirectoryError.NotConfigured

        val proposalPath = PaykitV0Protocol.subscriptionProposalPath(subscriberPubkey, proposalId)

        val result = adapter.delete(proposalPath)
        if (!result.success) {
            Logger.error("Failed to remove subscription proposal: ${result.error}", context = TAG)
            throw DirectoryError.PublishFailed(result.error ?: "Unknown error")
        }
        Logger.info("Removed subscription proposal $proposalId", context = TAG)
    }

    /**
     * Decrypt and parse a payment request from an encrypted sealed blob.
     *
     * @param requestId The request ID
     * @param envelopeJson The JSON string of the sealed blob (or null)
     * @param recipientPubkey Our pubkey (used for canonical AAD computation)
     * @return The parsed request or null if decryption/parsing fails
     */
    private fun decryptAndParsePaymentRequest(
        requestId: String,
        envelopeJson: String?,
        recipientPubkey: String,
    ): to.bitkit.paykit.workers.DiscoveredRequest? {
        if (envelopeJson.isNullOrBlank()) return null

        // Verify it's an encrypted sealed blob
        if (!com.pubky.noise.isSealedBlob(envelopeJson)) {
            Logger.error("Payment request $requestId is not encrypted (sealed blob required)", context = TAG)
            return null
        }

        // Get our Noise secret key for decryption
        val noiseKeypair = keyManager.getCachedNoiseKeypair()
        if (noiseKeypair == null) {
            Logger.error("No Noise keypair available for decryption", context = TAG)
            return null
        }

        val myNoiseSk = PubkyRingBridge.hexStringToByteArray(noiseKeypair.secretKeyHex)
        val aad = PaykitV0Protocol.paymentRequestAad(recipientPubkey, requestId)

        return try {
            val plaintextBytes = com.pubky.noise.sealedBlobDecrypt(myNoiseSk, envelopeJson, aad)
            val plaintextJson = String(plaintextBytes)
            val obj = org.json.JSONObject(plaintextJson)

            to.bitkit.paykit.workers.DiscoveredRequest(
                requestId = requestId,
                type = to.bitkit.paykit.workers.RequestType.PaymentRequest,
                fromPubkey = obj.optString("from_pubkey", ""),
                amountSats = obj.optLong("amount_sats", 0),
                description = if (obj.has("description")) obj.getString("description") else null,
                createdAt = obj.optLong("created_at", System.currentTimeMillis()),
            )
        } catch (e: Exception) {
            Logger.error("Failed to decrypt/parse payment request $requestId", e, context = TAG)
            null
        }
    }

    /**
     * Decrypt and parse a subscription proposal from an encrypted sealed blob.
     *
     * @param proposalId The proposal ID
     * @param envelopeJson The JSON string of the sealed blob (or null)
     * @param subscriberPubkey Our pubkey (used for canonical AAD computation)
     * @param expectedProviderPubkey If provided, verifies that provider_pubkey in the proposal matches this value
     * @return The parsed proposal or null if decryption/parsing/validation fails
     */
    private fun decryptAndParseSubscriptionProposal(
        proposalId: String,
        envelopeJson: String?,
        subscriberPubkey: String,
        expectedProviderPubkey: String? = null,
    ): to.bitkit.paykit.workers.DiscoveredSubscriptionProposal? {
        if (envelopeJson.isNullOrBlank()) return null

        // Verify it's an encrypted sealed blob
        if (!com.pubky.noise.isSealedBlob(envelopeJson)) {
            Logger.error("Subscription proposal $proposalId is not encrypted (sealed blob required)", context = TAG)
            return null
        }

        // Get our Noise secret key for decryption
        val noiseKeypair = keyManager.getCachedNoiseKeypair()
        if (noiseKeypair == null) {
            Logger.error("No Noise keypair available for decryption", context = TAG)
            return null
        }

        val myNoiseSk = PubkyRingBridge.hexStringToByteArray(noiseKeypair.secretKeyHex)
        val aad = PaykitV0Protocol.subscriptionProposalAad(subscriberPubkey, proposalId)

        return try {
            val plaintextBytes = com.pubky.noise.sealedBlobDecrypt(myNoiseSk, envelopeJson, aad)
            val plaintextJson = String(plaintextBytes)
            val obj = org.json.JSONObject(plaintextJson)

            val providerPubkey = obj.optString("provider_pubkey", "")

            // SECURITY: Verify provider identity binding
            if (expectedProviderPubkey != null && providerPubkey.isNotEmpty()) {
                val normalizedExpected = PaykitV0Protocol.normalizePubkeyZ32(expectedProviderPubkey)
                val normalizedActual = PaykitV0Protocol.normalizePubkeyZ32(providerPubkey)
                if (normalizedExpected != normalizedActual) {
                    Logger.error(
                        "Provider identity mismatch for proposal $proposalId: expected $normalizedExpected, got $normalizedActual",
                        context = TAG,
                    )
                    return null
                }
            }

            to.bitkit.paykit.workers.DiscoveredSubscriptionProposal(
                subscriptionId = proposalId,
                providerPubkey = providerPubkey,
                amountSats = obj.optLong("amount_sats", 0),
                description = if (obj.has("description")) obj.getString("description") else null,
                frequency = obj.optString("frequency", "monthly"),
                createdAt = obj.optLong("created_at", System.currentTimeMillis()),
            )
        } catch (e: Exception) {
            Logger.error("Failed to decrypt/parse subscription proposal $proposalId", e, context = TAG)
            null
        }
    }

    // MARK: - Profile Operations

    /**
     * Fetch profile for a pubkey from Pubky directory
     * Uses PubkySDKService first, falls back to direct FFI if unavailable
     */
    suspend fun fetchProfile(pubkey: String, context: Context? = null): PubkyProfile? {
        // Try PubkySDKService first (preferred, direct homeserver access)
        try {
            val sdkProfile = pubkySDKService.fetchProfile(pubkey)
            // Convert to local PubkyProfile type
            return PubkyProfile(
                name = sdkProfile.name,
                bio = sdkProfile.bio,
                image = sdkProfile.image,
                links = sdkProfile.links?.map { PubkyProfileLink(title = it.title, url = it.url) },
            )
        } catch (e: Exception) {
            Logger.debug("PubkySDKService profile fetch failed: ${e.message}", context = TAG)
        }
        
        // Try PubkyRingBridge if Pubky-ring is installed and context is provided
        if (context != null && pubkyRingBridge.isPubkyRingInstalled(context)) {
            try {
                val profile = pubkyRingBridge.requestProfile(context, pubkey)
                if (profile != null) {
                    Logger.debug("Got profile from Pubky-ring", context = TAG)
                    return profile
                }
            } catch (e: Exception) {
                Logger.debug("PubkyRingBridge profile fetch failed: ${e.message}", context = TAG)
            }
        }

        // Fallback to direct FFI
        return fetchProfileViaFFI(pubkey)
    }

    /**
     * Fetch profile using direct FFI (fallback)
     */
    private suspend fun fetchProfileViaFFI(pubkey: String): PubkyProfile? {
        val adapter = pubkyStorage.createUnauthenticatedAdapter(homeserverURL)
        val profilePath = "/pub/pubky.app/profile.json"

        return try {
            val data = pubkyStorage.retrieve(profilePath, adapter, pubkey)
            if (data != null) {
                val json = org.json.JSONObject(String(data))
                val links = json.optJSONArray("links")?.let { linksArray ->
                    (0 until linksArray.length()).mapNotNull { i ->
                        val linkObj = linksArray.optJSONObject(i)
                        if (linkObj != null) {
                            PubkyProfileLink(
                                title = linkObj.optString("title", ""),
                                url = linkObj.optString("url", "")
                            )
                        } else {
                            null
                        }
                    }
                }
                PubkyProfile(
                    name = json.optString("name").takeIf { it.isNotEmpty() },
                    bio = json.optString("bio").takeIf { it.isNotEmpty() },
                    image = json.optString("image").takeIf { it.isNotEmpty() },
                    links = links,
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.error("Failed to fetch profile for $pubkey", e, context = TAG)
            null
        }
    }

    /**
     * Publish profile to Pubky directory
     */
    suspend fun publishProfile(profile: PubkyProfile) {
        // Auto-restore from keychain if not configured
        if (!isConfigured) tryRestoreFromKeychain()
        val adapter = authenticatedAdapter ?: throw DirectoryError.NotConfigured
        val profilePath = "/pub/pubky.app/profile.json"

        val profileJson = org.json.JSONObject().apply {
            profile.name?.let { put("name", it) }
            profile.bio?.let { put("bio", it) }
            profile.image?.let { put("image", it) }
            profile.links?.let { links ->
                val linksArray = org.json.JSONArray()
                links.forEach { link ->
                    linksArray.put(
                        org.json.JSONObject().apply {
                            put("title", link.title)
                            put("url", link.url)
                        }
                    )
                }
                put("links", linksArray)
            }
        }

        val result = adapter.put(profilePath, profileJson.toString())
        if (!result.success) {
            Logger.error("Failed to publish profile: ${result.error}", context = TAG)
            throw DirectoryError.PublishFailed(result.error ?: "Unknown error")
        }
        Logger.info("Published profile to Pubky directory", context = TAG)
    }

    /**
     * Publish profile to Pubky directory with verification.
     * After publishing, performs a best-effort read to confirm the profile was written.
     *
     * @param profile The profile to publish
     * @return true if profile was verified, false if verification failed (but publish may have succeeded)
     */
    suspend fun publishProfileWithVerification(profile: PubkyProfile): Boolean {
        publishProfile(profile)

        // Verify by reading back (best effort)
        val ownerPubkey = keychainStorage.getString(KEY_PUBLIC) ?: return true
        return try {
            kotlinx.coroutines.delay(500) // Brief delay for propagation
            val fetched = fetchProfileViaFFI(ownerPubkey)
            val verified = fetched?.name == profile.name
            if (verified) {
                Logger.debug("Profile verification succeeded", context = TAG)
            } else {
                Logger.warn("Profile verification: name mismatch", context = TAG)
            }
            verified
        } catch (e: Exception) {
            Logger.warn("Profile verification failed: ${e.message}", context = TAG)
            true // Assume success if verification fails
        }
    }

    // MARK: - Follows Operations

    /**
     * Fetch list of pubkeys user follows
     * Uses PubkySDKService first, falls back to direct FFI if unavailable
     */
    suspend fun fetchFollows(context: Context? = null): List<String> {
        val ownerPubkey = keyManager.getCurrentPublicKeyZ32() ?: return emptyList()

        // Try PubkySDKService first (preferred, direct homeserver access)
        try {
            return pubkySDKService.fetchFollows(ownerPubkey)
        } catch (e: Exception) {
            Logger.debug("PubkySDKService follows fetch failed: ${e.message}", context = TAG)
        }
        
        // Try PubkyRingBridge if Pubky-ring is installed and context is provided
        if (context != null && pubkyRingBridge.isPubkyRingInstalled(context)) {
            try {
                val follows = pubkyRingBridge.requestFollows(context)
                if (follows.isNotEmpty()) {
                    Logger.debug("Got ${follows.size} follows from Pubky-ring", context = TAG)
                    return follows
                }
            } catch (e: Exception) {
                Logger.debug("PubkyRingBridge follows fetch failed: ${e.message}", context = TAG)
            }
        }

        // Fallback to direct FFI
        return fetchFollowsViaFFI(ownerPubkey)
    }

    /**
     * Fetch follows using direct FFI (fallback)
     */
    private suspend fun fetchFollowsViaFFI(ownerPubkey: String): List<String> {
        val adapter = pubkyStorage.createUnauthenticatedAdapter(homeserverURL)
        val followsPath = "/pub/pubky.app/follows/"

        return try {
            pubkyStorage.listDirectory(followsPath, adapter, ownerPubkey)
        } catch (e: Exception) {
            Logger.error("Failed to fetch follows", e, context = TAG)
            emptyList()
        }
    }

    /**
     * Add a follow to the Pubky directory
     * Per pubky-app-specs, follows require a created_at timestamp
     */
    suspend fun addFollow(pubkey: String) {
        // Auto-restore from keychain if not configured
        if (!isConfigured) tryRestoreFromKeychain()
        val adapter = authenticatedAdapter ?: throw DirectoryError.NotConfigured
        val followPath = "/pub/pubky.app/follows/$pubkey"

        // Per pubky-app-specs: PubkyAppFollow requires created_at (Unix timestamp in microseconds)
        val createdAt = System.currentTimeMillis() * 1000 // Convert millis to micros
        val followJson = """{"created_at":$createdAt}"""

        val result = adapter.put(followPath, followJson)
        if (!result.success) {
            Logger.error("Failed to add follow $pubkey: ${result.error}", context = TAG)
            throw DirectoryError.PublishFailed(result.error ?: "Unknown error")
        }
        Logger.info("Added follow: $pubkey", context = TAG)
    }

    /**
     * Remove a follow from the Pubky directory
     */
    suspend fun removeFollow(pubkey: String) {
        // Auto-restore from keychain if not configured
        if (!isConfigured) tryRestoreFromKeychain()
        val adapter = authenticatedAdapter ?: throw DirectoryError.NotConfigured
        val followPath = "/pub/pubky.app/follows/$pubkey"

        val result = adapter.delete(followPath)
        if (!result.success) {
            Logger.error("Failed to remove follow $pubkey: ${result.error}", context = TAG)
            throw DirectoryError.PublishFailed(result.error ?: "Unknown error")
        }
        Logger.info("Removed follow: $pubkey", context = TAG)
    }

    /**
     * Discover contacts from Pubky follows directory
     */
    suspend fun discoverContactsFromFollows(): List<DiscoveredContact> = withContext(Dispatchers.IO) {
        // Ensure session is restored from keychain (configures homeserverURL)
        if (!isConfigured) tryRestoreFromKeychain()

        // Use same keychain key as tryRestoreFromKeychain to get the stored pubkey
        val ownerPubkey = keychainStorage.getString(KEY_PUBLIC) ?: run {
            Logger.warn("No pubkey found in keychain for discovery", context = TAG)
            return@withContext emptyList()
        }

        Logger.debug("Starting contact discovery for $ownerPubkey", context = TAG)

        // Create unauthenticated adapter for reading follows (using configured homeserverURL)
        val unauthAdapter = pubkyStorage.createUnauthenticatedAdapter(homeserverURL)

        // Fetch follows list from Pubky
        val followsPath = "/pub/pubky.app/follows/"
        val followsList = runCatching {
            pubkyStorage.listDirectory(followsPath, unauthAdapter, ownerPubkey)
        }.getOrElse { e ->
            Logger.error("Failed to list follows: ${e.message}", context = TAG)
            return@withContext emptyList()
        }

        Logger.debug("Found ${followsList.size} follows: $followsList", context = TAG)

        val discovered = mutableListOf<DiscoveredContact>()

        for (followPubkey in followsList) {
            // Fetch profile to get name (best effort)
            val profile = runCatching {
                pubkySDKService.fetchProfile(followPubkey)
            }.getOrNull()

            // Check if this follow has payment methods
            val paymentMethods = discoverPaymentMethods(followPubkey)

            // Include contact if they have a profile OR payment methods
            if (profile != null || paymentMethods.isNotEmpty()) {
                discovered.add(
                    DiscoveredContact(
                        pubkey = followPubkey,
                        name = profile?.name,
                        hasPaymentMethods = paymentMethods.isNotEmpty(),
                        supportedMethods = paymentMethods.map { it.methodId },
                    )
                )
            }
        }

        Logger.debug("Discovered ${discovered.size} contacts", context = TAG)
        discovered
    }
}

/**
 * Profile from Pubky directory
 */
data class PubkyProfile(
    val name: String? = null,
    val bio: String? = null,
    val image: String? = null,
    val links: List<PubkyProfileLink>? = null,
)

data class PubkyProfileLink(
    val title: String,
    val url: String
)

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
    class EncryptionFailed(msg: String) : DirectoryError("Encryption failed: $msg")
}
