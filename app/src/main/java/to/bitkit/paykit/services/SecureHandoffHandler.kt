package to.bitkit.paykit.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import to.bitkit.paykit.storage.PaykitKeychainStorage
import to.bitkit.utils.Logger
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Handles secure handoff payload fetching and processing for cross-device authentication.
 * 
 * Secure handoff v2: Payloads are encrypted using Paykit Sealed Blob v1 format.
 * Bitkit generates an ephemeral X25519 keypair, Ring encrypts to that key,
 * and Bitkit decrypts using the stored ephemeral secret.
 */
@Singleton
class SecureHandoffHandler @Inject constructor(
    private val noiseKeyCache: NoiseKeyCache,
    private val pubkyStorageAdapter: PubkyStorageAdapter,
    private val keychainStorage: PaykitKeychainStorage,
) {
    companion object {
        private const val TAG = "SecureHandoffHandler"
        private const val EPHEMERAL_KEY_KEY = "paykit.ephemeral_handoff_key"
    }

    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Store ephemeral secret key for handoff decryption.
     * Called before initiating the Ring request.
     */
    fun storeEphemeralKey(secretKeyHex: String) {
        keychainStorage.setStringSync(EPHEMERAL_KEY_KEY, secretKeyHex)
        Logger.debug("Stored ephemeral handoff key", context = TAG)
    }
    
    private fun getEphemeralKey(): String? = keychainStorage.getString(EPHEMERAL_KEY_KEY)
    
    private fun clearEphemeralKey() {
        keychainStorage.deleteSync(EPHEMERAL_KEY_KEY)
        Logger.debug("Cleared ephemeral handoff key", context = TAG)
    }

    suspend fun fetchAndProcessPayload(
        pubkey: String,
        requestId: String,
        scope: CoroutineScope,
        onSessionPersisted: suspend (PubkySession) -> Unit,
        ephemeralSecretKey: String? = null,
    ): PaykitSetupResult = withContext(Dispatchers.IO) {
        // Get ephemeral key (from parameter or stored)
        val secretKey = ephemeralSecretKey ?: getEphemeralKey()
        
        val payload = fetchHandoffPayload(pubkey, requestId, secretKey)
        
        // Clear ephemeral key now that we've decrypted
        if (ephemeralSecretKey == null) {
            clearEphemeralKey()
        }
        
        validatePayload(payload)
        val result = buildSetupResultFromPayload(payload)
        cacheAndPersistResult(result, payload.deviceId, scope, onSessionPersisted)
        schedulePayloadDeletion(result.session, requestId, scope)
        result
    }

    private suspend fun fetchHandoffPayload(
        pubkey: String,
        requestId: String,
        ephemeralSecretKey: String?,
    ): SecureHandoffPayload {
        val handoffUri = "pubky://$pubkey/pub/paykit.app/v0/handoff/$requestId"
        Logger.info("Fetching secure handoff payload from ${handoffUri.take(50)}...", context = TAG)

        val result = uniffi.pubkycore.get(handoffUri)
        if (result[0] == "error") {
            throw PubkyRingException.InvalidCallback
        }
        
        val payloadJson = result[1]
        
        // SECURITY: Require encrypted sealed blob - no plaintext fallback
        if (!com.pubky.noise.isSealedBlob(payloadJson)) {
            Logger.error("Handoff payload is not an encrypted sealed blob - rejecting", context = TAG)
            throw PubkyRingException.InvalidCallback
        }
        
        Logger.debug("Detected encrypted sealed blob envelope", context = TAG)
        return decryptHandoffEnvelope(payloadJson, pubkey, requestId, ephemeralSecretKey)
    }
    
    private fun decryptHandoffEnvelope(
        envelopeJson: String,
        pubkey: String,
        requestId: String,
        ephemeralSecretKey: String?,
    ): SecureHandoffPayload {
        if (ephemeralSecretKey == null) {
            Logger.error("Ephemeral key required for decryption but not found", context = TAG)
            throw PubkyRingException.MissingEphemeralKey
        }

        // Build AAD following Paykit v0 protocol: paykit:v0:handoff:{pubkey}:{path}:{requestId}
        val storagePath = "/pub/paykit.app/v0/handoff/$requestId"
        val aad = "paykit:v0:handoff:$pubkey:$storagePath:$requestId"

        try {
            // Convert secret key from hex to ByteArray
            val secretKeyBytes = hexStringToByteArray(ephemeralSecretKey)

            // Decrypt using pubky-noise sealed blob
            val plaintextBytes = com.pubky.noise.sealedBlobDecrypt(
                secretKeyBytes,
                envelopeJson,
                aad,
            )

            // Decode decrypted JSON
            val plaintextJson = plaintextBytes.toString(Charsets.UTF_8)
            val payload = json.decodeFromString<SecureHandoffPayload>(plaintextJson)
            Logger.info("Successfully decrypted handoff payload v${payload.version}", context = TAG)
            return payload
        } catch (e: Exception) {
            Logger.error("Sealed blob decryption failed: ${e.message}", e, context = TAG)
            throw PubkyRingException.DecryptionFailed(e.message ?: "Unknown error")
        }
    }
    
    @OptIn(ExperimentalStdlibApi::class)
    private fun hexStringToByteArray(hex: String): ByteArray =
        hex.hexToByteArray()

    private fun validatePayload(payload: SecureHandoffPayload) {
        if (System.currentTimeMillis() > payload.expiresAt) {
            throw PubkyRingException.Timeout
        }
    }

    private fun buildSetupResultFromPayload(payload: SecureHandoffPayload): PaykitSetupResult {
        val session = PubkySession(
            pubkey = payload.pubky,
            sessionSecret = payload.sessionSecret,
            capabilities = payload.capabilities,
            createdAt = Date(payload.createdAt),
            expiresAt = null,
        )

        var keypair0: NoiseKeypair? = null
        var keypair1: NoiseKeypair? = null

        for (kp in payload.noiseKeypairs) {
            val keypair = NoiseKeypair(
                publicKey = kp.publicKey,
                secretKey = kp.secretKey,
                deviceId = payload.deviceId,
                epoch = kp.epoch.toULong(),
            )

            when (kp.epoch) {
                0 -> keypair0 = keypair
                1 -> keypair1 = keypair
            }
        }

        Logger.info(
            "Secure handoff payload received for ${payload.pubky.take(12)}..., noiseSeed=${payload.noiseSeed != null}",
            context = TAG,
        )

        return PaykitSetupResult(
            session = session,
            deviceId = payload.deviceId,
            noiseKeypair0 = keypair0,
            noiseKeypair1 = keypair1,
            noiseSeed = payload.noiseSeed,
        )
    }

    private suspend fun cacheAndPersistResult(
        result: PaykitSetupResult,
        deviceId: String,
        scope: CoroutineScope,
        onSessionPersisted: suspend (PubkySession) -> Unit,
    ) {
        onSessionPersisted(result.session)

        result.noiseKeypair0?.let { keypair ->
            persistKeypair(keypair, deviceId, 0u)
        }
        result.noiseKeypair1?.let { keypair ->
            persistKeypair(keypair, deviceId, 1u)
        }
        
        // Persist noise seed for future epoch derivation
        result.noiseSeed?.let { seed ->
            persistNoiseSeed(seed, deviceId)
        }
    }
    
    private fun persistNoiseSeed(noiseSeed: String, deviceId: String) {
        try {
            val key = "paykit.noise_seed.$deviceId"
            keychainStorage.setStringSync(key, noiseSeed)
            Logger.debug("Persisted noise seed for device ${deviceId.take(8)}...", context = TAG)
        } catch (e: Exception) {
            Logger.warn("Failed to persist noise seed: ${e.message}", e, context = TAG)
        }
    }
    
    /**
     * Get stored noise seed for a device
     */
    fun getNoiseSeed(deviceId: String): String? {
        val key = "paykit.noise_seed.$deviceId"
        return keychainStorage.getString(key)
    }

    private fun persistKeypair(keypair: NoiseKeypair, deviceId: String, epoch: UInt) {
        try {
            val secretKeyData = keypair.secretKey.toByteArray(Charsets.UTF_8)
            noiseKeyCache.setKeySync(secretKeyData, deviceId, epoch)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn("Failed to store noise keypair epoch $epoch: ${e.message}", e, context = TAG)
        }
    }

    private fun schedulePayloadDeletion(session: PubkySession, requestId: String, scope: CoroutineScope) {
        scope.launch {
            try {
                val handoffPath = "/pub/paykit.app/v0/handoff/$requestId"
                val adapter = pubkyStorageAdapter.createAuthenticatedAdapter(
                    sessionSecret = session.sessionSecret,
                    ownerPubkey = session.pubkey,
                    homeserverURL = null,
                )
                val result = adapter.delete(handoffPath)
                if (result.success) {
                    Logger.info("Deleted secure handoff payload: $requestId", context = TAG)
                } else {
                    Logger.warn("Failed to delete handoff payload: ${result.error}", context = TAG)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warn("Error deleting handoff payload: ${e.message}", e, context = TAG)
            }
        }
    }
}

@Serializable
data class SecureHandoffPayload(
    val version: Int,
    val pubky: String,
    @SerialName("session_secret")
    val sessionSecret: String,
    val capabilities: List<String>,
    @SerialName("device_id")
    val deviceId: String,
    @SerialName("created_at")
    val createdAt: Long,
    @SerialName("expires_at")
    val expiresAt: Long,
    @SerialName("noise_keypairs")
    val noiseKeypairs: List<NoiseKeypairPayload>,
    /** Noise seed for local epoch derivation (so Bitkit doesn't need to re-call Ring) */
    @SerialName("noise_seed")
    val noiseSeed: String? = null,
)

@Serializable
data class NoiseKeypairPayload(
    val epoch: Int,
    @SerialName("public_key")
    val publicKey: String,
    @SerialName("secret_key")
    val secretKey: String,
)

