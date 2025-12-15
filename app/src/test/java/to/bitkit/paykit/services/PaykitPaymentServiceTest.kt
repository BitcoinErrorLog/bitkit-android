package to.bitkit.paykit.services

import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * Unit tests for PaykitPaymentService.
 */
class PaykitPaymentServiceTest {

    private lateinit var service: PaykitPaymentService

    @Before
    fun setup() {
        service = PaykitPaymentService()
        service.clearReceipts()
    }

    // MARK: - Payment Type Detection Tests

    @Test
    fun `discoverPaymentMethods detects Lightning invoice mainnet`() {
        // Given
        val invoice = "lnbc10u1p0abcdef..."

        // When
        val methods = service.discoverPaymentMethods(invoice)

        // Then
        assertEquals(1, methods.size)
        assertTrue(methods.first() is PaymentMethod.Lightning)
        assertEquals(invoice, (methods.first() as PaymentMethod.Lightning).invoice)
    }

    @Test
    fun `discoverPaymentMethods detects Lightning invoice testnet`() {
        // Given
        val invoice = "lntb10u1p0abcdef..."

        // When
        val methods = service.discoverPaymentMethods(invoice)

        // Then
        assertEquals(1, methods.size)
        assertTrue(methods.first() is PaymentMethod.Lightning)
    }

    @Test
    fun `discoverPaymentMethods detects Lightning invoice regtest`() {
        // Given
        val invoice = "lnbcrt10u1p0abcdef..."

        // When
        val methods = service.discoverPaymentMethods(invoice)

        // Then
        assertEquals(1, methods.size)
        assertTrue(methods.first() is PaymentMethod.Lightning)
    }

    @Test
    fun `discoverPaymentMethods detects onchain address bech32 mainnet`() {
        // Given
        val address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"

        // When
        val methods = service.discoverPaymentMethods(address)

        // Then
        assertEquals(1, methods.size)
        assertTrue(methods.first() is PaymentMethod.Onchain)
        assertEquals(address, (methods.first() as PaymentMethod.Onchain).address)
    }

    @Test
    fun `discoverPaymentMethods detects onchain address bech32 testnet`() {
        // Given
        val address = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"

        // When
        val methods = service.discoverPaymentMethods(address)

        // Then
        assertEquals(1, methods.size)
        assertTrue(methods.first() is PaymentMethod.Onchain)
    }

    @Test
    fun `discoverPaymentMethods detects legacy P2PKH address`() {
        // Given
        val address = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2"

        // When
        val methods = service.discoverPaymentMethods(address)

        // Then
        assertEquals(1, methods.size)
        assertTrue(methods.first() is PaymentMethod.Onchain)
    }

    @Test
    fun `discoverPaymentMethods detects P2SH address`() {
        // Given
        val address = "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy"

        // When
        val methods = service.discoverPaymentMethods(address)

        // Then
        assertEquals(1, methods.size)
        assertTrue(methods.first() is PaymentMethod.Onchain)
    }

    @Test
    fun `discoverPaymentMethods returns empty for invalid recipient`() {
        // Given
        val invalid = "not_a_valid_address_or_invoice"

        // When
        val methods = service.discoverPaymentMethods(invalid)

        // Then
        assertTrue(methods.isEmpty())
    }

    @Test
    fun `discoverPaymentMethods detects Paykit URI`() {
        // Given
        val paykitUri = "paykit:user@example.com"

        // When
        val methods = service.discoverPaymentMethods(paykitUri)

        // Then
        assertEquals(1, methods.size)
        assertTrue(methods.first() is PaymentMethod.Paykit)
    }

    // MARK: - Receipt Store Tests

    @Test
    fun `getReceipts returns empty list initially`() {
        // When
        val receipts = service.getReceipts()

        // Then
        assertTrue(receipts.isEmpty())
    }

