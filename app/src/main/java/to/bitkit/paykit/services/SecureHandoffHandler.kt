package to.bitkit.paykit.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import to.bitkit.paykit.storage.PaykitKeychainStorage
import to.bitkit.paykit.utils.z32Decode
import to.bitkit.utils.Logger
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Handles secure handoff payload fetching and processing for cross-device authentication.
 *
 * Secure handoff v2: Payloads are encrypted using Sealed Blob v2 format with spec-compliant
 * binary AAD per PUBKY_CRYPTO_SPEC. Bitkit generates an ephemeral X25519 keypair, Ring
 * encrypts to that key using sealedBlobEncryptWithContext, and Bitkit decrypts using
 * sealedBlobDecryptWithContext with the stored ephemeral secret.
 */
@Singleton
class SecureHandoffHandler @Inject constructor(
    private val noiseKeyCache: NoiseKeyCache,
    private val pubkyStorageAdapter: PubkyStorageAdapter,
    private val keychainStorage: PaykitKeychainStorage,
    private val directoryServiceProvider: dagger.Lazy<DirectoryService>,
    private val keyManager: to.bitkit.paykit.KeyManager,
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
        homeserver: String? = null,
    ): PaykitSetupResult = withContext(Dispatchers.IO) {
        // Get ephemeral key (from parameter or stored)
        val secretKey = ephemeralSecretKey ?: getEphemeralKey()

        Logger.debug(
            "fetchAndProcessPayload: pubkey=${pubkey.take(16)}..., requestId=${requestId.take(16)}..., " +
                "ephemeralKey=${if (secretKey != null) "present(${secretKey.length} chars, prefix=${secretKey.take(16)})" else "MISSING"}",
            context = TAG,
        )

        val payload = fetchHandoffPayload(pubkey, requestId, secretKey)

        // Clear ephemeral key now that we've decrypted
        if (ephemeralSecretKey == null) {
            clearEphemeralKey()
        }

        validatePayload(payload)
        val result = buildSetupResultFromPayload(payload, homeserver)
        cacheAndPersistResult(result, payload, payload.deviceId, scope, onSessionPersisted)
        schedulePayloadDeletion(result.session, requestId, scope)

        // Verify Ring published the Noise endpoint, or publish it ourselves as fallback
        scope.launch {
            ensureNoiseEndpointPublished(pubkey, result.noiseKeypair0?.publicKey, payload.deviceId)
        }

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
        val canonicalPath = "/pub/paykit.app/v0/handoff/$requestId"

        // DEBUG: Log the first 200 chars of payload to diagnose format issues
        Logger.debug("Payload received (${payloadJson.length} chars): ${payloadJson.take(200)}", context = TAG)

        // Check for SB2 binary wrapper: {"sb2": base64, ...}
        if (payloadJson.contains("\"sb2\"")) {
            Logger.debug("Detected SB2 binary wrapper format", context = TAG)
            return decryptSb2Envelope(payloadJson, pubkey, canonicalPath, ephemeralSecretKey)
        }

        // SECURITY: Require encrypted sealed blob - no plaintext fallback
        if (!com.pubky.noise.isSealedBlob(payloadJson)) {
            Logger.error(
                "Handoff payload is not an encrypted sealed blob - rejecting. Contains 'v':1 = ${payloadJson.contains(
                    "\"v\":1"
                )} or 'v': 1 = ${payloadJson.contains("\"v\": 1")}",
                context = TAG
            )
            throw PubkyRingException.InvalidCallback
        }

        Logger.debug("Detected JSON sealed blob envelope", context = TAG)
        return decryptHandoffEnvelope(payloadJson, pubkey, requestId, ephemeralSecretKey)
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun decryptSb2Envelope(
        wrapperJson: String,
        pubkey: String,
        canonicalPath: String,
        ephemeralSecretKey: String?,
    ): SecureHandoffPayload {
        if (ephemeralSecretKey == null) {
            Logger.error("Ephemeral key required for SB2 decryption but not found", context = TAG)
            throw PubkyRingException.MissingEphemeralKey
        }

        try {
            // Parse the wrapper JSON to extract base64-encoded SB2 bytes
            val wrapperObj = org.json.JSONObject(wrapperJson)
            val sb2Base64 = wrapperObj.getString("sb2")

            Logger.debug(
                "SB2 base64 length=${sb2Base64.length}, first16=${sb2Base64.take(16)}, last16=${sb2Base64.takeLast(16)}",
                context = TAG,
            )

            val sb2Bytes = android.util.Base64.decode(sb2Base64, android.util.Base64.NO_WRAP)

            Logger.debug(
                "SB2 decoded bytes size=${sb2Bytes.size}, magic=${sb2Bytes.take(3).toByteArray().toString(Charsets.US_ASCII)}",
                context = TAG,
            )

            // Verify it's a valid SB2 envelope
            if (!com.pubky.noise.sb2IsSb2(sb2Bytes)) {
                Logger.error("Invalid SB2 envelope in wrapper (sb2IsSb2=false, size=${sb2Bytes.size})", context = TAG)
                throw PubkyRingException.InvalidCallback
            }

            // Decrypt SB2 using ephemeral secret key as InboxKey
            val secretKeyBytes = hexStringToByteArray(ephemeralSecretKey)
            val ownerPeeridBytes = z32Decode(pubkey)

            Logger.debug(
                "SB2 decrypt params: sk_size=${secretKeyBytes.size}, sk_prefix=${secretKeyBytes.toHexString().take(16)}, " +
                    "owner_size=${ownerPeeridBytes.size}, owner_hex=${ownerPeeridBytes.toHexString().take(16)}..., " +
                    "pubkey_z32=${pubkey.take(16)}..., canonical=$canonicalPath",
                context = TAG,
            )

            val decryptResult = com.pubky.noise.sb2Decrypt(
                sb2Bytes,
                secretKeyBytes,
                ownerPeeridBytes,
                canonicalPath,
            )

            // Decode decrypted JSON
            val plaintextJson = decryptResult.plaintext.toString(Charsets.UTF_8)
            val payload = json.decodeFromString<SecureHandoffPayload>(plaintextJson)
            Logger.info("Successfully decrypted SB2 handoff payload v${payload.version}", context = TAG)
            return payload
        } catch (e: PubkyRingException) {
            throw e
        } catch (e: Exception) {
            Logger.error("SB2 decryption failed: ${e.message}", e, context = TAG)

            // Attempt fallback: try decoding with Base64.DEFAULT in case of encoding mismatch
            try {
                Logger.debug("Attempting SB2 decode with Base64.DEFAULT flag as fallback", context = TAG)
                val wrapperObj = org.json.JSONObject(wrapperJson)
                val sb2Base64 = wrapperObj.getString("sb2")
                val sb2BytesFallback = android.util.Base64.decode(sb2Base64, android.util.Base64.DEFAULT)
                val secretKeyBytes = hexStringToByteArray(ephemeralSecretKey)
                val ownerPeeridBytes = z32Decode(pubkey)

                Logger.debug(
                    "Fallback SB2 bytes size=${sb2BytesFallback.size} (original was different=${sb2BytesFallback.size != android.util.Base64.decode(sb2Base64, android.util.Base64.NO_WRAP).size})",
                    context = TAG,
                )

                val decryptResult = com.pubky.noise.sb2Decrypt(
                    sb2BytesFallback,
                    secretKeyBytes,
                    ownerPeeridBytes,
                    canonicalPath,
                )
                val plaintextJson = decryptResult.plaintext.toString(Charsets.UTF_8)
                val payload = json.decodeFromString<SecureHandoffPayload>(plaintextJson)
                Logger.info("SB2 fallback decryption succeeded with DEFAULT flag, v${payload.version}", context = TAG)
                return payload
            } catch (fallbackError: Exception) {
                Logger.debug("SB2 fallback also failed: ${fallbackError.message}", context = TAG)
            }

            // Attempt fallback: try JSON sealed blob decryption in case Ring fell back but stored in sb2 wrapper
            try {
                Logger.debug("Attempting JSON sealed blob fallback for SB2-wrapped content", context = TAG)
                val wrapperObj = org.json.JSONObject(wrapperJson)
                val sb2Base64 = wrapperObj.getString("sb2")
                val decodedStr = String(android.util.Base64.decode(sb2Base64, android.util.Base64.NO_WRAP), Charsets.UTF_8)
                if (com.pubky.noise.isSealedBlob(decodedStr)) {
                    Logger.debug("Decoded content IS a JSON sealed blob, attempting sealedBlobDecryptWithContext", context = TAG)
                    val secretKeyBytes = hexStringToByteArray(ephemeralSecretKey)
                    val ownerPeeridBytes = z32Decode(pubkey)
                    val plaintextBytes = com.pubky.noise.sealedBlobDecryptWithContext(
                        secretKeyBytes,
                        decodedStr,
                        ownerPeeridBytes,
                        canonicalPath,
                    )
                    val plaintextJson = plaintextBytes.toString(Charsets.UTF_8)
                    val payload = json.decodeFromString<SecureHandoffPayload>(plaintextJson)
                    Logger.info("JSON sealed blob fallback succeeded, v${payload.version}", context = TAG)
                    return payload
                }
            } catch (jsonFallbackError: Exception) {
                Logger.debug("JSON sealed blob fallback also failed: ${jsonFallbackError.message}", context = TAG)
            }

            throw PubkyRingException.DecryptionFailed(e.message ?: "Unknown error")
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
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

        // Spec-compliant AAD construction per PUBKY_CRYPTO_SPEC v2:
        // AAD = "pubky-envelope/v2:" || owner_peerid_bytes || canonical_path_bytes || header_bytes
        // sealedBlobDecryptWithContext handles AAD construction internally
        val canonicalPath = "/pub/paykit.app/v0/handoff/$requestId"

        try {
            // Convert secret key from hex to ByteArray
            val secretKeyBytes = hexStringToByteArray(ephemeralSecretKey)

            // Derive ephemeral PK from SK so we can compare with what Ring received
            val derivedPkBytes = com.pubky.noise.x25519PublicFromSecret(secretKeyBytes)

            // Convert pubkey (z32) to raw Ed25519 bytes (owner_peerid)
            val ownerPeeridBytes = z32Decode(pubkey)

            // Log ALL parameters for diagnosis
            Logger.debug(
                "DECRYPT PARAMS:\n" +
                    "  ephemeralSk(hex): ${secretKeyBytes.toHexString()}\n" +
                    "  derivedPk(hex): ${derivedPkBytes.toHexString()}\n" +
                    "  ownerPeerid(hex): ${ownerPeeridBytes.toHexString()}\n" +
                    "  ownerPeerid(z32): $pubkey\n" +
                    "  canonicalPath: $canonicalPath\n" +
                    "  envelopeJson(full): $envelopeJson",
                context = TAG,
            )

            // Decrypt using spec-compliant sealed blob with context
            val plaintextBytes = com.pubky.noise.sealedBlobDecryptWithContext(
                secretKeyBytes,
                envelopeJson,
                ownerPeeridBytes,
                canonicalPath,
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
        // Validate payload version (MUST be v2 or v3 per PUBKY_CRYPTO_SPEC)
        // v2: Basic handoff with noise keypairs
        // v3: Adds InboxKey for SB2 stored delivery and AppKey for delegated signing
        if (payload.version !in listOf(2, 3)) {
            Logger.error(
                "Unsupported handoff version: expected 2 or 3, got ${payload.version}",
                context = TAG,
            )
            throw PubkyRingException.InvalidVersion(
                "Unsupported handoff version. Expected v2 or v3, got v${payload.version}. Please update Pubky Ring."
            )
        }

        // Ring sends timestamps in Unix seconds per PUBKY_CRYPTO_SPEC
        val nowSeconds = System.currentTimeMillis() / 1000
        if (nowSeconds > payload.expiresAt) {
            throw PubkyRingException.Timeout
        }
    }

    private fun buildSetupResultFromPayload(
        payload: SecureHandoffPayload,
        homeserver: String? = null,
    ): PaykitSetupResult {
        // Ring sends timestamps in Unix seconds per PUBKY_CRYPTO_SPEC
        // Convert to milliseconds for Date constructor
        val session = PubkySession(
            pubkey = payload.pubky,
            sessionSecret = payload.sessionSecret,
            capabilities = payload.capabilities,
            createdAt = Date(payload.createdAt * 1000),
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
            "Secure handoff payload received for ${payload.pubky.take(12)}..., noiseSeed=${payload.noiseSeed != null}, homeserver=$homeserver",
            context = TAG,
        )

        return PaykitSetupResult(
            session = session,
            deviceId = payload.deviceId,
            noiseKeypair0 = keypair0,
            noiseKeypair1 = keypair1,
            noiseSeed = payload.noiseSeed,
            homeserver = homeserver,
        )
    }

    private suspend fun cacheAndPersistResult(
        result: PaykitSetupResult,
        payload: SecureHandoffPayload,
        deviceId: String,
        scope: CoroutineScope,
        onSessionPersisted: suspend (PubkySession) -> Unit,
    ) {
        onSessionPersisted(result.session)

        result.noiseKeypair0?.let { keypair ->
            persistKeypair(keypair, deviceId, 0u)
            // Also cache to KeyManager for unified access
            cacheToKeyManager(keypair, 0u)
        }
        result.noiseKeypair1?.let { keypair ->
            persistKeypair(keypair, deviceId, 1u)
            // Also cache to KeyManager for unified access
            cacheToKeyManager(keypair, 1u)
        }

        // Persist noise seed for future epoch derivation
        result.noiseSeed?.let { seed ->
            persistNoiseSeed(seed, deviceId)
        }

        // Persist InboxKey for SB2 stored delivery (v3+)
        payload.inboxKeypair?.let { inboxKp ->
            persistInboxKeypair(inboxKp)
        }

        // Persist AppKey for delegated signing (v3+)
        payload.appKey?.let { appKey ->
            persistAppKey(appKey)
        }
    }

    private suspend fun persistInboxKeypair(inboxKeypair: InboxKeypairPayload) {
        try {
            keychainStorage.storeInboxKeypair(inboxKeypair.publicKey, inboxKeypair.secretKey)
            Logger.info("Persisted InboxKey from handoff", context = TAG)
        } catch (e: Exception) {
            Logger.warn("Failed to persist InboxKey: ${e.message}", e, context = TAG)
        }
    }

    private suspend fun persistAppKey(appKey: AppKeyPayload) {
        try {
            keychainStorage.storeAppKey(
                ed25519Sk = appKey.ed25519Sk,
                ed25519Pk = appKey.ed25519Pk,
                certId = appKey.certId,
                certBody = appKey.certBody,
                certSig = appKey.certSig,
            )
            Logger.info("Persisted AppKey from handoff (cert_id=${appKey.certId.take(16)}...)", context = TAG)
        } catch (e: Exception) {
            Logger.warn("Failed to persist AppKey: ${e.message}", e, context = TAG)
        }
    }

    private suspend fun cacheToKeyManager(keypair: NoiseKeypair, epoch: UInt) {
        try {
            val x25519Keypair = uniffi.paykit_mobile.X25519Keypair(
                keypair.secretKey,
                keypair.publicKey,
                keypair.deviceId,
                epoch,
            )
            keyManager.cacheNoiseKeypair(x25519Keypair, epoch)
            Logger.debug("Cached keypair to KeyManager for epoch $epoch", context = TAG)
        } catch (e: Exception) {
            Logger.warn("Failed to cache keypair to KeyManager: ${e.message}", e, context = TAG)
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
            // keypair.secretKey is a hex string (64 chars for 32-byte X25519 key)
            // Decode hex to actual key bytes before storing
            val secretKeyData = keypair.secretKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            noiseKeyCache.setKeySync(secretKeyData, deviceId, epoch)
            Logger.debug("Stored noise keypair for epoch $epoch (${secretKeyData.size} bytes)", context = TAG)
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

    /**
     * Ensure the Noise endpoint is published for discoverability.
     *
     * First verifies if Ring already published it. If not, publishes it ourselves
     * using the keypair and session we received during handoff.
     *
     * @param pubkey The user's pubkey in z32 format
     * @param noisePubkeyHex The X25519 Noise public key (hex encoded) from epoch 0
     * @param deviceId The device ID used for this connection
     */
    suspend fun ensureNoiseEndpointPublished(
        pubkey: String,
        noisePubkeyHex: String?,
        deviceId: String,
    ) = withContext(Dispatchers.IO) {
        try {
            Logger.debug("Verifying Noise endpoint for ${pubkey.take(12)}...", context = TAG)

            // Check if endpoint already exists and is valid
            val endpoint = directoryServiceProvider.get().discoverNoiseEndpoint(pubkey)
            if (endpoint != null && endpoint.host != "pending") {
                Logger.info(
                    "Noise endpoint already published for ${pubkey.take(
                        12
                    )}...: host=${endpoint.host}, port=${endpoint.port}",
                    context = TAG,
                )
                return@withContext
            }

            // Endpoint missing or has placeholder values - publish it ourselves
            if (noisePubkeyHex == null) {
                Logger.warn("Cannot publish Noise endpoint: no keypair available", context = TAG)
                return@withContext
            }

            Logger.info("Ring did not publish Noise endpoint - publishing as fallback", context = TAG)

            // Publish with "pending" host/port - will be updated when Noise server starts
            val directoryService = directoryServiceProvider.get()
            if (!directoryService.isConfigured) {
                if (!directoryService.tryRestoreFromKeychain()) {
                    Logger.warn("Cannot publish Noise endpoint: DirectoryService not configured", context = TAG)
                    return@withContext
                }
            }

            directoryService.publishNoiseEndpoint(
                host = "pending",
                port = 0,
                noisePubkey = noisePubkeyHex,
                metadata = """{"provisioned_by":"bitkit-fallback","device_id":"$deviceId"}""",
            )
            Logger.info("Published Noise endpoint for ${pubkey.take(12)}... as fallback", context = TAG)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn("Error ensuring Noise endpoint: ${e.message}", e, context = TAG)
        }
    }

    /**
     * Verify that Ring published the Noise endpoint during handoff.
     *
     * Ring v2 publishes the Noise endpoint using SDK put() which signs with Ed25519.
     * This verification uses DirectoryService.discoverNoiseEndpoint() to actually parse
     * the endpoint with the same logic that Bitkit will use later, ensuring schema compatibility.
     *
     * @param pubkey The user's pubkey in z32 format
     * @return True if the Noise endpoint is discoverable and parseable, false otherwise
     */
    suspend fun verifyNoiseEndpointPublished(pubkey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Logger.debug("Verifying Noise endpoint via DirectoryService for ${pubkey.take(12)}...", context = TAG)

            // Use DirectoryService to parse the endpoint - validates schema matches PaykitMobile FFI
            val endpoint = directoryServiceProvider.get().discoverNoiseEndpoint(pubkey)
            if (endpoint != null) {
                Logger.info(
                    "Verified Noise endpoint for ${pubkey.take(12)}...: host=${endpoint.host}, port=${endpoint.port}",
                    context = TAG,
                )
                return@withContext true
            }

            Logger.warn(
                "Noise endpoint not found or invalid schema for ${pubkey.take(
                    12
                )}... - Ring may not have published it correctly",
                context = TAG,
            )
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn("Error verifying Noise endpoint: ${e.message}", e, context = TAG)
            false
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
    /** InboxKey X25519 keypair for SB2 stored delivery (v3+) */
    @SerialName("inbox_keypair")
    val inboxKeypair: InboxKeypairPayload? = null,
    /** AppKey for delegated Ed25519 signing (v3+) */
    @SerialName("app_key")
    val appKey: AppKeyPayload? = null,
)

@Serializable
data class NoiseKeypairPayload(
    val epoch: Int,
    @SerialName("public_key")
    val publicKey: String,
    @SerialName("secret_key")
    val secretKey: String,
)

@Serializable
data class InboxKeypairPayload(
    @SerialName("public_key")
    val publicKey: String,
    @SerialName("secret_key")
    val secretKey: String,
)

@Serializable
data class AppKeyPayload(
    @SerialName("ed25519_sk")
    val ed25519Sk: String,
    @SerialName("ed25519_pk")
    val ed25519Pk: String,
    @SerialName("cert_id")
    val certId: String,
    @SerialName("cert_body")
    val certBody: String,
    @SerialName("cert_sig")
    val certSig: String,
)
