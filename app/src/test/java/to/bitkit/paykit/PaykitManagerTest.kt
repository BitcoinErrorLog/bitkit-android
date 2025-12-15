package to.bitkit.paykit

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import to.bitkit.repositories.LightningRepo

/**
 * Unit tests for PaykitManager.
 *
 * Tests initialization, executor registration, and network configuration.
 */
class PaykitManagerTest {

    private lateinit var manager: PaykitManager
    private lateinit var lightningRepo: LightningRepo

    @Before
    fun setup() {
        manager = PaykitManager()
        lightningRepo = mockk(relaxed = true)
    }

    // MARK: - Initialization Tests

    @Test
    fun `manager is not initialized by default`() {
        assertFalse(manager.isInitialized)
    }

    @Test
    fun `manager is not registered by default`() {
        assertFalse(manager.hasExecutors)
    }

    @Test
    fun `initialize sets isInitialized to true`() = runTest {
        // When
        manager.initialize()

        // Then
        assertTrue(manager.isInitialized)
    }

    @Test
    fun `initialize is idempotent`() = runTest {
        // When
        manager.initialize()
        manager.initialize() // Should not throw

        // Then
        assertTrue(manager.isInitialized)
    }

    // MARK: - Executor Registration Tests

    @Test(expected = PaykitException.NotInitialized::class)
    fun `registerExecutors throws if not initialized`() = runTest {
        // When - should throw
        manager.registerExecutors(lightningRepo)
    }

    @Test
    fun `registerExecutors succeeds after initialization`() = runTest {
        // Given
        manager.initialize()

        // When
        manager.registerExecutors(lightningRepo)

        // Then
        assertTrue(manager.hasExecutors)
    }

    @Test
    fun `registerExecutors is idempotent`() = runTest {
        // Given
        manager.initialize()

        // When
        manager.registerExecutors(lightningRepo)
        manager.registerExecutors(lightningRepo) // Should not throw

        // Then
        assertTrue(manager.hasExecutors)
    }

    // MARK: - Network Configuration Tests

    @Test
    fun `network configuration is set`() {
        // Then - verify network configs are set (actual values depend on Env.network)
        assertTrue(
            manager.bitcoinNetwork in listOf(
                BitcoinNetworkConfig.MAINNET,
                BitcoinNetworkConfig.TESTNET,
                BitcoinNetworkConfig.REGTEST
            )
        )
        assertTrue(
            manager.lightningNetwork in listOf(
                LightningNetworkConfig.MAINNET,
                LightningNetworkConfig.TESTNET,
                LightningNetworkConfig.REGTEST
            )
        )
    }

    // MARK: - Reset Tests

    @Test
    fun `reset clears initialization state`() = runTest {
        // Given
        manager.initialize()
        manager.registerExecutors(lightningRepo)
        assertTrue(manager.isInitialized)
        assertTrue(manager.hasExecutors)

        // When
        manager.reset()

        // Then
        assertFalse(manager.isInitialized)
        assertFalse(manager.hasExecutors)
    }

    @Test
    fun `can reinitialize after reset`() = runTest {
        // Given
        manager.initialize()
        manager.reset()

        // When
        manager.initialize()

        // Then
        assertTrue(manager.isInitialized)
    }

    // MARK: - Singleton Tests

    @Test
    fun `getInstance returns same instance`() {
        // When
        val instance1 = PaykitManager.getInstance()
        val instance2 = PaykitManager.getInstance()

        // Then
        assertEquals(instance1, instance2)
    }
}
