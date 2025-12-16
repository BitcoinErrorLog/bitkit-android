package to.bitkit.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import to.bitkit.paykit.services.PubkyRingBridge
import to.bitkit.paykit.services.PubkySession
import java.util.Base64

/**
 * Simulates Pubky-ring app responses for testing purposes
 * This allows E2E tests to run without requiring the actual Pubky-ring app
 */
object PubkyRingSimulator {
    
    // Test data
    const val TEST_PUBKEY = "test123456789abcdefghijklmnopqrstuvwxyz"
    const val TEST_SESSION_SECRET = "secret123456789abcdefghijklmnop"
    const val TEST_NOISE_KEY = "noise123456789abcdefghijklmnopqrst"
    
    // MARK: - Session Simulation
    
    /**
     * Inject a session callback as if Pubky-ring returned it
     * @param context Android context
     * @param pubkey The pubkey to use (defaults to test pubkey)
     * @param sessionSecret The session secret to use (defaults to test secret)
     */
    fun injectSessionCallback(
        context: Context,
        pubkey: String = TEST_PUBKEY,
        sessionSecret: String = TEST_SESSION_SECRET
    ) {
        val callbackUri = Uri.parse("bitkit://paykit-session?pubkey=$pubkey&session_secret=$sessionSecret")
        
        // Trigger the callback handler
        val handled = PubkyRingBridge.getInstance().handleCallback(callbackUri)
        
        if (!handled) {
            println("PubkyRingSimulator: Failed to inject session callback")
        }
    }
    
    /**
     * Inject a session callback via Intent
     */
    fun injectSessionCallbackViaIntent(
        context: Context,
        pubkey: String = TEST_PUBKEY,
        sessionSecret: String = TEST_SESSION_SECRET
    ) {
        val callbackUri = Uri.parse("bitkit://paykit-session?pubkey=$pubkey&session_secret=$sessionSecret")
        
        val intent = Intent(Intent.ACTION_VIEW, callbackUri).apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        context.startActivity(intent)
    }
    
    /**
     * Inject a keypair callback
     * @param pubkey The public key
     * @param privateKey The private key (base64 encoded)
     */
    fun injectKeypairCallback(
        context: Context,
        pubkey: String = TEST_PUBKEY,
        privateKey: String = "privatekey123456789"
    ) {
        val callbackUri = Uri.parse("bitkit://paykit-keypair?pubkey=$pubkey&private_key=$privateKey")
        
        val handled = PubkyRingBridge.getInstance().handleCallback(callbackUri)
        
        if (!handled) {
            println("PubkyRingSimulator: Failed to inject keypair callback")
        }
    }
    
    /**
     * Inject a profile callback
     * @param name Profile name
     * @param bio Profile bio
     * @param pubkey The pubkey
     */
    fun injectProfileCallback(
        context: Context,
        name: String = "Test User",
        bio: String = "Test bio",
        pubkey: String = TEST_PUBKEY
    ) {
        val profileJson = """{"name":"$name","bio":"$bio","pubkey":"$pubkey"}"""
        val encoded = Base64.getEncoder().encodeToString(profileJson.toByteArray())
        
        val callbackUri = Uri.parse("bitkit://paykit-profile?data=$encoded")
        
        val handled = PubkyRingBridge.getInstance().handleCallback(callbackUri)
        
        if (!handled) {
            println("PubkyRingSimulator: Failed to inject profile callback")
        }
    }
    
    /**
     * Inject a follows list callback
     * @param follows List of pubkeys being followed
     */
    fun injectFollowsCallback(
        context: Context,
        follows: List<String> = emptyList()
    ) {
        val followsJson = follows.joinToString(",") { "\"$it\"" }
        val encoded = Base64.getEncoder().encodeToString("[$followsJson]".toByteArray())
        
        val callbackUri = Uri.parse("bitkit://paykit-follows?data=$encoded")
        
        val handled = PubkyRingBridge.getInstance().handleCallback(callbackUri)
        
        if (!handled) {
            println("PubkyRingSimulator: Failed to inject follows callback")
        }
    }
    
    // MARK: - Test Session Helpers
    
    /**
     * Create a test PubkySession
     */
    fun createTestSession(
        pubkey: String = TEST_PUBKEY,
        sessionSecret: String = TEST_SESSION_SECRET
    ): PubkySession {
        return PubkySession(
            pubkey = pubkey,
            sessionSecret = sessionSecret
        )
    }
    
    /**
     * Directly cache a test session in PubkyRingBridge
     */
    fun cacheTestSession(
        pubkey: String = TEST_PUBKEY,
        sessionSecret: String = TEST_SESSION_SECRET
    ) {
        val session = createTestSession(pubkey, sessionSecret)
        PubkyRingBridge.getInstance().cacheSession(session)
    }
    
    // MARK: - Cleanup
    
    /**
     * Clear all cached sessions and state
     */
    fun reset() {
        PubkyRingBridge.getInstance().clearCache()
    }
    
    // MARK: - Test Assertions
    
    /**
     * Verify that a session was successfully cached
     */
    fun assertSessionCached(pubkey: String = TEST_PUBKEY): Boolean {
        return PubkyRingBridge.getInstance().getCachedSession(pubkey) != null
    }
    
    /**
     * Get the cached session for verification
     */
    fun getCachedSession(pubkey: String = TEST_PUBKEY): PubkySession? {
        return PubkyRingBridge.getInstance().getCachedSession(pubkey)
    }
}

