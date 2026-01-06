package to.bitkit.paykit.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import to.bitkit.paykit.services.SpendingLimit
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for SpendingLimitStorage.
 *
 * Tests thread safety and concurrent access to spending limits.
 */
class SpendingLimitStorageTest {

    private lateinit var keychain: PaykitKeychainStorage
    private lateinit var storage: SpendingLimitStorage

    @Before
    fun setup() {
        keychain = mock()
        whenever(keychain.retrieve(any())).thenReturn(null)
        storage = SpendingLimitStorage(keychain)
    }

    @Test
    fun `getSpendingLimitForPeer returns null when no limit set`() = runBlocking {
        val result = storage.getSpendingLimitForPeer("test-peer")
        assertNull(result)
    }

    @Test
    fun `setSpendingLimitForPeer persists and retrieves limit`() = runBlocking {
        val limit = SpendingLimit(
            totalLimitSats = 10000,
            currentSpentSats = 0,
            period = "daily",
            lastResetTimestamp = System.currentTimeMillis(),
        )

        storage.setSpendingLimitForPeer("test-peer", limit)
        val result = storage.getSpendingLimitForPeer("test-peer")

        assertEquals(10000L, result?.totalLimitSats)
        assertEquals(0L, result?.currentSpentSats)
    }

    @Test
    fun `recordSpending updates spent amount atomically`() = runBlocking {
        val limit = SpendingLimit(
            totalLimitSats = 10000,
            currentSpentSats = 0,
            period = "daily",
            lastResetTimestamp = System.currentTimeMillis(),
        )

        storage.setSpendingLimitForPeer("test-peer", limit)
        storage.recordSpending("test-peer", 1000)

        val result = storage.getSpendingLimitForPeer("test-peer")
        assertEquals(1000L, result?.currentSpentSats)
    }

    @Test
    fun `concurrent spending operations are thread-safe`() = runBlocking {
        val limit = SpendingLimit(
            totalLimitSats = 100000,
            currentSpentSats = 0,
            period = "daily",
            lastResetTimestamp = System.currentTimeMillis(),
        )

        storage.setSpendingLimitForPeer("test-peer", limit)

        // Launch 100 concurrent spending operations of 100 sats each
        val jobs = (1..100).map {
            async(Dispatchers.Default) {
                storage.recordSpending("test-peer", 100)
            }
        }

        // Wait for all to complete
        jobs.awaitAll()

        // Should have recorded exactly 10000 sats (100 * 100)
        val result = storage.getSpendingLimitForPeer("test-peer")
        assertEquals(10000L, result?.currentSpentSats)
    }

    @Test
    fun `resetSpending clears spent amount`() = runBlocking {
        val limit = SpendingLimit(
            totalLimitSats = 10000,
            currentSpentSats = 5000,
            period = "daily",
            lastResetTimestamp = System.currentTimeMillis(),
        )

        storage.setSpendingLimitForPeer("test-peer", limit)
        storage.resetSpending("test-peer")

        val result = storage.getSpendingLimitForPeer("test-peer")
        assertEquals(0L, result?.currentSpentSats)
    }
}
