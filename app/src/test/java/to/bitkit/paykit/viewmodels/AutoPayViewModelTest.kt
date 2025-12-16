package to.bitkit.paykit.viewmodels

import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.wheneverBlocking
import to.bitkit.paykit.models.AutoPaySettings
import to.bitkit.paykit.storage.AutoPayStorage
import to.bitkit.test.BaseUnitTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Unit tests for AutoPayViewModel.
 *
 * Tests autopay evaluation logic and settings management.
 */
class AutoPayViewModelTest : BaseUnitTest() {

    private lateinit var autoPayStorage: AutoPayStorage
    private lateinit var viewModel: AutoPayViewModel

    @Before
    fun setup() {
        autoPayStorage = mock()
        wheneverBlocking { autoPayStorage.getSettings() }.thenReturn(AutoPaySettings())
        wheneverBlocking { autoPayStorage.getPeerLimits() }.thenReturn(emptyList())
        wheneverBlocking { autoPayStorage.getRules() }.thenReturn(emptyList())
        viewModel = AutoPayViewModel(autoPayStorage)
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
    }

    @Test
    fun `evaluate returns Denied when autopay disabled`() = test {
        // Given
        val settings = AutoPaySettings(isEnabled = false)
        wheneverBlocking { autoPayStorage.getSettings() }.thenReturn(settings)
        viewModel.loadSettings()

        // When
        val result = viewModel.evaluate("pk:sender", 1000L, "lightning")

        // Then
        assert(result is to.bitkit.paykit.services.AutopayEvaluationResult.Denied)
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
