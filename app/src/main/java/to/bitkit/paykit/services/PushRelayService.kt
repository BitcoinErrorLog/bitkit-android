package to.bitkit.paykit.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.bitkit.paykit.KeyManager
import to.bitkit.utils.Logger
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for the private push relay service.
 *
 * The push relay service stores push tokens server-side (never publicly)
 * and forwards authorized wake notifications to APNs/FCM.
 *
 * Benefits over public publishing:
 * - Tokens never exposed publicly (no DoS via spam)
 * - Rate limiting at relay level
 * - Sender authentication required
 */
@Singleton
class PushRelayService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: KeyManager,
    private val pubkyRingBridge: PubkyRingBridge,
) {
    companion object {
        private const val TAG = "PushRelayService"

        // Production and staging URLs
        private const val PRODUCTION_URL = "https://push.paykit.app/v1"
        private const val STAGING_URL = "https://push-staging.paykit.app/v1"

        fun getBaseUrl(): String {
            return System.getProperty("PUSH_RELAY_URL")
                ?: if (to.bitkit.BuildConfig.DEBUG) STAGING_URL else PRODUCTION_URL
        }

        fun isEnabled(): Boolean {
            return System.getProperty("PUSH_RELAY_ENABLED") != "false"
        }

        fun shouldFallbackToHomeserver(): Boolean {
            return System.getProperty("PUSH_RELAY_FALLBACK") == "true"
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private var currentRelayId: String? = null
    private var registrationExpiresAt: Long? = null

    // MARK: - Types

    enum class WakeType(val value: String) {
        NOISE_CONNECT("noise_connect"),
        PAYMENT_RECEIVED("payment_received"),
        CHANNEL_UPDATE("channel_update"),
    }

    @Serializable
    data class RegistrationResponse(
        val status: String,
        @SerialName("relay_id")
        val relayId: String,
        @SerialName("expires_at")
        val expiresAt: Long,
    )

    @Serializable
    data class WakeResponse(
        val status: String,
        @SerialName("wake_id")
        val wakeId: String? = null,
    )

    @Serializable
    private data class ErrorResponse(
        val error: String,
        val message: String,
        @SerialName("retry_after")
        val retryAfter: Int? = null,
    )

    sealed class PushRelayException(message: String) : Exception(message) {
        object NotConfigured : PushRelayException("Push relay not configured - missing pubkey")
        object InvalidSignature : PushRelayException("Invalid signature for relay request")
        data class RateLimited(val retryAfterSeconds: Int) :
            PushRelayException("Rate limited, retry after $retryAfterSeconds seconds")
        object RecipientNotFound : PushRelayException("Recipient not registered for push notifications")
        data class NetworkError(val error: Throwable) : PushRelayException("Network error: ${error.message}")
        data class ServerError(val serverMessage: String) : PushRelayException("Server error: $serverMessage")
        object Disabled : PushRelayException("Push relay is disabled")
    }

    // MARK: - Registration

    /**
     * Register device for push notifications via relay.
     *
     * @param token FCM device token
     * @param capabilities Notification types to receive
     * @return Registration response with relay ID and expiry
     */
    suspend fun register(
        token: String,
        capabilities: List<String> = listOf("wake", "payment_received"),
    ): RegistrationResponse = withContext(Dispatchers.IO) {
        if (!isEnabled()) {
            throw PushRelayException.Disabled
        }

        val pubkey = keyManager.getCurrentPublicKeyZ32()
            ?: throw PushRelayException.NotConfigured

        val body = mapOf(
            "platform" to "android",
            "token" to token,
            "capabilities" to capabilities,
            "device_id" to keyManager.getDeviceId(),
        )

        val response = makeAuthenticatedRequest<RegistrationResponse>(
            method = "POST",
            path = "/register",
            body = body,
            pubkey = pubkey,
        )

        currentRelayId = response.relayId
        registrationExpiresAt = response.expiresAt * 1000 // Convert to millis

        Logger.info("Registered with push relay, expires: ${response.expiresAt}", context = TAG)

        response
    }

    /**
     * Unregister from push relay.
     */
    suspend fun unregister() = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext

        val pubkey = keyManager.getCurrentPublicKeyZ32()
            ?: throw PushRelayException.NotConfigured

        val body = mapOf(
            "device_id" to keyManager.getDeviceId(),
        )

        makeAuthenticatedRequest<Map<String, String>>(
            method = "DELETE",
            path = "/register",
            body = body,
            pubkey = pubkey,
        )

        currentRelayId = null
        registrationExpiresAt = null

        Logger.info("Unregistered from push relay", context = TAG)
    }

    /**
     * Check if registration needs renewal (within 7 days of expiry).
     */
    val needsRenewal: Boolean
        get() {
            val expiresAt = registrationExpiresAt ?: return true
            val renewalThreshold = expiresAt - (7 * 24 * 60 * 60 * 1000) // 7 days before expiry
            return System.currentTimeMillis() > renewalThreshold
        }

    // MARK: - Wake Notifications

    /**
     * Send a wake notification to a recipient.
     *
     * @param recipientPubkey The recipient's z32 pubkey
     * @param wakeType Type of wake notification
     * @param payload Optional encrypted payload
     * @return Wake response with status
     */
    suspend fun wake(
        recipientPubkey: String,
        wakeType: WakeType,
        payload: ByteArray? = null,
    ): WakeResponse = withContext(Dispatchers.IO) {
        if (!isEnabled()) {
            throw PushRelayException.Disabled
        }

        val senderPubkey = keyManager.getCurrentPublicKeyZ32()
            ?: throw PushRelayException.NotConfigured

        val body = mutableMapOf<String, Any>(
            "recipient_pubkey" to recipientPubkey,
            "wake_type" to wakeType.value,
            "sender_pubkey" to senderPubkey,
            "nonce" to generateNonce(),
        )

        if (payload != null) {
            body["payload"] = android.util.Base64.encodeToString(payload, android.util.Base64.NO_WRAP)
        }

        val response = makeAuthenticatedRequest<WakeResponse>(
            method = "POST",
            path = "/wake",
            body = body,
            pubkey = senderPubkey,
        )

        Logger.debug("Wake sent to ${recipientPubkey.take(12)}..., status: ${response.status}", context = TAG)

        response
    }

    // MARK: - Private Helpers

    private suspend inline fun <reified T> makeAuthenticatedRequest(
        method: String,
        path: String,
        body: Map<String, Any>,
        pubkey: String,
    ): T {
        val url = URL(getBaseUrl() + path)
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = method
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            // Serialize body
            val bodyJson = json.encodeToString(body)
            val bodyBytes = bodyJson.toByteArray(Charsets.UTF_8)

            // Add authentication headers
            val timestamp = System.currentTimeMillis() / 1000
            val bodyHash = sha256Hex(bodyBytes)
            val message = "$method:$path:$timestamp:$bodyHash"

            // Sign with Ed25519 via Pubky Ring (Ring holds secret key)
            val signature = signMessage(message, pubkey)

            connection.setRequestProperty("X-Pubky-Signature", signature)
            connection.setRequestProperty("X-Pubky-Timestamp", timestamp.toString())
            connection.setRequestProperty("X-Pubky-Pubkey", pubkey)

            // Send body
            connection.outputStream.use { it.write(bodyBytes) }

            // Handle response
            return when (connection.responseCode) {
                200, 201 -> {
                    val responseBody = connection.inputStream.bufferedReader().readText()
                    json.decodeFromString<T>(responseBody)
                }
                401 -> throw PushRelayException.InvalidSignature
                404 -> throw PushRelayException.RecipientNotFound
                429 -> {
                    val retryAfter = connection.getHeaderField("Retry-After")?.toIntOrNull() ?: 60
                    throw PushRelayException.RateLimited(retryAfter)
                }
                else -> {
                    val errorBody = try {
                        connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    } catch (e: Exception) {
                        "Unknown error"
                    }
                    throw PushRelayException.ServerError(errorBody)
                }
            }
        } catch (e: PushRelayException) {
            throw e
        } catch (e: Exception) {
            throw PushRelayException.NetworkError(e)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun signMessage(message: String, pubkey: String): String {
        // Request Ed25519 signature from Pubky Ring
        // Ring holds the secret key and performs the signing
        return pubkyRingBridge.requestSignature(context, message)
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
