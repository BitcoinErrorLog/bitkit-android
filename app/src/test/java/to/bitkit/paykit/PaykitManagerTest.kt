package to.bitkit.paykit

import org.junit.Test
import to.bitkit.test.BaseUnitTest
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for PaykitManager.
 */
class PaykitManagerTest : BaseUnitTest() {

    @Test
    fun `manager is not initialized by default`() = test {
        val manager = PaykitManager.getInstance()
        assertFalse(manager.isInitialized)
    }
}
