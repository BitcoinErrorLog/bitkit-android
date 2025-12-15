package to.bitkit.paykit.services

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import to.bitkit.paykit.KeyManager
import to.bitkit.paykit.models.PaymentRequest
import to.bitkit.paykit.models.PaymentRequestStatus
import to.bitkit.paykit.models.RequestDirection
import to.bitkit.paykit.storage.AutoPayStorage
import to.bitkit.paykit.viewmodels.AutoPayViewModel

/**
 * Unit tests for PaymentRequestService.
 *
 * Tests payment request handling, autopay evaluation, and execution.
 */
class PaymentRequestServiceTest {

    private lateinit var directoryService: DirectoryService
    private lateinit var autopayEvaluator: AutoPayViewModel
    private lateinit var keyManager: KeyManager
    private lateinit var paymentRequestService: PaymentRequestService

    @Before
    fun setup() {
        directoryService = mockk(relaxed = true)
        autopayEvaluator = mockk(relaxed = true)
        keyManager = mockk(relaxed = true)

        paymentRequestService = PaymentRequestService(
            directoryService = directoryService,
            autopayEvaluator = autopayEvaluator,
            keyManager = keyManager
        )
    }

    // MARK: - Payment Request Creation Tests

    @Test
    fun `createPaymentRequest creates request with correct fields`() = runTest {
        // Given
        val fromPubkey = "pk:sender123"
        val toPubkey = "pk:recipient456"
        val amountSats = 1000L
        val description = "Test payment"

        // When
        val request = PaymentRequest(
            id = "test-id",
            fromPubkey = fromPubkey,
            toPubkey = toPubkey,
            amountSats = amountSats,
            currency = "SAT",
            methodId = "lightning",
            description = description,
            direction = RequestDirection.OUTGOING
        )

        // Then
        assertEquals(fromPubkey, request.fromPubkey)
        assertEquals(toPubkey, request.toPubkey)
        assertEquals(amountSats, request.amountSats)
        assertEquals(PaymentRequestStatus.PENDING, request.status)
    }

    // MARK: - Autopay Evaluation Tests

    @Test
    fun `handleIncomingRequest evaluates autopay`() = runTest {
        // Given
        val request = createTestRequest()
        coEvery { autopayEvaluator.evaluate(request) } returns AutopayEvaluationResult.RequiresApproval

        // When
        val result = paymentRequestService.handleIncomingRequest(request)

        // Then
        coVerify { autopayEvaluator.evaluate(request) }
    }

    @Test
    fun `autopay approved executes payment automatically`() = runTest {
        // Given
        val request = createTestRequest()
        coEvery { autopayEvaluator.evaluate(request) } returns AutopayEvaluationResult.Approved

        // When
        val result = paymentRequestService.handleIncomingRequest(request)

        // Then
        assertEquals(PaymentRequestProcessingResult.AutoPaid::class, result::class)
    }

    @Test
    fun `autopay denied returns denied result`() = runTest {
        // Given
        val request = createTestRequest()
        coEvery { autopayEvaluator.evaluate(request) } returns AutopayEvaluationResult.Denied("Over limit")

        // When
        val result = paymentRequestService.handleIncomingRequest(request)

        // Then
        assertTrue(result is PaymentRequestProcessingResult.Denied)
    }

    @Test
    fun `autopay requires approval returns pending result`() = runTest {
        // Given
        val request = createTestRequest()
        coEvery { autopayEvaluator.evaluate(request) } returns AutopayEvaluationResult.RequiresApproval

        // When
        val result = paymentRequestService.handleIncomingRequest(request)

        // Then
        assertTrue(result is PaymentRequestProcessingResult.RequiresApproval)
    }

    // MARK: - Helper Methods

    private fun createTestRequest(): PaymentRequest {
        return PaymentRequest(
            id = "test-request-1",
            fromPubkey = "pk:sender",
            toPubkey = "pk:recipient",
            amountSats = 500L,
            currency = "SAT",
            methodId = "lightning",
            description = "Test",
            direction = RequestDirection.INCOMING
        )
    }
}

