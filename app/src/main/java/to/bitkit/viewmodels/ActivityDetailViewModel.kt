package to.bitkit.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synonym.bitkitcore.Activity
import com.synonym.bitkitcore.IBtOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.bitkit.data.SettingsStore
import to.bitkit.di.BgDispatcher
import to.bitkit.ext.rawId
import to.bitkit.repositories.ActivityRepo
import to.bitkit.repositories.BlocktankRepo
import to.bitkit.utils.AddressChecker
import to.bitkit.utils.Logger
import to.bitkit.utils.TxDetails
import javax.inject.Inject

@HiltViewModel
class ActivityDetailViewModel @Inject constructor(
    @BgDispatcher private val bgDispatcher: CoroutineDispatcher,
    private val addressChecker: AddressChecker,
    private val activityRepo: ActivityRepo,
    private val settingsStore: SettingsStore,
    private val blocktankRepo: BlocktankRepo,
) : ViewModel() {
    private val _txDetails = MutableStateFlow<TxDetails?>(null)
    val txDetails = _txDetails.asStateFlow()

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags = _tags.asStateFlow()

    private val _boostSheetVisible = MutableStateFlow(false)
    val boostSheetVisible = _boostSheetVisible.asStateFlow()

    private var activity: Activity? = null

    fun setActivity(activity: Activity) {
        this.activity = activity
        loadTags()
    }

    fun loadTags() {
        val id = activity?.rawId() ?: return
        viewModelScope.launch(bgDispatcher) {
            activityRepo.getActivityTags(id)
                .onSuccess { activityTags ->
                    _tags.value = activityTags
                }
                .onFailure { e ->
                    Logger.error("Failed to load tags for activity $id", e, TAG)
                    _tags.value = emptyList()
                }
        }
    }

    fun removeTag(tag: String) {
        val id = activity?.rawId() ?: return
        viewModelScope.launch(bgDispatcher) {
            activityRepo.removeTagsFromActivity(id, listOf(tag))
                .onSuccess {
                    loadTags()
                }
                .onFailure { e ->
                    Logger.error("Failed to remove tag $tag from activity $id", e, TAG)
                }
        }
    }

    fun addTag(tag: String) {
        val id = activity?.rawId() ?: return
        viewModelScope.launch(bgDispatcher) {
            activityRepo.addTagsToActivity(id, listOf(tag))
                .onSuccess {
                    settingsStore.addLastUsedTag(tag)
                    loadTags()
                }
                .onFailure { e ->
                    Logger.error("Failed to add tag $tag to activity $id", e, TAG)
                }
        }
    }

    fun fetchTransactionDetails(txid: String) {
        viewModelScope.launch(bgDispatcher) {
            try {
                // TODO replace with bitkit-core method when available
                _txDetails.value = addressChecker.getTransaction(txid)
            } catch (e: Throwable) {
                Logger.error("fetchTransactionDetails error", e, context = TAG)
                _txDetails.value = null
            }
        }
    }

    fun clearTransactionDetails() {
        _txDetails.value = null
    }

    fun onClickBoost() {
        _boostSheetVisible.update { true }
    }

    fun onDismissBoostSheet() {
        _boostSheetVisible.update { false }
    }

    suspend fun findOrderForTransfer(
        channelId: String?,
        txId: String?,
    ): IBtOrder? = withContext(bgDispatcher) {
        try {
            val orders = blocktankRepo.blocktankState.value.orders

            if (channelId != null) {
                orders.find { it.id == channelId }?.let { return@withContext it }
            }

            if (txId != null) {
                orders.firstOrNull { order ->
                    order.payment?.onchain?.transactions?.any { it.txId == txId } == true
                }?.let { return@withContext it }
            }

            null
        } catch (e: Exception) {
            Logger.warn("Failed to find order for transfer: channelId=$channelId, txId=$txId", e, context = TAG)
            null
        }
    }

    private companion object {
        const val TAG = "ActivityDetailViewModel"
    }
}
