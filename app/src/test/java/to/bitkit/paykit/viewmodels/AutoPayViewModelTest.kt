package to.bitkit.paykit.viewmodels

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import to.bitkit.paykit.models.AutoPayRule
import to.bitkit.paykit.models.AutoPaySettings
import to.bitkit.paykit.models.PaymentRequest
import to.bitkit.paykit.models.PeerSpendingLimit
import to.bitkit.paykit.models.RequestDirection
import to.bitkit.paykit.services.AutopayEvaluationResult
import to.bitkit.paykit.storage.AutoPayStorage
import to.bitkit.test.BaseUnitTest

/**
 * Unit tests for AutoPayViewModel.
 *
 * Tests autopay evaluation logic, spending limit tracking, and rule matching.
 */
class AutoPayViewModelTest : BaseUnitTest() {

    private lateinit var autoPayStorage: AutoPayStorage
    private lateinit var viewModel: AutoPayViewModel

    @Before
    fun setup() {
        autoPayStorage = mockk(relaxed = true)
        viewModel = AutoPayViewModel(autoPayStorage)
    }

    // MARK: - Settings Tests

    @Test
    fun `loadSettings loads from storage`() = test {
        // Given
        val settings = AutoPaySettings(
            globalEnabled = true,
            globalMaxAmount = 10000L,
            globalDailyLimit = 50000L,
            requireConfirmationAbove = 5000L,
            rules = emptyList()
        )
        coEvery { autoPayStorage.getSettings() } returns settings

        // When
        viewModel.loadSettings()

        // Then
        assertEquals(true, viewModel.uiState.value.settings?.globalEnabled)
    }

    @Test
    fun `saveSettings persists to storage`() = test {
        // Given
        val settings = AutoPaySettings(
            globalEnabled = true,
            globalMaxAmount = 10000L,
            globalDailyLimit = 50000L,
            requireConfirmationAbove = 5000L,
            rules = emptyList()
        )

        // When
        viewModel.saveSettings(settings)

        // Then
        coVerify { autoPayStorage.saveSettings(settings) }
    }

    // MARK: - Evaluation Tests

    @Test
    fun `evaluate returns Denied when autopay disabled`() = test {
        // Given
        val settings = AutoPaySettings(globalEnabled = false)
        coEvery { autoPayStorage.getSettings() } returns settings
        viewModel.loadSettings()

        val request = createTestRequest(amountSats = 1000L)

        // When
        val result = viewModel.evaluate(request)

        // Then
        assertTrue(result is AutopayEvaluationResult.RequiresApproval)
    }

    @Test
    fun `evaluate returns Approved when under global max`() = test {
        // Given
        val settings = AutoPaySettings(
            globalEnabled = true,
            globalMaxAmount = 10000L,
            requireConfirmationAbove = 50000L
        )
        coEvery { autoPayStorage.getSettings() } returns settings
        coEvery { autoPayStorage.getPeerLimit(any()) } returns null
        viewModel.loadSettings()

        val request = createTestRequest(amountSats = 5000L)

        // When
        val result = viewModel.evaluate(request)

        // Then
        assertTrue(result is AutopayEvaluationResult.Approved)
    }

    @Test
    fun `evaluate returns RequiresApproval when over global max`() = test {
        // Given
        val settings = AutoPaySettings(
            globalEnabled = true,
            globalMaxAmount = 1000L,
            requireConfirmationAbove = 500L
        )
        coEvery { autoPayStorage.getSettings() } returns settings
        viewModel.loadSettings()

        val request = createTestRequest(amountSats = 5000L)

        // When
        val result = viewModel.evaluate(request)

        // Then
        assertTrue(result is AutopayEvaluationResult.RequiresApproval)
    }

    @Test
    fun `evaluate respects peer spending limit`() = test {
        // Given
        val settings = AutoPaySettings(
            globalEnabled = true,
            globalMaxAmount = 100000L
        )
        val peerLimit = PeerSpendingLimit(
            peerPubkey = "pk:sender",
            dailyLimit = 10000L,
            spentToday = 9500L
        )
        coEvery { autoPayStorage.getSettings() } returns settings
        coEvery { autoPayStorage.getPeerLimit("pk:sender") } returns peerLimit
        viewModel.loadSettings()

        val request = createTestRequest(amountSats = 1000L, fromPubkey = "pk:sender")

        // When
        val result = viewModel.evaluate(request)

        // Then
        // Should require approval since 9500 + 1000 > 10000 daily limit
        assertTrue(result is AutopayEvaluationResult.RequiresApproval)
    }

    @Test
    fun `evaluate matches allow rule`() = test {
        // Given
        val rule = AutoPayRule(
            id = "rule1",
            name = "Allow trusted",
            peerPubkey = "pk:trusted",
            action = "allow",
            maxAmount = 50000L
        )
        val settings = AutoPaySettings(
            globalEnabled = true,
            globalMaxAmount = 1000L, // Low global max
            rules = listOf(rule)
        )
        coEvery { autoPayStorage.getSettings() } returns settings
        coEvery { autoPayStorage.getPeerLimit(any()) } returns null
        viewModel.loadSettings()

        val request = createTestRequest(amountSats = 5000L, fromPubkey = "pk:trusted")

        // When
        val result = viewModel.evaluate(request)

        // Then
        // Should be approved because rule allows up to 50000
        assertTrue(result is AutopayEvaluationResult.Approved)
    }

    @Test
    fun `evaluate matches deny rule`() = test {
        // Given
        val rule = AutoPayRule(
            id = "rule1",
            name = "Block spammer",
            peerPubkey = "pk:spammer",
            action = "deny"
        )
        val settings = AutoPaySettings(
            globalEnabled = true,
            globalMaxAmount = 100000L,
            rules = listOf(rule)
        )
        coEvery { autoPayStorage.getSettings() } returns settings
        viewModel.loadSettings()

        val request = createTestRequest(amountSats = 100L, fromPubkey = "pk:spammer")

        // When
        val result = viewModel.evaluate(request)

        // Then
        assertTrue(result is AutopayEvaluationResult.Denied)
    }

    // MARK: - Spending Limit Tests

    @Test
    fun `recordPayment updates peer spending`() = test {
        // Given
        val peerPubkey = "pk:sender"
        val amountSats = 1000L
        val existingLimit = PeerSpendingLimit(
            peerPubkey = peerPubkey,
            dailyLimit = 10000L,
            spentToday = 500L
        )
        coEvery { autoPayStorage.getPeerLimit(peerPubkey) } returns existingLimit

        // When
        viewModel.recordPayment(peerPubkey, amountSats)

        // Then
        coVerify {
            autoPayStorage.savePeerLimit(match {
                it.peerPubkey == peerPubkey && it.spentToday == 1500L
            })
        }
    }

    // MARK: - Helper Methods

    private fun createTestRequest(
        amountSats: Long = 1000L,
        fromPubkey: String = "pk:sender"
    ): PaymentRequest {
        return PaymentRequest(
            id = "test-request",
            fromPubkey = fromPubkey,
            toPubkey = "pk:recipient",
            amountSats = amountSats,
            currency = "SAT",
            methodId = "lightning",
            description = "Test payment",
            direction = RequestDirection.INCOMING
        )
    }
}

