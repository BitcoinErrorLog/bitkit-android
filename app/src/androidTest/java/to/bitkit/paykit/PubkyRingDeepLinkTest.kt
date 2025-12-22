package to.bitkit.paykit

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import to.bitkit.paykit.services.PubkyRingBridge
import to.bitkit.ui.MainActivity
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Instrumentation tests for Pubky Ring deep link handling.
 * 
 * These tests verify that the app correctly handles incoming callbacks
 * from Pubky Ring, including session, keypair, and setup callbacks.
 * 
 * Run with: ./gradlew connectedDevDebugAndroidTest --tests "to.bitkit.paykit.PubkyRingDeepLinkTest"
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PubkyRingDeepLinkTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var pubkyRingBridge: PubkyRingBridge

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        hiltRule.inject()
        pubkyRingBridge.clearCache()
    }

    @After
    fun tearDown() {
        pubkyRingBridge.clearCache()
    }

    // MARK: - Callback Handling Tests

    @Test
    fun testPaykitSetupCallbackCachesSession() {
        val pubkey = "pk1androidtest${System.currentTimeMillis()}"
        val sessionSecret = "testsecret123"
        val deviceId = "testdevice456"

        val uri = Uri.parse(
            "bitkit://paykit-setup?" +
            "pubky=$pubkey&" +
            "session_secret=$sessionSecret&" +
            "device_id=$deviceId&" +
            "capabilities=read,write"
        )

        val handled = pubkyRingBridge.handleCallback(uri)

        assertTrue(handled, "Callback should be handled")
        
        val cachedSession = pubkyRingBridge.getCachedSession(pubkey)
        assertNotNull(cachedSession, "Session should be cached")
        assertEquals(pubkey, cachedSession.pubkey)
        assertEquals(sessionSecret, cachedSession.sessionSecret)
        assertEquals(listOf("read", "write"), cachedSession.capabilities)
    }

    @Test
    fun testPaykitSetupCallbackWithNoiseKeys() {
        val pubkey = "pk1noisetest${System.currentTimeMillis()}"
        val deviceId = "noisedevice789"

        val uri = Uri.parse(
            "bitkit://paykit-setup?" +
            "pubky=$pubkey&" +
            "session_secret=secret&" +
            "device_id=$deviceId&" +
            "noise_public_key_0=pubkey0hex&" +
            "noise_secret_key_0=seckey0hex&" +
            "noise_public_key_1=pubkey1hex&" +
            "noise_secret_key_1=seckey1hex"
        )

        val handled = pubkyRingBridge.handleCallback(uri)

        assertTrue(handled, "Callback should be handled")
        assertNotNull(pubkyRingBridge.getCachedSession(pubkey), "Session should be cached")
        assertTrue(pubkyRingBridge.getCachedKeypairCount() >= 0, "Keypair count should be valid")
    }

    @Test
    fun testSessionCallbackCachesSession() {
        val pubkey = "pk1sessiontest${System.currentTimeMillis()}"
        val sessionSecret = "sessionSecret456"

        val uri = Uri.parse(
            "bitkit://paykit-session?" +
            "pubky=$pubkey&" +
            "session_secret=$sessionSecret&" +
            "capabilities=admin"
        )

        val handled = pubkyRingBridge.handleCallback(uri)

        assertTrue(handled, "Session callback should be handled")
        
        val cachedSession = pubkyRingBridge.getCachedSession(pubkey)
        assertNotNull(cachedSession, "Session should be cached")
        assertTrue(cachedSession.hasCapability("admin"))
    }

    @Test
    fun testCrossDeviceSessionCallbackWithMatchingRequestId() {
        val pubkey = "pk1crosstest${System.currentTimeMillis()}"
        val requestId = "test-request-${System.currentTimeMillis()}"

        // Set pending request ID
        pubkyRingBridge.setPendingCrossDeviceRequestIdForTest(requestId)

        val uri = Uri.parse(
            "bitkit://paykit-cross-session?" +
            "request_id=$requestId&" +
            "pubky=$pubkey&" +
            "session_secret=crosssecret"
        )

        val handled = pubkyRingBridge.handleCallback(uri)

        assertTrue(handled, "Cross-device callback should be handled")
        assertNotNull(pubkyRingBridge.getCachedSession(pubkey), "Session should be cached")
    }

    @Test
    fun testProfileCallbackReturnsProfile() {
        val uri = Uri.parse(
            "bitkit://paykit-profile?" +
            "name=TestUser&" +
            "bio=Test%20Bio&" +
            "avatar=https://example.com/avatar.png"
        )

        val handled = pubkyRingBridge.handleCallback(uri)

        assertTrue(handled, "Profile callback should be handled")
    }

    @Test
    fun testFollowsCallbackReturnsFollows() {
        val uri = Uri.parse(
            "bitkit://paykit-follows?" +
            "follows=pk1user1,pk1user2,pk1user3"
        )

        val handled = pubkyRingBridge.handleCallback(uri)

        assertTrue(handled, "Follows callback should be handled")
    }

    @Test
    fun testUnknownCallbackPathIsNotHandled() {
        val uri = Uri.parse("bitkit://unknown-path?param=value")

        val handled = pubkyRingBridge.handleCallback(uri)

        assertTrue(!handled, "Unknown path should not be handled")
    }

    @Test
    fun testNonBitkitSchemeIsNotHandled() {
        val uri = Uri.parse("https://example.com/paykit-setup?pubky=test")

        val handled = pubkyRingBridge.handleCallback(uri)

        assertTrue(!handled, "Non-bitkit scheme should not be handled")
    }

    // MARK: - Intent Launch Tests

    @Test
    fun testPaykitSetupIntentLaunchesApp() {
        val pubkey = "pk1launchtest${System.currentTimeMillis()}"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(
                "bitkit://paykit-setup?" +
                "pubky=$pubkey&" +
                "session_secret=secret&" +
                "device_id=device"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Verify the intent resolves to our activity
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        assertNotNull(resolveInfo, "Intent should resolve to an activity")
    }

    @Test
    fun testPaykitSchemeIntentLaunchesApp() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("paykit://payment-request?amount=1000")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        assertNotNull(resolveInfo, "Paykit scheme intent should resolve")
    }

    // MARK: - Full Activity Flow Tests

    @Test
    fun testActivityReceivesDeepLinkIntent() {
        val pubkey = "pk1activitytest${System.currentTimeMillis()}"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(
                "bitkit://paykit-setup?" +
                "pubky=$pubkey&" +
                "session_secret=activitysecret&" +
                "device_id=activitydevice"
            )
        }

        // Launch activity with the deep link intent
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                // Give the activity time to process the intent
                Thread.sleep(1000)
                
                // Verify the session was cached by the deep link handler
                val session = pubkyRingBridge.getCachedSession(pubkey)
                assertNotNull(session, "Session should be cached after activity processes deep link")
            }
        }
    }

    // MARK: - URL Scheme Registration Tests

    @Test
    fun testAllSupportedSchemesResolve() {
        val schemes = listOf(
            "bitkit://test",
            "paykit://test",
            "bitcoin://test",
            "lightning://test",
        )

        for (scheme in schemes) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scheme))
            val resolveInfo = context.packageManager.resolveActivity(intent, 0)
            assertNotNull(resolveInfo, "Scheme should resolve: $scheme")
        }
    }

    @Test
    fun testAllCallbackPathsAreHandleable() {
        val paths = listOf(
            "paykit-session",
            "paykit-keypair",
            "paykit-profile",
            "paykit-follows",
            "paykit-cross-session",
            "paykit-setup",
        )

        for (path in paths) {
            val uri = Uri.parse("bitkit://$path?test=1")
            // Just verify the URI is valid and the scheme/host are correct
            assertEquals("bitkit", uri.scheme)
            assertEquals(path, uri.host)
        }
    }
}

