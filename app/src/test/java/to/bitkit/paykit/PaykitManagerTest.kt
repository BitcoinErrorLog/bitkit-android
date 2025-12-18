package to.bitkit.paykit

import org.junit.Test
import org.mockito.kotlin.mock
import to.bitkit.paykit.services.PubkyRingBridge
import to.bitkit.paykit.services.PubkySDKService
import to.bitkit.test.BaseUnitTest
import kotlin.test.assertFalse

/**
 * Unit tests for PaykitManager.
 */
class PaykitManagerTest : BaseUnitTest() {

    @Test
    fun `manager is not initialized by default`() = test {
        val mockBridge: PubkyRingBridge = mock()
        val mockSDKService: PubkySDKService = mock()
        val manager = PaykitManager(mockBridge, mockSDKService)
        assertFalse(manager.isInitialized)
    }
}
