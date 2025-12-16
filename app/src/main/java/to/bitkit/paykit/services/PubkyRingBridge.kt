package to.bitkit.paykit.services

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import to.bitkit.utils.Logger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bridge service for communicating with Pubky-ring app via Intent/URL schemes.
 *
 * Pubky-ring handles:
 * - Session management (sign in/sign up to homeserver)
 * - Key derivation from Ed25519 seed
 * - Profile and follows management
 *
 * Communication Flows:
 *
 * **Same Device:**
 * 1. Bitkit sends request via URL scheme: `pubkyring://session?callback=bitkit://paykit-session`
 * 2. Pubky-ring prompts user to select a pubky
 * 3. Pubky-ring signs in to homeserver
 * 4. Pubky-ring opens callback URL with data: `bitkit://paykit-session?pubky=...&session_secret=...`
 *
 * **Cross Device (QR/Link):**
 * 1. Bitkit generates a session request URL/QR with request_id
 * 2. User scans QR or opens link on device with Pubky-ring
 * 3. Pubky-ring processes request and publishes session to relay/homeserver
 * 4. Bitkit polls relay for session response using request_id
 */
@Singleton
class PubkyRingBridge @Inject constructor() {

    companion object {
        private const val TAG = "PubkyRingBridge"

        // Package name for Pubky-ring app
        private const val PUBKY_RING_PACKAGE = "com.pubkyring"

        // URL schemes
        private const val PUBKY_RING_SCHEME = "pubkyring"
        private const val BITKIT_SCHEME = "bitkit"

        // Cross-device authentication
        const val CROSS_DEVICE_WEB_URL = "https://pubky.app/auth"
        private const val DEFAULT_RELAY_URL = "https://relay.pubky.app/sessions"
        
        fun getRelayUrl(): String {
            // Can be overridden via system property for testing: -DPUBKY_RELAY_URL=...
            // In production, this will use the default relay URL
            return System.getProperty("PUBKY_RELAY_URL") ?: DEFAULT_RELAY_URL
        }

        // Callback paths
        const val CALLBACK_PATH_SESSION = "paykit-session"
        const val CALLBACK_PATH_KEYPAIR = "paykit-keypair"
        const val CALLBACK_PATH_PROFILE = "paykit-profile"
        const val CALLBACK_PATH_FOLLOWS = "paykit-follows"
        const val CALLBACK_PATH_CROSS_DEVICE_SESSION = "paykit-cross-session"

        @Volatile
        private var instance: PubkyRingBridge? = null

        fun getInstance(): PubkyRingBridge {
            return instance ?: synchronized(this) {
                instance ?: PubkyRingBridge().also { instance = it }
            }
        }
    }

    // Pending continuations for async requests
    private var pendingSessionContinuation: Continuation<PubkySession>? = null
    private var pendingKeypairContinuation: Continuation<NoiseKeypair>? = null

    // Pending cross-device request ID
    private var pendingCrossDeviceRequestId: String? = null

    // Cached sessions by pubkey
    private val sessionCache = mutableMapOf<String, PubkySession>()

    // Cached keypairs by derivation path
    private val keypairCache = mutableMapOf<String, NoiseKeypair>()

