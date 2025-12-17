package to.bitkit.paykit.services

import android.content.Context
import com.pubky.sdk.AuthFlowInfo
import com.pubky.sdk.KeyProvider
import com.pubky.sdk.ListItem
import com.pubky.sdk.PubkyException
import com.pubky.sdk.PubkySession as FfiPubkySession
import com.pubky.sdk.Sdk
import com.pubky.sdk.SessionInfo
import com.pubky.sdk.SignupOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.bitkit.paykit.storage.PaykitKeychainStorage
import to.bitkit.utils.Logger
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for direct Pubky homeserver operations using real FFI bindings
 */
@Singleton
class PubkySDKService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keychainStorage: PaykitKeychainStorage,
) {
    companion object {
        private const val TAG = "PubkySDKService"
        private const val PROFILE_CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
        private const val FOLLOWS_CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    }

    // FFI SDK instance
    private var sdk: Sdk? = null

    // Current homeserver
    var homeserver: String = PubkyConfig.DEFAULT_HOMESERVER
        private set

    // Session cache (FFI sessions)
    private val ffiSessionCache = mutableMapOf<String, FfiPubkySession>()
    private val sessionMutex = Mutex()

    // Legacy session cache for compatibility
    private val legacySessionCache = mutableMapOf<String, LegacyPubkySession>()

    // Profile cache
    private val profileCache = mutableMapOf<String, CachedProfile>()
    private val profileMutex = Mutex()

    // Follows cache
    private val followsCache = mutableMapOf<String, CachedFollows>()
    private val followsMutex = Mutex()

    init {
        try {
            sdk = Sdk()
            Logger.info("PubkySDKService initialized with real FFI SDK", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to initialize FFI SDK", e = e, context = TAG)
        }
    }

    /**
     * Configure the service with a homeserver
     */
    fun configure(homeserver: String? = null) {
        this.homeserver = homeserver ?: PubkyConfig.DEFAULT_HOMESERVER
        Logger.info("PubkySDKService configured with homeserver: ${this.homeserver}", context = TAG)
    }

    /**
     * Sign in to homeserver using a secret key
     */
    suspend fun signin(secretKey: ByteArray, homeserver: String? = null): LegacyPubkySession = withContext(Dispatchers.IO) {
        val currentSdk = sdk ?: throw PubkySDKException.NotConfigured()

        val keyProvider = SecretKeyProviderImpl(secretKey)
        val hs = homeserver ?: this@PubkySDKService.homeserver

        val ffiSession = currentSdk.signin(keyProvider, hs)

        sessionMutex.withLock {
            val info = ffiSession.info()
            ffiSessionCache[info.pubkey] = ffiSession

            // Create legacy session for compatibility
            val session = LegacyPubkySession(
                pubkey = info.pubkey,
                sessionSecret = info.sessionSecret ?: "",
                capabilities = info.capabilities,
                expiresAt = info.expiresAt?.let { Date(it.toLong() * 1000) }
            )
            legacySessionCache[info.pubkey] = session
            persistSession(session)

            Logger.info("Signed in as ${info.pubkey.take(12)}...", context = TAG)
            session
        }
    }

    /**
     * Sign up to homeserver
     */
    suspend fun signup(
        secretKey: ByteArray,
        homeserver: String? = null,
        signupToken: ULong? = null
    ): LegacyPubkySession = withContext(Dispatchers.IO) {
        val currentSdk = sdk ?: throw PubkySDKException.NotConfigured()

        val keyProvider = SecretKeyProviderImpl(secretKey)
        val hs = homeserver ?: this@PubkySDKService.homeserver

        val options = signupToken?.let { SignupOptions(capabilities = null, signupToken = it) }

        val ffiSession = currentSdk.signup(keyProvider, hs, options)

        sessionMutex.withLock {
            val info = ffiSession.info()
            ffiSessionCache[info.pubkey] = ffiSession

            // Create legacy session for compatibility
            val session = LegacyPubkySession(
                pubkey = info.pubkey,
                sessionSecret = info.sessionSecret ?: "",
                capabilities = info.capabilities,
                expiresAt = info.expiresAt?.let { Date(it.toLong() * 1000) }
            )
            legacySessionCache[info.pubkey] = session
            persistSession(session)

            Logger.info("Signed up as ${info.pubkey.take(12)}...", context = TAG)
            session
        }
    }

    /**
     * Start auth flow for QR/deeplink authentication
     */
    fun startAuthFlow(capabilities: List<String>): AuthFlowInfo {
        val currentSdk = sdk ?: throw PubkySDKException.NotConfigured()
        return currentSdk.startAuthFlow(capabilities)
    }

    /**
     * Await approval of auth flow
     */
    suspend fun awaitApproval(requestId: String): LegacyPubkySession = withContext(Dispatchers.IO) {
        val currentSdk = sdk ?: throw PubkySDKException.NotConfigured()

        val ffiSession = currentSdk.awaitApproval(requestId)

        sessionMutex.withLock {
            val info = ffiSession.info()
            ffiSessionCache[info.pubkey] = ffiSession

            // Create legacy session for compatibility
            val session = LegacyPubkySession(
                pubkey = info.pubkey,
                sessionSecret = info.sessionSecret ?: "",
                capabilities = info.capabilities,
                expiresAt = info.expiresAt?.let { Date(it.toLong() * 1000) }
            )
            legacySessionCache[info.pubkey] = session
            persistSession(session)

            Logger.info("Auth flow approved for ${info.pubkey.take(12)}...", context = TAG)
            session
        }
    }

    /**
     * Set a session from Pubky-ring callback (for compatibility)
     */
    suspend fun setSession(session: LegacyPubkySession) = sessionMutex.withLock {
        legacySessionCache[session.pubkey] = session
        persistSession(session)
        Logger.info("Session set for pubkey: ${session.pubkey.take(12)}...", context = TAG)
    }

    /**
     * Get cached session for a pubkey
     */
    suspend fun getSession(pubkey: String): LegacyPubkySession? = sessionMutex.withLock {
        legacySessionCache[pubkey]
    }

    /**
     * Check if we have an active session
     */
    suspend fun hasActiveSession(): Boolean = sessionMutex.withLock {
        legacySessionCache.isNotEmpty() || ffiSessionCache.isNotEmpty()
    }

    /**
     * Get the current active session (first available)
     */
    suspend fun activeSession(): LegacyPubkySession? = sessionMutex.withLock {
        legacySessionCache.values.firstOrNull()
    }

    // MARK: - Profile Operations

    /**
     * Fetch a user's profile from their homeserver
     */
    suspend fun fetchProfile(pubkey: String, app: String = "pubky.app"): SDKPubkyProfile = profileMutex.withLock {
        // Check cache first
        profileCache[pubkey]?.let { cached ->
            if (!cached.isExpired(PROFILE_CACHE_TTL_MS)) {
                Logger.debug("Profile cache hit for ${pubkey.take(12)}...", context = TAG)
                return@withLock cached.profile
            }
        }

        val currentSdk = sdk ?: throw PubkySDKException.NotConfigured()

        val profileUri = "pubky://$pubkey/pub/$app/profile.json"
        Logger.debug("Fetching profile from $profileUri", context = TAG)

        val publicStorage = currentSdk.publicStorage()
        val data = withContext(Dispatchers.IO) {
            publicStorage.get(profileUri)
        }

        val profileJson = String(data.map { it.toByte() }.toByteArray())
        val profile = Json.decodeFromString<SDKPubkyProfile>(profileJson)

        // Cache the result
        profileCache[pubkey] = CachedProfile(profile, System.currentTimeMillis())

        Logger.info("Fetched profile for ${pubkey.take(12)}...: ${profile.name ?: "unnamed"}", context = TAG)
        profile
    }

    /**
     * Fetch a user's follows list from their homeserver
     */
    suspend fun fetchFollows(pubkey: String, app: String = "pubky.app"): List<String> = followsMutex.withLock {
        // Check cache first
        followsCache[pubkey]?.let { cached ->
            if (!cached.isExpired(FOLLOWS_CACHE_TTL_MS)) {
                Logger.debug("Follows cache hit for ${pubkey.take(12)}...", context = TAG)
                return@withLock cached.follows
            }
        }

        val currentSdk = sdk ?: throw PubkySDKException.NotConfigured()

        val followsUri = "pubky://$pubkey/pub/$app/follows/"
        Logger.debug("Fetching follows from $followsUri", context = TAG)

        val publicStorage = currentSdk.publicStorage()
        val items = withContext(Dispatchers.IO) {
            publicStorage.list(followsUri)
        }

        // Extract pubkeys from entry names
        val follows = items.mapNotNull { item ->
            item.name.takeIf { it.isNotEmpty() }
        }

        // Cache the result
        followsCache[pubkey] = CachedFollows(follows, System.currentTimeMillis())

        Logger.info("Fetched ${follows.size} follows for ${pubkey.take(12)}...", context = TAG)
        follows
    }

    // MARK: - Storage Operations

    /**
     * Get data from homeserver (public read)
     */
    suspend fun get(uri: String): ByteArray? = withContext(Dispatchers.IO) {
        val currentSdk = sdk ?: throw PubkySDKException.NotConfigured()

        val publicStorage = currentSdk.publicStorage()
        try {
            publicStorage.get(uri).map { it.toByte() }.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Put data to homeserver (requires session)
     */
    suspend fun put(path: String, data: ByteArray, pubkey: String) = withContext(Dispatchers.IO) {
        val ffiSession = sessionMutex.withLock {
            ffiSessionCache[pubkey]
        } ?: throw PubkySDKException.NoSession()

        val storage = ffiSession.storage()
        storage.put(path, data.map { it.toUByte() })

        Logger.debug("Put data to $path", context = TAG)
    }

    /**
     * Delete data from homeserver (requires session)
     */
    suspend fun delete(path: String, pubkey: String) = withContext(Dispatchers.IO) {
        val ffiSession = sessionMutex.withLock {
            ffiSessionCache[pubkey]
        } ?: throw PubkySDKException.NoSession()

        val storage = ffiSession.storage()
        storage.delete(path)

        Logger.debug("Deleted $path", context = TAG)
    }

    /**
     * List directory contents
     */
    suspend fun listDirectory(uri: String): List<ListItem> = withContext(Dispatchers.IO) {
        val currentSdk = sdk ?: throw PubkySDKException.NotConfigured()

        val publicStorage = currentSdk.publicStorage()
        publicStorage.list(uri)
    }

    // MARK: - Session Persistence

    /**
     * Restore sessions from keychain on app launch
     */
    suspend fun restoreSessions() = sessionMutex.withLock {
        val sessionKeys = keychainStorage.listKeys("pubky.session.")

        for (key in sessionKeys) {
            try {
                val json = keychainStorage.getString(key) ?: continue
                val session = Json.decodeFromString<LegacyPubkySession>(json)

                // Check if session is expired
                session.expiresAt?.let { expiresAt ->
                    if (expiresAt.time < System.currentTimeMillis()) {
                        Logger.info("Session expired for ${session.pubkey.take(12)}..., removing", context = TAG)
                        keychainStorage.delete(key)
                        return@withLock
                    }
                }

                legacySessionCache[session.pubkey] = session
                Logger.info("Restored session for ${session.pubkey.take(12)}...", context = TAG)
            } catch (e: Exception) {
                Logger.error("Failed to restore session from $key", e = e, context = TAG)
            }
        }

        Logger.info("Restored ${legacySessionCache.size} sessions from keychain", context = TAG)
    }

    /**
     * Clear all cached sessions
     */
    suspend fun clearSessions() = sessionMutex.withLock {
        for (pubkey in legacySessionCache.keys) {
            keychainStorage.delete("pubky.session.$pubkey")
        }
        legacySessionCache.clear()
        ffiSessionCache.clear()

        Logger.info("Cleared all sessions", context = TAG)
    }

    /**
     * Sign out a specific session
     */
    suspend fun signout(pubkey: String) = withContext(Dispatchers.IO) {
        val ffiSession = sessionMutex.withLock {
            ffiSessionCache[pubkey]
        }

        ffiSession?.signout()

        sessionMutex.withLock {
            ffiSessionCache.remove(pubkey)
            legacySessionCache.remove(pubkey)
            keychainStorage.delete("pubky.session.$pubkey")
        }

        Logger.info("Signed out ${pubkey.take(12)}...", context = TAG)
    }

    /**
     * Clear caches
     */
    suspend fun clearCaches() {
        profileMutex.withLock { profileCache.clear() }
        followsMutex.withLock { followsCache.clear() }
        Logger.debug("Cleared profile and follows caches", context = TAG)
    }

    // MARK: - Private Helpers

    private suspend fun persistSession(session: LegacyPubkySession) {
        try {
            val json = Json.encodeToString(session)
            keychainStorage.setString("pubky.session.${session.pubkey}", json)
            Logger.debug("Persisted session for ${session.pubkey.take(12)}...", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to persist session", e = e, context = TAG)
        }
    }
}

/**
 * Key provider implementation for FFI
 */
private class SecretKeyProviderImpl(private val key: ByteArray) : KeyProvider {
    override fun secretKey(): List<UByte> {
        if (key.size != 32) {
            throw PubkyException.InvalidInput("Secret key must be 32 bytes")
        }
        return key.map { it.toUByte() }
    }
}

/**
 * SDK Exception types
 */
sealed class PubkySDKException(message: String) : Exception(message) {
    class NotConfigured : PubkySDKException("PubkySDKService is not configured")
    class NoSession : PubkySDKException("No active session - authenticate with Pubky-ring first")
    class FetchFailed(msg: String) : PubkySDKException("Fetch failed: $msg")
    class WriteFailed(msg: String) : PubkySDKException("Write failed: $msg")
    class NotFound(msg: String) : PubkySDKException("Not found: $msg")
    class InvalidData(msg: String) : PubkySDKException("Invalid data: $msg")
    class InvalidUri(msg: String) : PubkySDKException("Invalid URI: $msg")
}

/**
 * Legacy session for compatibility with existing code
 */
@Serializable
data class LegacyPubkySession(
    val pubkey: String,
    val sessionSecret: String,
    val capabilities: List<String>,
    @Serializable(with = DateSerializer::class)
    val expiresAt: Date? = null,
)

/**
 * Date serializer for kotlinx.serialization
 */
object DateSerializer : kotlinx.serialization.KSerializer<Date> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("Date", kotlinx.serialization.descriptors.PrimitiveKind.LONG)
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Date) = encoder.encodeLong(value.time)
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder) = Date(decoder.decodeLong())
}

/**
 * SDK Profile data
 */
@Serializable
data class SDKPubkyProfile(
    val name: String? = null,
    val bio: String? = null,
    val image: String? = null,
    val links: List<SDKProfileLink>? = null,
)

/**
 * SDK Profile link
 */
@Serializable
data class SDKProfileLink(
    val title: String,
    val url: String,
)

// MARK: - Cache Types

private data class CachedProfile(
    val profile: SDKPubkyProfile,
    val fetchedAt: Long,
) {
    fun isExpired(ttlMs: Long): Boolean = System.currentTimeMillis() - fetchedAt > ttlMs
}

private data class CachedFollows(
    val follows: List<String>,
    val fetchedAt: Long,
) {
    fun isExpired(ttlMs: Long): Boolean = System.currentTimeMillis() - fetchedAt > ttlMs
}

