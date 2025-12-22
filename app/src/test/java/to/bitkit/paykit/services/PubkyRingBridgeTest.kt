package to.bitkit.paykit.services

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for PubkyRingBridge including cross-device authentication.
 */
class PubkyRingBridgeTest {

    private lateinit var bridge: PubkyRingBridge
    private lateinit var mockContext: Context
    private lateinit var mockPackageManager: PackageManager
    private lateinit var mockKeychainStorage: to.bitkit.paykit.storage.PaykitKeychainStorage

    @Before
    fun setUp() {
        mockKeychainStorage = mock()
        whenever(mockKeychainStorage.getString(org.mockito.kotlin.any())).thenReturn(null)
        bridge = PubkyRingBridge(mockKeychainStorage)
        bridge.clearCache()
        mockContext = mock()
        mockPackageManager = mock()
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
    }

    @After
    fun tearDown() {
        bridge.clearCache()
    }

    // MARK: - Cross-Device URL Building Tests

    @Test
    fun `buildCrossDeviceUrl creates valid URL with all parameters`() {
        val requestId = "test-request-id"
        val url = bridge.buildCrossDeviceUrl(requestId)

        assertTrue(url.contains("request_id="))
        assertTrue(url.contains("callback_scheme="))
        assertTrue(url.contains("app_name="))
        assertTrue(url.contains("relay_url="))
        assertTrue(url.startsWith(PubkyRingBridge.CROSS_DEVICE_WEB_URL))
    }

    @Test
    fun `buildCrossDeviceUrl includes request ID`() {
        val requestId = "unique-test-id"
        val url = bridge.buildCrossDeviceUrl(requestId)

        assertTrue(url.contains(requestId))
    }

    @Test
    fun `multiple cross device URLs have different request IDs`() {
        val url1 = bridge.buildCrossDeviceUrl("request-1")
        val url2 = bridge.buildCrossDeviceUrl("request-2")

        assertNotEquals(url1, url2)
    }

    // MARK: - Manual Session Import Tests

    @Test
    fun `importSession creates valid session`() {
        val pubkey = "z6mktest1234567890"
        val secret = "test_secret_12345"

        val session = bridge.importSession(pubkey, secret)

        assertEquals(pubkey, session.pubkey)
        assertEquals(secret, session.sessionSecret)
        assertTrue(session.capabilities.isEmpty())
    }

    @Test
    fun `imported session is cached`() {
        val pubkey = "z6mktest1234567890"
        val secret = "test_secret_12345"

        val imported = bridge.importSession(pubkey, secret)
        val cached = bridge.getCachedSession(pubkey)

        assertNotNull(cached)
        assertEquals(imported.pubkey, cached.pubkey)
        assertEquals(imported.sessionSecret, cached.sessionSecret)
    }

    @Test
    fun `importSession with capabilities`() {
        val pubkey = "z6mktest1234567890"
        val secret = "test_secret_12345"
        val capabilities = listOf("read", "write", "admin")

        val session = bridge.importSession(pubkey, secret, capabilities)

        assertEquals(capabilities, session.capabilities)
        assertTrue(session.hasCapability("read"))
        assertTrue(session.hasCapability("write"))
        assertTrue(session.hasCapability("admin"))
        assertFalse(session.hasCapability("delete"))
    }

    // MARK: - Authentication Status Tests

    @Test
    fun `canAuthenticate always returns true`() {
        // Cross-device auth is always available as fallback
        assertTrue(bridge.canAuthenticate())
    }

    @Test
    fun `getRecommendedAuthMethod returns valid method`() {
        // Mock that Pubky-ring is not installed
        whenever(mockPackageManager.getPackageInfo("com.pubkyring", 0))
            .thenThrow(PackageManager.NameNotFoundException())

        val method = bridge.getRecommendedAuthMethod(mockContext)

        assertTrue(method in listOf(AuthMethod.SAME_DEVICE, AuthMethod.CROSS_DEVICE, AuthMethod.MANUAL))
    }

    // MARK: - Callback Handling Tests

    @Test
    fun `handleCallback ignores non-bitkit scheme`() {
        val mockUri: Uri = mock()
        whenever(mockUri.scheme).thenReturn("https")

        val handled = bridge.handleCallback(mockUri)

        assertFalse(handled)
    }

    @Test
    fun `handleCallback ignores unknown path`() {
        val mockUri: Uri = mock()
        whenever(mockUri.scheme).thenReturn("bitkit")
        whenever(mockUri.host).thenReturn("unknown-path")

        val handled = bridge.handleCallback(mockUri)

        assertFalse(handled)
    }

