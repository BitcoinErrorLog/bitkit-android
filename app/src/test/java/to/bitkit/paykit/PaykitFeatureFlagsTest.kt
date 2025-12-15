package to.bitkit.paykit

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for PaykitFeatureFlags functionality.
 */
class PaykitFeatureFlagsTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    // In-memory storage for flags
    private val flagStorage = mutableMapOf<String, Boolean>()

    @Before
    fun setUp() {
        // Reset storage
        flagStorage.clear()

        // Create mocks
        mockContext = mockk()
        mockPrefs = mockk()
        mockEditor = mockk()

        // Setup SharedPreferences mock behavior
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs

        every { mockPrefs.getBoolean(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<Boolean>()
            flagStorage[key] ?: default
        }

        every { mockPrefs.contains(any()) } answers {
            flagStorage.containsKey(firstArg())
        }

        every { mockPrefs.edit() } returns mockEditor

        every { mockEditor.putBoolean(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<Boolean>()
            flagStorage[key] = value
            mockEditor
        }

        every { mockEditor.apply() } just Runs

        // Initialize feature flags with mock context
        PaykitFeatureFlags.init(mockContext)
    }

    @After
    fun tearDown() {
        flagStorage.clear()
        clearAllMocks()
    }

    // MARK: - isEnabled Tests

    @Test
    fun `isEnabled defaults to false`() {
        // Remove any stored value to test default
        flagStorage.remove("paykit_enabled")

        assertFalse(PaykitFeatureFlags.isEnabled)
    }

    @Test
    fun `isEnabled can be set to true`() {
        PaykitFeatureFlags.isEnabled = true

        assertTrue(PaykitFeatureFlags.isEnabled)
    }

    @Test
    fun `isEnabled can be toggled`() {
        PaykitFeatureFlags.isEnabled = true
        assertTrue(PaykitFeatureFlags.isEnabled)

        PaykitFeatureFlags.isEnabled = false
        assertFalse(PaykitFeatureFlags.isEnabled)
    }

    // MARK: - isLightningEnabled Tests

    @Test
    fun `isLightningEnabled defaults to true`() {
        flagStorage.remove("paykit_lightning_enabled")

        assertTrue(PaykitFeatureFlags.isLightningEnabled)
    }

    @Test
    fun `isLightningEnabled can be set`() {
        PaykitFeatureFlags.isLightningEnabled = false
        assertFalse(PaykitFeatureFlags.isLightningEnabled)

        PaykitFeatureFlags.isLightningEnabled = true
        assertTrue(PaykitFeatureFlags.isLightningEnabled)
    }

    // MARK: - isOnchainEnabled Tests

    @Test
    fun `isOnchainEnabled defaults to true`() {
        flagStorage.remove("paykit_onchain_enabled")

        assertTrue(PaykitFeatureFlags.isOnchainEnabled)
    }

    @Test
    fun `isOnchainEnabled can be set`() {
        PaykitFeatureFlags.isOnchainEnabled = false
        assertFalse(PaykitFeatureFlags.isOnchainEnabled)

        PaykitFeatureFlags.isOnchainEnabled = true
        assertTrue(PaykitFeatureFlags.isOnchainEnabled)
    }

    // MARK: - isReceiptStorageEnabled Tests

    @Test
    fun `isReceiptStorageEnabled defaults to true`() {
        flagStorage.remove("paykit_receipt_storage_enabled")

        assertTrue(PaykitFeatureFlags.isReceiptStorageEnabled)
    }

    @Test
    fun `isReceiptStorageEnabled can be set`() {
        PaykitFeatureFlags.isReceiptStorageEnabled = false
        assertFalse(PaykitFeatureFlags.isReceiptStorageEnabled)

        PaykitFeatureFlags.isReceiptStorageEnabled = true
        assertTrue(PaykitFeatureFlags.isReceiptStorageEnabled)
    }

    // MARK: - updateFromRemoteConfig Tests

    @Test
    fun `updateFromRemoteConfig updates all flags`() {
        // Given all flags are disabled
        PaykitFeatureFlags.isEnabled = false
        PaykitFeatureFlags.isLightningEnabled = false
        PaykitFeatureFlags.isOnchainEnabled = false
        PaykitFeatureFlags.isReceiptStorageEnabled = false

        // When we update from remote config
        val config = mapOf<String, Any>(
            "paykit_enabled" to true,
            "paykit_lightning_enabled" to true,
            "paykit_onchain_enabled" to true,
            "paykit_receipt_storage_enabled" to true
        )
        PaykitFeatureFlags.updateFromRemoteConfig(config)

        // Then all flags should be updated
        assertTrue(PaykitFeatureFlags.isEnabled)
        assertTrue(PaykitFeatureFlags.isLightningEnabled)
        assertTrue(PaykitFeatureFlags.isOnchainEnabled)
        assertTrue(PaykitFeatureFlags.isReceiptStorageEnabled)
    }

    @Test
    fun `updateFromRemoteConfig handles partial update`() {
        // Given initial state
        PaykitFeatureFlags.isEnabled = false
        PaykitFeatureFlags.isLightningEnabled = true

        // When we update only some flags
        val config = mapOf<String, Any>(
            "paykit_enabled" to true
            // Other flags not included
        )
        PaykitFeatureFlags.updateFromRemoteConfig(config)

        // Then only specified flags should be updated
        assertTrue(PaykitFeatureFlags.isEnabled)
        assertTrue(PaykitFeatureFlags.isLightningEnabled) // Unchanged
    }

    @Test
    fun `updateFromRemoteConfig ignores invalid types`() {
        // Given
        PaykitFeatureFlags.isEnabled = false

        // When config has wrong types
        val config = mapOf<String, Any>(
            "paykit_enabled" to "true", // String instead of Boolean
            "paykit_lightning_enabled" to 1 // Int instead of Boolean
        )
        PaykitFeatureFlags.updateFromRemoteConfig(config)

        // Then flags should remain unchanged
        assertFalse(PaykitFeatureFlags.isEnabled)
    }

    // MARK: - emergencyRollback Tests

    @Test
    fun `emergencyRollback disables Paykit`() {
        // Given Paykit is enabled
        PaykitFeatureFlags.isEnabled = true
        assertTrue(PaykitFeatureFlags.isEnabled)

        // Mock PaykitManager to avoid actual reset
        mockkObject(PaykitManager)
        every { PaykitManager.getInstance() } returns mockk {
            every { reset() } just Runs
        }

        // When emergency rollback is triggered
        PaykitFeatureFlags.emergencyRollback()

        // Then Paykit should be disabled
        assertFalse(PaykitFeatureFlags.isEnabled)

        unmockkObject(PaykitManager)
    }

    // MARK: - Persistence Tests

    @Test
    fun `flags persist in SharedPreferences`() {
        // Given we set a flag
        PaykitFeatureFlags.isEnabled = true

        // Then the value should be in storage
        assertTrue(flagStorage["paykit_enabled"] == true)
    }

    @Test
    fun `all flags are stored with correct keys`() {
        PaykitFeatureFlags.isEnabled = true
        PaykitFeatureFlags.isLightningEnabled = false
        PaykitFeatureFlags.isOnchainEnabled = true
        PaykitFeatureFlags.isReceiptStorageEnabled = false

        assertEquals(true, flagStorage["paykit_enabled"])
        assertEquals(false, flagStorage["paykit_lightning_enabled"])
        assertEquals(true, flagStorage["paykit_onchain_enabled"])
        assertEquals(false, flagStorage["paykit_receipt_storage_enabled"])
    }
}
