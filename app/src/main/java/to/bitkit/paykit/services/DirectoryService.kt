package to.bitkit.paykit.services

import android.content.Context
import uniffi.paykit_mobile.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import to.bitkit.paykit.KeyManager
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
 * Configuration for Pubky homeserver connections
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
    const val PAYKIT_PATH_PREFIX = "/pub/paykit.app/v0/"
    const val PAYMENT_REQUESTS_PATH = "/pub/paykit.app/v0/requests/"

    /**
     * Get the homeserver base URL for directory operations
     */
    fun homeserverUrl(homeserver: String = DEFAULT_HOMESERVER): String {
        // The homeserver pubkey is used as the base for directory operations
        return homeserver
    }

    /**
     * Get the path for a payment request addressed to a recipient.
     * Path format: /pub/paykit.app/v0/requests/{recipientPubkey}/{requestId}
     */
    fun paymentRequestPath(recipientPubkey: String, requestId: String): String =
        "${PAYMENT_REQUESTS_PATH}$recipientPubkey/$requestId"

    @Deprecated(
        "Use paymentRequestPath(recipientPubkey, requestId) for proper request addressing",
        ReplaceWith("paymentRequestPath(recipientPubkey, requestId)")
    )
    fun paymentRequestPath(requestId: String): String = "${PAYMENT_REQUESTS_PATH}$requestId"
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
     */
    fun configureWithPubkySession(session: PubkySession) {
        homeserverURL = HomeserverDefaults.defaultHomeserverURL

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

        configureWithPubkySession(
            PubkySession(
                pubkey = storedPubkey,
                sessionSecret = sessionSecret,
                capabilities = listOf("read", "write"),
                createdAt = java.util.Date(),
            )
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
     * Stores the request at: /pub/paykit.app/v0/requests/{recipientPubkey}/{requestId}
     * on the sender's homeserver so the recipient can fetch it later.
     *
     * @param request The payment request to publish
     * @param recipientPubkey The pubkey of the recipient (who should process the request)
     */
    suspend fun publishPaymentRequest(
        request: to.bitkit.paykit.models.PaymentRequest,
        recipientPubkey: String,
    ) {
        val transport = authenticatedTransport ?: throw DirectoryError.NotConfigured

        val requestJson = kotlinx.serialization.json.Json.encodeToString(
            to.bitkit.paykit.models.PaymentRequest.serializer(),
            request
        )

        val path = PubkyConfig.paymentRequestPath(recipientPubkey, request.id)
        try {
            pubkyStorage.store(path, requestJson.toByteArray(), transport)
            Logger.info("Published payment request: ${request.id} to $recipientPubkey", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to publish payment request ${request.id}", e, context = TAG)
            throw DirectoryError.PublishFailed(e.message ?: "Unknown error")
        }
    }

    /**
     * Fetch a payment request from a sender's Pubky storage.
     * Retrieves from: pubky://{senderPubkey}/pub/paykit.app/v0/requests/{recipientPubkey}/{requestId}
     *
     * @param requestId The unique request ID
     * @param senderPubkey The pubkey of the sender (who published the request)
     * @param recipientPubkey The pubkey of the recipient (owner of the requests directory)
     */
    suspend fun fetchPaymentRequest(
        requestId: String,
        senderPubkey: String,
        recipientPubkey: String,
    ): to.bitkit.paykit.models.PaymentRequest? {
        val path = PubkyConfig.paymentRequestPath(recipientPubkey, requestId)
        
        // Use the proper pubky:// URI which uses DHT/Pkarr resolution
        val pubkyUri = "pubky://$senderPubkey$path"
        Logger.debug("Fetching payment request from: $pubkyUri", context = TAG)

        return try {
            val requestBytes = pubkySDKService.getData(pubkyUri)
            if (requestBytes != null) {
                val requestJson = String(requestBytes)
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
     * @param requestId The unique request ID
     * @param recipientPubkey The pubkey of the recipient (owner of the requests directory)
     */
    suspend fun removePaymentRequest(requestId: String, recipientPubkey: String) {
        val transport = authenticatedTransport ?: throw DirectoryError.NotConfigured
        val path = PubkyConfig.paymentRequestPath(recipientPubkey, requestId)

        try {
            pubkyStorage.delete(path, transport)
            Logger.info("Removed payment request: $requestId from $recipientPubkey", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to remove payment request $requestId", e, context = TAG)
            throw DirectoryError.PublishFailed(e.message ?: "Unknown error")
        }
    }

    /**
     * Discover pending payment requests from the directory
     */
    suspend fun discoverPendingRequests(ownerPubkey: String): List<to.bitkit.paykit.workers.DiscoveredRequest> {
        val adapter = pubkyStorage.createUnauthenticatedAdapter(homeserverURL)

        return try {
            // List pending requests from the requests directory
            val requestsPath = "${PAYKIT_PATH_PREFIX}requests/$ownerPubkey/"
            val requestFiles = pubkyStorage.listDirectory(requestsPath, adapter, ownerPubkey)

            requestFiles.mapNotNull { requestId ->
                try {
                    val requestPath = "$requestsPath$requestId"
                    val requestBytes = pubkyStorage.retrieve(requestPath, adapter, ownerPubkey)
                    val requestJson = requestBytes?.let { String(it) }
                    parsePaymentRequest(requestId, requestJson)
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
     * Discover subscription proposals from the directory
     */
    suspend fun discoverSubscriptionProposals(
        ownerPubkey: String
    ): List<to.bitkit.paykit.workers.DiscoveredSubscriptionProposal> {
        val adapter = pubkyStorage.createUnauthenticatedAdapter(homeserverURL)

        return try {
            // List subscription proposals from the subscriptions directory
            val proposalsPath = "${PAYKIT_PATH_PREFIX}subscriptions/proposals/$ownerPubkey/"
            val proposalFiles = pubkyStorage.listDirectory(proposalsPath, adapter, ownerPubkey)

            proposalFiles.mapNotNull { proposalId ->
                try {
                    val proposalPath = "$proposalsPath$proposalId"
                    val proposalBytes = pubkyStorage.retrieve(proposalPath, adapter, ownerPubkey)
                    val proposalJson = proposalBytes?.let { String(it) }
                    parseSubscriptionProposal(proposalId, proposalJson)
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

    private fun parsePaymentRequest(requestId: String, json: String?): to.bitkit.paykit.workers.DiscoveredRequest? {
        if (json.isNullOrBlank()) return null

        return try {
            val jsonObject = org.json.JSONObject(json)
            to.bitkit.paykit.workers.DiscoveredRequest(
                requestId = requestId,
                type = to.bitkit.paykit.workers.RequestType.PaymentRequest,
                fromPubkey = jsonObject.optString("from_pubkey", ""),
                amountSats = jsonObject.optLong("amount_sats", 0),
                description = if (jsonObject.has("description")) jsonObject.getString("description") else null,
                createdAt = jsonObject.optLong("created_at", System.currentTimeMillis()),
            )
        } catch (e: Exception) {
            Logger.error("Failed to parse payment request JSON", e, context = TAG)
            null
        }
    }

    private fun parseSubscriptionProposal(proposalId: String, json: String?): to.bitkit.paykit.workers.DiscoveredSubscriptionProposal? {
        if (json.isNullOrBlank()) return null

        return try {
            val jsonObject = org.json.JSONObject(json)
            to.bitkit.paykit.workers.DiscoveredSubscriptionProposal(
                subscriptionId = proposalId,
                providerPubkey = jsonObject.optString("provider_pubkey", ""),
                amountSats = jsonObject.optLong("amount_sats", 0),
                description = if (jsonObject.has("description")) jsonObject.getString("description") else null,
                frequency = jsonObject.optString("frequency", "monthly"),
                createdAt = jsonObject.optLong("created_at", System.currentTimeMillis()),
            )
        } catch (e: Exception) {
            Logger.error("Failed to parse subscription proposal JSON", e, context = TAG)
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
}