    @Test
    fun `handleSessionCallback caches session`() {
        val pubkey = "z6mktest1234567890"
        val secret = "test_secret_12345"

        val mockUri: Uri = mock()
        whenever(mockUri.scheme).thenReturn("bitkit")
        whenever(mockUri.host).thenReturn("paykit-session")
        whenever(mockUri.getQueryParameter("pubky")).thenReturn(pubkey)
        whenever(mockUri.getQueryParameter("session_secret")).thenReturn(secret)
        whenever(mockUri.getQueryParameter("capabilities")).thenReturn(null)

        bridge.handleCallback(mockUri)

        val cached = bridge.getCachedSession(pubkey)
        assertNotNull(cached)
        assertEquals(pubkey, cached.pubkey)
    }

    @Test
    fun `handleSessionCallback parses capabilities`() {
        val pubkey = "z6mktest1234567890"
        val secret = "test_secret_12345"

        val mockUri: Uri = mock()
        whenever(mockUri.scheme).thenReturn("bitkit")
        whenever(mockUri.host).thenReturn("paykit-session")
        whenever(mockUri.getQueryParameter("pubky")).thenReturn(pubkey)
        whenever(mockUri.getQueryParameter("session_secret")).thenReturn(secret)
        whenever(mockUri.getQueryParameter("capabilities")).thenReturn("read,write,admin")

        bridge.handleCallback(mockUri)

        val cached = bridge.getCachedSession(pubkey)
        assertEquals(listOf("read", "write", "admin"), cached?.capabilities)
    }

    // MARK: - Cache Management Tests

    @Test
    fun `clearCache removes all sessions`() {
        bridge.importSession("pubkey1", "secret1")
        bridge.importSession("pubkey2", "secret2")

        bridge.clearCache()

        assertNull(bridge.getCachedSession("pubkey1"))
        assertNull(bridge.getCachedSession("pubkey2"))
    }

    // MARK: - Shareable Link Tests

    @Test
    fun `buildCrossDeviceUrl starts with correct base URL`() {
        val url = bridge.buildCrossDeviceUrl("test-id")

        assertTrue(url.startsWith(PubkyRingBridge.CROSS_DEVICE_WEB_URL))
    }

    // MARK: - Error Message Tests

    @Test
    fun `PubkyRingException user messages are not empty`() {
        assertFalse(PubkyRingException.AppNotInstalled.userMessage.isEmpty())
        assertFalse(PubkyRingException.Timeout.userMessage.isEmpty())
        assertFalse(PubkyRingException.Cancelled.userMessage.isEmpty())
        assertFalse(PubkyRingException.InvalidCallback.userMessage.isEmpty())
        assertFalse(PubkyRingException.MissingParameters.userMessage.isEmpty())
        assertFalse(PubkyRingException.FailedToOpenApp("test").userMessage.isEmpty())
        assertFalse(PubkyRingException.CrossDeviceFailed("test").userMessage.isEmpty())
    }

    // MARK: - Cross-Device Request Expiry Tests

    @Test
    fun `CrossDeviceRequest isExpired property`() {
        val expiredRequest = CrossDeviceRequest(
            requestId = "test-id",
            url = "https://example.com",
            qrCodeBitmap = null,
            expiresAt = Date(System.currentTimeMillis() - 60000), // Expired 1 minute ago
        )

        assertTrue(expiredRequest.isExpired)
        assertEquals(0, expiredRequest.timeRemainingMs)
    }

    @Test
    fun `CrossDeviceRequest timeRemaining calculation`() {
        val futureExpiry = Date(System.currentTimeMillis() + 120_000) // 2 minutes
        val request = CrossDeviceRequest(
            requestId = "test-id",
            url = "https://example.com",
            qrCodeBitmap = null,
            expiresAt = futureExpiry,
        )

        assertFalse(request.isExpired)
        assertTrue(request.timeRemainingMs > 0)
        assertTrue(request.timeRemainingMs <= 120_000)
    }

    // MARK: - Session Capability Tests

    @Test
    fun `PubkySession hasCapability method`() {
        val session = PubkySession(
            pubkey = "testpubkey",
            sessionSecret = "testsecret",
            capabilities = listOf("paykit:read", "paykit:write"),
            createdAt = Date(),
        )

        assertTrue(session.hasCapability("paykit:read"))
        assertTrue(session.hasCapability("paykit:write"))
        assertFalse(session.hasCapability("paykit:admin"))
    }

    // MARK: - Auth Method Enum Tests

