package to.bitkit.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.util.UUID

/**
 * Test helper for simulating Pubky-ring app interactions in E2E tests
 */
object PubkyRingTestHelper {

    // MARK: - Test Data

    /** Test pubkey for E2E tests */
    const val TEST_PUBKEY = "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"

    /** Test device ID */
    val testDeviceId: String
        get() = "test_device_${UUID.randomUUID()}"

    /** Test session secret */
    val testSessionSecret: String
        get() = "test_session_secret_${UUID.randomUUID()}"

    // MARK: - Session Simulation

    /**
     * Create test session data
     */
    fun createTestSession(
        pubkey: String = TEST_PUBKEY,
        expiresInHours: Int = 24
    ): Map<String, Any> {
        val expiresAt = System.currentTimeMillis() + (expiresInHours * 3600 * 1000)
        return mapOf(
            "pubkey" to pubkey,
            "session_secret" to testSessionSecret,
            "capabilities" to listOf("read", "write"),
            "expires_at" to expiresAt
        )
    }

    /**
     * Simulate a session callback from Pubky-ring
     */
    fun simulateSessionCallback(
        context: Context,
        pubkey: String = TEST_PUBKEY,
        sessionSecret: String = testSessionSecret
    ) {
        val callbackUrl = "bitkit://paykit-session?pubky=$pubkey&session_secret=$sessionSecret"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(callbackUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        
        // Wait for callback to be processed
        Thread.sleep(2000)
    }

    // MARK: - Keypair Simulation

    /**
     * Create test keypair data
     */
    fun createTestKeypair(): Map<String, String> {
        // Generate deterministic test keys
        val secretKey = "a1".repeat(32)
        val publicKey = "b2".repeat(32)
        return mapOf(
            "secret_key" to secretKey,
            "public_key" to publicKey
        )
    }

    /**
     * Simulate a keypair callback from Pubky-ring
     */
    fun simulateKeypairCallback(
        context: Context,
        deviceId: String = testDeviceId,
        epoch: Int = 0
    ) {
        val keypair = createTestKeypair()
        val callbackUrl = "bitkit://paykit-keypair?" +
            "public_key=${keypair["public_key"]}&" +
            "secret_key=${keypair["secret_key"]}&" +
            "device_id=$deviceId&" +
            "epoch=$epoch"
        
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(callbackUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        
        Thread.sleep(2000)
    }

    // MARK: - Profile Simulation

    /**
     * Create test profile data
     */
    fun createTestProfile(
        name: String = "Test User",
        bio: String = "Test bio for E2E tests"
    ): Map<String, Any> {
        return mapOf(
            "name" to name,
            "bio" to bio,
            "image" to "https://example.com/avatar.png",
            "links" to listOf(
                mapOf("title" to "Website", "url" to "https://example.com")
            )
        )
    }

    // MARK: - App Detection

    /**
     * Check if Pubky-ring app is installed
     */
    fun isPubkyRingInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("to.pubky.ring", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Launch Pubky-ring app if installed
     */
    fun launchPubkyRing(context: Context): Boolean {
        if (!isPubkyRingInstalled(context)) return false
        
        val intent = context.packageManager.getLaunchIntentForPackage("to.pubky.ring")
        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
            Thread.sleep(2000)
            return true
        }
        return false
    }

    // MARK: - Wait Helpers

    /**
     * Wait for app to return to foreground
     */
    fun waitForAppForeground(
        packageName: String,
        timeoutMs: Long = 10000
    ): Boolean {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (device.currentPackageName == packageName) {
                return true
            }
            Thread.sleep(500)
        }
        return false
    }
}

/**
 * Factory for creating consistent test data
 */
object TestDataFactory {

    /**
     * Generate a unique test pubkey
     */
    fun generatePubkey(): String {
        return "z6Mk${UUID.randomUUID().toString().replace("-", "").take(44)}"
    }

    /**
     * Generate a unique device ID
     */
    fun generateDeviceId(): String {
        return UUID.randomUUID().toString().lowercase().replace("-", "")
    }

    /**
     * Generate a test session secret
     */
    fun generateSessionSecret(): String {
        return UUID.randomUUID().toString() + UUID.randomUUID().toString()
    }

    /**
     * Generate test hex keypair
     */
    fun generateHexKeypair(): Pair<String, String> {
        val chars = "0123456789abcdef"
        val secretKey = (1..64).map { chars.random() }.joinToString("")
        val publicKey = (1..64).map { chars.random() }.joinToString("")
        return Pair(secretKey, publicKey)
    }
}

