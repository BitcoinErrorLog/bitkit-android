package to.bitkit.paykit

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.bitkit.data.keychain.Keychain
import to.bitkit.utils.Logger
import uniffi.paykit_mobile.X25519Keypair
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages device identity and X25519 noise keys for Paykit
 *
 * SECURITY: Ed25519 master keys are owned exclusively by Pubky Ring.
 * Bitkit only stores:
 * - Public key (z-base32) for identification
 * - Device ID for key derivation context
 * - Epoch for key rotation
 * - Cached X25519 noise keypairs (derived by Ring)
 */
@Singleton
class KeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keychain: Keychain,
) {
    companion object {
        private const val TAG = "PaykitKeyManager"
        private const val KEY_PUBLIC_Z32 = "paykit.identity.public.z32"
        private const val KEY_DEVICE_ID = "paykit.device.id"
        private const val KEY_EPOCH = "paykit.device.epoch"
        private const val KEY_NOISE_KEYPAIR_PREFIX = "paykit.noise.keypair."
    }

    private val _hasIdentity = MutableStateFlow(false)
    val hasIdentity: StateFlow<Boolean> = _hasIdentity.asStateFlow()

    private val _publicKeyZ32 = MutableStateFlow("")
    val publicKeyZ32: StateFlow<String> = _publicKeyZ32.asStateFlow()

    private val deviceId: String = getOrCreateDeviceId()
    private var currentEpoch: UInt = loadEpoch()

    init {
        loadIdentityState()
    }

    // MARK: - Public Key (from Ring)

    /**
     * Store public key received from Pubky Ring
     */
    suspend fun storePublicKey(pubkeyZ32: String) {
        keychain.upsertString(KEY_PUBLIC_Z32, pubkeyZ32)
        _hasIdentity.value = true
        _publicKeyZ32.value = pubkeyZ32
        Logger.info("Stored Paykit public key: ${pubkeyZ32.take(16)}...", context = TAG)
    }

    /**
     * Get current public key in z-base32 format
     */
    fun getCurrentPublicKeyZ32(): String? = keychain.loadString(KEY_PUBLIC_Z32)

    // MARK: - Device Management

    /**
     * Get device ID (used for key derivation context)
     */
    fun getDeviceId(): String = deviceId

    /**
     * Get current epoch (used for key rotation)
     */
    fun getCurrentEpoch(): UInt = currentEpoch

    /**
     * Set current epoch to a specific value
     * Used for key rotation when switching to a pre-cached epoch
     */
    suspend fun setCurrentEpoch(epoch: UInt) {
        currentEpoch = epoch
        saveEpoch(epoch)
    }

    /**
     * Rotate keys by incrementing epoch
     */
    suspend fun rotateKeys() {
        currentEpoch++
        saveEpoch(currentEpoch)
        Logger.info("Rotated Paykit keys to epoch $currentEpoch", context = TAG)
    }

    // MARK: - X25519 Noise Keypair Caching

    /**
     * Serializable wrapper for X25519 keypair storage
     */
    @Serializable
    private data class StoredKeypair(
        val publicKeyHex: String,
        val secretKeyHex: String,
    )

    /**
     * Cache an X25519 noise keypair received from Pubky Ring
     * @param keypair The X25519 keypair from Ring
     * @param epoch The epoch this keypair was derived for
     */
    suspend fun cacheNoiseKeypair(keypair: X25519Keypair, epoch: UInt) {
        val key = noiseKeypairKey(epoch)
        val stored = StoredKeypair(
            publicKeyHex = keypair.publicKeyHex,
            secretKeyHex = keypair.secretKeyHex,
        )
        val json = Json.encodeToString(stored)
        keychain.upsertString(key, json)
        Logger.debug("Cached noise keypair for epoch $epoch", context = TAG)
    }

    /**
     * Get cached X25519 noise keypair for a given epoch
     * @param epoch The epoch to retrieve keypair for (defaults to current)
     * @return The cached keypair, or null if not cached
     */
    fun getCachedNoiseKeypair(epoch: UInt = currentEpoch): X25519Keypair? {
        val key = noiseKeypairKey(epoch)
        val json = keychain.loadString(key) ?: return null
        return try {
            val stored = Json.decodeFromString<StoredKeypair>(json)
            X25519Keypair(
                stored.secretKeyHex,
                stored.publicKeyHex,
                getDeviceId(),
                epoch,
            )
        } catch (e: Exception) {
            Logger.warn("Failed to decode cached keypair for epoch $epoch", e, context = TAG)
            null
        }
    }

    /**
     * Check if we have a cached noise keypair for the current epoch
     */
    fun hasNoiseKeypair(): Boolean = getCachedNoiseKeypair() != null

    // MARK: - Cleanup

    /**
     * Delete all Paykit identity data
     */
    suspend fun deleteIdentity() {
        keychain.delete(KEY_PUBLIC_Z32)
        // Clean up noise keypairs for epochs 0-10 (reasonable range)
        for (epoch in 0u..10u) {
            keychain.delete(noiseKeypairKey(epoch))
        }
        _hasIdentity.value = false
        _publicKeyZ32.value = ""
        Logger.info("Deleted Paykit identity", context = TAG)
    }

    // MARK: - Private

    private fun loadIdentityState() {
        val pubkeyZ32 = keychain.loadString(KEY_PUBLIC_Z32)
        if (pubkeyZ32 != null) {
            _hasIdentity.value = true
            _publicKeyZ32.value = pubkeyZ32
        }
    }

    private fun getOrCreateDeviceId(): String {
        val existing = keychain.loadString(KEY_DEVICE_ID)
        if (existing != null) {
            return existing
        }

        val newId = "${Build.MANUFACTURER}_${Build.MODEL}_${Build.ID}_${System.currentTimeMillis()}"
        // Note: We store it synchronously on first access
        // This is acceptable since device ID is not sensitive
        return newId
    }

    private fun loadEpoch(): UInt {
        val epochStr = keychain.loadString(KEY_EPOCH)
        return epochStr?.toUIntOrNull() ?: 0u
    }

    private suspend fun saveEpoch(epoch: UInt) {
        keychain.upsertString(KEY_EPOCH, epoch.toString())
    }

    private fun noiseKeypairKey(epoch: UInt): String = "$KEY_NOISE_KEYPAIR_PREFIX$deviceId.$epoch"
}
