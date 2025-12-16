package to.bitkit.paykit.services

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import to.bitkit.paykit.storage.PaykitKeychainStorage
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache for X25519 Noise protocol keys
 */
@Singleton
class NoiseKeyCache @Inject constructor(
    private val keychain: PaykitKeychainStorage,
) {
    companion object {
        private const val TAG = "NoiseKeyCache"
        
        @Volatile
        private var instance: NoiseKeyCache? = null
        
        /**
         * Get singleton instance (for non-DI access)
         */
        fun getInstance(): NoiseKeyCache {
            return instance ?: throw IllegalStateException("NoiseKeyCache not initialized. Use dependency injection.")
        }
        
        internal fun setInstance(cache: NoiseKeyCache) {
            instance = cache
        }
    }
    
    init {
        setInstance(this)
    }

    private val memoryCache = mutableMapOf<String, ByteArray>()
    private val cacheMutex = Mutex()

    var maxCachedEpochs: Int = 5

    /**
     * Get a cached key if available (suspend version with full persistence check)
     */
    suspend fun getKey(deviceId: String, epoch: UInt): ByteArray? {
        val key = cacheKey(deviceId, epoch)

        // Check memory cache first
        cacheMutex.withLock {
            memoryCache[key]?.let { return it }
        }

        // Check persistent cache
        val keyData = keychain.retrieve(key)
        if (keyData != null) {
            cacheMutex.withLock {
                memoryCache[key] = keyData
            }
            return keyData
        }

        return null
    }
    
    /**
     * Get a cached key from memory only (non-suspend, for callbacks)
     */
    fun getKeySync(deviceId: String, epoch: UInt): ByteArray? {
        val key = cacheKey(deviceId, epoch)
        return synchronized(memoryCache) {
            memoryCache[key]
        }
    }

    /**
     * Store a key in the cache (suspend version with persistence)
     */
    suspend fun setKey(keyData: ByteArray, deviceId: String, epoch: UInt) {
        val key = cacheKey(deviceId, epoch)

        // Store in memory cache
        cacheMutex.withLock {
            memoryCache[key] = keyData
        }

        // Store in keychain
        try {
            keychain.store(key, keyData)
        } catch (e: Exception) {
            Logger.error("NoiseKeyCache: Failed to store key", e, context = TAG)
        }

        // Cleanup old epochs if needed
        cleanupOldEpochs(deviceId, epoch)
    }
    
    /**
     * Store a key in memory cache only (non-suspend, for callbacks)
     * Call persistKey() later to persist to keychain
     */
    fun setKeySync(keyData: ByteArray, deviceId: String, epoch: UInt) {
        val key = cacheKey(deviceId, epoch)
        synchronized(memoryCache) {
            memoryCache[key] = keyData
        }
        Logger.debug("Stored key in memory cache: $key", context = TAG)
    }
    
    /**
     * Persist a key from memory cache to keychain (suspend)
     */
    suspend fun persistKey(deviceId: String, epoch: UInt) {
        val key = cacheKey(deviceId, epoch)
        val keyData = synchronized(memoryCache) {
            memoryCache[key]
        } ?: return
        
        try {
            keychain.store(key, keyData)
            Logger.debug("Persisted key to keychain: $key", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to persist key: $key", e, context = TAG)
        }
    }

    /**
     * Clear all cached keys
     */
    suspend fun clearAll() {
        cacheMutex.withLock {
            memoryCache.clear()
        }
    }

    // MARK: - Private

    private fun cacheKey(deviceId: String, epoch: UInt): String {
        return "noise.key.cache.$deviceId.$epoch"
    }

    private suspend fun cleanupOldEpochs(deviceId: String, currentEpoch: UInt) {
        // Implementation would clean up old epochs beyond maxCachedEpochs
        // Simplified for now
    }
}
