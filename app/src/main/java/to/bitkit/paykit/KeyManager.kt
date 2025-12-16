package to.bitkit.paykit

import android.content.Context
import android.os.Build
import com.paykit.mobile.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import to.bitkit.data.keychain.Keychain
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Ed25519 identity keys and X25519 device keys for Paykit
 * Uses Bitkit's Keychain for secure storage
 */
@Singleton
class KeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keychain: Keychain
) {
    companion object {
        private const val TAG = "PaykitKeyManager"
        private const val KEY_SECRET = "paykit.identity.secret"
        private const val KEY_PUBLIC = "paykit.identity.public"
        private const val KEY_PUBLIC_Z32 = "paykit.identity.public.z32"
        private const val KEY_DEVICE_ID = "paykit.device.id"
        private const val KEY_EPOCH = "paykit.device.epoch"
    }

    private val _hasIdentity = MutableStateFlow(false)
    val hasIdentity: StateFlow<Boolean> = _hasIdentity.asStateFlow()

    private val _publicKeyZ32 = MutableStateFlow("")
    val publicKeyZ32: StateFlow<String> = _publicKeyZ32.asStateFlow()

    private val _publicKeyHex = MutableStateFlow("")
    val publicKeyHex: StateFlow<String> = _publicKeyHex.asStateFlow()

    private val deviceId: String = getOrCreateDeviceId()
    private var currentEpoch: UInt = loadEpoch()

    init {
        loadIdentityState()
    }

    /**
     * Get or create Ed25519 identity
     */
    suspend fun getOrCreateIdentity(): Ed25519Keypair {
        val existingSecret = keychain.loadString(KEY_SECRET)
        return if (existingSecret != null) {
            ed25519KeypairFromSecret(existingSecret)
        } else {
            generateNewIdentity()
        }
    }

    /**
     * Generate a new Ed25519 identity
     */
    suspend fun generateNewIdentity(): Ed25519Keypair {
        val keypair = generateEd25519Keypair()

        // Store in keychain
        keychain.upsertString(KEY_SECRET, keypair.secretKeyHex)
        keychain.upsertString(KEY_PUBLIC, keypair.publicKeyHex)
        keychain.upsertString(KEY_PUBLIC_Z32, keypair.publicKeyZ32)

        // Update state
        _hasIdentity.value = true
        _publicKeyZ32.value = keypair.publicKeyZ32
        _publicKeyHex.value = keypair.publicKeyHex

        Logger.info("Generated new Paykit identity: ${keypair.publicKeyZ32.take(16)}...", context = TAG)
        return keypair
    }

    /**
     * Get current public key in z-base32 format
     */
    fun getCurrentPublicKeyZ32(): String? {
        return keychain.loadString(KEY_PUBLIC_Z32)
    }

    /**
     * Get current secret key hex
     */
    fun getSecretKeyHex(): String? {
        return keychain.loadString(KEY_SECRET)
    }

    /**
     * Get secret key as bytes
     */
    fun getSecretKeyBytes(): ByteArray? {
        val hex = getSecretKeyHex() ?: return null
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    /**
     * Derive X25519 keypair for Noise protocol
     */
    fun deriveX25519Keypair(epoch: UInt = currentEpoch): X25519Keypair {
        val secretHex = getSecretKeyHex() ?: throw PaykitException.Unknown("No identity configured")
        return deriveX25519Keypair(secretHex, deviceId, epoch)
    }

    /**
     * Get device ID
     */
    fun getDeviceId(): String = deviceId

    /**
     * Get current epoch
     */
    fun getCurrentEpoch(): UInt = currentEpoch

    /**
     * Rotate keys by incrementing epoch
     */
    suspend fun rotateKeys() {
        currentEpoch++
        saveEpoch(currentEpoch)
        Logger.info("Rotated Paykit keys to epoch $currentEpoch", context = TAG)
    }

    /**
     * Delete identity
     */
    suspend fun deleteIdentity() {
        keychain.delete(KEY_SECRET)
        keychain.delete(KEY_PUBLIC)
        keychain.delete(KEY_PUBLIC_Z32)
        _hasIdentity.value = false
        _publicKeyZ32.value = ""
        _publicKeyHex.value = ""
    }

    // MARK: - Private

    private fun loadIdentityState() {
        val pubkeyZ32 = keychain.loadString(KEY_PUBLIC_Z32)
        val pubkeyHex = keychain.loadString(KEY_PUBLIC)

        if (pubkeyZ32 != null && pubkeyHex != null) {
            _hasIdentity.value = true
            _publicKeyZ32.value = pubkeyZ32
            _publicKeyHex.value = pubkeyHex
        }
    }

    private fun getOrCreateDeviceId(): String {
        val existing = keychain.loadString(KEY_DEVICE_ID)
        if (existing != null) {
            return existing
        }

        val newId = "${Build.MANUFACTURER}_${Build.MODEL}_${Build.ID}_${System.currentTimeMillis()}"
        // Note: We can't use suspend here, so we'll store it on first use
        return newId
    }

    private fun loadEpoch(): UInt {
        val epochStr = keychain.loadString(KEY_EPOCH)
        return epochStr?.toUIntOrNull() ?: 0u
    }

    private suspend fun saveEpoch(epoch: UInt) {
        keychain.upsertString(KEY_EPOCH, epoch.toString())
    }
}