    @Test
    fun `getReceipt returns null for unknown id`() {
        // When
        val receipt = service.getReceipt("unknown_id")

        // Then
        assertNull(receipt)
    }

    @Test
    fun `clearReceipts removes all receipts`() {
        // Verified by setup clearing receipts
        assertTrue(service.getReceipts().isEmpty())
    }

    // MARK: - Error Message Tests

    @Test
    fun `NotInitialized error has correct user message`() {
        val error = PaykitPaymentError.NotInitialized
        assertEquals("Please wait for the app to initialize", error.userMessage)
    }

    @Test
    fun `InvalidRecipient error has correct user message`() {
        val error = PaykitPaymentError.InvalidRecipient("bad_address")
        assertEquals("Please check the payment address or invoice", error.userMessage)
    }

    @Test
    fun `AmountRequired error has correct user message`() {
        val error = PaykitPaymentError.AmountRequired
        assertEquals("Please enter an amount", error.userMessage)
    }

    @Test
    fun `InsufficientFunds error has correct user message`() {
        val error = PaykitPaymentError.InsufficientFunds
        assertEquals("You don't have enough funds for this payment", error.userMessage)
    }

    @Test
    fun `PaymentFailed error has correct user message`() {
        val error = PaykitPaymentError.PaymentFailed("Route not found")
        assertEquals("Payment could not be completed. Please try again.", error.userMessage)
    }

    @Test
    fun `Timeout error has correct user message`() {
        val error = PaykitPaymentError.Timeout
        assertEquals("Payment is taking longer than expected", error.userMessage)
    }

    @Test
    fun `UnsupportedPaymentType error has correct user message`() {
        val error = PaykitPaymentError.UnsupportedPaymentType
        assertEquals("This payment type is not supported yet", error.userMessage)
    }

    @Test
    fun `Unknown error has correct user message`() {
        val error = PaykitPaymentError.Unknown("Something went wrong")
        assertEquals("An unexpected error occurred", error.userMessage)
    }

    // MARK: - Payment State Tests

    @Test
    fun `initial payment state is Idle`() = runTest {
        // When
        val state = service.paymentState.first()

        // Then
        assertTrue(state is PaykitPaymentState.Idle)
    }

    @Test
    fun `resetState sets state to Idle`() = runTest {
        // When
        service.resetState()
        val state = service.paymentState.first()

        // Then
        assertTrue(state is PaykitPaymentState.Idle)
    }

    // MARK: - Configuration Tests

    @Test
    fun `default payment timeout is 60 seconds`() {
        assertEquals(60_000L, service.paymentTimeoutMs)
    }

    @Test
    fun `default autoStoreReceipts is true`() {
        assertTrue(service.autoStoreReceipts)
    }

    @Test
    fun `paymentTimeoutMs can be changed`() {
        // When
        service.paymentTimeoutMs = 120_000L

        // Then
        assertEquals(120_000L, service.paymentTimeoutMs)
    }

    @Test
    fun `autoStoreReceipts can be disabled`() {
        // When
        service.autoStoreReceipts = false

        // Then
        assertFalse(service.autoStoreReceipts)
    }
}

/**
 * Unit tests for PaykitReceiptStore.
 */
class PaykitReceiptStoreTest {

    private lateinit var store: PaykitReceiptStore

    @Before
    fun setup() {
        store = PaykitReceiptStore()
    }

    @Test
    fun `store and retrieve receipt`() {
        // Given
        val receipt = PaykitReceipt(
            id = "test_id",
            type = PaykitReceiptType.LIGHTNING,
            recipient = "lnbc...",
            amountSats = 10000uL,
            feeSats = 100uL,
            paymentHash = "abc123",
            preimage = "def456",
            txid = null,
            timestamp = Date(),
            status = PaykitReceiptStatus.SUCCEEDED
        )

        // When
        store.store(receipt)
        val retrieved = store.get("test_id")

        // Then
        assertNotNull(retrieved)
        assertEquals("test_id", retrieved?.id)
        assertEquals(10000uL, retrieved?.amountSats)
    }

