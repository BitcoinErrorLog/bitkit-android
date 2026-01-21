package to.bitkit.paykit.services

import com.pubky.noise.FfiAppKeyEntry
import com.pubky.noise.FfiInboxKeyEntry
import com.pubky.noise.FfiKeyBinding
import com.pubky.noise.FfiTransportKeyEntry
import com.pubky.noise.keybindingDecode
import org.json.JSONObject
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
     * Fetch KeyBinding from homeserver for the given peer.
     *
     * Attempts to parse both JSON (from pubky-ring handoff) and CBOR formats.
     */
    private suspend fun fetchKeyBindingFromPkarr(peerPubkeyZ32: String): FfiKeyBinding? {
        try {
            val adapter = pubkyStorageAdapter.createUnauthenticatedAdapter(null)
            val path = "/pub/paykit.app/v0/keybinding"
            val bytes = pubkyStorageAdapter.retrieve(path, adapter, peerPubkeyZ32)

            if (bytes == null || bytes.isEmpty()) {
                return null
            }

            // Try JSON first (from pubky-ring handoff which stores JSON)
            val jsonKeyBinding = tryParseJsonKeyBinding(bytes)
            if (jsonKeyBinding != null) {
                return jsonKeyBinding
            }

            // Fall back to CBOR (future-proof for PKARR-native KeyBinding)
            return keybindingDecode(bytes)
        } catch (e: Exception) {
            Logger.warn("KeyBinding fetch failed for ${peerPubkeyZ32.take(PUBKEY_LOG_LEN)}...: ${e.message}", e, context = TAG)
        }

        return null
    }

    /**
     * Try to parse KeyBinding from JSON format (stored by pubky-ring).
     *
     * Expected JSON structure:
     * {
     *   "inbox_keys": [{"inbox_kid": "hex", "x25519_pub": "hex"}],
     *   "transport_keys": [{"x25519_pub": "hex"}],
     *   "app_keys": [{"cert_id": "hex", "ed25519_pub": "hex"}]
     * }
     */
    private fun tryParseJsonKeyBinding(bytes: ByteArray): FfiKeyBinding? {
        return try {
            val jsonStr = String(bytes, Charsets.UTF_8)
            // Quick check if it looks like JSON
            if (!jsonStr.trimStart().startsWith("{")) {
                return null
            }

            val json = JSONObject(jsonStr)

            // Parse inbox_keys
            val inboxKeys = mutableListOf<FfiInboxKeyEntry>()
            val inboxKeysArray = json.optJSONArray("inbox_keys")
            if (inboxKeysArray != null) {
                for (i in 0 until inboxKeysArray.length()) {
                    val entry = inboxKeysArray.getJSONObject(i)
                    inboxKeys.add(
                        FfiInboxKeyEntry(
                            inboxKidHex = entry.getString("inbox_kid"),
                            x25519PubHex = entry.getString("x25519_pub"),
                        ),
                    )
                }
            }

            // Parse transport_keys
            val transportKeys = mutableListOf<FfiTransportKeyEntry>()
            val transportKeysArray = json.optJSONArray("transport_keys")
            if (transportKeysArray != null) {
                for (i in 0 until transportKeysArray.length()) {
                    val entry = transportKeysArray.getJSONObject(i)
                    transportKeys.add(
                        FfiTransportKeyEntry(x25519PubHex = entry.getString("x25519_pub")),
                    )
                }
            }

            // Parse app_keys (optional)
            val appKeys = mutableListOf<FfiAppKeyEntry>()
            val appKeysArray = json.optJSONArray("app_keys")
            if (appKeysArray != null) {
                for (i in 0 until appKeysArray.length()) {
                    val entry = appKeysArray.getJSONObject(i)
                    appKeys.add(
                        FfiAppKeyEntry(
                            certIdHex = entry.getString("cert_id"),
                            ed25519PubHex = entry.getString("ed25519_pub"),
                        ),
                    )
                }
            }

            FfiKeyBinding(
                inboxKeys = inboxKeys,
                transportKeys = transportKeys,
                appKeys = if (appKeys.isNotEmpty()) appKeys else null,
            )
        } catch (e: Exception) {
            // Not JSON or parsing failed, return null to try CBOR
            null
        }
    }
}
