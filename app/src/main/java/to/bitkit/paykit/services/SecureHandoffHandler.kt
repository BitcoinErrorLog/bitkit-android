package to.bitkit.paykit.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import to.bitkit.utils.Logger
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Handles secure handoff payload fetching and processing for cross-device authentication.
 */
@Singleton
class SecureHandoffHandler @Inject constructor(
    private val noiseKeyCache: NoiseKeyCache,
    private val pubkyStorageAdapter: PubkyStorageAdapter,
) {
    companion object {
        private const val TAG = "SecureHandoffHandler"
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchAndProcessPayload(
        pubkey: String,
        requestId: String,
        scope: CoroutineScope,
        onSessionPersisted: suspend (PubkySession) -> Unit,
    ): PaykitSetupResult = withContext(Dispatchers.IO) {
        val payload = fetchHandoffPayload(pubkey, requestId)
        validatePayload(payload)
        val result = buildSetupResultFromPayload(payload)
        cacheAndPersistResult(result, payload.deviceId, scope, onSessionPersisted)
        schedulePayloadDeletion(result.session, requestId, scope)
        result
    }

    private suspend fun fetchHandoffPayload(pubkey: String, requestId: String): SecureHandoffPayload {
        val handoffUri = "pubky://$pubkey/pub/paykit.app/v0/handoff/$requestId"
        Logger.info("Fetching secure handoff payload from ${handoffUri.take(50)}...", context = TAG)

        val result = uniffi.pubkycore.get(handoffUri)
        if (result[0] == "error") {
            throw PubkyRingException.InvalidCallback
        }

        return json.decodeFromString<SecureHandoffPayload>(result[1])
    }

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

        Logger.info("Secure handoff payload received for ${payload.pubky.take(12)}...", context = TAG)

        return PaykitSetupResult(
            session = session,
            deviceId = payload.deviceId,
            noiseKeypair0 = keypair0,
            noiseKeypair1 = keypair1,
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
                    sessionId = session.sessionSecret,
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
)

@Serializable
data class NoiseKeypairPayload(
    val epoch: Int,
    @SerialName("public_key")
    val publicKey: String,
    @SerialName("secret_key")
    val secretKey: String,
)

