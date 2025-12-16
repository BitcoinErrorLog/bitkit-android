package to.bitkit.paykit.services

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import to.bitkit.utils.Logger
import java.net.URLEncoder
import java.util.Date
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
 * Communication Flow:
 * 1. Bitkit sends request via URL scheme: `pubkyring://session?callback=bitkit://paykit-session`
 * 2. Pubky-ring prompts user to select a pubky
 * 3. Pubky-ring signs in to homeserver
 * 4. Pubky-ring opens callback URL with data: `bitkit://paykit-session?pubky=...&session_secret=...`
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
        
        // Callback paths
        const val CALLBACK_PATH_SESSION = "paykit-session"
        const val CALLBACK_PATH_KEYPAIR = "paykit-keypair"
        const val CALLBACK_PATH_PROFILE = "paykit-profile"
        const val CALLBACK_PATH_FOLLOWS = "paykit-follows"
        
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
    suspend fun requestNoiseKeypair(context: Context, deviceId: String, epoch: ULong): NoiseKeypair = suspendCancellableCoroutine { continuation ->
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
            epoch = epoch
        )
        
        // Cache the keypair
        val cacheKey = "$deviceId:$epoch"
        keypairCache[cacheKey] = keypair
        
        pendingKeypairContinuation?.resume(keypair)
        pendingKeypairContinuation = null
        
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
}

