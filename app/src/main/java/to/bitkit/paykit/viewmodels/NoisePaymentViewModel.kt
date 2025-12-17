package to.bitkit.paykit.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import to.bitkit.paykit.services.NoisePaymentRequest
import to.bitkit.paykit.services.NoisePaymentResponse
import to.bitkit.paykit.services.NoisePaymentService
import to.bitkit.paykit.services.PaykitPaymentService
import to.bitkit.repositories.LightningRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for Noise payment flows
 */
@HiltViewModel
class NoisePaymentViewModel @Inject constructor(
    private val noisePaymentService: NoisePaymentService,
    private val paymentService: PaykitPaymentService,
    private val lightningRepo: LightningRepo,
) : ViewModel() {

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _paymentRequest = MutableStateFlow<NoisePaymentRequest?>(null)
    val paymentRequest: StateFlow<NoisePaymentRequest?> = _paymentRequest.asStateFlow()

    private val _paymentResponse = MutableStateFlow<NoisePaymentResponse?>(null)
    val paymentResponse: StateFlow<NoisePaymentResponse?> = _paymentResponse.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun sendPayment(request: NoisePaymentRequest) {
        viewModelScope.launch {
            _isConnecting.value = true
            _errorMessage.value = null

            try {
                val response = noisePaymentService.sendPaymentRequest(request)
                _paymentResponse.value = response
                _isConnected.value = true
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isConnecting.value = false
            }
        }
    }

    fun receivePayment() {
        viewModelScope.launch {
            _isConnecting.value = true
            _errorMessage.value = null

            try {
                val request = noisePaymentService.receivePaymentRequest()
                if (request != null) {
                    _paymentRequest.value = request
                    _isConnected.value = true
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isConnecting.value = false
            }
        }
    }

    fun acceptIncomingRequest() {
        val request = _paymentRequest.value ?: return

        viewModelScope.launch {
            _errorMessage.value = null

            try {
                // Parse the amount (assuming it's in sats if currency is not specified or is "sats")
                val amountSats = request.amount?.toULongOrNull() ?: run {
                    _errorMessage.value = "Invalid payment amount"
                    return@launch
                }

                // Pay the requester
                // The payer becomes the recipient when we accept the request
                val result = paymentService.pay(
                    lightningRepo = lightningRepo,
                    recipient = request.payerPubkey,
                    amountSats = amountSats,
                    peerPubkey = request.payerPubkey,
                )

                // Store the payment response
                _paymentResponse.value = NoisePaymentResponse(
                    success = true,
                    receiptId = request.receiptId,
                    confirmedAt = System.currentTimeMillis(),
                    errorCode = null,
                    errorMessage = null,
                )

                // Clear the request
                _paymentRequest.value = null

            } catch (e: Exception) {
                _errorMessage.value = "Payment failed: ${e.message}"
            }
        }
    }

    fun declineIncomingRequest() {
        viewModelScope.launch {
            // Decline by simply clearing the request
            _paymentRequest.value = null
        }
    }
}
