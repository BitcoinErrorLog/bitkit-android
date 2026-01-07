package to.bitkit.paykit.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import to.bitkit.paykit.models.Contact
import to.bitkit.paykit.models.Receipt
import to.bitkit.paykit.services.DirectoryService
import to.bitkit.paykit.services.PubkyRingBridge
import to.bitkit.paykit.storage.*
import javax.inject.Inject

/**
 * ViewModel for Paykit Dashboard
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val receiptStorage: ReceiptStorage,
    private val contactStorage: ContactStorage,
    private val autoPayStorage: AutoPayStorage,
    private val subscriptionStorage: SubscriptionStorage,
    private val paymentRequestStorage: PaymentRequestStorage,
    private val pubkyRingBridge: PubkyRingBridge,
    private val directoryService: DirectoryService,
) : ViewModel() {

    private val _recentReceipts = MutableStateFlow<List<Receipt>>(emptyList())
    val recentReceipts: StateFlow<List<Receipt>> = _recentReceipts.asStateFlow()

    private val _contactCount = MutableStateFlow(0)
    val contactCount: StateFlow<Int> = _contactCount.asStateFlow()

    private val _totalSent = MutableStateFlow(0L)
    val totalSent: StateFlow<Long> = _totalSent.asStateFlow()

    private val _totalReceived = MutableStateFlow(0L)
    val totalReceived: StateFlow<Long> = _totalReceived.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasPaymentMethods = MutableStateFlow(false)
    val hasPaymentMethods: StateFlow<Boolean> = _hasPaymentMethods.asStateFlow()

    private val _hasPublishedMethods = MutableStateFlow(false)
    val hasPublishedMethods: StateFlow<Boolean> = _hasPublishedMethods.asStateFlow()

    private val _autoPayEnabled = MutableStateFlow(false)
    val autoPayEnabled: StateFlow<Boolean> = _autoPayEnabled.asStateFlow()

    private val _activeSubscriptions = MutableStateFlow(0)
    val activeSubscriptions: StateFlow<Int> = _activeSubscriptions.asStateFlow()

    private val _pendingRequests = MutableStateFlow(0)
    val pendingRequests: StateFlow<Int> = _pendingRequests.asStateFlow()

    private val _publishedMethodsCount = MutableStateFlow(0)
    val publishedMethodsCount: StateFlow<Int> = _publishedMethodsCount.asStateFlow()

    private val _isPubkyRingInstalled = MutableStateFlow(false)
    val isPubkyRingInstalled: StateFlow<Boolean> = _isPubkyRingInstalled.asStateFlow()

    init {
        _isPubkyRingInstalled.value = pubkyRingBridge.isPubkyRingInstalled(context)
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _isLoading.value = true

            // Load recent receipts
            _recentReceipts.value = receiptStorage.recentReceipts(limit = 5)

            // Load local stats first for immediate display
            _contactCount.value = contactStorage.listContacts().size
            _totalSent.value = receiptStorage.totalSent()
            _totalReceived.value = receiptStorage.totalReceived()
            _pendingCount.value = receiptStorage.pendingCount()

            // Load Auto-Pay status
            val autoPaySettings = autoPayStorage.getSettings()
            _autoPayEnabled.value = autoPaySettings.isEnabled

            // Load Subscriptions count
            _activeSubscriptions.value = subscriptionStorage.activeSubscriptions().size

            // Load Payment Requests count
            _pendingRequests.value = paymentRequestStorage.pendingCount()

            _isLoading.value = false

            // Sync contacts from Pubky follows in background
            syncContactsFromFollows()
        }
    }

    private suspend fun syncContactsFromFollows() = withContext(Dispatchers.IO) {
        runCatching {
            val discoveredContacts = directoryService.discoverContactsFromFollows()
            for (discovered in discoveredContacts) {
                if (contactStorage.getContact(discovered.pubkey) == null) {
                    val newContact = Contact.create(
                        publicKeyZ32 = discovered.pubkey,
                        name = discovered.name ?: discovered.pubkey.take(12),
                    )
                    contactStorage.saveContact(newContact)
                }
            }
            // Update count after sync
            _contactCount.value = contactStorage.listContacts().size
        }
    }

    val isSetupComplete: Boolean
        get() = _contactCount.value > 0 && _hasPaymentMethods.value && _hasPublishedMethods.value

    val setupProgress: Int
        get() {
            var completed = 1 // Identity is always created at this point
            if (_contactCount.value > 0) completed += 1
            if (_hasPaymentMethods.value) completed += 1
            if (_hasPublishedMethods.value) completed += 1
            return (completed * 100) / 4
        }
}