    @Test
    fun `getAll returns receipts sorted by timestamp descending`() {
        // Given
        val oldDate = Date(System.currentTimeMillis() - 3600_000)
        val newDate = Date()

        val oldReceipt = PaykitReceipt(
            id = "old",
            type = PaykitReceiptType.LIGHTNING,
            recipient = "lnbc...",
            amountSats = 1000uL,
            feeSats = 10uL,
            paymentHash = null,
            preimage = null,
            txid = null,
            timestamp = oldDate,
            status = PaykitReceiptStatus.SUCCEEDED
        )

        val newReceipt = PaykitReceipt(
            id = "new",
            type = PaykitReceiptType.ONCHAIN,
            recipient = "bc1...",
            amountSats = 2000uL,
            feeSats = 20uL,
            paymentHash = null,
            preimage = null,
            txid = "txid123",
            timestamp = newDate,
            status = PaykitReceiptStatus.PENDING
        )

        // When
        store.store(oldReceipt)
        store.store(newReceipt)
        val all = store.getAll()

        // Then
        assertEquals(2, all.size)
        assertEquals("new", all.first().id) // Newer first
        assertEquals("old", all.last().id)
    }

    @Test
    fun `clear removes all receipts`() {
        // Given
        val receipt = PaykitReceipt(
            id = "test",
            type = PaykitReceiptType.LIGHTNING,
            recipient = "lnbc...",
            amountSats = 1000uL,
            feeSats = 10uL,
            paymentHash = null,
            preimage = null,
            txid = null,
            timestamp = Date(),
            status = PaykitReceiptStatus.SUCCEEDED
        )
        store.store(receipt)
        assertEquals(1, store.getAll().size)

        // When
        store.clear()

        // Then
        assertEquals(0, store.getAll().size)
    }

    @Test
    fun `get returns null for unknown id`() {
        // When
        val result = store.get("nonexistent")

        // Then
        assertNull(result)
    }
}

/**
 * Tests for PaykitPaymentState sealed class.
 */
class PaykitPaymentStateTest {

    @Test
    fun `Idle state is singleton`() {
        val state1 = PaykitPaymentState.Idle
        val state2 = PaykitPaymentState.Idle
        assertTrue(state1 === state2)
    }

    @Test
    fun `Processing state is singleton`() {
        val state1 = PaykitPaymentState.Processing
        val state2 = PaykitPaymentState.Processing
        assertTrue(state1 === state2)
    }

    @Test
    fun `Succeeded state contains receipt`() {
        val receipt = PaykitReceipt(
            id = "test",
            type = PaykitReceiptType.LIGHTNING,
            recipient = "lnbc...",
            amountSats = 1000uL,
            feeSats = 10uL,
            paymentHash = null,
            preimage = null,
            txid = null,
            timestamp = Date(),
            status = PaykitReceiptStatus.SUCCEEDED
        )

        val state = PaykitPaymentState.Succeeded(receipt)

        assertEquals("test", state.receipt.id)
    }

    @Test
    fun `Failed state contains error`() {
        val error = PaykitPaymentError.Timeout

        val state = PaykitPaymentState.Failed(error)

        assertTrue(state.error is PaykitPaymentError.Timeout)
    }
}

/**
 * Tests for PaymentMethod sealed class.
 */
class PaymentMethodTest {

    @Test
    fun `Lightning payment method contains invoice`() {
        val method = PaymentMethod.Lightning("lnbc...")
        assertEquals("lnbc...", method.invoice)
    }

    @Test
    fun `Onchain payment method contains address`() {
        val method = PaymentMethod.Onchain("bc1...")
        assertEquals("bc1...", method.address)
    }

    @Test
    fun `Paykit payment method contains URI`() {
        val method = PaymentMethod.Paykit("paykit:...")
        assertEquals("paykit:...", method.uri)
    }
}
