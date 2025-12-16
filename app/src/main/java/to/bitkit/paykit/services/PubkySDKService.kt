package to.bitkit.paykit.services

import android.content.Context
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
 * Service for direct Pubky homeserver operations
 * Wraps the existing Paykit FFI storage adapters with higher-level methods
 */
@Singleton
class PubkySDKService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keychainStorage: PaykitKeychainStorage,
    private val pubkyStorageAdapter: PubkyStorageAdapter,
) {
    companion object {
        private const val TAG = "PubkySDKService"
        private const val PROFILE_CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
        private const val FOLLOWS_CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    }

    // Current homeserver
    var homeserver: String = PubkyConfig.DEFAULT_HOMESERVER
        private set

    // Session cache
    private val sessionCache = mutableMapOf<String, PubkySession>()
    private val sessionMutex = Mutex()

    // Profile cache
    private val profileCache = mutableMapOf<String, CachedProfile>()
    private val profileMutex = Mutex()

    // Follows cache
    private val followsCache = mutableMapOf<String, CachedFollows>()
    private val followsMutex = Mutex()

    // Adapters
    private var unauthenticatedAdapter: PubkyUnauthenticatedStorageAdapter? = null
    private var authenticatedAdapter: PubkyAuthenticatedStorageAdapter? = null

    init {
        setupUnauthenticatedAdapter()
    }

    private fun setupUnauthenticatedAdapter() {
        unauthenticatedAdapter = pubkyStorageAdapter.createUnauthenticatedAdapter(homeserver)
    }

    /**
     * Configure the service with a homeserver
     */
    fun configure(homeserver: String? = null) {
        this.homeserver = homeserver ?: PubkyConfig.DEFAULT_HOMESERVER
        setupUnauthenticatedAdapter()
        Logger.info("PubkySDKService configured with homeserver: ${this.homeserver}", context = TAG)
    }

    /**
     * Set a session from Pubky-ring callback
     */
    suspend fun setSession(session: PubkySession) = sessionMutex.withLock {
        sessionCache[session.pubkey] = session
        persistSession(session)

        // Create authenticated adapter for writes
        authenticatedAdapter = pubkyStorageAdapter.createAuthenticatedAdapter(
            sessionId = session.sessionSecret,
            homeserverBaseURL = homeserver
        )

        Logger.info("Session set for pubkey: ${session.pubkey.take(12)}...", context = TAG)
    }

    /**
     * Get cached session for a pubkey
     */
    suspend fun getSession(pubkey: String): PubkySession? = sessionMutex.withLock {
        sessionCache[pubkey]
    }

    /**
     * Check if we have an active session
     */
    suspend fun hasActiveSession(): Boolean = sessionMutex.withLock {
        sessionCache.isNotEmpty()
    }

    /**
     * Get the current active session (first available)
     */
    suspend fun getActiveSession(): PubkySession? = sessionMutex.withLock {
        sessionCache.values.firstOrNull()
    }

    // MARK: - Profile Operations

    /**
     * Fetch a user's profile from their homeserver
     */
    internal suspend fun fetchProfile(pubkey: String, app: String = "pubky.app"): SDKPubkyProfile = withContext(Dispatchers.IO) {
        // Check cache first
        profileMutex.withLock {
            profileCache[pubkey]?.let { cached ->
                if (!cached.isExpired(PROFILE_CACHE_TTL_MS)) {
                    Logger.debug("Profile cache hit for ${pubkey.take(12)}...", context = TAG)
                    return@withContext cached.profile
                }
            }
        }

        val adapter = unauthenticatedAdapter
            ?: throw PubkySDKException("PubkySDKService is not configured")

        val profilePath = "/pub/$app/profile.json"
        Logger.debug("Fetching profile from ${pubkey.take(12)}...$profilePath", context = TAG)

        val result = adapter.get(pubkey, profilePath)

        if (!result.success) {
            throw PubkySDKException("Fetch failed: ${result.error ?: "Unknown error"}")
        }

        val content = result.content
            ?: throw PubkySDKException("Profile not found for ${pubkey.take(12)}...")

        val profile = try {
            Json.decodeFromString<SDKPubkyProfile>(content)
        } catch (e: Exception) {
            throw PubkySDKException("Invalid profile data: ${e.message}")
        }

        // Cache the result
        profileMutex.withLock {
            profileCache[pubkey] = CachedProfile(profile, System.currentTimeMillis())
        }

        Logger.info("Fetched profile for ${pubkey.take(12)}...: ${profile.name ?: "unnamed"}", context = TAG)
        profile
    }

    /**
     * Fetch a user's follows list from their homeserver
     */
    suspend fun fetchFollows(pubkey: String, app: String = "pubky.app"): List<String> = withContext(Dispatchers.IO) {
        // Check cache first
        followsMutex.withLock {
            followsCache[pubkey]?.let { cached ->
                if (!cached.isExpired(FOLLOWS_CACHE_TTL_MS)) {
                    Logger.debug("Follows cache hit for ${pubkey.take(12)}...", context = TAG)
                    return@withContext cached.follows
                }
            }
        }

        val adapter = unauthenticatedAdapter
            ?: throw PubkySDKException("PubkySDKService is not configured")

        val followsPath = "/pub/$app/follows/"
        Logger.debug("Fetching follows from ${pubkey.take(12)}...$followsPath", context = TAG)

        val result = adapter.list(pubkey, followsPath)

        if (!result.success) {
            throw PubkySDKException("Fetch failed: ${result.error ?: "Unknown error"}")
        }

        // The entries are file names in the follows directory
        // Each file name is a pubkey
        val follows = result.entries.mapNotNull { entry ->
            entry.split("/").lastOrNull()?.takeIf { it.isNotEmpty() }
        }

        // Cache the result
        followsMutex.withLock {
            followsCache[pubkey] = CachedFollows(follows, System.currentTimeMillis())
        }

        Logger.info("Fetched ${follows.size} follows for ${pubkey.take(12)}...", context = TAG)
        follows
    }

    // MARK: - Storage Operations

    /**
     * Get data from homeserver (public read)
     */
    suspend fun get(uri: String, ownerPubkey: String? = null): ByteArray? = withContext(Dispatchers.IO) {
        val adapter = unauthenticatedAdapter
            ?: throw PubkySDKException("PubkySDKService is not configured")

        val (pubkey, path) = parseUri(uri, ownerPubkey)

        val result = adapter.get(pubkey, path)

        if (!result.success) {
            throw PubkySDKException("Fetch failed: ${result.error ?: "Unknown error"}")
        }

        result.content?.toByteArray(Charsets.UTF_8)
    }

    /**
     * Put data to homeserver (requires session)
     */
    suspend fun put(path: String, data: ByteArray) = withContext(Dispatchers.IO) {
        val adapter = authenticatedAdapter
            ?: throw PubkySDKException("No active session - authenticate with Pubky-ring first")

        val content = data.toString(Charsets.UTF_8)
        val result = adapter.put(path, content)

        if (!result.success) {
            throw PubkySDKException("Write failed: ${result.error ?: "Unknown error"}")
        }

        Logger.debug("Put data to $path", context = TAG)
    }

    /**
     * List directory contents
     */
    suspend fun listDirectory(prefix: String, ownerPubkey: String): List<String> = withContext(Dispatchers.IO) {
        val adapter = unauthenticatedAdapter
            ?: throw PubkySDKException("PubkySDKService is not configured")

        val result = adapter.list(ownerPubkey, prefix)

        if (!result.success) {
            throw PubkySDKException("Fetch failed: ${result.error ?: "Unknown error"}")
        }

        result.entries
    }

    // MARK: - Session Persistence

    /**
     * Restore sessions from keychain on app launch
     */
    suspend fun restoreSessions() = sessionMutex.withLock {
        val sessionKeys = keychainStorage.listKeys("pubky.session.")

        for (key in sessionKeys) {
            try {
                val data = keychainStorage.getString(key) ?: continue
                val session = kotlinx.serialization.json.Json.decodeFromString(PubkySession.serializer(), data)

                // Check if session is expired
                session.expiresAt?.let { expiresAt ->
                    if (expiresAt.time < System.currentTimeMillis()) {
                        Logger.info("Session expired for ${session.pubkey.take(12)}..., removing", context = TAG)
                        keychainStorage.delete(key)
                        return@let
                    }
                }

                sessionCache[session.pubkey] = session
                Logger.info("Restored session for ${session.pubkey.take(12)}...", context = TAG)
            } catch (e: Exception) {
                Logger.error("Failed to restore session from $key: ${e.message}", context = TAG)
            }
        }

        // Setup authenticated adapter if we have a session
        sessionCache.values.firstOrNull()?.let { session ->
            authenticatedAdapter = pubkyStorageAdapter.createAuthenticatedAdapter(
                sessionId = session.sessionSecret,
                homeserverBaseURL = homeserver
            )
        }

        Logger.info("Restored ${sessionCache.size} sessions from keychain", context = TAG)
    }

    /**
     * Clear all cached sessions
     */
    suspend fun clearSessions() = sessionMutex.withLock {
        for (pubkey in sessionCache.keys) {
            keychainStorage.delete("pubky.session.$pubkey")
        }
        sessionCache.clear()
        authenticatedAdapter = null

        Logger.info("Cleared all sessions", context = TAG)
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

    private suspend fun persistSession(session: PubkySession) {
        try {
            val data = kotlinx.serialization.json.Json.encodeToString(PubkySession.serializer(), session)
            keychainStorage.setString("pubky.session.${session.pubkey}", data)
            Logger.debug("Persisted session for ${session.pubkey.take(12)}...", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to persist session: ${e.message}", context = TAG)
        }
    }

    private fun parseUri(uri: String, defaultPubkey: String?): Pair<String, String> {
        return if (uri.startsWith("pubky://")) {
            // Parse full URI: pubky://{pubkey}/path
            val withoutScheme = uri.removePrefix("pubky://")
            val slashIndex = withoutScheme.indexOf('/')
            if (slashIndex == -1) {
                throw PubkySDKException("Invalid URI: $uri")
            }
            val pubkey = withoutScheme.substring(0, slashIndex)
            val path = withoutScheme.substring(slashIndex)
            Pair(pubkey, path)
        } else if (defaultPubkey != null) {
            // Path only, use default pubkey
            Pair(defaultPubkey, uri)
        } else {
            throw PubkySDKException("URI requires pubkey: $uri")
        }
    }
}

/**
 * Exception for PubkySDK operations
 */
class PubkySDKException(message: String) : Exception(message)

/**
 * Pubky profile data (internal SDK type)
 */
@Serializable
internal data class SDKPubkyProfile(
    val name: String? = null,
    val bio: String? = null,
    val image: String? = null,
    val links: List<SDKProfileLink>? = null,
)

/**
 * Profile link (internal SDK type)
 */
@Serializable
internal data class SDKProfileLink(
    val title: String,
    val url: String,
)

/**
 * Cached profile with timestamp
 */
private data class CachedProfile(
    val profile: SDKPubkyProfile,
    val fetchedAt: Long,
) {
    fun isExpired(ttlMs: Long): Boolean =
        System.currentTimeMillis() - fetchedAt > ttlMs
}

/**
 * Cached follows with timestamp
 */
private data class CachedFollows(
    val follows: List<String>,
    val fetchedAt: Long,
) {
    fun isExpired(ttlMs: Long): Boolean =
        System.currentTimeMillis() - fetchedAt > ttlMs
}