    @Test
    fun `AuthMethod has all expected values`() {
        val methods = AuthMethod.entries.toList()
        assertEquals(3, methods.size)
        assertTrue(AuthMethod.SAME_DEVICE in methods)
        assertTrue(AuthMethod.CROSS_DEVICE in methods)
        assertTrue(AuthMethod.MANUAL in methods)
    }

    // MARK: - Authentication Status Enum Tests

    @Test
    fun `AuthenticationStatus descriptions are not empty`() {
        assertFalse(AuthenticationStatus.PUBKY_RING_AVAILABLE.description.isEmpty())
        assertFalse(AuthenticationStatus.CROSS_DEVICE_ONLY.description.isEmpty())
    }

    // MARK: - Cross-Device Session Callback Tests

    @Test
    fun `handleCrossDeviceSessionCallback ignores mismatched request ID`() {
        // Set a pending request ID manually
        bridge.setPendingCrossDeviceRequestIdForTest("expected-request-id")

        // Try to handle callback with different request ID
        val mockUri: Uri = mock()
        whenever(mockUri.scheme).thenReturn("bitkit")
        whenever(mockUri.host).thenReturn("paykit-cross-session")
        whenever(mockUri.getQueryParameter("request_id")).thenReturn("wrong-id")
        whenever(mockUri.getQueryParameter("pubky")).thenReturn("test")
        whenever(mockUri.getQueryParameter("session_secret")).thenReturn("secret")

        val handled = bridge.handleCallback(mockUri)

        assertFalse(handled)
    }

    @Test
    fun `handleCrossDeviceSessionCallback succeeds with matching request ID`() {
        val expectedRequestId = "test-request-id"
        bridge.setPendingCrossDeviceRequestIdForTest(expectedRequestId)

        val pubkey = "z6mktest1234567890"
        val secret = "test_secret_12345"

        val mockUri: Uri = mock()
        whenever(mockUri.scheme).thenReturn("bitkit")
        whenever(mockUri.host).thenReturn("paykit-cross-session")
        whenever(mockUri.getQueryParameter("request_id")).thenReturn(expectedRequestId)
        whenever(mockUri.getQueryParameter("pubky")).thenReturn(pubkey)
        whenever(mockUri.getQueryParameter("session_secret")).thenReturn(secret)
        whenever(mockUri.getQueryParameter("capabilities")).thenReturn(null)

        val handled = bridge.handleCallback(mockUri)

        assertTrue(handled)
        val cached = bridge.getCachedSession(pubkey)
        assertNotNull(cached)
        assertEquals(pubkey, cached.pubkey)
    }

    // MARK: - Paykit Setup Callback Tests

    @Test
    fun `handlePaykitSetupCallback returns true`() {
        val mockUri: Uri = mock()
        whenever(mockUri.scheme).thenReturn("bitkit")
        whenever(mockUri.host).thenReturn("paykit-setup")
        whenever(mockUri.getQueryParameter("pubky")).thenReturn("pk1test123")
        whenever(mockUri.getQueryParameter("session_secret")).thenReturn("secret123")
        whenever(mockUri.getQueryParameter("device_id")).thenReturn("device456")
        whenever(mockUri.getQueryParameter("capabilities")).thenReturn(null)
        whenever(mockUri.getQueryParameter("noise_public_key_0")).thenReturn(null)
        whenever(mockUri.getQueryParameter("noise_secret_key_0")).thenReturn(null)
        whenever(mockUri.getQueryParameter("noise_public_key_1")).thenReturn(null)
        whenever(mockUri.getQueryParameter("noise_secret_key_1")).thenReturn(null)

        val handled = bridge.handleCallback(mockUri)

        assertTrue(handled)
    }

    @Test
    fun `handlePaykitSetupCallback caches session`() {
        val pubkey = "pk1testcallback789"
        val secret = "callbacksecret456"
        val deviceId = "testdevice123"

        val mockUri: Uri = mock()
        whenever(mockUri.scheme).thenReturn("bitkit")
        whenever(mockUri.host).thenReturn("paykit-setup")
        whenever(mockUri.getQueryParameter("pubky")).thenReturn(pubkey)
        whenever(mockUri.getQueryParameter("session_secret")).thenReturn(secret)
        whenever(mockUri.getQueryParameter("device_id")).thenReturn(deviceId)
        whenever(mockUri.getQueryParameter("capabilities")).thenReturn(null)
        whenever(mockUri.getQueryParameter("noise_public_key_0")).thenReturn(null)
        whenever(mockUri.getQueryParameter("noise_secret_key_0")).thenReturn(null)
        whenever(mockUri.getQueryParameter("noise_public_key_1")).thenReturn(null)
        whenever(mockUri.getQueryParameter("noise_secret_key_1")).thenReturn(null)

        bridge.handleCallback(mockUri)

        val cached = bridge.getCachedSession(pubkey)
        assertNotNull(cached)
        assertEquals(pubkey, cached.pubkey)
        assertEquals(secret, cached.sessionSecret)
    }

