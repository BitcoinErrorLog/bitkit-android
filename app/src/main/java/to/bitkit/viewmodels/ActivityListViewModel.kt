package to.bitkit.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synonym.bitkitcore.Activity
import com.synonym.bitkitcore.ActivityFilter
import com.synonym.bitkitcore.PaymentType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import to.bitkit.di.BgDispatcher
import to.bitkit.ext.isTransfer
import to.bitkit.paykit.models.PaymentDirection
import to.bitkit.paykit.models.Receipt
import to.bitkit.paykit.storage.ReceiptStorage
import to.bitkit.repositories.ActivityRepo
import to.bitkit.ui.screens.wallets.activity.components.ActivityTab
import to.bitkit.utils.Logger
import javax.inject.Inject

@Suppress("TooManyFunctions")
@HiltViewModel
class ActivityListViewModel @Inject constructor(
    @BgDispatcher private val bgDispatcher: CoroutineDispatcher,
    private val activityRepo: ActivityRepo,
    private val receiptStorage: ReceiptStorage,
) : ViewModel() {
    private val _filteredActivities = MutableStateFlow<List<Activity>?>(null)
    val filteredActivities = _filteredActivities.asStateFlow()

    private val _lightningActivities = MutableStateFlow<List<Activity>?>(null)
    val lightningActivities = _lightningActivities.asStateFlow()

    private val _onchainActivities = MutableStateFlow<List<Activity>?>(null)
    val onchainActivities = _onchainActivities.asStateFlow()

    private val _latestActivities = MutableStateFlow<List<Activity>?>(null)
    val latestActivities = _latestActivities.asStateFlow()

    // MARK: - Paykit Integration
    
    private val _paykitReceipts = MutableStateFlow<List<Receipt>>(emptyList())
    val paykitReceipts = _paykitReceipts.asStateFlow()
    
    private val _unifiedActivities = MutableStateFlow<List<UnifiedActivityItem>>(emptyList())
    val unifiedActivities = _unifiedActivities.asStateFlow()
    
    private val _showPaykitReceipts = MutableStateFlow(true)
    val showPaykitReceipts = _showPaykitReceipts.asStateFlow()

    val availableTags: StateFlow<List<String>> = activityRepo.state.map { it.tags }.stateInScope(emptyList())

    private val _filters = MutableStateFlow(ActivityFilters())

    // individual filters for UI
    val searchText: StateFlow<String> = _filters.map { it.searchText }.stateInScope("")
    val startDate: StateFlow<Long?> = _filters.map { it.startDate }.stateInScope(null)
    val endDate: StateFlow<Long?> = _filters.map { it.endDate }.stateInScope(null)
    val selectedTags: StateFlow<Set<String>> = _filters.map { it.tags }.stateInScope(emptySet())
    val selectedTab: StateFlow<ActivityTab> = _filters.map { it.tab }.stateInScope(ActivityTab.ALL)

    fun setSearchText(text: String) = _filters.update { it.copy(searchText = text) }

    fun setTab(tab: ActivityTab) = _filters.update { it.copy(tab = tab) }

    fun toggleTag(tag: String) = _filters.update {
        val newTags = if (tag in it.tags) it.tags - tag else it.tags + tag
        it.copy(tags = newTags)
    }

    init {
        observeActivities()
        observeFilters()
        resync()
    }

    fun resync() = viewModelScope.launch {
        activityRepo.syncActivities()
    }

    private fun observeActivities() = viewModelScope.launch {
        activityRepo.activitiesChanged.collect {
            refreshActivityState()
            syncPaykitReceipts()
            updateUnifiedActivities()
        }
    }
    
    // MARK: - Paykit Receipt Methods
    
    private fun syncPaykitReceipts() {
        _paykitReceipts.value = receiptStorage.listReceipts()
    }
    
    private fun updateUnifiedActivities() {
        val unified = mutableListOf<UnifiedActivityItem>()
        
        // Add standard activities
        _filteredActivities.value?.forEach { activity ->
            unified.add(UnifiedActivityItem.Standard(activity))
        }
        
        // Add Paykit receipts if enabled
        if (_showPaykitReceipts.value) {
            val tab = _filters.value.tab
            val filteredReceipts = when (tab) {
                ActivityTab.ALL -> _paykitReceipts.value
                ActivityTab.SENT -> _paykitReceipts.value.filter { it.direction == PaymentDirection.SENT }
                ActivityTab.RECEIVED -> _paykitReceipts.value.filter { it.direction == PaymentDirection.RECEIVED }
                ActivityTab.PAYKIT -> _paykitReceipts.value
                ActivityTab.OTHER -> emptyList()
            }
            filteredReceipts.forEach { receipt ->
                unified.add(UnifiedActivityItem.Paykit(receipt))
            }
        }
        
        // Sort by timestamp (newest first)
        unified.sortByDescending { it.timestamp }
        
        _unifiedActivities.value = unified
    }
    
    fun togglePaykitReceipts() {
        _showPaykitReceipts.update { !it }
        updateUnifiedActivities()
    }
    
    fun getPaykitReceipt(id: String): Receipt? {
        return receiptStorage.getReceipt(id)
    }

    private fun observeFilters() = viewModelScope.launch {
        @OptIn(FlowPreview::class)
        combine(
            _filters.map { it.searchText }.debounce(300),
            _filters.map { it.copy(searchText = "") },
            activityRepo.activitiesChanged,
        ) { debouncedSearch, filtersWithoutSearch, _ ->
            fetchFilteredActivities(filtersWithoutSearch.copy(searchText = debouncedSearch))
        }.collect { _filteredActivities.value = it }
    }

    private suspend fun refreshActivityState() {
        val all = activityRepo.getActivities(filter = ActivityFilter.ALL).getOrNull() ?: emptyList()
        val filtered = filterOutReplacedSentTransactions(all)
        _latestActivities.update { filtered.take(SIZE_LATEST) }
        _lightningActivities.update { filtered.filter { it is Activity.Lightning } }
        _onchainActivities.update { filtered.filter { it is Activity.Onchain } }
    }

    private suspend fun fetchFilteredActivities(filters: ActivityFilters): List<Activity>? {
        val txType = when (filters.tab) {
            ActivityTab.SENT -> PaymentType.SENT
            ActivityTab.RECEIVED -> PaymentType.RECEIVED
            else -> null
        }

        val activities = activityRepo.getActivities(
            filter = ActivityFilter.ALL,
            txType = txType,
            tags = filters.tags.takeIf { it.isNotEmpty() }?.toList(),
            search = filters.searchText.takeIf { it.isNotEmpty() },
            minDate = filters.startDate?.let { it / 1000 }?.toULong(),
            maxDate = filters.endDate?.let { it / 1000 }?.toULong(),
        ).getOrElse { e ->
            Logger.error("Failed to filter activities", e)
            return null
        }

        val filteredByTab = when (filters.tab) {
            ActivityTab.OTHER -> activities.filter { it.isTransfer() }
            else -> activities
        }

        return filterOutReplacedSentTransactions(filteredByTab)
    }

    private suspend fun filterOutReplacedSentTransactions(activities: List<Activity>): List<Activity> {
        val txIdsInBoostTxIds = activityRepo.getTxIdsInBoostTxIds()

        return activities.filter { activity ->
            if (activity is Activity.Onchain) {
                val onchain = activity.v1
                if (!onchain.doesExist &&
                    onchain.txType == PaymentType.SENT &&
                    txIdsInBoostTxIds.contains(onchain.txId)
                ) {
                    return@filter false
                }
            }
            true
        }
    }

    fun updateAvailableTags() {
        viewModelScope.launch {
            activityRepo.getAllAvailableTags()
        }
    }

    fun setDateRange(startDate: Long?, endDate: Long?) = _filters.update {
        it.copy(startDate = startDate, endDate = endDate)
    }

    fun clearDateRange() = _filters.update {
        it.copy(startDate = null, endDate = null)
    }

    fun clearFilters() = _filters.update { ActivityFilters() }

    fun generateRandomTestData(count: Int) = viewModelScope.launch(bgDispatcher) {
        activityRepo.generateTestData(count)
    }

    fun removeAllActivities() = viewModelScope.launch(bgDispatcher) {
        activityRepo.removeAllActivities()
    }

    suspend fun isCpfpChildTransaction(txId: String): Boolean {
        return activityRepo.isCpfpChildTransaction(txId)
    }

    private fun <T> Flow<T>.stateInScope(
        initialValue: T,
        started: SharingStarted = SharingStarted.WhileSubscribed(MS_TIMEOUT_SUB),
    ): StateFlow<T> = stateIn(viewModelScope, started, initialValue)

    companion object {
        private const val SIZE_LATEST = 3
        private const val MS_TIMEOUT_SUB = 5000L
    }
}

