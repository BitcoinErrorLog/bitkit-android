package to.bitkit.paykit.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import uniffi.paykit_mobile.X25519Keypair
import to.bitkit.paykit.KeyManager
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Integration for X25519 keypair retrieval from Pubky Ring
 * 
 * SECURITY: All key derivation happens in Pubky Ring.
 * This class only retrieves cached keypairs that were received via Ring callbacks.
 * If no cached keypair is available, callers must request new keys from Ring.
 */
@Singleton
class PubkyRingIntegration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: KeyManager,
    private val noiseKeyCache: NoiseKeyCache,
    private val pubkyRingBridge: PubkyRingBridge,
) {
    companion object {
        private const val TAG = "PubkyRingIntegration"
    }

    /**
     * Get cached X25519 keypair for the given epoch
     * 
     * This retrieves a keypair that was previously received from Pubky Ring.
     * If no keypair is cached, the caller should request new keys via PubkyRingBridge.
     *
     * @param deviceId The device ID used for derivation context
     * @param epoch The epoch for this keypair
     * @return The cached keypair
     * @throws NoisePaymentError.NoKeypairCached if no keypair is available
     */
    fun getCachedKeypair(deviceId: String, epoch: UInt): X25519Keypair {
        // First check NoiseKeyCache (legacy cache) - use sync version
        val cachedSecret = noiseKeyCache.getKeySync(deviceId, epoch)
        if (cachedSecret != null) {
            // We have a cached secret but need the full keypair
            // Check KeyManager for full keypair
            val keypair = keyManager.getCachedNoiseKeypair(epoch)
            if (keypair != null) {
                return keypair
            }
        }

        // Check KeyManager directly
        val keypair = keyManager.getCachedNoiseKeypair(epoch)
        if (keypair != null) {
            return keypair
        }

        throw NoisePaymentError.NoKeypairCached(
            "No X25519 keypair cached for epoch $epoch. Please reconnect to Pubky Ring."
        )
    }

    /**
     * Get the current noise keypair (for current epoch)
     * @return The cached keypair for current epoch
     * @throws NoisePaymentError.NoKeypairCached if no keypair is available
     */
    fun getCurrentKeypair(): X25519Keypair {
        val deviceId = keyManager.getDeviceId()
        val epoch = keyManager.getCurrentEpoch()
        return getCachedKeypair(deviceId, epoch)
    }

    /**
     * Check if we have a cached keypair for the current epoch
     */
    fun hasCurrentKeypair(): Boolean = keyManager.hasNoiseKeypair()

    /**
     * Get or refresh X25519 keypair with automatic cache miss recovery
     *
     * If the keypair is cached, returns it immediately.
     * If not cached, automatically requests new setup from Ring.
     *
     * @param deviceId The device ID used for derivation context
     * @param epoch The epoch for this keypair
     * @return The keypair (either cached or freshly retrieved)
     * @throws NoisePaymentError.NoKeypairCached if Ring request fails
     */
    suspend fun getOrRefreshKeypair(deviceId: String, epoch: UInt): X25519Keypair {
        // Try cache first
        return try {
            getCachedKeypair(deviceId, epoch)
        } catch (e: NoisePaymentError.NoKeypairCached) {
            // Cache miss - request new setup from Ring
            Logger.warn("Keypair cache miss for epoch $epoch, requesting from Ring", context = TAG)
            pubkyRingBridge.requestPaykitSetup(context)
            
            // The bridge callback handler will have cached the result
            // Try retrieving again
            try {
                getCachedKeypair(deviceId, epoch)
            } catch (e2: NoisePaymentError.NoKeypairCached) {
                // Still not available - this shouldn't happen
                throw NoisePaymentError.NoKeypairCached(
                    "Failed to refresh keypair from Ring for epoch $epoch"
                )
            }
        }
    }

    /**
     * Get the current keypair with automatic refresh on cache miss
     * @return The cached or refreshed keypair for current epoch
     * @throws NoisePaymentError.NoKeypairCached if Ring request fails
     */
    suspend fun getCurrentKeypairOrRefresh(): X25519Keypair {
        val deviceId = keyManager.getDeviceId()
        val epoch = keyManager.getCurrentEpoch()
        return getOrRefreshKeypair(deviceId, epoch)
    }

    /**
     * Cache a keypair received from Pubky Ring
     * Called by PubkyRingBridge when receiving keypairs via callback
     */
    suspend fun cacheKeypair(keypair: X25519Keypair, deviceId: String, epoch: UInt) {
        // Store in KeyManager (primary cache)
        keyManager.cacheNoiseKeypair(keypair, epoch)

        // Also store secret in NoiseKeyCache for backward compatibility
        val secretBytes = keypair.secretKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        noiseKeyCache.setKey(secretBytes, deviceId, epoch)

        Logger.debug("Cached X25519 keypair for device $deviceId, epoch $epoch", context = TAG)
    }
}
