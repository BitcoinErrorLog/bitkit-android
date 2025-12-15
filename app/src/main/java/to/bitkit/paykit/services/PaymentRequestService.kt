package to.bitkit.paykit.services

import com.paykit.mobile.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import to.bitkit.paykit.models.PaymentRequest
import to.bitkit.paykit.models.PaymentRequestStatus
import to.bitkit.paykit.storage.PaymentRequestStorage
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of autopay evaluation
 */
sealed class AutopayEvaluationResult {
    data class Approved(val ruleId: String?, val ruleName: String?) : AutopayEvaluationResult()
    data class Denied(val reason: String) : AutopayEvaluationResult()
    object NeedsApproval : AutopayEvaluationResult()
    
    val isApproved: Boolean
        get() = this is Approved
}

/**
 * Result of payment request processing
 */
sealed class PaymentRequestProcessingResult {
    data class AutoPaid(val paymentResult: PaymentExecutionResult) : PaymentRequestProcessingResult()
    data class NeedsApproval(val request: PaymentRequest) : PaymentRequestProcessingResult()
    data class Denied(val reason: String) : PaymentRequestProcessingResult()
    data class Error(val error: Throwable) : PaymentRequestProcessingResult()
}

/**
 * Protocol for autopay evaluation
 */
interface AutopayEvaluator {
    /**
     * Evaluate if a payment should be auto-approved
     */
    fun evaluate(peerPubkey: String, amount: Long, methodId: String): AutopayEvaluationResult
}

/**
 * Service for handling payment requests with autopay support.
 * Designed for Bitkit integration.
 */
@Singleton
class PaymentRequestService @Inject constructor(
    private val paykitClient: PaykitClient,
    private val autopayEvaluator: AutopayEvaluator,
    private val paymentRequestStorage: PaymentRequestStorage,
    private val directoryService: DirectoryService
) {
    companion object {
        private const val TAG = "PaymentRequestService"
    }
    
    /**
     * Handle an incoming payment request
     * @param requestId Payment request ID
     * @param fromPubkey Requester's public key
     * @return Result with processing result
     */
    suspend fun handleIncomingRequest(
        requestId: String,
        fromPubkey: String
    ): Result<PaymentRequestProcessingResult> = withContext(Dispatchers.IO) {
        try {
            // Fetch payment request details from storage
            val request = fetchPaymentRequest(requestId, fromPubkey)
            
            // Evaluate autopay
            val evaluation = autopayEvaluator.evaluate(
                peerPubkey = fromPubkey,
                amount = request.amountSats,
                methodId = request.methodId
            )
            
            when (evaluation) {
                is AutopayEvaluationResult.Approved -> {
                    // Execute payment automatically
                    try {
                        val endpoint = resolveEndpoint(request)
                        val paymentResult = executePayment(request, endpoint, null)
                        
                        // Update request status
                        paymentRequestStorage.updateStatus(requestId, PaymentRequestStatus.PAID)
                        
                        Result.success(
                            PaymentRequestProcessingResult.AutoPaid(paymentResult)
                        )
                    } catch (e: Exception) {
                        Logger.error("PaymentRequestService: Failed to execute payment", e, context = TAG)
                        Result.success(
                            PaymentRequestProcessingResult.Error(e)
                        )
                    }
                }
                is AutopayEvaluationResult.Denied -> {
                    // Update request status
                    paymentRequestStorage.updateStatus(requestId, PaymentRequestStatus.DECLINED)
                    Result.success(
                        PaymentRequestProcessingResult.Denied(evaluation.reason)
                    )
                }
                is AutopayEvaluationResult.NeedsApproval -> {
                    Result.success(
                        PaymentRequestProcessingResult.NeedsApproval(request)
                    )
                }
            }
        } catch (e: Exception) {
            Logger.error("PaymentRequestService: Failed to handle request", e, context = TAG)
            Result.failure(e)
        }
    }
    
    /**
     * Evaluate autopay for a payment request
     */
    fun evaluateAutopay(
        peerPubkey: String,
        amount: Long,
        methodId: String
    ): AutopayEvaluationResult {
        return autopayEvaluator.evaluate(peerPubkey, amount, methodId)
    }
    
    /**
     * Execute a payment request
     */
    suspend fun executePayment(
        request: PaymentRequest,
        endpoint: String,
        metadataJson: String?
    ): PaymentExecutionResult = withContext(Dispatchers.IO) {
        // Execute payment via PaykitClient
        paykitClient.executePayment(
            methodId = request.methodId,
            endpoint = endpoint,
            amountSats = request.amountSats.toULong(),
            metadataJson = metadataJson
        )
    }
    
    // MARK: - Private Helpers
    
    /**
     * Fetch payment request details from storage
     */
    private suspend fun fetchPaymentRequest(requestId: String, fromPubkey: String): PaymentRequest {
        val request = paymentRequestStorage.getRequest(requestId)
            ?: throw IllegalStateException("Payment request not found: $requestId")
        
        // Verify the request is from the expected pubkey
        if (request.fromPubkey != fromPubkey) {
            throw IllegalStateException("Payment request pubkey mismatch")
        }
        
        return request
    }
    
    /**
     * Resolve payment endpoint from request
     */
    private suspend fun resolveEndpoint(request: PaymentRequest): String {
        // Try to discover payment methods for the sender
        val paymentMethods = directoryService.discoverPaymentMethods(request.fromPubkey)
        
        // Find matching method - PaymentMethod from FFI has methodId and endpoint
        val matchingMethod = paymentMethods.firstOrNull { it.methodId == request.methodId }
            ?: throw IllegalStateException("Payment method not found: ${request.methodId}")
        
        // Return the endpoint from the discovered payment method
        return matchingMethod.endpoint
    }
}

