package to.bitkit.paykit.services

import android.content.Context
import uniffi.paykit_mobile.*
import dagger.hilt.android.qualifiers.ApplicationContext
import to.bitkit.paykit.KeyManager
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
     * Get the path for a payment request
     */
    fun paymentRequestPath(requestId: String): String = "${PAYMENT_REQUESTS_PATH}$requestId"
}

/**
 * Service for interacting with the Pubky directory
 * Uses PaykitClient FFI methods for directory operations
 */
@Singleton
class DirectoryService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: KeyManager,
    private val pubkyStorage: PubkyStorageAdapter,
    private val pubkySDKService: PubkySDKService,
    private val pubkyRingBridge: PubkyRingBridge,
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
     * @param homeserverBaseURL The homeserver pubkey (defaults to PubkyConfig.DEFAULT_HOMESERVER)
     */
    fun configurePubkyTransport(homeserverBaseURL: String? = null) {
        this.homeserverBaseURL = homeserverBaseURL ?: PubkyConfig.DEFAULT_HOMESERVER
        val adapter = PubkyUnauthenticatedStorageAdapter(this.homeserverBaseURL)
        unauthenticatedTransport = UnauthenticatedTransportFfi.fromCallback(adapter)
    }

    /**
     * Configure authenticated transport with session
     * @param sessionId The session ID from Pubky-ring
     * @param ownerPubkey The owner's public key
     * @param homeserverBaseURL The homeserver pubkey (defaults to PubkyConfig.DEFAULT_HOMESERVER)
     */
    fun configureAuthenticatedTransport(sessionId: String, ownerPubkey: String, homeserverBaseURL: String? = null) {
        this.homeserverBaseURL = homeserverBaseURL ?: PubkyConfig.DEFAULT_HOMESERVER
        val adapter = PubkyAuthenticatedStorageAdapter(sessionId, this.homeserverBaseURL)
        authenticatedTransport = AuthenticatedTransportFfi.fromCallback(adapter, ownerPubkey)
    }

    /**
     * Configure transport using a Pubky session from Pubky-ring
     */
    fun configureWithPubkySession(session: PubkySession) {
        homeserverBaseURL = PubkyConfig.DEFAULT_HOMESERVER

        // Configure authenticated transport
        val adapter = PubkyAuthenticatedStorageAdapter(session.sessionSecret, homeserverBaseURL)
        authenticatedTransport = AuthenticatedTransportFfi.fromCallback(adapter, session.pubkey)

        // Also configure unauthenticated transport
        val unauthAdapter = PubkyUnauthenticatedStorageAdapter(homeserverBaseURL)
        unauthenticatedTransport = UnauthenticatedTransportFfi.fromCallback(unauthAdapter)

        Logger.info("Configured DirectoryService with Pubky session for ${session.pubkey}", context = TAG)
    }

    /**
     * Discover noise endpoint for a recipient
     */
    suspend fun discoverNoiseEndpoint(recipientPubkey: String): NoiseEndpointInfo? {
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

    // MARK: - Push Notification Endpoints

    /**
     * Push endpoint for receiving wake notifications
     */
    @kotlinx.serialization.Serializable
    data class PushNotificationEndpoint(
        val deviceToken: String,
        val platform: String,  // "ios" or "android"
        val noiseHost: String? = null,
        val noisePort: Int? = null,
        val noisePubkey: String? = null,
        val createdAt: Long = System.currentTimeMillis() / 1000
    )

    /**
     * Publish our push notification endpoint to the directory.
     * This allows other users to discover how to wake our device for Noise connections.
     *
     * @deprecated This publishes tokens publicly, enabling DoS attacks.
     *   Use [PushRelayService.register] instead for secure push token registration.
     *   This method will be removed in a future release.
     */
    @Deprecated(
        message = "Use PushRelayService.register() for secure push registration",
        replaceWith = ReplaceWith("PushRelayService.register(deviceToken, listOf(\"wake\", \"payment_received\"))"),
    )
    suspend fun publishPushNotificationEndpoint(
        deviceToken: String,
        platform: String,
        noiseHost: String? = null,
        noisePort: Int? = null,
        noisePubkey: String? = null
    ) {
        val transport = authenticatedTransport ?: throw DirectoryError.NotConfigured

        val endpoint = PushNotificationEndpoint(
            deviceToken = deviceToken,
            platform = platform,
            noiseHost = noiseHost,
            noisePort = noisePort,
            noisePubkey = noisePubkey
        )

        val pushPath = "${PAYKIT_PATH_PREFIX}push"
        val json = kotlinx.serialization.json.Json.encodeToString(endpoint)

        try {
            pubkyStorage.store(pushPath, json.toByteArray(), transport)
            Logger.info("Published push notification endpoint to directory", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to publish push endpoint", e, context = TAG)
            throw DirectoryError.PublishFailed(e.message ?: "Unknown error")
        }
    }

    /**
     * Discover push notification endpoint for a recipient.
     * Used to send wake notifications before attempting Noise connections.
     *
     * @deprecated Use [PushRelayService.wake] instead.
     *   Direct discovery exposes tokens publicly. The push relay service
     *   handles routing without exposing tokens.
     */
    @Deprecated(
        message = "Use PushRelayService.wake() for secure wake notifications",
        replaceWith = ReplaceWith("PushRelayService.wake(recipientPubkey, WakeType.NOISE_CONNECT)"),
    )
    suspend fun discoverPushNotificationEndpoint(recipientPubkey: String): PushNotificationEndpoint? {
        val adapter = PubkyUnauthenticatedStorageAdapter(homeserverBaseURL)
        val pushPath = "${PAYKIT_PATH_PREFIX}push"

        return try {
            val data = pubkyStorage.retrieve(pushPath, adapter, recipientPubkey) ?: return null
            val json = String(data)
            kotlinx.serialization.json.Json.decodeFromString<PushNotificationEndpoint>(json)
        } catch (e: Exception) {
            Logger.error("Failed to discover push endpoint for $recipientPubkey", e, context = TAG)
            null
        }
    }

    /**
     * Remove our push notification endpoint from the directory.
     */
    suspend fun removePushNotificationEndpoint() {
        val transport = authenticatedTransport ?: throw DirectoryError.NotConfigured
        val pushPath = "${PAYKIT_PATH_PREFIX}push"

        try {
            pubkyStorage.delete(pushPath, transport)
            Logger.info("Removed push notification endpoint from directory", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to remove push endpoint", e, context = TAG)
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
            val adapter = PubkyUnauthenticatedStorageAdapter(homeserverBaseURL)
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
     * Stores the request at: /pub/paykit.app/v0/requests/{requestId}
     * on the sender's homeserver so the recipient can fetch it later.
     */
    suspend fun publishPaymentRequest(request: to.bitkit.paykit.models.PaymentRequest) {
        val transport = authenticatedTransport ?: throw DirectoryError.NotConfigured

        val requestJson = kotlinx.serialization.json.Json.encodeToString(
            to.bitkit.paykit.models.PaymentRequest.serializer(),
            request
        )

        val path = PubkyConfig.paymentRequestPath(request.id)
        try {
            pubkyStorage.store(path, requestJson.toByteArray(), transport)
            Logger.info("Published payment request: ${request.id}", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to publish payment request ${request.id}", e, context = TAG)
            throw DirectoryError.PublishFailed(e.message ?: "Unknown error")
        }
    }

    /**
     * Fetch a payment request from a sender's Pubky storage.
     * Retrieves from: pubky://{senderPubkey}/pub/paykit.app/v0/requests/{requestId}
     */
    suspend fun fetchPaymentRequest(
        requestId: String,
        senderPubkey: String
    ): to.bitkit.paykit.models.PaymentRequest? {
        val path = PubkyConfig.paymentRequestPath(requestId)
        
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
     * Remove a payment request from storage (after it's been processed)
     */
    suspend fun removePaymentRequest(requestId: String) {
        val transport = authenticatedTransport ?: throw DirectoryError.NotConfigured
        val path = PubkyConfig.paymentRequestPath(requestId)

        try {
            pubkyStorage.delete(path, transport)
            Logger.info("Removed payment request: $requestId", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to remove payment request $requestId", e, context = TAG)
            throw DirectoryError.PublishFailed(e.message ?: "Unknown error")
        }
    }

    /**
     * Discover pending payment requests from the directory
     */
    suspend fun discoverPendingRequests(ownerPubkey: String): List<to.bitkit.paykit.workers.DiscoveredRequest> {
        val adapter = PubkyUnauthenticatedStorageAdapter(homeserverBaseURL)

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
        val adapter = PubkyUnauthenticatedStorageAdapter(homeserverBaseURL)

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
                avatar = sdkProfile.image, // SDK uses 'image', DirectoryService uses 'avatar'
                links = sdkProfile.links?.map { PubkyProfileLink(title = it.title, url = it.url) }
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
        val adapter = pubkyStorage.createUnauthenticatedAdapter(homeserverBaseURL)
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
                    name = json.optString("name", null),
                    bio = json.optString("bio", null),
                    avatar = json.optString("avatar", null),
                    links = links
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
        val transport = authenticatedTransport ?: throw DirectoryError.NotConfigured
        val profilePath = "/pub/pubky.app/profile.json"

        val profileJson = org.json.JSONObject().apply {
            profile.name?.let { put("name", it) }
            profile.bio?.let { put("bio", it) }
            profile.avatar?.let { put("avatar", it) }
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

        try {
            pubkyStorage.store(profilePath, profileJson.toString().toByteArray(), transport)
            Logger.info("Published profile to Pubky directory", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to publish profile", e, context = TAG)
            throw DirectoryError.PublishFailed(e.message ?: "Unknown error")
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
        val adapter = pubkyStorage.createUnauthenticatedAdapter(homeserverBaseURL)
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
     */
    suspend fun addFollow(pubkey: String) {
        val transport = authenticatedTransport ?: throw DirectoryError.NotConfigured
        val followPath = "/pub/pubky.app/follows/$pubkey"

        try {
            pubkyStorage.store(followPath, "{}".toByteArray(), transport)
            Logger.info("Added follow: $pubkey", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to add follow $pubkey", e, context = TAG)
            throw DirectoryError.PublishFailed(e.message ?: "Unknown error")
        }
    }

    /**
     * Remove a follow from the Pubky directory
     */
    suspend fun removeFollow(pubkey: String) {
        val transport = authenticatedTransport ?: throw DirectoryError.NotConfigured
        val followPath = "/pub/pubky.app/follows/$pubkey"

        try {
            pubkyStorage.delete(followPath, transport)
            Logger.info("Removed follow: $pubkey", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to remove follow $pubkey", e, context = TAG)
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
                        name = null as String?, // Could fetch from Pubky profile
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
 * Profile from Pubky directory
 */
data class PubkyProfile(
    val name: String? = null,
    val bio: String? = null,
    val avatar: String? = null,
    val links: List<PubkyProfileLink>? = null
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
