package to.bitkit.paykit.services

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
class PubkyRingBridge @Inject constructor(
    private val keychainStorage: to.bitkit.paykit.storage.PaykitKeychainStorage,
    private val noiseKeyCache: NoiseKeyCache,
    private val pubkyStorageAdapter: PubkyStorageAdapter,
) {

    companion object {
        private const val TAG = "PubkyRingBridge"

        // Package name for Pubky-ring app
        private const val PUBKY_RING_PACKAGE = "to.pubky.ring"

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
        const val CALLBACK_PATH_PAYKIT_SETUP = "paykit-setup"  // Combined session + noise keys
        const val CALLBACK_PATH_SIGNATURE_RESULT = "signature-result"  // Ed25519 signature result

        @Volatile
        private var instance: PubkyRingBridge? = null

        @Deprecated("Use dependency injection instead", ReplaceWith("Inject PubkyRingBridge"))
        fun getInstance(): PubkyRingBridge {
            return instance ?: throw IllegalStateException("PubkyRingBridge not initialized. Use dependency injection.")
        }
        
        internal fun setInstance(bridge: PubkyRingBridge) {
            instance = bridge
        }
    }
    
    init {
        setInstance(this)
    }

    // Coroutine scope for fire-and-forget persistence operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Cleanup resources. Call when the bridge is no longer needed.
     */
    fun cleanup() {
        scope.cancel("PubkyRingBridge cleanup")
        pendingSessionContinuation = null
        pendingKeypairContinuation = null
        pendingPaykitSetupContinuation = null
        Logger.debug("PubkyRingBridge cleaned up", context = TAG)
    }

    // Pending continuations for async requests
    private var pendingSessionContinuation: Continuation<PubkySession>? = null
    private var pendingKeypairContinuation: Continuation<NoiseKeypair>? = null
    private var pendingPaykitSetupContinuation: Continuation<PaykitSetupResult>? = null

    // Pending cross-device request ID
    private var pendingCrossDeviceRequestId: String? = null

    // Cached sessions by pubkey
    private val sessionCache = mutableMapOf<String, PubkySession>()
    
    // Device ID for noise key derivation
    private var _deviceId: String? = null
    
    /**
     * Get consistent device ID for noise key derivations
     */
    val deviceId: String
        get() {
            return _deviceId ?: loadOrGenerateDeviceId().also { _deviceId = it }
        }
    
    private fun loadOrGenerateDeviceId(): String {
        val key = "paykit.device_id"
        
        // Try to load existing
        keychainStorage.getString(key)?.takeIf { it.isNotEmpty() }?.let { id ->
            Logger.debug("Loaded device ID: ${id.take(8)}...", context = TAG)
            return id
        }
        
        // Generate new UUID
        val newId = UUID.randomUUID().toString().lowercase()
        
        // Persist asynchronously (fire-and-forget, device ID is already in memory)
        scope.launch {
            try {
                keychainStorage.setString(key, newId)
            } catch (e: Exception) {
                Logger.error("Failed to persist device ID", e, context = TAG)
            }
        }
        
        Logger.info("Generated new device ID: ${newId.take(8)}...", context = TAG)
        return newId
    }
    
    /**
     * Reset device ID (for debugging/testing only)
     */
    suspend fun resetDeviceId() {
        keychainStorage.delete("paykit.device_id")
        _deviceId = loadOrGenerateDeviceId()
        Logger.info("Device ID reset", context = TAG)
    }

    // Cached keypairs by derivation path
    private val keypairCache = mutableMapOf<String, NoiseKeypair>()

    /**
     * Check if Pubky-ring app is installed
     */
    fun isPubkyRingInstalled(context: Context): Boolean {
        return try {
            // Method 1: Try to get package info (works on all Android versions)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    // Android 13+ requires explicit query
                    context.packageManager.getPackageInfo(PUBKY_RING_PACKAGE, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(PUBKY_RING_PACKAGE, 0)
                }
                Logger.debug("Pubky Ring detected via package info", context = TAG)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                // Package not found, try URL scheme check
                Logger.debug("Package not found, trying URL scheme check", context = TAG)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$PUBKY_RING_SCHEME://"))
                val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                val found = resolveInfo != null
                Logger.debug("URL scheme check result: $found", context = TAG)
                found
            }
        } catch (e: Exception) {
            Logger.error("Pubky Ring detection error: ${e.message}", e, context = TAG)
            false
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
     * Request complete Paykit setup from Pubky-ring (session + noise keys in one request)
     *
     * This is the preferred method for initial Paykit setup as it:
     * - Gets everything in a single user interaction
     * - Ensures noise keys are available even if Ring is later unavailable
     * - Includes both epoch 0 and epoch 1 keypairs for key rotation
     *
     * @param context Android context
     * @return PaykitSetupResult containing session and noise keypairs
     * @throws PubkyRingException if request fails or app not installed
     */
    suspend fun requestPaykitSetup(context: Context): PaykitSetupResult = suspendCancellableCoroutine { continuation ->
        if (!isPubkyRingInstalled(context)) {
            continuation.resumeWithException(PubkyRingException.AppNotInstalled)
            return@suspendCancellableCoroutine
        }

        val actualDeviceId = deviceId
        val callbackUrl = "$BITKIT_SCHEME://$CALLBACK_PATH_PAYKIT_SETUP"
        val encodedCallback = URLEncoder.encode(callbackUrl, "UTF-8")
        val requestUrl = "$PUBKY_RING_SCHEME://paykit-connect?deviceId=$actualDeviceId&callback=$encodedCallback"

        Logger.info("Requesting Paykit setup from Pubky Ring", context = TAG)

        pendingPaykitSetupContinuation = continuation

        continuation.invokeOnCancellation {
            pendingPaykitSetupContinuation = null
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.error("Failed to open Pubky-ring for Paykit setup", e, context = TAG)
            pendingPaykitSetupContinuation = null
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
    /**
     * Request a noise keypair derivation from Pubky-ring.
     * 
     * First checks NoiseKeyCache, then requests from Pubky-ring if not found.
     * 
     * @param context Android context for launching intents
     * @param deviceIdOverride Override device ID (uses stored ID if null)
     * @param epoch Epoch for key rotation
     * @return X25519 keypair for Noise protocol
     * @throws PubkyRingException if request fails
     */
    suspend fun requestNoiseKeypair(
        context: Context,
        deviceIdOverride: String? = null,
        epoch: ULong,
    ): NoiseKeypair {
        val actualDeviceId = deviceIdOverride ?: deviceId
        val cacheKey = "$actualDeviceId:$epoch"
        
        // Check memory cache first
        keypairCache[cacheKey]?.let { cached ->
            Logger.debug("Noise keypair cache hit for $cacheKey", context = TAG)
            return cached
        }
        
        // Check persistent cache (NoiseKeyCache)
        try {
            val cachedKey = noiseKeyCache.getKey(actualDeviceId, epoch.toUInt())
            if (cachedKey != null) {
                Logger.debug("Noise keypair found in persistent cache for $cacheKey", context = TAG)
                // We have the secret key, but to reconstruct the full keypair we'd need the public key
                // For now, proceed to request from Pubky-ring for the full keypair
            }
        } catch (e: Exception) {
            Logger.warn("Error checking NoiseKeyCache: ${e.message}", e, context = TAG)
        }
        
        // Request from Pubky-ring
        return suspendCancellableCoroutine { continuation ->
            if (!isPubkyRingInstalled(context)) {
                continuation.resumeWithException(PubkyRingException.AppNotInstalled)
                return@suspendCancellableCoroutine
            }

            val callbackUrl = "$BITKIT_SCHEME://$CALLBACK_PATH_KEYPAIR"
            val encodedCallback = URLEncoder.encode(callbackUrl, "UTF-8")
            val requestUrl = "$PUBKY_RING_SCHEME://derive-keypair?deviceId=$actualDeviceId&epoch=$epoch&callback=$encodedCallback"

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

    // MARK: - Profile & Follows Requests
    
    // Pending profile request continuation
    private var pendingProfileContinuation: Continuation<PubkyProfile?>? = null
    
    // Pending follows request continuation
    private var pendingFollowsContinuation: Continuation<List<String>>? = null
    
    /**
     * Request a profile from Pubky-ring (which fetches from homeserver)
     *
     * @param context Android context for launching intents
     * @param pubkey The pubkey of the profile to fetch
     * @return PubkyProfile if found, null otherwise
     * @throws PubkyRingException if request fails
     */
    suspend fun requestProfile(context: Context, pubkey: String): PubkyProfile? = suspendCancellableCoroutine { continuation ->
        if (!isPubkyRingInstalled(context)) {
            continuation.resumeWithException(PubkyRingException.AppNotInstalled)
            return@suspendCancellableCoroutine
        }

        val callbackUrl = "$BITKIT_SCHEME://$CALLBACK_PATH_PROFILE"
        val encodedCallback = URLEncoder.encode(callbackUrl, "UTF-8")
        val requestUrl = "$PUBKY_RING_SCHEME://get-profile?pubkey=$pubkey&callback=$encodedCallback"

        pendingProfileContinuation = continuation

        continuation.invokeOnCancellation {
            pendingProfileContinuation = null
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.error("Failed to open Pubky-ring for profile", e, context = TAG)
            pendingProfileContinuation = null
            continuation.resumeWithException(PubkyRingException.FailedToOpenApp(e.message))
        }
    }
    
    /**
     * Request follows list from Pubky-ring (which fetches from homeserver)
     *
     * @param context Android context for launching intents
     * @return List of followed pubkeys
     * @throws PubkyRingException if request fails
     */
    suspend fun requestFollows(context: Context): List<String> = suspendCancellableCoroutine { continuation ->
        if (!isPubkyRingInstalled(context)) {
            continuation.resumeWithException(PubkyRingException.AppNotInstalled)
            return@suspendCancellableCoroutine
        }

        val callbackUrl = "$BITKIT_SCHEME://$CALLBACK_PATH_FOLLOWS"
        val encodedCallback = URLEncoder.encode(callbackUrl, "UTF-8")
        val requestUrl = "$PUBKY_RING_SCHEME://get-follows?callback=$encodedCallback"

        pendingFollowsContinuation = continuation

        continuation.invokeOnCancellation {
            pendingFollowsContinuation = null
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.error("Failed to open Pubky-ring for follows", e, context = TAG)
            pendingFollowsContinuation = null
            continuation.resumeWithException(PubkyRingException.FailedToOpenApp(e.message))
        }
    }

    // MARK: - Ed25519 Signing

    private var pendingSignatureContinuation: Continuation<String>? = null

    /**
     * Request an Ed25519 signature from Pubky-ring
     *
     * Ring signs the message with the user's Ed25519 secret key.
     * Used for authenticating requests to external services (e.g., push relay).
     *
     * @param context Android context for launching Ring
     * @param message The message to sign (UTF-8 string)
     * @return Hex-encoded Ed25519 signature
     */
    suspend fun requestSignature(context: Context, message: String): String = suspendCancellableCoroutine { continuation ->
        if (!isPubkyRingInstalled(context)) {
            continuation.resumeWithException(PubkyRingException.AppNotInstalled)
            return@suspendCancellableCoroutine
        }

        val callbackUrl = "$BITKIT_SCHEME://$CALLBACK_PATH_SIGNATURE_RESULT"
        val encodedMessage = URLEncoder.encode(message, "UTF-8")
        val encodedCallback = URLEncoder.encode(callbackUrl, "UTF-8")
        val requestUrl = "$PUBKY_RING_SCHEME://sign-message?message=$encodedMessage&callback=$encodedCallback"

        pendingSignatureContinuation = continuation

        continuation.invokeOnCancellation {
            pendingSignatureContinuation = null
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.error("Failed to open Pubky-ring for signature", e, context = TAG)
            pendingSignatureContinuation = null
            continuation.resumeWithException(PubkyRingException.FailedToOpenApp(e.message))
        }
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
     * Handle an authentication URL from a scanned QR code or pasted link
     *
     * Parses URLs in the format:
     * - pubky://session?pubkey=...&session_secret=...
     * - pubkyring://session?pubkey=...&session_secret=...
     * - bitkit://paykit-session?pubkey=...&session_secret=...
     *
     * @param url The URL to parse
     * @return PubkySession if successful
     * @throws PubkyRingException if URL is invalid
     */
    fun handleAuthUrl(url: String): PubkySession {
        val uri = Uri.parse(url)
        
        // Check if it's a callback URL with session data
        val pubkey = uri.getQueryParameter("pubkey") ?: uri.getQueryParameter("pk")
        val sessionSecret = uri.getQueryParameter("session_secret") ?: uri.getQueryParameter("ss")
        
        if (pubkey != null && sessionSecret != null) {
            val capabilities = uri.getQueryParameter("capabilities")
                ?.split(",")
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            
            return importSession(pubkey, sessionSecret, capabilities)
        }
        
        throw PubkyRingException.InvalidCallback
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
            CALLBACK_PATH_PROFILE -> handleProfileCallback(uri)
            CALLBACK_PATH_FOLLOWS -> handleFollowsCallback(uri)
            CALLBACK_PATH_CROSS_DEVICE_SESSION -> handleCrossDeviceSessionCallback(uri)
            CALLBACK_PATH_PAYKIT_SETUP -> handlePaykitSetupCallback(uri)
            CALLBACK_PATH_SIGNATURE_RESULT -> handleSignatureCallback(uri)
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
        
        // Persist to keychain (fire-and-forget)
        scope.launch {
            persistSession(session)
        }

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

        // Cache the keypair in memory
        val cacheKey = "$deviceId:$epoch"
        keypairCache[cacheKey] = keypair
        
        // Persist secret key to NoiseKeyCache (memory first, keychain persists async)
        try {
            val secretKeyData = secretKey.toByteArray(Charsets.UTF_8)
            noiseKeyCache.setKeySync(secretKeyData, deviceId, epoch.toUInt())
            Logger.debug("Stored noise keypair in NoiseKeyCache for $cacheKey", context = TAG)
        } catch (e: Exception) {
            Logger.warn("Failed to store noise keypair: ${e.message}", e, context = TAG)
        }

        pendingKeypairContinuation?.resume(keypair)
        pendingKeypairContinuation = null

        return true
    }

    private fun handlePaykitSetupCallback(uri: Uri): Boolean {
        // Check for secure handoff mode
        val mode = uri.getQueryParameter("mode")
        if (mode == "secure_handoff") {
            val pubkey = uri.getQueryParameter("pubky")
            val requestId = uri.getQueryParameter("request_id")
            
            if (pubkey == null || requestId == null) {
                pendingPaykitSetupContinuation?.resumeWithException(PubkyRingException.MissingParameters)
                pendingPaykitSetupContinuation = null
                return true
            }
            
            // Fetch payload from homeserver asynchronously
            scope.launch {
                try {
                    val result = fetchSecureHandoffPayload(pubkey, requestId)
                    pendingPaykitSetupContinuation?.resume(result)
                } catch (e: Exception) {
                    pendingPaykitSetupContinuation?.resumeWithException(e)
                }
                pendingPaykitSetupContinuation = null
            }
            return true
        }
        
        // Legacy mode: secrets in URL
        val pubkey = uri.getQueryParameter("pubky")
        val sessionSecret = uri.getQueryParameter("session_secret")
        val deviceId = uri.getQueryParameter("device_id")

        if (pubkey == null || sessionSecret == null || deviceId == null) {
            pendingPaykitSetupContinuation?.resumeWithException(PubkyRingException.MissingParameters)
            pendingPaykitSetupContinuation = null
            return true
        }

        val capabilities = uri.getQueryParameter("capabilities")
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        // Create session
        val session = PubkySession(
            pubkey = pubkey,
            sessionSecret = sessionSecret,
            capabilities = capabilities,
            createdAt = Date(),
            expiresAt = null,  // Sessions from paykit-connect don't expire
        )

        // Parse noise keypair epoch 0 (optional but expected)
        var keypair0: NoiseKeypair? = null
        val publicKey0 = uri.getQueryParameter("noise_public_key_0")
        val secretKey0 = uri.getQueryParameter("noise_secret_key_0")
        if (publicKey0 != null && secretKey0 != null) {
            keypair0 = NoiseKeypair(
                publicKey = publicKey0,
                secretKey = secretKey0,
                deviceId = deviceId,
                epoch = 0u,
            )
        }

        // Parse noise keypair epoch 1 (optional)
        var keypair1: NoiseKeypair? = null
        val publicKey1 = uri.getQueryParameter("noise_public_key_1")
        val secretKey1 = uri.getQueryParameter("noise_secret_key_1")
        if (publicKey1 != null && secretKey1 != null) {
            keypair1 = NoiseKeypair(
                publicKey = publicKey1,
                secretKey = secretKey1,
                deviceId = deviceId,
                epoch = 1u,
            )
        }

        val result = PaykitSetupResult(
            session = session,
            deviceId = deviceId,
            noiseKeypair0 = keypair0,
            noiseKeypair1 = keypair1,
        )

        // Cache session
        sessionCache[pubkey] = session

        // Cache and persist noise keypairs
        if (keypair0 != null) {
            val cacheKey = "$deviceId:0"
            keypairCache[cacheKey] = keypair0
            try {
                val secretKeyData = keypair0.secretKey.toByteArray(Charsets.UTF_8)
                noiseKeyCache.setKeySync(secretKeyData, deviceId, 0u)
                Logger.debug("Stored noise keypair for epoch 0", context = TAG)
            } catch (e: Exception) {
                Logger.warn("Failed to store noise keypair epoch 0: ${e.message}", e, context = TAG)
            }
        }

        if (keypair1 != null) {
            val cacheKey = "$deviceId:1"
            keypairCache[cacheKey] = keypair1
            try {
                val secretKeyData = keypair1.secretKey.toByteArray(Charsets.UTF_8)
                noiseKeyCache.setKeySync(secretKeyData, deviceId, 1u)
                Logger.debug("Stored noise keypair for epoch 1", context = TAG)
            } catch (e: Exception) {
                Logger.warn("Failed to store noise keypair epoch 1: ${e.message}", e, context = TAG)
            }
        }

        Logger.info("Paykit setup callback received for ${pubkey.take(12)}...", context = TAG)

        pendingPaykitSetupContinuation?.resume(result)
        pendingPaykitSetupContinuation = null

        return true
    }
    
    /**
     * Fetch secure handoff payload from homeserver
     */
    private suspend fun fetchSecureHandoffPayload(pubkey: String, requestId: String): PaykitSetupResult = withContext(Dispatchers.IO) {
        val handoffUri = "pubky://$pubkey/pub/paykit.app/v0/handoff/$requestId"
        
        Logger.info("Fetching secure handoff payload from ${handoffUri.take(50)}...", context = TAG)
        
        // Fetch payload using uniffi.pubkycore.get
        val result = uniffi.pubkycore.get(handoffUri)
        if (result[0] == "error") {
            throw PubkyRingException.InvalidCallback
        }
        
        // Parse JSON payload
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val payload = json.decodeFromString<SecureHandoffPayload>(result[1])
        
        // Validate payload hasn't expired
        if (System.currentTimeMillis() > payload.expiresAt) {
            throw PubkyRingException.Timeout
        }
        
        // Build session
        val session = PubkySession(
            pubkey = payload.pubky,
            sessionSecret = payload.sessionSecret,
            capabilities = payload.capabilities,
            createdAt = Date(payload.createdAt),
            expiresAt = null,
        )
        
        // Build noise keypairs
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
        
        val setupResult = PaykitSetupResult(
            session = session,
            deviceId = payload.deviceId,
            noiseKeypair0 = keypair0,
            noiseKeypair1 = keypair1,
        )
        
        Logger.info("Secure handoff payload received for ${payload.pubky.take(12)}...", context = TAG)
        
        // Cache session and keypairs
        sessionCache[session.pubkey] = session
        persistSession(session)
        
        if (keypair0 != null) {
            val cacheKey0 = "${payload.deviceId}:0"
            keypairCache[cacheKey0] = keypair0
            try {
                val secretKeyData = keypair0.secretKey.toByteArray(Charsets.UTF_8)
                noiseKeyCache.setKeySync(secretKeyData, payload.deviceId, 0u)
            } catch (e: Exception) {
                Logger.warn("Failed to store noise keypair epoch 0: ${e.message}", e, context = TAG)
            }
        }
        if (keypair1 != null) {
            val cacheKey1 = "${payload.deviceId}:1"
            keypairCache[cacheKey1] = keypair1
            try {
                val secretKeyData = keypair1.secretKey.toByteArray(Charsets.UTF_8)
                noiseKeyCache.setKeySync(secretKeyData, payload.deviceId, 1u)
            } catch (e: Exception) {
                Logger.warn("Failed to store noise keypair epoch 1: ${e.message}", e, context = TAG)
            }
        }
        
        // Delete handoff file from homeserver to minimize attack window
        scope.launch {
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
        }
        
        setupResult
    }

    private fun handleProfileCallback(uri: Uri): Boolean {
        // Check for error response
        val error = uri.getQueryParameter("error")
        if (error != null) {
            Logger.warn("Profile request returned error: $error", context = TAG)
            pendingProfileContinuation?.resume(null)
            pendingProfileContinuation = null
            return true
        }
        
        // Build profile from response
        val profile = PubkyProfile(
            name = uri.getQueryParameter("name"),
            bio = uri.getQueryParameter("bio"),
            avatar = uri.getQueryParameter("avatar"),
            links = null, // Links would need JSON parsing, simplified for now
        )
        
        Logger.debug("Received profile from Pubky-ring: ${profile.name ?: "unknown"}", context = TAG)
        
        pendingProfileContinuation?.resume(profile)
        pendingProfileContinuation = null
        
        return true
    }
    
    private fun handleFollowsCallback(uri: Uri): Boolean {
        // Check for error response
        val error = uri.getQueryParameter("error")
        if (error != null) {
            Logger.warn("Follows request returned error: $error", context = TAG)
            pendingFollowsContinuation?.resume(emptyList())
            pendingFollowsContinuation = null
            return true
        }
        
        // Parse follows list (comma-separated pubkeys)
        val follows = uri.getQueryParameter("follows")
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        
        Logger.debug("Received ${follows.size} follows from Pubky-ring", context = TAG)
        
        pendingFollowsContinuation?.resume(follows)
        pendingFollowsContinuation = null
        
        return true
    }

    private fun handleSignatureCallback(uri: Uri): Boolean {
        // Check for error response
        val error = uri.getQueryParameter("error")
        if (error != null) {
            Logger.warn("Signature request returned error: $error", context = TAG)
            pendingSignatureContinuation?.resumeWithException(PubkyRingException.SignatureFailed)
            pendingSignatureContinuation = null
            return true
        }
        
        // Get signature from response
        val signature = uri.getQueryParameter("signature")
        if (signature == null) {
            pendingSignatureContinuation?.resumeWithException(PubkyRingException.InvalidCallback)
            pendingSignatureContinuation = null
            return true
        }
        
        Logger.debug("Received Ed25519 signature from Pubky-ring", context = TAG)
        
        pendingSignatureContinuation?.resume(signature)
        pendingSignatureContinuation = null
        
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
        
        // Persist to keychain for cross-device sessions too (fire-and-forget)
        scope.launch {
            persistSession(session)
        }

        return true
    }
    
    // MARK: - Session Persistence
    
    /**
     * Persist a session to keychain
     */
    private suspend fun persistSession(session: PubkySession) {
        try {
            val json = kotlinx.serialization.json.Json.encodeToString(PubkySession.serializer(), session)
            keychainStorage.setString("pubky.session.${session.pubkey}", json)
            Logger.debug("Persisted session for ${session.pubkey.take(12)}...", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to persist session", e, context = TAG)
        }
    }
    
    /**
     * Restore all sessions from keychain on app launch
     */
    suspend fun restoreSessions() {
        val sessionKeys = keychainStorage.listKeys("pubky.session.")
        
        for (key in sessionKeys) {
            try {
                val json = keychainStorage.getString(key) ?: continue
                val session = kotlinx.serialization.json.Json.decodeFromString<PubkySession>(json)
                sessionCache[session.pubkey] = session
                Logger.info("Restored session for ${session.pubkey.take(12)}...", context = TAG)
            } catch (e: Exception) {
                Logger.error("Failed to restore session from $key", e, context = TAG)
            }
        }
        
        Logger.info("Restored ${sessionCache.size} sessions from keychain", context = TAG)
    }
    
    /**
     * Get all cached sessions
     */
    fun getCachedSessions(): List<PubkySession> = sessionCache.values.toList()
    
    /**
     * Get count of cached keypairs
     */
    fun getCachedKeypairCount(): Int = keypairCache.size
    
    /**
     * Clear a specific session from cache and keychain
     */
    suspend fun clearSession(pubkey: String) {
        sessionCache.remove(pubkey)
        keychainStorage.delete("pubky.session.$pubkey")
        Logger.info("Cleared session for ${pubkey.take(12)}...", context = TAG)
    }
    
    /**
     * Clear all sessions from cache and keychain
     */
    suspend fun clearAllSessions() {
        for (pubkey in sessionCache.keys.toList()) {
            keychainStorage.delete("pubky.session.$pubkey")
        }
        sessionCache.clear()
        Logger.info("Cleared all sessions", context = TAG)
    }
    
    /**
     * Set a session directly (for manual or imported sessions)
     */
    fun setCachedSession(session: PubkySession) {
        sessionCache[session.pubkey] = session
        // Persist to keychain (fire-and-forget)
        scope.launch {
            persistSession(session)
        }
    }
    
    // MARK: - Backup & Restore
    
    /**
     * Export all sessions and noise keys for backup
     *
     * @return BackupData containing device ID, sessions, and noise keys
     */
    fun exportBackup(): BackupData {
        val sessions = sessionCache.values.toList()
        val noiseKeys = keypairCache.map { (_, keypair) ->
            BackupNoiseKey(
                deviceId = keypair.deviceId,
                epoch = keypair.epoch,
                secretKey = keypair.secretKey,
            )
        }
        
        val backup = BackupData(
            deviceId = deviceId,
            sessions = sessions,
            noiseKeys = noiseKeys,
            exportedAt = Date(),
            version = 1,
        )
        
        Logger.info("Exported backup with ${sessions.size} sessions and ${noiseKeys.size} noise keys", context = TAG)
        return backup
    }
    
    /**
     * Export backup as JSON string
     */
    fun exportBackupAsJSON(): String {
        val backup = exportBackup()
        return com.google.gson.Gson().toJson(backup)
    }
    
    /**
     * Import backup data and restore sessions/keys
     *
     * @param backup The backup data to restore
     * @param overwriteDeviceId Whether to overwrite the local device ID with the backup's
     */
    suspend fun importBackup(backup: BackupData, overwriteDeviceId: Boolean = false) {
        // Optionally restore device ID
        if (overwriteDeviceId) {
            val key = "paykit.device_id"
            keychainStorage.store(key, backup.deviceId.toByteArray())
            _deviceId = backup.deviceId
            Logger.info("Restored device ID from backup", context = TAG)
        }
        
        // Restore sessions
        for (session in backup.sessions) {
            sessionCache[session.pubkey] = session
            persistSession(session)
        }
        
        // Restore noise keys
        for (noiseKey in backup.noiseKeys) {
            val cacheKey = "${noiseKey.deviceId}:${noiseKey.epoch}"
            
            // Restore to keypair cache (we only have the secret key, not public)
            val secretKeyData = noiseKey.secretKey.toByteArray()
            noiseKeyCache.setKey(secretKeyData, noiseKey.deviceId, noiseKey.epoch.toUInt())
        }
        
        Logger.info("Imported backup with ${backup.sessions.size} sessions and ${backup.noiseKeys.size} noise keys", context = TAG)
    }
    
    /**
     * Import backup from JSON string
     */
    suspend fun importBackup(jsonString: String, overwriteDeviceId: Boolean = false) {
        val backup = com.google.gson.Gson().fromJson(jsonString, BackupData::class.java)
        importBackup(backup, overwriteDeviceId)
    }
}

// MARK: - Backup Data Models

/**
 * Backup data structure for export/import
 */
data class BackupData(
    val deviceId: String,
    val sessions: List<PubkySession>,
    val noiseKeys: List<BackupNoiseKey>,
    val exportedAt: Date,
    val version: Int,
)

/**
 * Noise key backup structure
 */
data class BackupNoiseKey(
    val deviceId: String,
    val epoch: ULong,
    val secretKey: String,
)

/**
 * Session data returned from Pubky-ring
 */
@kotlinx.serialization.Serializable
data class PubkySession(
    val pubkey: String,
    val sessionSecret: String,
    val capabilities: List<String>,
    @kotlinx.serialization.Contextual
    val createdAt: Date,
    @kotlinx.serialization.Contextual
    val expiresAt: Date? = null,
) {
    /**
     * Check if session has a specific capability
     */
    fun hasCapability(capability: String): Boolean = capabilities.contains(capability)
    
    /**
     * Check if session is expired
     */
    val isExpired: Boolean
        get() = expiresAt?.let { Date().after(it) } ?: false
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
 * Secure handoff payload structure (stored on homeserver by Ring)
 */
@kotlinx.serialization.Serializable
private data class SecureHandoffPayload(
    val version: Int,
    val pubky: String,
    @kotlinx.serialization.SerialName("session_secret")
    val sessionSecret: String,
    val capabilities: List<String>,
    @kotlinx.serialization.SerialName("device_id")
    val deviceId: String,
    @kotlinx.serialization.SerialName("noise_keypairs")
    val noiseKeypairs: List<SecureHandoffNoiseKeypair>,
    @kotlinx.serialization.SerialName("created_at")
    val createdAt: Long,
    @kotlinx.serialization.SerialName("expires_at")
    val expiresAt: Long,
)

@kotlinx.serialization.Serializable
private data class SecureHandoffNoiseKeypair(
    val epoch: Int,
    @kotlinx.serialization.SerialName("public_key")
    val publicKey: String,
    @kotlinx.serialization.SerialName("secret_key")
    val secretKey: String,
)

/**
 * Result from combined Paykit setup request.
 * Contains everything needed to operate Paykit: session + noise keys.
 */
data class PaykitSetupResult(
    /** Homeserver session for authenticated storage access */
    val session: PubkySession,
    /** Device ID used for noise key derivation */
    val deviceId: String,
    /** X25519 keypair for epoch 0 (current) */
    val noiseKeypair0: NoiseKeypair?,
    /** X25519 keypair for epoch 1 (for rotation) */
    val noiseKeypair1: NoiseKeypair?,
) {
    /** Check if noise keys are available */
    val hasNoiseKeys: Boolean get() = noiseKeypair0 != null
}

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
    object SignatureFailed : PubkyRingException("Failed to generate signature")
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
            is SignatureFailed -> "Failed to sign the message. Please try again."
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
