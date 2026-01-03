package to.bitkit.paykit.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for sending push notifications to peers for Paykit operations.
 * Used to wake remote devices before attempting Noise connections.
 */
@Singleton
class PushNotificationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directoryService: DirectoryService
) {
    companion object {
        private const val TAG = "PushNotificationService"
    }

    /**
     * Push notification platforms
     */
    enum class Platform {
        IOS, ANDROID
    }

    /**
     * A registered push endpoint for a peer
     */
    @Serializable
    data class PushEndpoint(
        val pubkey: String,
        val deviceToken: String,
        val platform: Platform,
        val noiseHost: String? = null,
        val noisePort: Int? = null,
        val noisePubkey: String? = null,
        val createdAt: Long = System.currentTimeMillis()
    )

    /**
     * Errors for push notification operations
     */
    sealed class PushError(message: String) : Exception(message) {
        object EndpointNotFound : PushError("Push endpoint not found for recipient")
        class SendFailed(msg: String) : PushError("Failed to send push notification: $msg")
        object InvalidConfiguration : PushError("Push notification configuration is invalid")
    }

    // Our own FCM token for push notifications
    private var localDeviceToken: String? = null

    /**
     * Update our FCM token (called when FCM registration succeeds)
     */
    fun updateDeviceToken(token: String) {
        this.localDeviceToken = token
        Logger.info("PushNotificationService: Updated device token", context = TAG)
    }

    /**
     * Discover a peer's push endpoint from the directory.
     * Currently returns null as push endpoint discovery is not yet implemented.
     *
     * @param recipientPubkey The public key of the recipient
     * @return The push endpoint if found, null otherwise
     */
    @Suppress("UnusedParameter")
    private suspend fun discoverPushEndpoint(recipientPubkey: String): PushEndpoint? {
        // TODO: Implement push endpoint discovery via directory service
        // For now, return null to indicate endpoint not found
        Logger.debug("PushNotificationService: discoverPushEndpoint not yet implemented", context = TAG)
        return null
    }

    /**
     * Send a wake notification to a peer before attempting Noise connection.
     * This wakes the recipient's device to start their Noise server.
     *
     * @param recipientPubkey The public key of the recipient
     * @param senderPubkey The public key of the sender
     * @param noiseHost Optional host the sender will connect to
     * @param noisePort Optional port the sender will connect to
     */
    suspend fun sendWakeNotification(
        recipientPubkey: String,
        senderPubkey: String,
        noiseHost: String? = null,
        noisePort: Int? = null
    ) {
        // Discover recipient's push endpoint
        val endpoint = discoverPushEndpoint(recipientPubkey)
            ?: throw PushError.EndpointNotFound

        // Send notification based on platform
        when (endpoint.platform) {
            Platform.IOS -> sendAPNsNotification(endpoint, senderPubkey, noiseHost, noisePort)
            Platform.ANDROID -> sendFCMNotification(endpoint, senderPubkey, noiseHost, noisePort)
        }

        Logger.info("PushNotificationService: Sent wake notification to ${recipientPubkey.take(12)}...", context = TAG)
    }

    /**
     * Send push notification via APNs for iOS recipients
     * Note: This typically goes through a backend service since APNs
     * authentication requires server-side keys.
     */
    private suspend fun sendAPNsNotification(
        endpoint: PushEndpoint,
        senderPubkey: String,
        noiseHost: String?,
        noisePort: Int?
    ) {
        // Build APNs payload
        val payload = mapOf(
            "aps" to mapOf(
                "content-available" to 1,
                "alert" to mapOf(
                    "title" to "Incoming Payment Request",
                    "body" to "Someone wants to send you a payment request"
                ),
                "sound" to "default"
            ),
            "type" to "paykit_noise_request",
            "from_pubkey" to senderPubkey,
            "endpoint_host" to (noiseHost ?: endpoint.noiseHost ?: ""),
            "endpoint_port" to (noisePort ?: endpoint.noisePort ?: 9000),
            "noise_pubkey" to (endpoint.noisePubkey ?: "")
        )

        // Note: In a real implementation, you would need to:
        // 1. Send this to your backend service
        // 2. Backend authenticates with APNs using auth key
        // 3. Backend sends HTTP/2 request to APNs

        Logger.info("PushNotificationService: Would send APNs notification to token ${endpoint.deviceToken.take(16)}...", context = TAG)
    }

    /**
     * Send push notification via FCM for Android recipients
     * Note: This typically goes through a backend service since FCM
     * server keys should not be embedded in client apps.
     */
    private suspend fun sendFCMNotification(
        endpoint: PushEndpoint,
        senderPubkey: String,
        noiseHost: String?,
        noisePort: Int?
    ) {
        // Build FCM payload
        val message = mapOf(
            "to" to endpoint.deviceToken,
            "priority" to "high",
            "data" to mapOf(
                "type" to "paykit_noise_request",
                "from_pubkey" to senderPubkey,
                "endpoint_host" to (noiseHost ?: endpoint.noiseHost ?: ""),
                "endpoint_port" to (noisePort ?: endpoint.noisePort ?: 9000),
                "noise_pubkey" to (endpoint.noisePubkey ?: "")
            )
        )

        // Note: In a real implementation, you would need to:
        // 1. Send this to your backend service
        // 2. Backend sends to FCM using server key or service account
        // 3. Backend handles FCM response

        Logger.info("PushNotificationService: Would send FCM notification to token ${endpoint.deviceToken.take(16)}...", context = TAG)
    }
}