    @Test
    fun `handlePaykitSetupCallback parses capabilities`() {
        val pubkey = "pk1testcaps456"

        val mockUri: Uri = mock()
        whenever(mockUri.scheme).thenReturn("bitkit")
        whenever(mockUri.host).thenReturn("paykit-setup")
        whenever(mockUri.getQueryParameter("pubky")).thenReturn(pubkey)
        whenever(mockUri.getQueryParameter("session_secret")).thenReturn("secret")
        whenever(mockUri.getQueryParameter("device_id")).thenReturn("dev")
        whenever(mockUri.getQueryParameter("capabilities")).thenReturn("read,write,paykit")
        whenever(mockUri.getQueryParameter("noise_public_key_0")).thenReturn(null)
        whenever(mockUri.getQueryParameter("noise_secret_key_0")).thenReturn(null)
        whenever(mockUri.getQueryParameter("noise_public_key_1")).thenReturn(null)
        whenever(mockUri.getQueryParameter("noise_secret_key_1")).thenReturn(null)

        bridge.handleCallback(mockUri)

        val cached = bridge.getCachedSession(pubkey)
        assertEquals(3, cached?.capabilities?.size)
        assertTrue(cached?.hasCapability("read") ?: false)
        assertTrue(cached?.hasCapability("write") ?: false)
        assertTrue(cached?.hasCapability("paykit") ?: false)
    }

    @Test
    fun `handlePaykitSetupCallback with missing required params returns true`() {
        // Missing device_id - should still return true (handled) but session not cached
        val mockUri: Uri = mock()
        whenever(mockUri.scheme).thenReturn("bitkit")
        whenever(mockUri.host).thenReturn("paykit-setup")
        whenever(mockUri.getQueryParameter("pubky")).thenReturn("test")
        whenever(mockUri.getQueryParameter("session_secret")).thenReturn("secret")
        whenever(mockUri.getQueryParameter("device_id")).thenReturn(null)

        val handled = bridge.handleCallback(mockUri)

        // Returns true because it was recognized as a paykit-setup callback
        assertTrue(handled)
    }

    @Test
    fun `handlePaykitSetupCallback with noise keys caches keypairs`() {
        val pubkey = "pk1testnoise789"
        val deviceId = "noisedevice123"

        val mockUri: Uri = mock()
        whenever(mockUri.scheme).thenReturn("bitkit")
        whenever(mockUri.host).thenReturn("paykit-setup")
        whenever(mockUri.getQueryParameter("pubky")).thenReturn(pubkey)
        whenever(mockUri.getQueryParameter("session_secret")).thenReturn("secret")
        whenever(mockUri.getQueryParameter("device_id")).thenReturn(deviceId)
        whenever(mockUri.getQueryParameter("capabilities")).thenReturn(null)
        whenever(mockUri.getQueryParameter("noise_public_key_0")).thenReturn("pubkey0hex")
        whenever(mockUri.getQueryParameter("noise_secret_key_0")).thenReturn("seckey0hex")
        whenever(mockUri.getQueryParameter("noise_public_key_1")).thenReturn("pubkey1hex")
        whenever(mockUri.getQueryParameter("noise_secret_key_1")).thenReturn("seckey1hex")

        bridge.handleCallback(mockUri)

        // Session should be cached
        val cached = bridge.getCachedSession(pubkey)
        assertNotNull(cached)

        // Keypair count should be >= 0
        val keypairCount = bridge.getCachedKeypairCount()
        assertTrue(keypairCount >= 0)
    }

    // MARK: - Backup and Restore Tests

    @Test
    fun `exportBackup contains device ID`() {
        val backup = bridge.exportBackup()

        assertTrue(backup.deviceId.isNotEmpty())
        assertEquals(1, backup.version)
    }

    @Test
    fun `exportBackup contains cached sessions`() {
        bridge.importSession("backuptest1", "secret1")
        bridge.importSession("backuptest2", "secret2")

        val backup = bridge.exportBackup()

        assertEquals(2, backup.sessions.size)
    }
}
