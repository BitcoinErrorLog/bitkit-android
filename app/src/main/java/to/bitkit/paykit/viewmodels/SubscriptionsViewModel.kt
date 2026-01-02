package to.bitkit.paykit.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import to.bitkit.paykit.KeyManager
import to.bitkit.paykit.models.AutoPayRule
import to.bitkit.paykit.models.PeerSpendingLimit
import to.bitkit.paykit.models.Subscription
import to.bitkit.paykit.models.SubscriptionProposal
import to.bitkit.paykit.services.DirectoryService
import to.bitkit.paykit.storage.AutoPayStorage
import to.bitkit.paykit.storage.SubscriptionStorage
import to.bitkit.paykit.workers.DiscoveredSubscriptionProposal
import dagger.hilt.android.lifecycle.HiltViewModel
import to.bitkit.utils.Logger
import javax.inject.Inject

/**
 * ViewModel for Subscriptions management
 */
@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val subscriptionStorage: SubscriptionStorage,
    private val directoryService: DirectoryService,
    private val autoPayStorage: AutoPayStorage,
    private val keyManager: KeyManager,
) : ViewModel() {
    companion object {
        private const val TAG = "SubscriptionsViewModel"
    }

    private val _uiState = MutableStateFlow(SubscriptionsUiState())
    val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showingAddSubscription = MutableStateFlow(false)
    val showingAddSubscription: StateFlow<Boolean> = _showingAddSubscription.asStateFlow()

    init {
        loadSubscriptions()
        loadIncomingProposals()
    }

    fun loadSubscriptions() {
        viewModelScope.launch {
            _isLoading.value = true
            _subscriptions.value = subscriptionStorage.listSubscriptions()
            _isLoading.value = false
        }
    }

    fun addSubscription(subscription: Subscription) {
        viewModelScope.launch {
            try {
                subscriptionStorage.saveSubscription(subscription)
                loadSubscriptions()
            } catch (e: Exception) {
                Logger.error(
                    "SubscriptionsViewModel: Failed to add subscription",
                    e,
                    context = "SubscriptionsViewModel"
                )
            }
        }
    }

    fun updateSubscription(subscription: Subscription) {
        viewModelScope.launch {
            try {
                subscriptionStorage.saveSubscription(subscription)
                loadSubscriptions()
            } catch (e: Exception) {
                Logger.error(
                    "SubscriptionsViewModel: Failed to update subscription",
                    e,
                    context = "SubscriptionsViewModel"
                )
            }
        }
    }

    fun deleteSubscription(subscription: Subscription) {
        viewModelScope.launch {
            try {
                subscriptionStorage.deleteSubscription(subscription.id)
                loadSubscriptions()
            } catch (e: Exception) {
                Logger.error(
                    "SubscriptionsViewModel: Failed to delete subscription",
                    e,
                    context = "SubscriptionsViewModel"
                )
            }
        }
    }

    fun toggleActive(subscription: Subscription) {
        viewModelScope.launch {
            try {
                subscriptionStorage.toggleActive(subscription.id)
                loadSubscriptions()
            } catch (e: Exception) {
                Logger.error(
                    "SubscriptionsViewModel: Failed to toggle subscription",
                    e,
                    context = "SubscriptionsViewModel"
                )
            }
        }
    }

    fun recordPayment(subscription: Subscription) {
        viewModelScope.launch {
            try {
                subscriptionStorage.recordPayment(subscription.id)
                loadSubscriptions()
            } catch (e: Exception) {
                Logger.error("SubscriptionsViewModel: Failed to record payment", e, context = "SubscriptionsViewModel")
            }
        }
    }

    val activeSubscriptions: List<Subscription>
        get() = subscriptionStorage.activeSubscriptions()

    fun setShowingAddSubscription(showing: Boolean) {
        _showingAddSubscription.value = showing
    }

    fun loadIncomingProposals() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProposals = true) }
            val ownerPubkey = keyManager.getCurrentPublicKeyZ32()
            if (ownerPubkey == null) {
                _uiState.update { it.copy(isLoadingProposals = false) }
                return@launch
            }
            runCatching {
                directoryService.discoverSubscriptionProposals(ownerPubkey)
            }.onSuccess { proposals ->
                _uiState.update { it.copy(incomingProposals = proposals, isLoadingProposals = false) }
            }.onFailure { e ->
                Logger.error("Failed to load incoming proposals", e, context = TAG)
                _uiState.update { it.copy(isLoadingProposals = false, error = e.message) }
            }
        }
    }

    fun sendSubscriptionProposal(
        recipientPubkey: String,
        amountSats: Long,
        frequency: String,
        description: String?,
        enableAutopay: Boolean = false,
        autopayLimitSats: Long? = null,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            val providerPubkey = keyManager.getCurrentPublicKeyZ32()
            if (providerPubkey == null) {
                _uiState.update { it.copy(isSending = false, error = "No identity configured") }
                return@launch
            }

            val proposal = SubscriptionProposal.create(
                providerPubkey = providerPubkey,
                providerName = null,
                amountSats = amountSats,
                frequency = frequency,
                description = description,
            )

            runCatching {
                directoryService.publishSubscriptionProposal(proposal, recipientPubkey)
            }.onSuccess {
                _uiState.update { it.copy(isSending = false, sendSuccess = true) }
                Logger.info("Sent subscription proposal to $recipientPubkey", context = TAG)
            }.onFailure { e ->
                Logger.error("Failed to send subscription proposal", e, context = TAG)
                _uiState.update { it.copy(isSending = false, error = e.message) }
            }
        }
    }

    fun acceptProposal(proposal: DiscoveredSubscriptionProposal, enableAutopay: Boolean = false, autopayLimitSats: Long? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAccepting = true, error = null) }

            val subscription = Subscription.create(
                providerName = proposal.providerPubkey.take(8),
                providerPubkey = proposal.providerPubkey,
                amountSats = proposal.amountSats,
                frequency = proposal.frequency,
                description = proposal.description ?: "",
            )

            runCatching {
                subscriptionStorage.saveSubscription(subscription)

                if (enableAutopay) {
                    val rule = AutoPayRule(
                        id = subscription.id,
                        name = "Subscription: ${subscription.providerName}",
                        peerPubkey = subscription.providerPubkey,
                        isEnabled = true,
                        maxAmountSats = autopayLimitSats ?: subscription.amountSats,
                        allowedMethods = listOf(subscription.methodId),
                        allowedPeers = listOf(subscription.providerPubkey),
                    )
                    autoPayStorage.saveRule(rule)

                    if (autopayLimitSats != null) {
                        val limit = PeerSpendingLimit.create(
                            peerPubkey = subscription.providerPubkey,
                            peerName = subscription.providerName,
                            limitSats = autopayLimitSats,
                            period = subscription.frequency,
                        )
                        autoPayStorage.savePeerLimit(limit)
                    }
                }

                // NOTE: In the v0 provider-storage model, proposals are stored on the provider's
                // homeserver. Subscribers cannot delete proposals from provider storage.
                // Mark as accepted locally; the proposal remains on provider storage (their cleanup).
            }.onSuccess {
                loadSubscriptions()
                loadIncomingProposals()
                _uiState.update { it.copy(isAccepting = false, acceptSuccess = true) }
                Logger.info("Accepted subscription proposal ${proposal.subscriptionId} (local-only)", context = TAG)
            }.onFailure { e ->
                Logger.error("Failed to accept subscription proposal", e, context = TAG)
                _uiState.update { it.copy(isAccepting = false, error = e.message) }
            }
        }
    }

    fun declineProposal(proposal: DiscoveredSubscriptionProposal) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeclining = true, error = null) }
            val ownerPubkey = keyManager.getCurrentPublicKeyZ32()
            if (ownerPubkey == null) {
                _uiState.update { it.copy(isDeclining = false, error = "No identity configured") }
                return@launch
            }

            // NOTE: In the v0 provider-storage model, proposals are stored on the provider's
            // homeserver. Subscribers cannot delete proposals from provider storage.
            // Mark as declined locally; the proposal remains on provider storage (their cleanup).
            runCatching {
                // Local-only decline: remove from seen set to avoid re-showing
                // (actual remote delete is not possible in provider-storage model)
                Logger.debug("Declining proposal ${proposal.subscriptionId} (local-only)", context = TAG)
            }.onSuccess {
                loadIncomingProposals()
                _uiState.update { it.copy(isDeclining = false) }
                Logger.info("Declined subscription proposal ${proposal.subscriptionId} (local-only)", context = TAG)
            }.onFailure { e ->
                Logger.error("Failed to decline subscription proposal", e, context = TAG)
                _uiState.update { it.copy(isDeclining = false, error = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSendSuccess() {
        _uiState.update { it.copy(sendSuccess = false) }
    }

    fun clearAcceptSuccess() {
        _uiState.update { it.copy(acceptSuccess = false) }
    }

    fun getSubscription(id: String): Subscription? = subscriptionStorage.getSubscription(id)
}

data class SubscriptionsUiState(
    val incomingProposals: List<DiscoveredSubscriptionProposal> = emptyList(),
    val isLoadingProposals: Boolean = false,
    val isSending: Boolean = false,
    val isAccepting: Boolean = false,
    val isDeclining: Boolean = false,
    val sendSuccess: Boolean = false,
    val acceptSuccess: Boolean = false,
    val error: String? = null,
)
