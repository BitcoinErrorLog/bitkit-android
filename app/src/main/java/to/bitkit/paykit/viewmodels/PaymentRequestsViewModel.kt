package to.bitkit.paykit.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import to.bitkit.paykit.models.PaymentRequest
import to.bitkit.paykit.models.PaymentRequestStatus
import to.bitkit.paykit.storage.PaymentRequestStorage
import to.bitkit.utils.Logger
import javax.inject.Inject

@HiltViewModel
class PaymentRequestsViewModel @Inject constructor(
    private val paymentRequestStorage: PaymentRequestStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentRequestsUiState())
    val uiState: StateFlow<PaymentRequestsUiState> = _uiState.asStateFlow()

    init {
        loadRequests()
    }

    fun loadRequests() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val requests = paymentRequestStorage.listRequests()
                _uiState.update { it.copy(requests = requests, isLoading = false) }
            } catch (e: Exception) {
                Logger.error("Failed to load payment requests", e, context = TAG)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun acceptRequest(request: PaymentRequest) {
        viewModelScope.launch {
            try {
                paymentRequestStorage.updateStatus(request.id, PaymentRequestStatus.ACCEPTED)
                loadRequests()
            } catch (e: Exception) {
                Logger.error("Failed to accept request", e, context = TAG)
            }
        }
    }

    fun declineRequest(request: PaymentRequest) {
        viewModelScope.launch {
            try {
                paymentRequestStorage.updateStatus(request.id, PaymentRequestStatus.DECLINED)
                loadRequests()
            } catch (e: Exception) {
                Logger.error("Failed to decline request", e, context = TAG)
            }
        }
    }

    companion object {
        private const val TAG = "PaymentRequestsViewModel"
    }
}

data class PaymentRequestsUiState(
    val requests: List<PaymentRequest> = emptyList(),
    val isLoading: Boolean = false,
)

