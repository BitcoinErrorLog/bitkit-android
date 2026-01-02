package to.bitkit.paykit.viewmodels

import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking
import to.bitkit.paykit.models.AutoPaySettings
import to.bitkit.paykit.services.AutoPayEvaluatorService
import to.bitkit.paykit.services.AutopayEvaluationResult
import to.bitkit.paykit.storage.AutoPayStorage
import to.bitkit.test.BaseUnitTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Unit tests for AutoPayViewModel.
 *
 * Tests delegation to AutoPayEvaluatorService and settings management.
 */
class AutoPayViewModelTest : BaseUnitTest() {

    private lateinit var autoPayStorage: AutoPayStorage
    private lateinit var autoPayEvaluatorService: AutoPayEvaluatorService
    private lateinit var viewModel: AutoPayViewModel

    @Before
    fun setup() {
        autoPayStorage = mock()
        autoPayEvaluatorService = mock()
        wheneverBlocking { autoPayStorage.getSettings() }.thenReturn(AutoPaySettings())
        wheneverBlocking { autoPayStorage.getPeerLimits() }.thenReturn(emptyList())
        wheneverBlocking { autoPayStorage.getRules() }.thenReturn(emptyList())
        wheneverBlocking { autoPayEvaluatorService.loadSettings() }.thenReturn(Unit)
        viewModel = AutoPayViewModel(autoPayStorage, autoPayEvaluatorService)
    }

    @Test
    fun `loadSettings loads from storage`() = test {
        // Given
        val settings = AutoPaySettings(isEnabled = true, globalDailyLimitSats = 10000L)
        wheneverBlocking { autoPayStorage.getSettings() }.thenReturn(settings)

        // When
        viewModel.loadSettings()

        // Then
        assertEquals(true, viewModel.settings.value.isEnabled)
        verifyBlocking(autoPayEvaluatorService, atLeast(1)) { loadSettings() }
    }

    @Test
    fun `evaluate delegates to AutoPayEvaluatorService`() = test {
        // Given
        val expectedResult = AutopayEvaluationResult.NeedsApproval
        wheneverBlocking { autoPayEvaluatorService.evaluate("pk:sender", 1000L, "lightning") }
            .thenReturn(expectedResult)

        // When
        val result = viewModel.evaluate("pk:sender", 1000L, "lightning")

        // Then
        assertEquals(expectedResult, result)
        verifyBlocking(autoPayEvaluatorService) { evaluate("pk:sender", 1000L, "lightning") }
    }

    @Test
    fun `evaluate returns NeedsApproval when service returns NeedsApproval`() = test {
        // Given
        wheneverBlocking { autoPayEvaluatorService.evaluate("pk:sender", 1000L, "lightning") }
            .thenReturn(AutopayEvaluationResult.NeedsApproval)

        // When
        val result = viewModel.evaluate("pk:sender", 1000L, "lightning")

        // Then
        assert(result is AutopayEvaluationResult.NeedsApproval)
    }

    @Test
    fun `initial loading state is false after load`() = test {
        // Given
        wheneverBlocking { autoPayStorage.getSettings() }.thenReturn(AutoPaySettings())

        // When
        viewModel.loadSettings()

        // Then
        assertFalse(viewModel.isLoading.value)
    }
}
