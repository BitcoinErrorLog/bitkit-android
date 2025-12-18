package to.bitkit.paykit.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import to.bitkit.paykit.models.PaymentDirection
import to.bitkit.paykit.models.PaymentStatus
import to.bitkit.paykit.models.Receipt
import dagger.hilt.android.lifecycle.HiltViewModel
import to.bitkit.paykit.storage.ReceiptStorage
import javax.inject.Inject

/**
 * ViewModel for Receipts view
 */
@HiltViewModel
class ReceiptsViewModel @Inject constructor(
    private val receiptStorage: ReceiptStorage
) : ViewModel() {

    private val _receipts = MutableStateFlow<List<Receipt>>(emptyList())
    val receipts: StateFlow<List<Receipt>> = _receipts.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedStatus = MutableStateFlow<PaymentStatus?>(null)
    val selectedStatus: StateFlow<PaymentStatus?> = _selectedStatus.asStateFlow()

    private val _selectedDirection = MutableStateFlow<PaymentDirection?>(null)
    val selectedDirection: StateFlow<PaymentDirection?> = _selectedDirection.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadReceipts() {
        viewModelScope.launch {
            _isLoading.value = true
            _receipts.value = receiptStorage.listReceipts()
            _isLoading.value = false
        }
    }

    fun filterReceipts() {
        viewModelScope.launch {
            var filtered = receiptStorage.listReceipts()

            _selectedStatus.value?.let { status ->
                filtered = filtered.filter { it.status == status }
            }

            _selectedDirection.value?.let { direction ->
                filtered = filtered.filter { it.direction == direction }
            }

            if (_searchQuery.value.isNotEmpty()) {
                filtered = receiptStorage.searchReceipts(_searchQuery.value)
            }

            _receipts.value = filtered
        }
    }

    fun clearFilters() {
        _selectedStatus.value = null
        _selectedDirection.value = null
        _searchQuery.value = ""
        loadReceipts()
    }

    val totalSent: Long
        get() = receiptStorage.totalSent()

    val totalReceived: Long
        get() = receiptStorage.totalReceived()

    val completedCount: Int
        get() = receiptStorage.completedCount()

    val pendingCount: Int
        get() = receiptStorage.pendingCount()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        filterReceipts()
    }

    fun setSelectedStatus(status: PaymentStatus?) {
        _selectedStatus.value = status
        filterReceipts()
    }

    fun setSelectedDirection(direction: PaymentDirection?) {
        _selectedDirection.value = direction
        filterReceipts()
    }
}
