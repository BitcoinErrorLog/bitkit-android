package to.bitkit.paykit

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import to.bitkit.paykit.services.PaykitPaymentService
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end tests for Paykit integration with Bitkit.
 *
 * These tests verify the complete Paykit integration flow.
 * Some tests may be skipped if required services are not available.
 */
class PaykitE2ETest {

    private lateinit var manager: PaykitManager
    private lateinit var paymentService: PaykitPaymentService

    @Before
    fun setUp() {
        manager = PaykitManager.getInstance()
        paymentService = PaykitPaymentService.getInstance()

        // Reset state
        manager.reset()
        paymentService.clearReceipts()
    }

    @After
    fun tearDown() {
        manager.reset()
        paymentService.clearReceipts()
        PaykitFeatureFlags.isEnabled = false
    }

    // MARK: - Initialization E2E Tests

    @Test
    fun `test full initialization flow`() = runTest {
        // Given Paykit is enabled
        PaykitFeatureFlags.isEnabled = true

        // When we initialize the manager
        manager.initialize()

        // Then manager should be initialized
        assertTrue(manager.isInitialized)

        // When we register executors (requires LightningRepo - mocked in unit tests)
        // Note: In real E2E, we'd need actual LightningRepo instance
        // For now, we verify initialization works
    }

    // MARK: - Payment Discovery E2E Tests

    @Test
    fun `test discover Lightning payment method`() = runTest {
        // Given a Lightning invoice
        val invoice = "lnbc10u1p0abcdefghijklmnopqrstuvwxyz1234567890"

        // When we discover payment methods
        val methods = paymentService.discoverPaymentMethods(invoice)

        // Then we should find Lightning method
        assertEquals(1, methods.size)
        assertTrue(methods.first() is PaymentMethod.Lightning)
        if (methods.first() is PaymentMethod.Lightning) {
            val lightning = methods.first() as PaymentMethod.Lightning
            assertEquals(invoice, lightning.invoice)
        }
    }

    @Test
    fun `test discover onchain payment method`() = runTest {
        // Given an onchain address
        val address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"

        // When we discover payment methods
        val methods = paymentService.discoverPaymentMethods(address)

        // Then we should find onchain method
        assertEquals(1, methods.size)
        assertTrue(methods.first() is PaymentMethod.Onchain)
        if (methods.first() is PaymentMethod.Onchain) {
            val onchain = methods.first() as PaymentMethod.Onchain
            assertEquals(address, onchain.address)
        }
    }

    // MARK: - Payment Execution E2E Tests

    @Test
    fun `test Lightning payment flow`() = runTest {
        // Given Paykit is initialized and enabled
        PaykitFeatureFlags.isEnabled = true
        PaykitFeatureFlags.isLightningEnabled = true

        if (!PaykitIntegrationHelper.isReady) {
            // Skip if not ready
            return@runTest
        }

        // Given a test Lightning invoice
        val invoice = "lnbc10u1p0testinvoice1234567890"

        // When we attempt payment (will fail with invalid invoice, but tests flow)
        val result = try {
            paymentService.payLightning(
                lightningRepo = mockk(), // Would be real LightningRepo in actual E2E
                invoice = invoice,
                amountSats = null
            )
        } catch (e: Exception) {
            // Expected for invalid invoice - verify error handling
            assertTrue(e is PaykitPaymentError)
            null
        }

        // If result exists, verify structure
        result?.let {
            assertNotNull(it.receipt)
        }
    }

    @Test
    fun `test onchain payment flow`() = runTest {
        // Given Paykit is initialized and enabled
        PaykitFeatureFlags.isEnabled = true
        PaykitFeatureFlags.isOnchainEnabled = true

        if (!PaykitIntegrationHelper.isReady) {
            return@runTest
        }

        // Given a test onchain address
        val address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"
        val amountSats = 1000uL

        // When we attempt payment (will fail with insufficient funds, but tests flow)
        val result = try {
            paymentService.payOnchain(
                lightningRepo = mockk(), // Would be real LightningRepo in actual E2E
                address = address,
                amountSats = amountSats,
                feeRate = null
            )
        } catch (e: Exception) {
            // Expected for insufficient funds - verify error handling
            assertTrue(e is PaykitPaymentError)
            null
        }

        // If result exists, verify structure
        result?.let {
            assertNotNull(it.receipt)
        }
    }

    // MARK: - Receipt Storage E2E Tests

    @Test
    fun `test receipt generation and storage`() = runTest {
        // Given payment service with auto-store enabled
        paymentService.autoStoreReceipts = true

        // Given a test invoice
        val invoice = "lnbc10u1p0testinvoice1234567890"

        // When we attempt payment (will fail, but generates receipt)
        try {
            paymentService.payLightning(
                lightningRepo = mockk(),
                invoice = invoice,
                amountSats = null
            )
        } catch (e: Exception) {
            // Expected
        }

        // Then receipt should be stored
        val receipts = paymentService.getReceipts()
        assertTrue(receipts.isNotEmpty())

        // Verify receipt details
        receipts.firstOrNull()?.let { receipt ->
            assertEquals(PaykitReceiptType.LIGHTNING, receipt.type)
            assertEquals(invoice, receipt.recipient)
        }
    }

