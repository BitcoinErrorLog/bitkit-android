package to.bitkit.paykit.services

import com.pubky.noise.FfiKeyBinding
import com.pubky.noise.keybindingDecode
import to.bitkit.utils.Logger
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for discovering and caching KeyBinding records via PKARR resolution.
 *
 * Per PUBKY_CRYPTO_SPEC v2.5 Section 7.3, KeyBinding contains:
 * - inbox_keys: InboxKey entries (for SB2 stored delivery encryption)
 * - transport_keys: TransportKey entries (for Noise live transport)
 * - app_keys: Optional AppKey entries (for delegated signing)
 *
 * This service caches KeyBinding per peer to avoid repeated PKARR lookups.
 * Cache TTL is configurable (default 1 hour).
 */
@Singleton
class KeyBindingService @Inject constructor(
    private val pubkyStorageAdapter: PubkyStorageAdapter,
) {
    companion object {
        private const val TAG = "KeyBindingService"

        /** Default cache TTL in milliseconds (1 hour). */
        private const val DEFAULT_CACHE_TTL_MS = 3600000L

        /** Logging truncation length for pubkeys. */
        private const val PUBKEY_LOG_LEN = 12
    }

    /** Cached KeyBinding entries with timestamp. */
    private data class CachedKeyBinding(
        val keyBinding: FfiKeyBinding,
        val fetchedAt: Long,
    )

    private val cache = ConcurrentHashMap<String, CachedKeyBinding>()
    private var cacheTtlMs = DEFAULT_CACHE_TTL_MS

    /**
     * Discover KeyBinding for a peer via PKARR resolution.
     *
     * Uses cached value if available and not expired. Otherwise fetches
     * from PKARR and caches the result.
     *
     * @param peerPubkeyZ32 The peer's z-base-32 encoded Ed25519 public key
     * @return FfiKeyBinding if found, null if peer has no KeyBinding published
     */
    suspend fun discoverKeyBinding(peerPubkeyZ32: String): FfiKeyBinding? {
        val normalizedPubkey = peerPubkeyZ32.lowercase().trim()

        // Check cache first
        val cached = cache[normalizedPubkey]
        if (cached != null && !isCacheExpired(cached)) {
            Logger.debug("KeyBinding cache hit for ${normalizedPubkey.take(PUBKEY_LOG_LEN)}...", context = TAG)
            return cached.keyBinding
        }

        // Fetch from PKARR
        Logger.debug("Fetching KeyBinding for ${normalizedPubkey.take(PUBKEY_LOG_LEN)}...", context = TAG)
        return try {
            val keyBinding = fetchKeyBindingFromPkarr(normalizedPubkey)
            if (keyBinding != null) {
                cache[normalizedPubkey] = CachedKeyBinding(keyBinding, System.currentTimeMillis())
                Logger.info(
                    "Cached KeyBinding for ${normalizedPubkey.take(PUBKEY_LOG_LEN)}...: " +
                        "${keyBinding.inboxKeys.size} inbox keys, " +
                        "${keyBinding.transportKeys.size} transport keys",
                    context = TAG,
                )
            }
            keyBinding
        } catch (e: Exception) {
            Logger.error("Failed to fetch KeyBinding for ${normalizedPubkey.take(PUBKEY_LOG_LEN)}...", e, context = TAG)
            null
        }
    }

    /**
     * Get the primary InboxKey for a peer (for SB2 stored delivery encryption).
     *
     * @param peerPubkeyZ32 The peer's z-base-32 encoded public key
     * @return Pair of (inbox_kid_hex, x25519_pub_hex) or null if not found
     */
    suspend fun getInboxKey(peerPubkeyZ32: String): Pair<String, String>? {
        val keyBinding = discoverKeyBinding(peerPubkeyZ32) ?: return null
        val inboxKey = keyBinding.inboxKeys.firstOrNull() ?: return null
        return Pair(inboxKey.inboxKidHex, inboxKey.x25519PubHex)
    }

    /**
     * Get the primary TransportKey for a peer (for Noise live transport).
     *
     * @param peerPubkeyZ32 The peer's z-base-32 encoded public key
     * @return X25519 public key as hex, or null if not found
     */
    suspend fun getTransportKey(peerPubkeyZ32: String): String? {
        val keyBinding = discoverKeyBinding(peerPubkeyZ32) ?: return null
        return keyBinding.transportKeys.firstOrNull()?.x25519PubHex
    }

    /**
     * Invalidate cached KeyBinding for a peer.
     */
    fun invalidateCache(peerPubkeyZ32: String) {
        val normalizedPubkey = peerPubkeyZ32.lowercase().trim()
        cache.remove(normalizedPubkey)
        Logger.debug("Invalidated KeyBinding cache for ${normalizedPubkey.take(PUBKEY_LOG_LEN)}...", context = TAG)
    }

    /**
     * Clear all cached KeyBindings.
     */
    fun clearCache() {
        cache.clear()
        Logger.info("Cleared KeyBinding cache", context = TAG)
    }

    /**
     * Set cache TTL in milliseconds.
     */
    fun setCacheTtl(ttlMs: Long) {
        cacheTtlMs = ttlMs
    }

    private fun isCacheExpired(cached: CachedKeyBinding): Boolean {
        return System.currentTimeMillis() - cached.fetchedAt > cacheTtlMs
    }

    /**
     * Fetch KeyBinding from PKARR for the given peer.
     *
     * NOTE: This is a placeholder implementation. The actual PKARR resolution
     * depends on how Pubky SDK exposes PKARR queries. This may need to be
     * updated once the PKARR resolution API is available.
     */
    @Suppress("ForbiddenComment")
    private suspend fun fetchKeyBindingFromPkarr(peerPubkeyZ32: String): FfiKeyBinding? {
        // NOTE: Homeserver fallback until PKARR DNS-over-HTTPS query is exposed by Pubky SDK.
        // Real implementation: resolve peer's PKARR TXT record → extract CBOR → keybindingDecode()
        try {
            val adapter = pubkyStorageAdapter.createUnauthenticatedAdapter(null)
            val path = "/pub/paykit.app/v0/keybinding"
            val cborBytes = pubkyStorageAdapter.retrieve(path, adapter, peerPubkeyZ32)

            if (cborBytes != null && cborBytes.isNotEmpty()) {
                return keybindingDecode(cborBytes)
            }
        } catch (e: Exception) {
            Logger.warn("PKARR/homeserver KeyBinding fetch failed: ${e.message}", e, context = TAG)
        }

        return null
    }
}
