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
import javax.inject.Inject

/**
 * ViewModel for Noise payment flows
 */
class NoisePaymentViewModel @Inject constructor(
    private val noisePaymentService: NoisePaymentService
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
}