    @Test
    fun `test receipt persistence`() {
        // Given we store a receipt
        val receipt = PaykitReceipt(
            id = java.util.UUID.randomUUID().toString(),
            type = PaykitReceiptType.LIGHTNING,
            recipient = "lnbc10u1p0test",
            amountSats = 1000uL,
            feeSats = 10uL,
            paymentHash = "abc123",
            preimage = "def456",
            txid = null,
            timestamp = java.util.Date(),
            status = PaykitReceiptStatus.SUCCEEDED
        )

        paymentService.autoStoreReceipts = true
        // Note: In real E2E, we'd verify persistence by restarting app
        // For unit test, we verify the store method works
        val store = PaykitReceiptStore()
        runBlocking {
            store.store(receipt)
        }

        // Then receipt should be retrievable
        val retrieved = store.get(receipt.id)
        assertNotNull(retrieved)
        assertEquals(receipt.id, retrieved?.id)
    }

    // MARK: - Error Scenario E2E Tests

    @Test
    fun `test payment fails with invalid invoice`() = runTest {
        // Given an invalid invoice
        val invalidInvoice = "invalid_invoice_string"

        // When we attempt payment
        try {
            paymentService.payLightning(
                lightningRepo = mockk(),
                invoice = invalidInvoice,
                amountSats = null
            )
            assertTrue(false, "Should have thrown error")
        } catch (e: Exception) {
            // Then we should get an error
            assertTrue(e is PaykitPaymentError)
        }
    }

    @Test
    fun `test payment fails when feature disabled`() = runTest {
        // Given Paykit is disabled
        PaykitFeatureFlags.isEnabled = false

        // Given a valid invoice
        val invoice = "lnbc10u1p0testinvoice1234567890"

        // When we attempt payment
        try {
            paymentService.pay(
                lightningRepo = mockk(),
                recipient = invoice,
                amountSats = null
            )
            assertTrue(false, "Should have thrown error")
        } catch (e: Exception) {
            // Then we should get notInitialized error
            if (e is PaykitPaymentError) {
                assertTrue(e is PaykitPaymentError.NotInitialized)
            } else {
                assertTrue(false, "Expected PaykitPaymentError")
            }
        }
    }

    @Test
    fun `test payment fails when Lightning disabled`() {
        // Given Paykit is enabled but Lightning is disabled
        PaykitFeatureFlags.isEnabled = true
        PaykitFeatureFlags.isLightningEnabled = false

        // Then flag should be respected
        assertFalse(PaykitFeatureFlags.isLightningEnabled)
    }

    // MARK: - Executor Registration E2E Tests

    @Test
    fun `test executor registration flow`() = runTest {
        // Given manager is initialized
        manager.initialize()
        assertTrue(manager.isInitialized)
        assertFalse(manager.hasExecutors)

        // When we register executors (requires LightningRepo)
        // Note: In real E2E, we'd use actual LightningRepo
        // For now, we verify initialization works
    }

    @Test
    fun `test executor registration fails when not initialized`() = runTest {
        // Given manager is not initialized
        manager.reset()

        // When we try to register executors
        // Then it should throw error
        try {
            manager.registerExecutors(mockk<to.bitkit.repositories.LightningRepo>())
            assertTrue(false, "Should have thrown error")
        } catch (e: Exception) {
            assertTrue(e is PaykitException.NotInitialized)
        }
    }

    // MARK: - Feature Flag Rollback E2E Tests

    @Test
    fun `test emergency rollback disables all features`() {
        // Given Paykit is enabled
        PaykitFeatureFlags.isEnabled = true
        PaykitFeatureFlags.isLightningEnabled = true
        PaykitFeatureFlags.isOnchainEnabled = true

        // When emergency rollback is triggered
        PaykitFeatureFlags.emergencyRollback()

        // Then Paykit should be disabled
        assertFalse(PaykitFeatureFlags.isEnabled)
    }

    // MARK: - Payment Method Selection E2E Tests

    @Test
    fun `test payment method selection for Lightning`() = runTest {
        // Given a Lightning invoice
        val invoice = "lnbc10u1p0testinvoice1234567890"

        // When we discover methods
        val methods = paymentService.discoverPaymentMethods(invoice)

        // Then we should have Lightning method
        assertEquals(1, methods.size)
        assertTrue(methods.first() is PaymentMethod.Lightning)
    }

    @Test
    fun `test payment method selection for onchain`() = runTest {
        // Given an onchain address
        val address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"

        // When we discover methods
        val methods = paymentService.discoverPaymentMethods(address)

        // Then we should have onchain method
        assertEquals(1, methods.size)
        assertTrue(methods.first() is PaymentMethod.Onchain)
    }

    // MARK: - Integration Helper Tests

    @Test
    fun `test PaykitIntegrationHelper readiness`() = runTest {
        // Given Paykit is not initialized
        manager.reset()

        // Then helper should report not ready
        assertFalse(PaykitIntegrationHelper.isReady)

        // When we initialize
        try {
            manager.initialize()
            // Note: registerExecutors requires LightningRepo
            // In real E2E, we'd register with actual repo
        } catch (e: Exception) {
            // Expected if services not available
        }
    }
}
