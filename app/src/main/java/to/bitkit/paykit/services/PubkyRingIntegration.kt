package to.bitkit.paykit.services

import uniffi.paykit_mobile.X25519Keypair
import uniffi.paykit_mobile.deriveX25519Keypair
import to.bitkit.paykit.KeyManager
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Integration for X25519 key derivation from Ed25519 identity
 * Uses PaykitMobile FFI to derive keys deterministically from identity seed
 */
@Singleton
class PubkyRingIntegration @Inject constructor(
    private val keyManager: KeyManager,
    private val noiseKeyCache: NoiseKeyCache
) {
    companion object {
        private const val TAG = "PubkyRingIntegration"
    }

    /**
     * Derive X25519 keypair from Ed25519 identity seed
     * Uses HKDF-based derivation via PaykitMobile FFI
     */
    suspend fun deriveX25519Keypair(deviceId: String, epoch: UInt): X25519Keypair {
        // Check cache first - we cache the secret key bytes
        val cachedSecret = noiseKeyCache.getKey(deviceId, epoch)
        if (cachedSecret != null) {
            // Reconstruct keypair from cached secret
            // Note: We need to compute public key from secret
            // For now, derive again (caching can be improved to store full keypair)
            Logger.debug("Found cached X25519 secret for device $deviceId, epoch $epoch", context = TAG)
        }

        // Get Ed25519 secret from KeyManager
        val ed25519SecretHex = keyManager.getSecretKeyHex()
            ?: throw NoisePaymentError.NoIdentity

        // Derive X25519 keypair using PaykitMobile FFI
        val keypair = try {
            deriveX25519Keypair(ed25519SecretHex, deviceId, epoch)
        } catch (e: Exception) {
            Logger.error("Failed to derive X25519 keypair", e, context = TAG)
            throw NoisePaymentError.KeyDerivationFailed(e.message ?: "Unknown error")
        }

        // Cache the secret key bytes
        val secretBytes = keypair.secretKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        noiseKeyCache.setKey(secretBytes, deviceId, epoch)

        Logger.debug("Derived X25519 keypair for device $deviceId, epoch $epoch", context = TAG)
        return keypair
    }

    /**
     * Get or derive X25519 keypair with caching
     */
    suspend fun getOrDeriveKeypair(deviceId: String, epoch: UInt): X25519Keypair {
        return deriveX25519Keypair(deviceId, epoch)
    }
}
