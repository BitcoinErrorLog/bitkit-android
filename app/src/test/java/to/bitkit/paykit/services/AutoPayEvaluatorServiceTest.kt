package to.bitkit.paykit.services

import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.wheneverBlocking
import to.bitkit.paykit.models.AutoPayRule
import to.bitkit.paykit.models.AutoPaySettings
import to.bitkit.paykit.models.PeerSpendingLimit
import to.bitkit.paykit.storage.AutoPayStorage
import to.bitkit.test.BaseUnitTest
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Unit tests for AutoPayEvaluatorService.
 *
 * Tests the autopay evaluation logic for various scenarios:
 * - Autopay disabled
 * - Global spending limits
 * - Per-peer spending limits
 * - Autopay rules
 */
class AutoPayEvaluatorServiceTest : BaseUnitTest() {

    private lateinit var autoPayStorage: AutoPayStorage
    private lateinit var service: AutoPayEvaluatorService

    @Before
    fun setup() {
        autoPayStorage = mock()
        service = AutoPayEvaluatorService(autoPayStorage)
    }

    @Test
    fun `evaluate returns NeedsApproval when settings not loaded`() = test {
        // Given: No settings loaded

        // When
        val result = service.evaluate("pk:sender", 1000L, "lightning")

        // Then
        assertIs<AutopayEvaluationResult.NeedsApproval>(result)
    }

    @Test
    fun `evaluate returns NeedsApproval when autopay disabled`() = test {
        // Given
        val settings = AutoPaySettings(isEnabled = false)
        wheneverBlocking { autoPayStorage.getSettings() }.thenReturn(settings)
        wheneverBlocking { autoPayStorage.getPeerLimits() }.thenReturn(emptyList())
        wheneverBlocking { autoPayStorage.getRules() }.thenReturn(emptyList())
        service.loadSettings()

        // When
        val result = service.evaluate("pk:sender", 1000L, "lightning")

        // Then
        assertIs<AutopayEvaluationResult.NeedsApproval>(result)
    }

    @Test
    fun `evaluate returns Denied when amount exceeds global daily limit`() = test {
        // Given
        val settings = AutoPaySettings(
            isEnabled = true,
            globalDailyLimitSats = 1000L,
            currentDailySpentSats = 0L,
        )
        wheneverBlocking { autoPayStorage.getSettings() }.thenReturn(settings)
        wheneverBlocking { autoPayStorage.getPeerLimits() }.thenReturn(emptyList())
        wheneverBlocking { autoPayStorage.getRules() }.thenReturn(emptyList())
        service.loadSettings()

        // When
        val result = service.evaluate("pk:sender", 1500L, "lightning")

        // Then
        assertIs<AutopayEvaluationResult.Denied>(result)
        assertEquals("Would exceed daily limit", result.reason)
    }

    @Test
    fun `evaluate returns Denied when amount would exceed peer-specific limit`() = test {
        // Given
        val settings = AutoPaySettings(
            isEnabled = true,
            globalDailyLimitSats = 100_000L,
        )
        val peerLimit = PeerSpendingLimit(
            id = "pk:sender",
            peerPubkey = "pk:sender",
            peerName = "Test Sender",
            limitSats = 5000L,
            spentSats = 4000L,
            lastResetDate = System.currentTimeMillis(),
        )
        wheneverBlocking { autoPayStorage.getSettings() }.thenReturn(settings)
        wheneverBlocking { autoPayStorage.getPeerLimits() }.thenReturn(listOf(peerLimit))
        wheneverBlocking { autoPayStorage.getRules() }.thenReturn(emptyList())
        service.loadSettings()

        // When
        val result = service.evaluate("pk:sender", 2000L, "lightning")

        // Then
        assertIs<AutopayEvaluationResult.Denied>(result)
        assertEquals("Would exceed peer limit", result.reason)
    }

    @Test
    fun `evaluate returns Approved when matching rule exists`() = test {
        // Given
        val settings = AutoPaySettings(
            isEnabled = true,
            globalDailyLimitSats = 100_000L,
        )
        val rule = AutoPayRule(
            id = "rule1",
            name = "Test Rule",
            peerPubkey = "pk:sender",
            maxAmountSats = 5000L,
            allowedMethods = listOf("lightning"),
            isEnabled = true,
        )
        wheneverBlocking { autoPayStorage.getSettings() }.thenReturn(settings)
        wheneverBlocking { autoPayStorage.getPeerLimits() }.thenReturn(emptyList())
        wheneverBlocking { autoPayStorage.getRules() }.thenReturn(listOf(rule))
        service.loadSettings()

        // When
        val result = service.evaluate("pk:sender", 1000L, "lightning")

        // Then
        assertIs<AutopayEvaluationResult.Approved>(result)
        assertEquals("rule1", result.ruleId)
        assertEquals("Test Rule", result.ruleName)
    }

    @Test
    fun `evaluate returns NeedsApproval when no matching rule exists`() = test {
        // Given
        val settings = AutoPaySettings(
            isEnabled = true,
            globalDailyLimitSats = 100_000L,
        )
        val rule = AutoPayRule(
            id = "rule1",
            name = "Test Rule",
            peerPubkey = "pk:different-sender",
            maxAmountSats = 5000L,
            allowedMethods = listOf("lightning"),
            isEnabled = true,
        )
        wheneverBlocking { autoPayStorage.getSettings() }.thenReturn(settings)
        wheneverBlocking { autoPayStorage.getPeerLimits() }.thenReturn(emptyList())
        wheneverBlocking { autoPayStorage.getRules() }.thenReturn(listOf(rule))
        service.loadSettings()

        // When
        val result = service.evaluate("pk:sender", 1000L, "lightning")

        // Then
        assertIs<AutopayEvaluationResult.NeedsApproval>(result)
    }

    @Test
    fun `loadSettings reloads configuration from storage`() = test {
        // Given
        val initialSettings = AutoPaySettings(isEnabled = false)
        wheneverBlocking { autoPayStorage.getSettings() }.thenReturn(initialSettings)
        wheneverBlocking { autoPayStorage.getPeerLimits() }.thenReturn(emptyList())
        wheneverBlocking { autoPayStorage.getRules() }.thenReturn(emptyList())
        service.loadSettings()

        // Verify initial state
        val initialResult = service.evaluate("pk:sender", 1000L, "lightning")
        assertIs<AutopayEvaluationResult.NeedsApproval>(initialResult)

        // When: settings change in storage
        val updatedSettings = AutoPaySettings(isEnabled = true, globalDailyLimitSats = 50_000L)
        val rule = AutoPayRule(
            id = "rule1",
            name = "Updated Rule",
            peerPubkey = "pk:sender",
            maxAmountSats = 10_000L,
            allowedMethods = listOf("lightning"),
            isEnabled = true,
        )
        wheneverBlocking { autoPayStorage.getSettings() }.thenReturn(updatedSettings)
        wheneverBlocking { autoPayStorage.getRules() }.thenReturn(listOf(rule))
        service.loadSettings()

        // Then: evaluate should reflect new settings
        val result = service.evaluate("pk:sender", 1000L, "lightning")
        assertIs<AutopayEvaluationResult.Approved>(result)
    }
}

