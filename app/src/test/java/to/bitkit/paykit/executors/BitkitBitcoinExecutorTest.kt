package to.bitkit.paykit.executors

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import to.bitkit.paykit.PaykitException
import to.bitkit.repositories.LightningRepo

/**
 * Unit tests for BitkitBitcoinExecutor.
 *
 * Tests all BitcoinExecutorFFI methods with mocked LightningRepo.
 */
class BitkitBitcoinExecutorTest {

    private lateinit var lightningRepo: LightningRepo
    private lateinit var executor: BitkitBitcoinExecutor

    @Before
    fun setup() {
        lightningRepo = mockk(relaxed = true)
        executor = BitkitBitcoinExecutor(lightningRepo)
    }

    // MARK: - sendToAddress Tests

    @Test
    fun `sendToAddress returns txid on success`() = runTest {
        // Given
        val address = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"
        val amountSats = 10000uL
        val expectedTxid = "abc123def456"

        coEvery {
            lightningRepo.sendOnChain(
                address = any(),
                sats = any(),
                speed = any(),
                utxosToSpend = any(),
                feeRates = any(),
                isTransfer = any(),
                channelId = any(),
                isMaxAmount = any()
            )
        } returns Result.success(expectedTxid)

        // When
        val result = executor.sendToAddress(address, amountSats, null)

        // Then
        assertEquals(expectedTxid, result.txid)
        assertEquals(0uL, result.confirmations)
    }

    @Test(expected = PaykitException.PaymentFailed::class)
    fun `sendToAddress throws on failure`() = runTest {
        // Given
        val address = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"
        val amountSats = 10000uL

        coEvery {
            lightningRepo.sendOnChain(
                address = any(),
                sats = any(),
                speed = any(),
                utxosToSpend = any(),
                feeRates = any(),
                isTransfer = any(),
                channelId = any(),
                isMaxAmount = any()
            )
        } returns Result.failure(Exception("Insufficient funds"))

        // When - should throw
        executor.sendToAddress(address, amountSats, null)
    }

    @Test
    fun `sendToAddress passes fee rate correctly`() = runTest {
        // Given
        val address = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"
        val amountSats = 10000uL
        val feeRate = 5.0

        coEvery {
            lightningRepo.sendOnChain(
                address = any(),
                sats = any(),
                speed = any(),
                utxosToSpend = any(),
                feeRates = any(),
                isTransfer = any(),
                channelId = any(),
                isMaxAmount = any()
            )
        } returns Result.success("txid123")

        // When
        val result = executor.sendToAddress(address, amountSats, feeRate)

        // Then
        assertEquals(feeRate, result.feeRate, 0.001)
    }

    // MARK: - estimateFee Tests

    @Test
    fun `estimateFee returns fee on success`() = runTest {
        // Given
        val address = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"
        val amountSats = 10000uL
        val expectedFee = 500uL

        coEvery {
            lightningRepo.calculateTotalFee(
                amountSats = any(),
                address = any(),
                speed = any(),
                utxosToSpend = any(),
                feeRates = any()
            )
        } returns Result.success(expectedFee)

        // When
        val result = executor.estimateFee(address, amountSats, 6u)

        // Then
        assertEquals(expectedFee, result)
    }

    @Test
    fun `estimateFee returns fallback on failure`() = runTest {
        // Given
        val address = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"
        val amountSats = 10000uL

        coEvery {
            lightningRepo.calculateTotalFee(
                amountSats = any(),
                address = any(),
                speed = any(),
                utxosToSpend = any(),
                feeRates = any()
            )
        } returns Result.failure(Exception("Fee estimation failed"))

        // When
        val result = executor.estimateFee(address, amountSats, 6u)

        // Then - should return fallback fee (250 * 1 for 6 blocks)
        assertEquals(250uL, result)
    }

    @Test
    fun `estimateFee scales with target blocks`() = runTest {
        // Given
        val address = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"
        val amountSats = 10000uL

        coEvery {
            lightningRepo.calculateTotalFee(
                amountSats = any(),
                address = any(),
                speed = any(),
                utxosToSpend = any(),
                feeRates = any()
            )
        } returns Result.failure(Exception("Fee estimation failed"))

        // When - high priority (1 block)
        val highPriorityFee = executor.estimateFee(address, amountSats, 1u)

        // When - normal priority (3 blocks)
        val normalFee = executor.estimateFee(address, amountSats, 3u)

        // When - low priority (10 blocks)
        val lowFee = executor.estimateFee(address, amountSats, 10u)

        // Then - high priority should be highest
        assertTrue(highPriorityFee > normalFee)
        assertTrue(normalFee > lowFee)
    }

    // MARK: - getTransaction Tests

    @Test
    fun `getTransaction returns null for unknown txid`() {
        // Given
        val txid = "unknown_txid"

        // When
        val result = executor.getTransaction(txid)

        // Then
        assertNull(result)
    }

    // MARK: - verifyTransaction Tests

    @Test
    fun `verifyTransaction returns false for unknown txid`() {
        // Given
        val txid = "unknown_txid"
        val address = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"
        val amount = 10000uL

        // When
        val result = executor.verifyTransaction(txid, address, amount)

        // Then
        assertTrue(!result)
    }
}
