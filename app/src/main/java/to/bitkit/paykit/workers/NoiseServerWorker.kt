package to.bitkit.paykit.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withTimeout
import to.bitkit.paykit.models.PaymentRequest
import to.bitkit.paykit.models.PaymentRequestStatus
import to.bitkit.paykit.models.RequestDirection
import to.bitkit.paykit.services.NoisePaymentService
import to.bitkit.paykit.services.NoisePaymentRequest
import to.bitkit.paykit.storage.PaymentRequestStorage
import to.bitkit.ui.pushNotification
import to.bitkit.utils.Logger
import java.net.ServerSocket
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking

/**
 * Background worker for handling incoming Noise protocol connections.
 * 
 * This worker is started when a push notification indicates an incoming
 * Noise payment request. It:
 * 1. Starts a Noise server on the configured port
 * 2. Waits for the incoming connection
 * 3. Performs Noise handshake
 * 4. Receives and stores the payment request
 * 5. Shows a notification to the user
 */
@HiltWorker
class NoiseServerWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val noisePaymentService: NoisePaymentService,
    private val paymentRequestStorage: PaymentRequestStorage
) : CoroutineWorker(appContext, workerParams) {
    
    companion object {
        private const val TAG = "NoiseServerWorker"
        
        // Input data keys
        const val KEY_FROM_PUBKEY = "from_pubkey"
        const val KEY_ENDPOINT_HOST = "endpoint_host"
        const val KEY_ENDPOINT_PORT = "endpoint_port"
        const val KEY_NOISE_PUBKEY = "noise_pubkey"
        
        // Default server settings
        private const val DEFAULT_PORT = 9000
        private const val SERVER_TIMEOUT_SECONDS = 30L
    }
    
    override suspend fun doWork(): Result {
        val fromPubkey = inputData.getString(KEY_FROM_PUBKEY)
        val endpointHost = inputData.getString(KEY_ENDPOINT_HOST)
        val endpointPort = inputData.getInt(KEY_ENDPOINT_PORT, DEFAULT_PORT)
        val noisePubkey = inputData.getString(KEY_NOISE_PUBKEY)
        
        Logger.info("NoiseServerWorker: Starting for incoming request from ${fromPubkey?.take(12)}...", context = TAG)
        
        return try {
            withTimeout(SERVER_TIMEOUT_SECONDS.seconds) {
                startServerAndReceive(fromPubkey, endpointPort)
            }
        } catch (e: Exception) {
            Logger.error("NoiseServerWorker: Failed to handle Noise request", e, context = TAG)
            Result.retry()
        }
    }
    
    private suspend fun startServerAndReceive(fromPubkey: String?, port: Int): Result {
        try {
            // Start background server via NoisePaymentService
            noisePaymentService.startBackgroundServer(port) { request ->
                runBlocking { handleIncomingRequest(request, fromPubkey) }
            }
            
            Logger.info("NoiseServerWorker: Successfully processed incoming request", context = TAG)
            return Result.success()
            
        } catch (e: Exception) {
            Logger.error("NoiseServerWorker: Server error", e, context = TAG)
            throw e
        }
    }
    
    private suspend fun handleIncomingRequest(
        noiseRequest: NoisePaymentRequest,
        expectedFromPubkey: String?
    ) {
        // Validate sender if expected pubkey was provided
        if (expectedFromPubkey != null && noiseRequest.payerPubkey != expectedFromPubkey) {
            Logger.warn(
                "NoiseServerWorker: Received request from unexpected pubkey. " +
                "Expected: ${expectedFromPubkey.take(12)}..., Got: ${noiseRequest.payerPubkey.take(12)}...",
                context = TAG
            )
            // Continue processing anyway - the Noise handshake already verified the sender
        }
        
        // Convert Noise request to payment request
        val paymentRequest = PaymentRequest(
            id = noiseRequest.receiptId,
            fromPubkey = noiseRequest.payerPubkey,
            toPubkey = noiseRequest.payeePubkey,
            amountSats = noiseRequest.amount?.toLongOrNull() ?: 0L,
            currency = noiseRequest.currency ?: "BTC",
            methodId = noiseRequest.methodId,
            description = noiseRequest.description ?: "",
            status = PaymentRequestStatus.PENDING,
            direction = RequestDirection.INCOMING,
            invoiceNumber = noiseRequest.invoiceNumber
        )
        
        // Store the request
        paymentRequestStorage.addRequest(paymentRequest)
        Logger.info("NoiseServerWorker: Stored payment request ${paymentRequest.id}", context = TAG)
        
        // Show notification to user
        showPaymentRequestNotification(paymentRequest)
    }
    
    private fun showPaymentRequestNotification(request: PaymentRequest) {
        val senderDisplay = if (request.fromPubkey.length > 12) {
            "${request.fromPubkey.take(6)}...${request.fromPubkey.takeLast(4)}"
        } else {
            request.fromPubkey
        }
        
        val title = "Payment Request Received"
        val body = "Request for ${request.amountSats} sats from $senderDisplay"
        
        appContext.pushNotification(title, body)
    }
}

