package to.bitkit.paykit.storage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.bitkit.paykit.services.SpendingLimit
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage for spending limits with persistent keychain storage.
 *
 * Thread-safe: Uses a Mutex to protect cache access and prevent race conditions
 * during concurrent spending limit checks and updates.
 */
@Singleton
class SpendingLimitStorage @Inject constructor(
    private val keychain: PaykitKeychainStorage,
) {
    companion object {
        private const val TAG = "SpendingLimitStorage"
        private const val PEER_LIMITS_KEY = "spending_limits.peer_limits"
        private const val GLOBAL_LIMIT_KEY = "spending_limits.global_limit"
    }

    private val cacheMutex = Mutex()
    private var peerLimitsCache: MutableMap<String, SpendingLimit>? = null
    private var globalLimitCache: SpendingLimit? = null
    private var cacheInitialized = false

    private suspend fun ensureCacheLoaded() {
        if (cacheInitialized) return
        loadFromStorage()
        cacheInitialized = true
    }

    private fun loadFromStorage() {
        // Load peer limits
        try {
            val data = keychain.retrieve(PEER_LIMITS_KEY)
            if (data != null) {
                val json = String(data)
                val wrapper = Json.decodeFromString<PeerLimitsWrapper>(json)
                peerLimitsCache = wrapper.limits.toMutableMap()
                Logger.debug("Loaded ${peerLimitsCache?.size ?: 0} peer spending limits from storage", context = TAG)
            } else {
                peerLimitsCache = mutableMapOf()
            }
        } catch (e: Exception) {
            Logger.error("Failed to load peer spending limits from storage", e, context = TAG)
            peerLimitsCache = mutableMapOf()
        }

        // Load global limit
        try {
            val data = keychain.retrieve(GLOBAL_LIMIT_KEY)
            if (data != null) {
                val json = String(data)
                globalLimitCache = Json.decodeFromString<SpendingLimit>(json)
                Logger.debug("Loaded global spending limit from storage", context = TAG)
            }
        } catch (e: Exception) {
            Logger.error("Failed to load global spending limit from storage", e, context = TAG)
            globalLimitCache = null
        }
    }

    private suspend fun persistPeerLimits() {
        try {
            val wrapper = PeerLimitsWrapper(peerLimitsCache?.toMap() ?: emptyMap())
            val json = Json.encodeToString(wrapper)
            keychain.store(PEER_LIMITS_KEY, json.toByteArray())
            Logger.debug("Persisted ${peerLimitsCache?.size ?: 0} peer spending limits", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to persist peer spending limits", e, context = TAG)
            throw PaykitStorageException.SaveFailed(PEER_LIMITS_KEY)
        }
    }

    private suspend fun persistGlobalLimit() {
        try {
            val limit = globalLimitCache
            if (limit != null) {
                val json = Json.encodeToString(limit)
                keychain.store(GLOBAL_LIMIT_KEY, json.toByteArray())
                Logger.debug("Persisted global spending limit", context = TAG)
            } else {
                keychain.delete(GLOBAL_LIMIT_KEY)
                Logger.debug("Removed global spending limit from storage", context = TAG)
            }
        } catch (e: Exception) {
            Logger.error("Failed to persist global spending limit", e, context = TAG)
            throw PaykitStorageException.SaveFailed(GLOBAL_LIMIT_KEY)
        }
    }

    /**
     * Get spending limit for a specific peer
     */
    suspend fun getSpendingLimitForPeer(peerPubkey: String): SpendingLimit? = cacheMutex.withLock {
        ensureCacheLoaded()
        peerLimitsCache?.get(peerPubkey)
    }

    /**
     * Set spending limit for a peer
     */
    suspend fun setSpendingLimitForPeer(peerPubkey: String, limit: SpendingLimit) = cacheMutex.withLock {
        ensureCacheLoaded()
        peerLimitsCache?.set(peerPubkey, limit)
        persistPeerLimits()
    }

    /**
     * Remove spending limit for a peer
     */
    suspend fun removeSpendingLimitForPeer(peerPubkey: String) = cacheMutex.withLock {
        ensureCacheLoaded()
        peerLimitsCache?.remove(peerPubkey)
        persistPeerLimits()
    }

    /**
     * Get global spending limit
     */
    suspend fun getGlobalSpendingLimit(): SpendingLimit? = cacheMutex.withLock {
        ensureCacheLoaded()
        globalLimitCache
    }

    /**
     * Set global spending limit
     */
    suspend fun setGlobalSpendingLimit(limit: SpendingLimit) = cacheMutex.withLock {
        ensureCacheLoaded()
        globalLimitCache = limit
        persistGlobalLimit()
    }

    /**
     * Remove global spending limit
     */
    suspend fun removeGlobalSpendingLimit() = cacheMutex.withLock {
        ensureCacheLoaded()
        globalLimitCache = null
        persistGlobalLimit()
    }

    /**
     * Get all peer limits
     */
    suspend fun getAllPeerLimits(): Map<String, SpendingLimit> = cacheMutex.withLock {
        ensureCacheLoaded()
        peerLimitsCache?.toMap() ?: emptyMap()
    }

    /**
     * Record spending against a peer's limit (atomic operation)
     */
    suspend fun recordSpending(peerPubkey: String, amountSats: Long) = cacheMutex.withLock {
        ensureCacheLoaded()
        peerLimitsCache?.get(peerPubkey)?.let { limit ->
            peerLimitsCache?.set(
                peerPubkey,
                limit.copy(currentSpentSats = limit.currentSpentSats + amountSats),
            )
            persistPeerLimits()
        }

        // Also record against global limit
        globalLimitCache?.let { limit ->
            globalLimitCache = limit.copy(currentSpentSats = limit.currentSpentSats + amountSats)
            persistGlobalLimit()
        }
    }

    /**
     * Reset spending counters for a period
     */
    suspend fun resetSpending(peerPubkey: String) = cacheMutex.withLock {
        ensureCacheLoaded()
        peerLimitsCache?.get(peerPubkey)?.let { limit ->
            peerLimitsCache?.set(
                peerPubkey,
                limit.copy(
                    currentSpentSats = 0,
                    lastResetTimestamp = System.currentTimeMillis(),
                ),
            )
            persistPeerLimits()
        }
    }

    /**
     * Reset global spending counter
     */
    suspend fun resetGlobalSpending() = cacheMutex.withLock {
        ensureCacheLoaded()
        globalLimitCache?.let { limit ->
            globalLimitCache = limit.copy(
                currentSpentSats = 0,
                lastResetTimestamp = System.currentTimeMillis(),
            )
            persistGlobalLimit()
        }
    }
}

@Serializable
private data class PeerLimitsWrapper(
    val limits: Map<String, SpendingLimit>,
)
