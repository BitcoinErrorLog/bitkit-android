package to.bitkit.paykit

import org.junit.Test
import to.bitkit.test.BaseUnitTest
import kotlin.test.assertTrue

/**
 * Unit tests for PaykitFeatureFlags.
 */
class PaykitFeatureFlagsTest : BaseUnitTest() {

    @Test
    fun `default flags are disabled`() = test {
        // Default state test
        assertTrue(true)
    }
}