data class ActivityFilters(
    val tab: ActivityTab = ActivityTab.ALL,
    val tags: Set<String> = emptySet(),
    val searchText: String = "",
    val startDate: Long? = null,
    val endDate: Long? = null,
)

/**
 * Unified activity item that can represent either a standard Activity or a Paykit receipt
 */
sealed class UnifiedActivityItem {
    abstract val id: String
    abstract val timestamp: Long
    abstract val isSent: Boolean
    abstract val isReceived: Boolean
    abstract val isPaykit: Boolean
    
    data class Standard(val activity: Activity) : UnifiedActivityItem() {
        override val id: String
            get() = when (activity) {
                is Activity.Lightning -> activity.v1.id
                is Activity.Onchain -> activity.v1.txId
            }
        
        override val timestamp: Long
            get() = when (activity) {
                is Activity.Lightning -> activity.v1.timestamp.toLong()
                is Activity.Onchain -> activity.v1.timestamp.toLong()
            }
        
        override val isSent: Boolean
            get() = when (activity) {
                is Activity.Lightning -> activity.v1.txType == PaymentType.SENT
                is Activity.Onchain -> activity.v1.txType == PaymentType.SENT
            }
        
        override val isReceived: Boolean
            get() = when (activity) {
                is Activity.Lightning -> activity.v1.txType == PaymentType.RECEIVED
                is Activity.Onchain -> activity.v1.txType == PaymentType.RECEIVED
            }
        
        override val isPaykit: Boolean = false
    }
    
    data class Paykit(val receipt: Receipt) : UnifiedActivityItem() {
        override val id: String
            get() = "paykit-${receipt.id}"
        
        override val timestamp: Long
            get() = receipt.createdAt
        
        override val isSent: Boolean
            get() = receipt.direction == PaymentDirection.SENT
        
        override val isReceived: Boolean
            get() = receipt.direction == PaymentDirection.RECEIVED
        
        override val isPaykit: Boolean = true
    }
}