    /**
     * Check if Pubky-ring app is installed
     */
    fun isPubkyRingInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PUBKY_RING_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            // Also try URL scheme check
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$PUBKY_RING_SCHEME://"))
            intent.resolveActivity(context.packageManager) != null
        }
    }

    /**
     * Request a session from Pubky-ring
     *
     * Opens Pubky-ring app which will:
     * 1. Prompt user to select a pubky
     * 2. Sign in to homeserver
     * 3. Return session data via callback URL
     *
     * @param context Android context
     * @return PubkySession with pubkey, session secret, and capabilities
     * @throws PubkyRingException if request fails or app not installed
     */
    suspend fun requestSession(context: Context): PubkySession = suspendCancellableCoroutine { continuation ->
        if (!isPubkyRingInstalled(context)) {
            continuation.resumeWithException(PubkyRingException.AppNotInstalled)
            return@suspendCancellableCoroutine
        }

        val callbackUrl = "$BITKIT_SCHEME://$CALLBACK_PATH_SESSION"
        val encodedCallback = URLEncoder.encode(callbackUrl, "UTF-8")
        val requestUrl = "$PUBKY_RING_SCHEME://session?callback=$encodedCallback"

        pendingSessionContinuation = continuation

        continuation.invokeOnCancellation {
            pendingSessionContinuation = null
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.error("Failed to open Pubky-ring", e, context = TAG)
            pendingSessionContinuation = null
            continuation.resumeWithException(PubkyRingException.FailedToOpenApp(e.message))
        }
    }

    /**
     * Request a noise keypair derivation from Pubky-ring
     *
     * @param context Android context
     * @param deviceId Device identifier for key derivation
     * @param epoch Epoch for key rotation
     * @return X25519 keypair for Noise protocol
     * @throws PubkyRingException if request fails
     */
    suspend fun requestNoiseKeypair(
        context: Context,
        deviceId: String,
        epoch: ULong
    ): NoiseKeypair = suspendCancellableCoroutine { continuation ->
        // Check cache first
        val cacheKey = "$deviceId:$epoch"
        keypairCache[cacheKey]?.let { cached ->
            continuation.resume(cached)
            return@suspendCancellableCoroutine
        }

        if (!isPubkyRingInstalled(context)) {
            continuation.resumeWithException(PubkyRingException.AppNotInstalled)
            return@suspendCancellableCoroutine
        }

        val callbackUrl = "$BITKIT_SCHEME://$CALLBACK_PATH_KEYPAIR"
        val encodedCallback = URLEncoder.encode(callbackUrl, "UTF-8")
        val requestUrl = "$PUBKY_RING_SCHEME://derive-keypair?deviceId=$deviceId&epoch=$epoch&callback=$encodedCallback"

        pendingKeypairContinuation = continuation

        continuation.invokeOnCancellation {
            pendingKeypairContinuation = null
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.error("Failed to open Pubky-ring for keypair", e, context = TAG)
            pendingKeypairContinuation = null
            continuation.resumeWithException(PubkyRingException.FailedToOpenApp(e.message))
        }
    }

    /**
     * Get cached session for a pubkey
     */
    fun getCachedSession(pubkey: String): PubkySession? = sessionCache[pubkey]

    /**
     * Clear all cached data
     */
    fun clearCache() {
        sessionCache.clear()
        keypairCache.clear()
        pendingCrossDeviceRequestId = null
    }

    /**
     * Set pending cross-device request ID (for testing only)
     */
    internal fun setPendingCrossDeviceRequestIdForTest(requestId: String?) {
        pendingCrossDeviceRequestId = requestId
    }

    // MARK: - Cross-Device Authentication

    /**
     * Generate a cross-device session request that can be shared as a link or QR
     *
     * @return CrossDeviceRequest with URL, QR code bitmap, and request ID
     */
    fun generateCrossDeviceRequest(): CrossDeviceRequest {
        val requestId = UUID.randomUUID().toString().lowercase()
        pendingCrossDeviceRequestId = requestId

        // Build the URL for cross-device auth (construct manually for testability)
        val url = buildCrossDeviceUrl(requestId)
        val qrBitmap = generateQRCode(url)

        return CrossDeviceRequest(
            requestId = requestId,
            url = url,
            qrCodeBitmap = qrBitmap,
            expiresAt = Date(System.currentTimeMillis() + 300_000), // 5 minutes
        )
    }

    /**
     * Build cross-device auth URL (visible for testing)
     */
    internal fun buildCrossDeviceUrl(requestId: String): String {
        val encodedRequestId = URLEncoder.encode(requestId, "UTF-8")
        val encodedCallback = URLEncoder.encode(BITKIT_SCHEME, "UTF-8")
        val encodedAppName = URLEncoder.encode("Bitkit", "UTF-8")
        val encodedRelay = URLEncoder.encode(Companion.getRelayUrl(), "UTF-8")

        return "$CROSS_DEVICE_WEB_URL?request_id=$encodedRequestId&callback_scheme=$encodedCallback&app_name=$encodedAppName&relay_url=$encodedRelay"
    }

    /**
     * Poll for a cross-device session response
     *
     * @param requestId The request ID from generateCrossDeviceRequest()
     * @param timeoutMs Maximum time to wait in milliseconds (default 5 minutes)
     * @return PubkySession if successful
     * @throws PubkyRingException on timeout or failure
     */
    suspend fun pollForCrossDeviceSession(requestId: String, timeoutMs: Long = 300_000L): PubkySession = withContext(
        Dispatchers.IO
    ) {
        val startTime = System.currentTimeMillis()
        val pollIntervalMs = 2000L

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            // Check if session arrived via direct callback
            sessionCache.values.firstOrNull()?.let { session ->
                if (pendingCrossDeviceRequestId == null) {
                    return@withContext session
                }
            }

            // Poll relay for session
            try {
                val session = pollRelayForSession(requestId)
                if (session != null) {
                    sessionCache[session.pubkey] = session
                    pendingCrossDeviceRequestId = null
                    return@withContext session
                }
            } catch (e: Exception) {
                Logger.debug("Relay poll failed: ${e.message}", context = TAG)
            }

            delay(pollIntervalMs)
        }

        pendingCrossDeviceRequestId = null
        throw PubkyRingException.Timeout
    }

    /**
     * Import a session manually (for offline/manual cross-device flow)
     *
     * @param pubkey The z-base32 encoded public key
     * @param sessionSecret The session secret from Pubky-ring
     * @param capabilities Optional list of capabilities
     * @return Imported PubkySession
     */
    fun importSession(pubkey: String, sessionSecret: String, capabilities: List<String> = emptyList()): PubkySession {
        val session = PubkySession(
            pubkey = pubkey,
            sessionSecret = sessionSecret,
            capabilities = capabilities,
            createdAt = Date(),
        )
        sessionCache[pubkey] = session
        return session
    }

    /**
     * Generate a shareable link for cross-device auth
     */
    fun generateShareableLink(): String {
        val request = generateCrossDeviceRequest()
        return request.url
    }

    /**
     * Get the current authentication availability status
     */
    fun getAuthenticationStatus(context: Context): AuthenticationStatus =
        if (isPubkyRingInstalled(context)) {
            AuthenticationStatus.PUBKY_RING_AVAILABLE
        } else {
            AuthenticationStatus.CROSS_DEVICE_ONLY
        }

    /**
     * Check if any authentication method is available
     */
    fun canAuthenticate(): Boolean = true // Cross-device is always available

    /**
     * Get recommended authentication method
     */
    fun getRecommendedAuthMethod(context: Context): AuthMethod =
        if (isPubkyRingInstalled(context)) {
            AuthMethod.SAME_DEVICE
        } else {
            AuthMethod.CROSS_DEVICE
        }

    // Private cross-device helpers

    private fun pollRelayForSession(requestId: String): PubkySession? {
        val relayUrl = Companion.getRelayUrl()
        val url = URL("$relayUrl/$requestId")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            when (connection.responseCode) {
                404 -> null // Session not yet available
                200 -> {
                    val response = connection.inputStream.bufferedReader().readText()
                    parseSessionFromJson(response)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSessionFromJson(json: String): PubkySession? = try {
        val jsonObj = org.json.JSONObject(json)
        PubkySession(
            pubkey = jsonObj.getString("pubkey"),
            sessionSecret = jsonObj.getString("session_secret"),
            capabilities = jsonObj.optJSONArray("capabilities")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            createdAt = Date(),
        )
    } catch (e: Exception) {
        Logger.error("Failed to parse session JSON", e, context = TAG)
        null
    }

    private fun generateQRCode(content: String, size: Int = 512): Bitmap? = try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(
                    x,
                    y,
                    if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }
        bitmap
    } catch (e: Exception) {
        Logger.error("Failed to generate QR code", e, context = TAG)
        null
    }

    /**
     * Handle incoming URL callback from Pubky-ring
     *
     * Call this from your Activity's onNewIntent or handleDeepLink method.
     *
     * @param uri The callback URI from Pubky-ring
     * @return True if the URI was handled
     */
    fun handleCallback(uri: Uri): Boolean {
        if (uri.scheme != BITKIT_SCHEME) return false

        return when (uri.host) {
            CALLBACK_PATH_SESSION -> handleSessionCallback(uri)
            CALLBACK_PATH_KEYPAIR -> handleKeypairCallback(uri)
            CALLBACK_PATH_CROSS_DEVICE_SESSION -> handleCrossDeviceSessionCallback(uri)
            else -> false
        }
    }

    private fun handleSessionCallback(uri: Uri): Boolean {
        val pubkey = uri.getQueryParameter("pubky")
        val sessionSecret = uri.getQueryParameter("session_secret")

        if (pubkey == null || sessionSecret == null) {
            pendingSessionContinuation?.resumeWithException(PubkyRingException.MissingParameters)
            pendingSessionContinuation = null
            return true
        }

        val capabilities = uri.getQueryParameter("capabilities")
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        val session = PubkySession(
            pubkey = pubkey,
            sessionSecret = sessionSecret,
            capabilities = capabilities,
            createdAt = Date()
        )

        // Cache the session
        sessionCache[pubkey] = session

        pendingSessionContinuation?.resume(session)
        pendingSessionContinuation = null

        return true
    }

    private fun handleKeypairCallback(uri: Uri): Boolean {
        val publicKey = uri.getQueryParameter("public_key")
        val secretKey = uri.getQueryParameter("secret_key")
        val deviceId = uri.getQueryParameter("device_id")
        val epochStr = uri.getQueryParameter("epoch")

        if (publicKey == null || secretKey == null || deviceId == null || epochStr == null) {
            pendingKeypairContinuation?.resumeWithException(PubkyRingException.MissingParameters)
            pendingKeypairContinuation = null
            return true
        }

        val epoch = epochStr.toULongOrNull()
        if (epoch == null) {
            pendingKeypairContinuation?.resumeWithException(PubkyRingException.InvalidCallback)
            pendingKeypairContinuation = null
            return true
        }

        val keypair = NoiseKeypair(
            publicKey = publicKey,
            secretKey = secretKey,
            deviceId = deviceId,
            epoch = epoch,
        )

        // Cache the keypair
        val cacheKey = "$deviceId:$epoch"
        keypairCache[cacheKey] = keypair

        pendingKeypairContinuation?.resume(keypair)
        pendingKeypairContinuation = null

        return true
    }

    private fun handleCrossDeviceSessionCallback(uri: Uri): Boolean {
        // Verify request ID matches
        val requestId = uri.getQueryParameter("request_id")
        if (requestId != null && requestId != pendingCrossDeviceRequestId) {
            Logger.warn("Cross-device request ID mismatch", context = TAG)
            return false
        }

        val pubkey = uri.getQueryParameter("pubky")
        val sessionSecret = uri.getQueryParameter("session_secret")

        if (pubkey == null || sessionSecret == null) {
            return false
        }

        val capabilities = uri.getQueryParameter("capabilities")
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        val session = PubkySession(
            pubkey = pubkey,
            sessionSecret = sessionSecret,
            capabilities = capabilities,
            createdAt = Date(),
        )

        // Cache the session
        sessionCache[pubkey] = session
        pendingCrossDeviceRequestId = null

        return true
    }
}

/**
 * Session data returned from Pubky-ring
 */
data class PubkySession(
    val pubkey: String,
    val sessionSecret: String,
    val capabilities: List<String>,
    val createdAt: Date,
) {
    /**
     * Check if session has a specific capability
     */
    fun hasCapability(capability: String): Boolean = capabilities.contains(capability)
}

/**
 * X25519 keypair for Noise protocol
 */
data class NoiseKeypair(
    val publicKey: String,
    val secretKey: String,
    val deviceId: String,
    val epoch: ULong,
)

/**
 * Errors from PubkyRingBridge
 */
sealed class PubkyRingException(message: String) : Exception(message) {
    object AppNotInstalled : PubkyRingException("Pubky-ring app is not installed")
    data class FailedToOpenApp(val reason: String?) : PubkyRingException("Failed to open Pubky-ring app: $reason")
    object InvalidCallback : PubkyRingException("Invalid callback from Pubky-ring")
    object MissingParameters : PubkyRingException("Missing parameters in Pubky-ring callback")
    object Timeout : PubkyRingException("Request to Pubky-ring timed out")
    object Cancelled : PubkyRingException("Request was cancelled")
    data class CrossDeviceFailed(val reason: String) : PubkyRingException("Cross-device authentication failed: $reason")

    /**
     * User-friendly message for UI display
     */
    val userMessage: String
        get() = when (this) {
            is AppNotInstalled -> "Pubky-ring is not installed on this device. You can use the QR code option to authenticate from another device."
            is InvalidCallback, is MissingParameters -> "Something went wrong. Please try again."
            is FailedToOpenApp -> "Could not open Pubky-ring. Please make sure it's installed correctly."
            is Timeout -> "The request timed out. Please try again."
            is Cancelled -> "Authentication was cancelled."
            is CrossDeviceFailed -> "Cross-device authentication failed. Please try again."
        }
}

/**
 * Cross-device session request data
 */
data class CrossDeviceRequest(
    /** Unique request identifier */
    val requestId: String,
    /** URL to share or open in browser */
    val url: String,
    /** QR code bitmap for scanning */
    val qrCodeBitmap: Bitmap?,
    /** When this request expires */
    val expiresAt: Date,
) {
    /** Whether this request has expired */
    val isExpired: Boolean
        get() = Date().after(expiresAt)

    /** Time remaining until expiration in milliseconds */
    val timeRemainingMs: Long
        get() = maxOf(0, expiresAt.time - System.currentTimeMillis())
}

/**
 * Authentication method
 */
enum class AuthMethod {
    /** Direct communication with Pubky-ring on same device */
    SAME_DEVICE,

    /** QR code/link for authentication from another device */
    CROSS_DEVICE,

    /** Manual session entry */
    MANUAL,
}

/**
 * Current authentication availability status
 */
enum class AuthenticationStatus {
    /** Pubky-ring is installed and available */
    PUBKY_RING_AVAILABLE,

    /** Only cross-device authentication is available */
    CROSS_DEVICE_ONLY,
    ;

    /** User-friendly description */
    val description: String
        get() = when (this) {
            PUBKY_RING_AVAILABLE -> "Pubky-ring is available on this device"
            CROSS_DEVICE_ONLY -> "Use QR code to authenticate from another device"
        }
}
