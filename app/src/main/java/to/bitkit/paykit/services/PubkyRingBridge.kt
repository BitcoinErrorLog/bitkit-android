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
import to.bitkit.paykit.protocol.PaykitV0Protocol
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
 *
 * Note: This class is large because it handles all Pubky-ring communication in one place.
 * Callback parsing is delegated to PubkyRingCallbackParser and secure handoff to SecureHandoffHandler.
 * The remaining complexity is inherent to the multi-mode authentication flows.
 */
@Suppress("TooManyFunctions", "LargeClass") // Bridge pattern requires centralized request/callback handling
@Singleton
class PubkyRingBridge @Inject constructor(
    private val keychainStorage: to.bitkit.paykit.storage.PaykitKeychainStorage,
    private val noiseKeyCache: NoiseKeyCache,
    private val pubkyStorageAdapter: PubkyStorageAdapter,
    private val callbackParser: PubkyRingCallbackParser,
    private val secureHandoffHandler: SecureHandoffHandler,
    private val keyManager: to.bitkit.paykit.KeyManager,
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
        const val CALLBACK_PATH_PROFILE = "paykit-profile"
        const val CALLBACK_PATH_FOLLOWS = "paykit-follows"
        const val CALLBACK_PATH_PAYKIT_SETUP = "paykit-setup" // Combined session + noise keys
        const val CALLBACK_PATH_SIGNATURE_RESULT = "signature-result" // Ed25519 signature result

        // Helper: convert ByteArray to hex string
        fun byteArrayToHexString(bytes: ByteArray): String =
            bytes.joinToString("") { "%02x".format(it) }

        // Helper: convert hex string to ByteArray
        fun hexStringToByteArray(hex: String): ByteArray =
            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    // Coroutine scope for fire-and-forget persistence operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Cleanup resources. Call when the bridge is no longer needed.
     */
    fun cleanup() {
        scope.cancel("PubkyRingBridge cleanup")
        pendingPaykitSetupContinuation = null
        Logger.debug("PubkyRingBridge cleaned up", context = TAG)
    }

    // Pending continuations for async requests
    private var pendingPaykitSetupContinuation: Continuation<PaykitSetupResult>? = null

    // Pending cross-device request ID and ephemeral key
    private var pendingCrossDeviceRequestId: String? = null
    private var pendingCrossDeviceEphemeralSk: ByteArray? = null

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
    suspend fun requestSession(context: Context): PubkySession {
        // Delegate to requestPaykitSetup which is the secure flow
        val result = requestPaykitSetup(context)
        return result.session
    }

    /**
     * Request complete Paykit setup from Pubky-ring (session + noise keys in one request)
     *
     * This is the preferred method for initial Paykit setup as it:
     * - Gets everything in a single user interaction
     * - Ensures noise keys are available even if Ring is later unavailable
     * - Includes both epoch 0 and epoch 1 keypairs for key rotation
     * - Uses encrypted handoff (ephemeralPk) to prevent plaintext secrets on homeserver
     *
     * Flow:
     * 1. Bitkit generates ephemeral X25519 keypair
     * 2. Bitkit stores ephemeral secret key temporarily
     * 3. Bitkit sends ephemeralPk to Ring
     * 4. Ring encrypts payload to ephemeralPk using Sealed Blob v1
     * 5. Ring stores encrypted envelope on homeserver
     * 6. Bitkit fetches and decrypts using ephemeral secret key
     * 7. Bitkit zeroizes ephemeral secret key
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

        // Generate ephemeral X25519 keypair for this handoff
        val ephemeralKeypair = com.pubky.noise.x25519GenerateKeypair()
        val ephemeralPkHex = ephemeralKeypair.publicKey.joinToString("") { byte -> "%02x".format(byte) }
        val ephemeralSkHex = ephemeralKeypair.secretKey.joinToString("") { byte -> "%02x".format(byte) }

        // Store ephemeral secret key for decryption when callback arrives
        secureHandoffHandler.storeEphemeralKey(ephemeralSkHex)

        Logger.debug("Generated ephemeral keypair for handoff, pk=${ephemeralPkHex.take(16)}...", context = TAG)

        val callbackUrl = "$BITKIT_SCHEME://$CALLBACK_PATH_PAYKIT_SETUP"
        val encodedCallback = URLEncoder.encode(callbackUrl, "UTF-8")
        val requestUrl = "$PUBKY_RING_SCHEME://paykit-connect?deviceId=$actualDeviceId&callback=$encodedCallback&ephemeralPk=$ephemeralPkHex"

        Logger.info("Requesting Paykit setup from Pubky Ring with encrypted handoff", context = TAG)

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
     * Request a noise keypair for the given epoch.
     *
     * Priority order:
     * 1. Memory cache
     * 2. Persistent cache (NoiseKeyCache) - if secret key stored, derive public key locally
     * 3. Local derivation using noise_seed (avoids Ring roundtrip)
     * 4. Fallback: Request from Pubky-ring (legacy, requires Ring app)
     *
     * @param context Android context for launching intents
     * @param deviceIdOverride Override device ID (uses stored ID if null)
     * @param epoch Epoch for key rotation
     * @return X25519 keypair for Noise protocol
     * @throws PubkyRingException if request fails and no local derivation possible
     */
    suspend fun requestNoiseKeypair(
        context: Context,
        deviceIdOverride: String? = null,
        epoch: ULong,
    ): NoiseKeypair {
        val actualDeviceId = deviceIdOverride ?: deviceId
        val cacheKey = "$actualDeviceId:$epoch"

        // 1. Check memory cache first
        keypairCache[cacheKey]?.let { cached ->
            Logger.debug("Noise keypair cache hit for $cacheKey", context = TAG)
            return cached
        }

        // 2. Check persistent cache (NoiseKeyCache) - derive public key if we have secret
        try {
            val secretKeyBytes = noiseKeyCache.getKey(actualDeviceId, epoch.toUInt())
            if (secretKeyBytes != null) {
                // Derive public key from secret key locally
                val publicKeyBytes = com.pubky.noise.publicKeyFromSecret(secretKeyBytes)
                val keypair = NoiseKeypair(
                    secretKey = byteArrayToHexString(secretKeyBytes),
                    publicKey = byteArrayToHexString(publicKeyBytes),
                    deviceId = actualDeviceId,
                    epoch = epoch,
                )
                keypairCache[cacheKey] = keypair
                Logger.debug("Noise keypair reconstructed from persistent cache for $cacheKey", context = TAG)
                return keypair
            }
        } catch (e: Exception) {
            Logger.warn("Error checking NoiseKeyCache or deriving public key: ${e.message}", e, context = TAG)
        }

        // 3. Try local derivation using noise_seed (avoids Ring roundtrip)
        val localKeypair = deriveKeypairLocally(actualDeviceId, epoch)
        if (localKeypair != null) {
            keypairCache[cacheKey] = localKeypair
            // Persist for future use
            val secretKeyBytes = hexStringToByteArray(localKeypair.secretKey)
            noiseKeyCache.setKey(secretKeyBytes, actualDeviceId, epoch.toUInt())
            Logger.info("Noise keypair derived locally for epoch $epoch", context = TAG)
            return localKeypair
        }

        // 4. No noise_seed available - require secure handoff
        // The derive-keypair Ring action has been removed for security (exposed secrets in URLs).
        // Users must re-authenticate using paykit-connect to get a noise_seed.
        throw PubkyRingException.Custom(
            "No noise_seed available for local derivation. Please reconnect to Pubky Ring to refresh your session.",
        )
    }

    /**
     * Derive a noise keypair locally using the stored noise_seed
     *
     * This avoids calling Ring for future epoch derivations.
     *
     * @param deviceId Device identifier for key derivation
     * @param epoch Epoch for key rotation
     * @return X25519 keypair if noise_seed is available, null otherwise
     */
    private fun deriveKeypairLocally(deviceId: String, epoch: ULong): NoiseKeypair? {
        // Get the stored noise_seed for this device
        val noiseSeed = secureHandoffHandler.getNoiseSeed(deviceId) ?: return null
        val seedBytes = hexStringToByteArray(noiseSeed)
        if (seedBytes.size < 32) return null

        val deviceIdBytes = deviceId.toByteArray(Charsets.UTF_8)

        return try {
            // Derive secret key using pubky-noise FFI
            val secretKeyBytes = com.pubky.noise.deriveDeviceKey(
                seedBytes,
                deviceIdBytes,
                epoch.toUInt(),
            )

            // Derive public key from secret key
            val publicKeyBytes = com.pubky.noise.publicKeyFromSecret(secretKeyBytes)

            NoiseKeypair(
                secretKey = byteArrayToHexString(secretKeyBytes),
                publicKey = byteArrayToHexString(publicKeyBytes),
                deviceId = deviceId,
                epoch = epoch,
            )
        } catch (e: Exception) {
            Logger.warn("Failed to derive keypair locally: ${e.message}", e, context = TAG)
            null
        }
    }

    /**
     * Get cached session for a pubkey
     */
    fun getCachedSession(pubkey: String): PubkySession? = sessionCache[pubkey]

    /**
     * Handle an auth URL callback (deprecated - use requestPaykitSetup instead)
     *
     * This method is kept for backward compatibility but always throws an exception
     * as the legacy URL-based auth flow has been deprecated for security reasons.
     */
    @Deprecated("Use requestPaykitSetup instead. Legacy URL-based auth is insecure.")
    @Suppress("UnusedParameter")
    fun handleAuthUrl(url: String): PubkySession {
        throw PubkyRingException.Custom(
            "Legacy URL-based authentication is deprecated. Please use the secure Paykit setup flow."
        )
    }

    /**
     * Import a session from pubkey and secret (deprecated - use requestPaykitSetup instead)
     *
     * This method creates a session from manually provided credentials.
     * Use with caution - prefer the secure handoff flow.
     */
    @Deprecated("Use requestPaykitSetup instead for secure session handling.")
    fun importSession(pubkey: String, sessionSecret: String): PubkySession {
        val session = PubkySession(
            pubkey = pubkey,
            sessionSecret = sessionSecret,
            capabilities = emptyList(),
            createdAt = java.util.Date(),
        )
        sessionCache[pubkey] = session
        // Persist asynchronously
        scope.launch {
            persistSession(session)
        }
        Logger.info("Imported session for ${pubkey.take(12)}...", context = TAG)
        return session
    }

    /**
     * Clear all cached data
     */
    fun clearCache() {
        sessionCache.clear()
        keypairCache.clear()
        pendingCrossDeviceRequestId = null
        pendingCrossDeviceEphemeralSk = null
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
    suspend fun requestProfile(
        context: Context,
        pubkey: String
    ): PubkyProfile? = suspendCancellableCoroutine { continuation ->
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
    suspend fun requestSignature(
        context: Context,
        message: String
    ): String = suspendCancellableCoroutine { continuation ->
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
     * Generate a cross-device session request that can be shared as a link or QR.
     *
     * SECURITY: Generates an ephemeral X25519 keypair and includes the public key
     * in the URL. The relay response must be encrypted to this key (Sealed Blob v1).
     *
     * @return CrossDeviceRequest with URL, QR code bitmap, and request ID
     */
    fun generateCrossDeviceRequest(): CrossDeviceRequest {
        val requestId = UUID.randomUUID().toString().lowercase()
        pendingCrossDeviceRequestId = requestId

        // Generate ephemeral X25519 keypair for secure relay response
        val ephemeralKeypair = com.pubky.noise.x25519GenerateKeypair()
        pendingCrossDeviceEphemeralSk = ephemeralKeypair.secretKey
        val ephemeralPkHex = byteArrayToHexString(ephemeralKeypair.publicKey)

        // Build the URL for cross-device auth (construct manually for testability)
        val url = buildCrossDeviceUrl(requestId, ephemeralPkHex)
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
     *
     * @param ephemeralPk Optional ephemeral public key for secure relay response (hex encoded)
     */
    internal fun buildCrossDeviceUrl(requestId: String, ephemeralPk: String? = null): String {
        val encodedRequestId = URLEncoder.encode(requestId, "UTF-8")
        val encodedCallback = URLEncoder.encode(BITKIT_SCHEME, "UTF-8")
        val encodedAppName = URLEncoder.encode("Bitkit", "UTF-8")
        val encodedRelay = URLEncoder.encode(Companion.getRelayUrl(), "UTF-8")

        val baseUrl = "$CROSS_DEVICE_WEB_URL?request_id=$encodedRequestId&callback_scheme=$encodedCallback&app_name=$encodedAppName&relay_url=$encodedRelay"
        return if (ephemeralPk != null) {
            "$baseUrl&ephemeralPk=$ephemeralPk"
        } else {
            baseUrl
        }
    }

    /**
     * Poll for a cross-device session response.
     *
     * SECURITY: The relay response must be a Sealed Blob v1 encrypted to our ephemeral key.
     * Plaintext responses are rejected.
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

        try {
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
                        persistSession(session)
                        pendingCrossDeviceRequestId = null
                        pendingCrossDeviceEphemeralSk = null
                        return@withContext session
                    }
                } catch (e: Exception) {
                    Logger.debug("Relay poll failed: ${e.message}", context = TAG)
                }

                delay(pollIntervalMs)
            }

            throw PubkyRingException.Timeout
        } finally {
            // Clean up ephemeral key on exit
            pendingCrossDeviceRequestId = null
            pendingCrossDeviceEphemeralSk = null
        }
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

    /**
     * Poll the relay for a session response.
     *
     * SECURITY: Expects a Sealed Blob v1 encrypted response when ephemeralSk is available.
     * Plaintext responses are REJECTED for security.
     */
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
                    parseRelayResponse(response, requestId)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Parse a relay response, decrypting if it's a sealed blob.
     *
     * SECURITY: Plaintext responses are REJECTED if we have an ephemeral key.
     */
    private fun parseRelayResponse(response: String, requestId: String): PubkySession? {
        val ephemeralSk = pendingCrossDeviceEphemeralSk

        // Check if it's an encrypted sealed blob
        if (com.pubky.noise.isSealedBlob(response)) {
            if (ephemeralSk == null) {
                Logger.error("SECURITY: Received sealed blob but no ephemeral key available", context = TAG)
                return null
            }

            return try {
                // AAD format for cross-device relay
                val aad = PaykitV0Protocol.relaySessionAad(requestId)
                val plaintextBytes = com.pubky.noise.sealedBlobDecrypt(ephemeralSk, response, aad)
                val plaintextJson = String(plaintextBytes)
                parseSessionFromJson(plaintextJson)
            } catch (e: Exception) {
                Logger.error("Failed to decrypt relay response", e, context = TAG)
                null
            }
        } else {
            // Plaintext response - REJECT for security
            Logger.error(
                "SECURITY: Cross-device relay returned plaintext (insecure). Use encrypted relay.",
                context = TAG
            )
            return null
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
            CALLBACK_PATH_PROFILE -> handleProfileCallback(uri)
            CALLBACK_PATH_FOLLOWS -> handleFollowsCallback(uri)
            CALLBACK_PATH_PAYKIT_SETUP -> handlePaykitSetupCallback(uri)
            CALLBACK_PATH_SIGNATURE_RESULT -> handleSignatureCallback(uri)
            else -> false
        }
    }

    private fun handlePaykitSetupCallback(uri: Uri): Boolean {
        if (callbackParser.isSecureHandoffMode(uri)) {
            return handleSecureHandoffMode(uri)
        }
        return handleLegacySetupMode(uri)
    }

    private fun handleSecureHandoffMode(uri: Uri): Boolean {
        when (val result = callbackParser.parseSecureHandoffReference(uri)) {
            is PubkyRingCallbackParser.CallbackResult.Error -> {
                pendingPaykitSetupContinuation?.resumeWithException(result.exception)
                pendingPaykitSetupContinuation = null
                return true
            }
            is PubkyRingCallbackParser.CallbackResult.Success -> {
                val ref = result.data
                scope.launch {
                    try {
                        val setupResult = secureHandoffHandler.fetchAndProcessPayload(
                            pubkey = ref.pubkey,
                            requestId = ref.requestId,
                            scope = scope,
                            onSessionPersisted = { session ->
                                sessionCache[session.pubkey] = session
                                persistSession(session)
                            }
                        )
                        pendingPaykitSetupContinuation?.resume(setupResult)
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        throw e
                    } catch (e: PubkyRingException) {
                        pendingPaykitSetupContinuation?.resumeWithException(e)
                    } catch (e: Exception) {
                        pendingPaykitSetupContinuation?.resumeWithException(
                            PubkyRingException.CrossDeviceFailed(e.message ?: "Unknown error")
                        )
                    }
                    pendingPaykitSetupContinuation = null
                }
                return true
            }
        }
    }

    private fun handleLegacySetupMode(uri: Uri): Boolean {
        when (val result = callbackParser.parseLegacySetupCallback(uri)) {
            is PubkyRingCallbackParser.CallbackResult.Error -> {
                pendingPaykitSetupContinuation?.resumeWithException(result.exception)
                pendingPaykitSetupContinuation = null
                return true
            }
            is PubkyRingCallbackParser.CallbackResult.Success -> {
                val data = result.data
                val setupResult = buildSetupResultFromLegacyData(data)
                cacheAndPersistSetupResult(setupResult)
                Logger.info("Paykit setup callback received for ${data.session.pubkey.take(12)}...", context = TAG)
                pendingPaykitSetupContinuation?.resume(setupResult)
                pendingPaykitSetupContinuation = null
                return true
            }
        }
    }

    private fun buildSetupResultFromLegacyData(data: PubkyRingCallbackParser.LegacySetupData): PaykitSetupResult {
        val session = PubkySession(
            pubkey = data.session.pubkey,
            sessionSecret = data.session.sessionSecret,
            capabilities = data.session.capabilities,
            createdAt = Date(),
            expiresAt = null,
        )

        val keypair0 = data.keypair0?.let {
            NoiseKeypair(
                publicKey = it.publicKey,
                secretKey = it.secretKey,
                deviceId = it.deviceId,
                epoch = it.epoch,
            )
        }

        val keypair1 = data.keypair1?.let {
            NoiseKeypair(
                publicKey = it.publicKey,
                secretKey = it.secretKey,
                deviceId = it.deviceId,
                epoch = it.epoch,
            )
        }

        return PaykitSetupResult(
            session = session,
            deviceId = data.deviceId,
            noiseKeypair0 = keypair0,
            noiseKeypair1 = keypair1,
        )
    }

    private fun cacheAndPersistSetupResult(result: PaykitSetupResult) {
        sessionCache[result.session.pubkey] = result.session

        result.noiseKeypair0?.let { keypair ->
            val cacheKey = "${result.deviceId}:0"
            keypairCache[cacheKey] = keypair
            persistKeypairQuietly(keypair, result.deviceId, 0u)
        }

        result.noiseKeypair1?.let { keypair ->
            val cacheKey = "${result.deviceId}:1"
            keypairCache[cacheKey] = keypair
            persistKeypairQuietly(keypair, result.deviceId, 1u)
        }
    }

    private fun persistKeypairQuietly(keypair: NoiseKeypair, deviceId: String, epoch: UInt) {
        try {
            // keypair.secretKey is a hex string (64 chars for 32-byte X25519 key)
            // Decode hex to actual key bytes before storing
            val secretKeyData = hexStringToByteArray(keypair.secretKey)
            noiseKeyCache.setKeySync(secretKeyData, deviceId, epoch)
            Logger.debug("Stored noise keypair for epoch $epoch (${secretKeyData.size} bytes)", context = TAG)
        } catch (e: Exception) {
            Logger.warn("Failed to store noise keypair epoch $epoch: ${e.message}", e, context = TAG)
        }
    }

    private fun handleProfileCallback(uri: Uri): Boolean {
        val profile = callbackParser.parseProfileCallback(uri)
        if (profile != null) {
            Logger.debug("Received profile from Pubky-ring: ${profile.name ?: "unknown"}", context = TAG)
        } else {
            Logger.warn("Profile request returned error or no data", context = TAG)
        }
        pendingProfileContinuation?.resume(profile)
        pendingProfileContinuation = null
        return true
    }

    private fun handleFollowsCallback(uri: Uri): Boolean {
        val follows = callbackParser.parseFollowsCallback(uri)
        if (follows == null) {
            Logger.warn("Follows request returned error", context = TAG)
            pendingFollowsContinuation?.resume(emptyList())
        } else {
            Logger.debug("Received ${follows.size} follows from Pubky-ring", context = TAG)
            pendingFollowsContinuation?.resume(follows)
        }
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

    /**
     * DISABLED: Cross-device session callback with plaintext session_secret.
     *
     * SECURITY: This flow is DISABLED because it exposes secrets in callback URLs.
     * Secrets in URLs are logged by system URL handlers, appear in app history,
     * and can be captured by malicious URL handlers.
     *
     * For cross-device authentication, use the secure pubkyauth:// flow which:
     * - Encrypts the AuthToken with a shared client_secret (exchanged via QR)
     * - Only transmits encrypted blobs to the relay
     * - Never exposes secrets in callback URLs
     *
     * See docs/ENCRYPTED_RELAY_PROTOCOL.md for details.
     */
    private fun handleCrossDeviceSessionCallback(uri: Uri): Boolean {
        Logger.error(
            "SECURITY: Plaintext cross-device session callback REJECTED. " +
                "Use secure pubkyauth:// flow instead. See ENCRYPTED_RELAY_PROTOCOL.md",
            context = TAG,
        )

        // Always reject plaintext secrets in callback URLs
        pendingCrossDeviceRequestId = null
        return false
    }

    // MARK: - Session Persistence

    /**
     * Persist a session to keychain
     */
    private suspend fun persistSession(session: PubkySession) {
        try {
            val json = kotlinx.serialization.json.Json.encodeToString(PubkySession.serializer(), session)
            keychainStorage.setString("pubky.session.${session.pubkey}", json)
            // Also store in DirectoryService format so tryRestoreFromKeychain works
            keychainStorage.setString("pubky.identity.public", session.pubkey)
            keychainStorage.setString("pubky.session.secret", session.sessionSecret)
            // Also update KeyManager with the identity's public key for subscription operations
            keyManager.storePublicKey(session.pubkey)
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
                // Update KeyManager with identity pubkey (ensures subscriptions work after app restart)
                keyManager.storePublicKey(session.pubkey)
                // Also store in DirectoryService format so tryRestoreFromKeychain works
                keychainStorage.setString("pubky.identity.public", session.pubkey)
                keychainStorage.setString("pubky.session.secret", session.sessionSecret)
                Logger.info("Restored session for ${session.pubkey.take(12)}...", context = TAG)

                // Verify Noise endpoint is published (fallback publish if Ring didn't)
                verifyOrPublishNoiseEndpoint(session.pubkey)
            } catch (e: Exception) {
                Logger.error("Failed to restore session from $key", e, context = TAG)
            }
        }

        Logger.info("Restored ${sessionCache.size} sessions from keychain", context = TAG)
    }

    /**
     * Verify that Noise endpoint is published for a pubkey, publishing it if not.
     * This is needed when Ring fails to publish during handoff.
     */
    private suspend fun verifyOrPublishNoiseEndpoint(pubkey: String) {
        try {
            // Get cached keypair from KeyManager (uses epoch 0 by default)
            val keypair = keyManager.getCachedNoiseKeypair()
            if (keypair != null) {
                secureHandoffHandler.ensureNoiseEndpointPublished(
                    pubkey = pubkey,
                    noisePubkeyHex = keypair.publicKeyHex,
                    deviceId = keypair.deviceId,
                )
            } else {
                // Just verify - can't publish without keypair
                val isPublished = secureHandoffHandler.verifyNoiseEndpointPublished(pubkey)
                if (!isPublished) {
                    Logger.warn(
                        "Cannot publish Noise endpoint: no keypair cached for ${pubkey.take(12)}...",
                        context = TAG
                    )
                }
            }
        } catch (e: Exception) {
            Logger.warn("Error checking Noise endpoint for ${pubkey.take(12)}...: ${e.message}", context = TAG)
        }
    }

    /**
     * Ensure any cached session's pubkey is stored in KeyManager.
     * Call this if sessions exist in memory but weren't properly persisted.
     */
    suspend fun ensureIdentitySynced() {
        for (session in sessionCache.values) {
            keyManager.storePublicKey(session.pubkey)
            // Also persist the session if not already persisted
            val existingJson = keychainStorage.getString("pubky.session.${session.pubkey}")
            if (existingJson == null) {
                persistSession(session)
                Logger.info("Synced session for ${session.pubkey.take(12)}...", context = TAG)
            }
        }
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

        Logger.info(
            "Imported backup with ${backup.sessions.size} sessions and ${backup.noiseKeys.size} noise keys",
            context = TAG
        )
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
 * Custom serializer for java.util.Date as epoch milliseconds
 */
object DateAsLongSerializer : kotlinx.serialization.KSerializer<Date> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "Date",
        kotlinx.serialization.descriptors.PrimitiveKind.LONG,
    )

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Date) =
        encoder.encodeLong(value.time)

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Date =
        Date(decoder.decodeLong())
}

/**
 * Session data returned from Pubky-ring
 */
@kotlinx.serialization.Serializable
data class PubkySession(
    val pubkey: String,
    val sessionSecret: String,
    val capabilities: List<String>,
    @kotlinx.serialization.Serializable(with = DateAsLongSerializer::class)
    val createdAt: Date,
    @kotlinx.serialization.Serializable(with = DateAsLongSerializer::class)
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
    /** Noise seed for local epoch derivation (so Bitkit doesn't need to re-call Ring) */
    val noiseSeed: String? = null,
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
    object MissingEphemeralKey : PubkyRingException("Ephemeral key not found - cannot decrypt handoff")
    data class DecryptionFailed(val reason: String) : PubkyRingException("Decryption failed: $reason")
    data class Custom(val reason: String) : PubkyRingException(reason)

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
            is MissingEphemeralKey -> "Session setup failed. Please update Bitkit and try again."
            is DecryptionFailed -> "Failed to decrypt session data. Please try again."
            is Custom -> reason
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
