package to.bitkit.paykit

import android.content.Context
import org.junit.Test
import org.mockito.kotlin.mock
import to.bitkit.paykit.services.DirectoryService
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
        val mockContext: Context = mock()
        val mockBridge: PubkyRingBridge = mock()
        val mockSDKService: PubkySDKService = mock()
        val mockDirectoryService: DirectoryService = mock()
        val manager = PaykitManager(mockContext, mockBridge, mockSDKService, mockDirectoryService)
        assertFalse(manager.isInitialized)
    }
}
