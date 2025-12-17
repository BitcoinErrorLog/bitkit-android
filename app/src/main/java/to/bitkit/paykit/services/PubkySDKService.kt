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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import to.bitkit.paykit.storage.PaykitKeychainStorage
import to.bitkit.utils.Logger
import uniffi.pubkycore.*
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for direct Pubky homeserver operations using pubky-core-ffi bindings
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

    // Current homeserver
    var homeserver: String = PubkyConfig.DEFAULT_HOMESERVER
        private set

    // Session cache
    private val sessionCache = mutableMapOf<String, PubkyCoreSession>()
    private val sessionMutex = Mutex()

    // Profile cache
    private val profileCache = mutableMapOf<String, CachedProfile>()
    private val profileMutex = Mutex()

    // Follows cache
    private val followsCache = mutableMapOf<String, CachedFollows>()
    private val followsMutex = Mutex()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        Logger.info("PubkySDKService initialized with pubky-core-ffi", context = TAG)
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
    suspend fun signin(secretKey: String): PubkyCoreSession = withContext(Dispatchers.IO) {
        val result = signIn(secretKey)
        checkResult(result)

        val sessionData = json.parseToJsonElement(result[1]).jsonObject
        val session = PubkyCoreSession(
            pubkey = sessionData["public_key"]?.jsonPrimitive?.content ?: "",
            sessionSecret = sessionData["session_secret"]?.jsonPrimitive?.content ?: "",
            capabilities = emptyList(),
            expiresAt = null
        )

        sessionMutex.withLock {
            sessionCache[session.pubkey] = session
        }
        persistSession(session)

        Logger.info("Signed in as ${session.pubkey.take(12)}...", context = TAG)
        session
    }

    /**
     * Sign up to homeserver
     */
    suspend fun signup(
        secretKey: String,
        homeserver: String? = null,
        signupToken: String? = null
    ): PubkyCoreSession = withContext(Dispatchers.IO) {
        val hs = homeserver ?: this@PubkySDKService.homeserver
        val result = signUp(secretKey, hs, signupToken)
        checkResult(result)

        val sessionData = json.parseToJsonElement(result[1]).jsonObject
        val session = PubkyCoreSession(
            pubkey = sessionData["public_key"]?.jsonPrimitive?.content ?: "",
            sessionSecret = sessionData["session_secret"]?.jsonPrimitive?.content ?: "",
            capabilities = emptyList(),
            expiresAt = null
        )

        sessionMutex.withLock {
            sessionCache[session.pubkey] = session
        }
        persistSession(session)

        Logger.info("Signed up as ${session.pubkey.take(12)}...", context = TAG)
        session
    }

    /**
     * Revalidate a session
     */
    suspend fun revalidateSession(sessionSecret: String): PubkyCoreSession = withContext(Dispatchers.IO) {
        val result = uniffi.pubkycore.revalidateSession(sessionSecret)
        checkResult(result)

        val sessionData = json.parseToJsonElement(result[1]).jsonObject
        val session = PubkyCoreSession(
            pubkey = sessionData["public_key"]?.jsonPrimitive?.content ?: "",
            sessionSecret = sessionData["session_secret"]?.jsonPrimitive?.content ?: "",
            capabilities = emptyList(),
            expiresAt = null
        )

        sessionMutex.withLock {
            sessionCache[session.pubkey] = session
        }
        persistSession(session)

        Logger.info("Session revalidated for ${session.pubkey.take(12)}...", context = TAG)
        session
    }

    /**
     * Parse an auth URL
     */
    fun parseAuthUrl(url: String): JsonObject {
        val result = uniffi.pubkycore.parseAuthUrl(url)
        checkResult(result)
        return json.parseToJsonElement(result[1]).jsonObject
    }

    /**
     * Approve an auth request
     */
    suspend fun approveAuth(url: String, secretKey: String) = withContext(Dispatchers.IO) {
        val result = auth(url, secretKey)
        checkResult(result)
        Logger.info("Auth approved", context = TAG)
    }

    /**
     * Get cached session for a pubkey
     */
    suspend fun getSession(pubkey: String): PubkyCoreSession? = sessionMutex.withLock {
        sessionCache[pubkey]
    }

    /**
     * Check if we have an active session
     */
    suspend fun hasActiveSession(): Boolean = sessionMutex.withLock {
        sessionCache.isNotEmpty()
    }

    /**
     * Get the current active session
     */
    suspend fun activeSession(): PubkyCoreSession? = sessionMutex.withLock {
        sessionCache.values.firstOrNull()
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

        val profileUri = "pubky://$pubkey/pub/$app/profile.json"
        Logger.debug("Fetching profile from $profileUri", context = TAG)

        val result = withContext(Dispatchers.IO) {
            get(profileUri)
        }
        checkResult(result)

        val profile = json.decodeFromString<SDKPubkyProfile>(result[1])

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

        val followsUri = "pubky://$pubkey/pub/$app/follows/"
        Logger.debug("Fetching follows from $followsUri", context = TAG)

        val result = withContext(Dispatchers.IO) {
            list(followsUri)
        }
        checkResult(result)

        // Parse the JSON array of URLs
        val urls = json.decodeFromString<List<String>>(result[1])

        // Extract pubkeys from URLs
        val follows = urls.mapNotNull { url ->
            url.split("/").lastOrNull()?.takeIf { it.isNotEmpty() }
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
    suspend fun getData(uri: String): ByteArray? = withContext(Dispatchers.IO) {
        val result = get(uri)

        if (result[0] == "error") {
            if (result[1].contains("404") || result[1].contains("Not found")) {
                return@withContext null
            }
            throw PubkySDKException.FetchFailed(result[1])
        }

        // Handle base64 encoded binary data
        if (result[1].startsWith("base64:")) {
            val base64String = result[1].removePrefix("base64:")
            return@withContext android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
        }

        result[1].toByteArray()
    }

    /**
     * Put data to homeserver (requires secret key)
     */
    suspend fun putData(url: String, content: String, secretKey: String) = withContext(Dispatchers.IO) {
        val result = put(url, content, secretKey)
        checkResult(result)
        Logger.debug("Put data to $url", context = TAG)
    }

    /**
     * Delete data from homeserver
     */
    suspend fun deleteData(url: String, secretKey: String) = withContext(Dispatchers.IO) {
        val result = deleteFile(url, secretKey)
        checkResult(result)
        Logger.debug("Deleted $url", context = TAG)
    }

    /**
     * List directory contents
     */
    suspend fun listDirectory(uri: String): List<String> = withContext(Dispatchers.IO) {
        val result = list(uri)
        checkResult(result)
        json.decodeFromString(result[1])
    }

    // MARK: - Key Operations

    /**
     * Generate a new secret key
     */
    fun generateNewSecretKey(): Triple<String, String, String> {
        val result = generateSecretKey()
        checkResult(result)

        val data = json.parseToJsonElement(result[1]).jsonObject
        return Triple(
            data["secret_key"]?.jsonPrimitive?.content ?: "",
            data["public_key"]?.jsonPrimitive?.content ?: "",
            data["uri"]?.jsonPrimitive?.content ?: ""
        )
    }

    /**
     * Get public key from secret key
     */
    fun getPublicKey(secretKey: String): Pair<String, String> {
        val result = getPublicKeyFromSecretKey(secretKey)
        checkResult(result)

        val data = json.parseToJsonElement(result[1]).jsonObject
        return Pair(
            data["public_key"]?.jsonPrimitive?.content ?: "",
            data["uri"]?.jsonPrimitive?.content ?: ""
        )
    }

    /**
     * Get homeserver for a pubkey
     */
    suspend fun getHomeserverFor(pubkey: String): String = withContext(Dispatchers.IO) {
        val result = getHomeserver(pubkey)
        checkResult(result)
        result[1]
    }

    // MARK: - Recovery

    /**
     * Create a recovery file
     */
    fun createRecoveryFileData(secretKey: String, passphrase: String): String {
        val result = createRecoveryFile(secretKey, passphrase)
        checkResult(result)
        return result[1]
    }

    /**
     * Decrypt a recovery file
     */
    fun decryptRecoveryFileData(recoveryFile: String, passphrase: String): String {
        val result = decryptRecoveryFile(recoveryFile, passphrase)
        checkResult(result)
        return result[1]
    }

    // MARK: - Mnemonic

    /**
     * Generate a mnemonic phrase
     */
    fun generateMnemonic(): String {
        val result = generateMnemonicPhrase()
        checkResult(result)
        return result[1]
    }

    /**
     * Convert mnemonic to keypair
     */
    fun mnemonicToKeypair(mnemonic: String): Triple<String, String, String> {
        val result = mnemonicPhraseToKeypair(mnemonic)
        checkResult(result)

        val data = json.parseToJsonElement(result[1]).jsonObject
        return Triple(
            data["secret_key"]?.jsonPrimitive?.content ?: "",
            data["public_key"]?.jsonPrimitive?.content ?: "",
            data["uri"]?.jsonPrimitive?.content ?: ""
        )
    }

    /**
     * Validate mnemonic phrase
     */
    fun validateMnemonic(mnemonic: String): Boolean {
        val result = validateMnemonicPhrase(mnemonic)
        return result[1] == "true"
    }

    // MARK: - Session Persistence

    /**
     * Restore sessions from keychain on app launch
     */
    suspend fun restoreSessions() = sessionMutex.withLock {
        val sessionKeys = keychainStorage.listKeys("pubky.session.")

        for (key in sessionKeys) {
            try {
                val jsonStr = keychainStorage.getString(key) ?: continue
                val session = json.decodeFromString<PubkyCoreSession>(jsonStr)

                // Check if session is expired
                session.expiresAt?.let { expiresAt ->
                    if (expiresAt.time < System.currentTimeMillis()) {
                        Logger.info("Session expired for ${session.pubkey.take(12)}..., removing", context = TAG)
                        keychainStorage.delete(key)
                        return@withLock
                    }
                }

                sessionCache[session.pubkey] = session
                Logger.info("Restored session for ${session.pubkey.take(12)}...", context = TAG)
            } catch (e: Exception) {
                Logger.error("Failed to restore session from $key", e = e, context = TAG)
            }
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

        Logger.info("Cleared all sessions", context = TAG)
    }

    /**
     * Sign out a specific session
     */
    suspend fun signout(sessionSecret: String) = withContext(Dispatchers.IO) {
        val result = signOut(sessionSecret)
        checkResult(result)
        Logger.info("Signed out", context = TAG)
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

    private suspend fun persistSession(session: PubkyCoreSession) {
        try {
            val jsonStr = json.encodeToString(session)
            keychainStorage.setString("pubky.session.${session.pubkey}", jsonStr)
            Logger.debug("Persisted session for ${session.pubkey.take(12)}...", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to persist session", e = e, context = TAG)
        }
    }

    private fun checkResult(result: List<String>) {
        if (result[0] == "error") {
            throw PubkySDKException.FetchFailed(result[1])
        }
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
 * Pubky Core session
 */
@Serializable
data class PubkyCoreSession(
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
