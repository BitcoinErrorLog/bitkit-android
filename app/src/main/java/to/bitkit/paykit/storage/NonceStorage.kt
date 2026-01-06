package to.bitkit.paykit.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent storage for nonces used in signature replay attack prevention.
 *
 * # Security
 *
 * This storage persists nonces across app restarts to prevent replay attacks.
 * Each nonce can only be used once - if a nonce is seen again, it indicates
 * a potential replay attack.
 *
 * Nonces are stored with their expiration timestamps and are cleaned up
 * periodically to prevent unbounded storage growth.
 */
@Singleton
class NonceStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "NonceStorage"
        private const val PREFS_NAME = "paykit_nonces"
        private const val KEY_PREFIX = "nonce_"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val mutex = Mutex()

    /**
     * Check if a nonce has been used, and mark it as used if not.
     *
     * # Security
     *
     * This is the critical function for replay attack prevention.
     * This operation is atomic - no other thread can modify the nonce
     * between checking and marking.
     *
     * @param nonce The 32-byte nonce as hex string
     * @param expiresAt When this nonce's signature expires (Unix timestamp)
     * @return true if nonce is fresh (never seen), false if used (replay attack)
     */
    suspend fun checkAndMark(nonce: String, expiresAt: Long): Boolean = mutex.withLock {
        val key = KEY_PREFIX + nonce

        // Check if nonce already exists
        if (prefs.contains(key)) {
            Logger.warn("Nonce already used: ${nonce.take(16)}... - potential replay attack", context = TAG)
            return@withLock false
        }

        // Mark as used with expiration time
        prefs.edit {
            putLong(key, expiresAt)
        }

        Logger.debug("Nonce marked as used: ${nonce.take(16)}...", context = TAG)
        return@withLock true
    }

    /**
     * Check if a nonce has been used (read-only, doesn't mark).
     *
     * @param nonce The 32-byte nonce as hex string
     * @return true if nonce has been used
     */
    suspend fun isUsed(nonce: String): Boolean = mutex.withLock {
        val key = KEY_PREFIX + nonce
        prefs.contains(key)
    }

    /**
     * Clean up expired nonces to prevent unbounded storage growth.
     *
     * Should be called periodically (e.g., on app startup or hourly).
     *
     * @param before Remove nonces that expired before this timestamp
     * @return Number of nonces removed
     */
    suspend fun cleanupExpired(before: Long): Int = mutex.withLock {
        val keysToRemove = mutableListOf<String>()

        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_PREFIX) && value is Long) {
                if (value < before) {
                    keysToRemove.add(key)
                }
            }
        }

        if (keysToRemove.isNotEmpty()) {
            prefs.edit {
                keysToRemove.forEach { key ->
                    remove(key)
                }
            }
            Logger.debug("Cleaned up ${keysToRemove.size} expired nonces", context = TAG)
        }

        return@withLock keysToRemove.size
    }

    /**
     * Get the count of tracked nonces (for monitoring/debugging).
     */
    suspend fun count(): Int = mutex.withLock {
        prefs.all.count { it.key.startsWith(KEY_PREFIX) }
    }

    /**
     * Clear all nonces (for testing only).
     */
    suspend fun clear() = mutex.withLock {
        prefs.edit {
            prefs.all.keys
                .filter { it.startsWith(KEY_PREFIX) }
                .forEach { remove(it) }
        }
    }
}
