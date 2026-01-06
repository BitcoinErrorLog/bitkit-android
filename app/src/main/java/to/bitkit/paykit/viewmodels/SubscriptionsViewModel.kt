package to.bitkit.paykit.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import to.bitkit.paykit.services.PubkyRingBridge
import to.bitkit.paykit.storage.AutoPayStorage
import to.bitkit.paykit.storage.SentProposal
import to.bitkit.paykit.storage.SubscriptionProposalStorage
import to.bitkit.paykit.storage.SubscriptionStorage
import to.bitkit.paykit.workers.DiscoveredSubscriptionProposal
import to.bitkit.utils.Logger
import javax.inject.Inject

/**
 * ViewModel for Subscriptions management
 */
@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val subscriptionStorage: SubscriptionStorage,
    private val proposalStorage: SubscriptionProposalStorage,
    private val directoryService: DirectoryService,
    private val autoPayStorage: AutoPayStorage,
    private val keyManager: KeyManager,
    private val pubkyRingBridge: PubkyRingBridge,
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
        // Ensure any cached session's pubkey is synced to KeyManager
        viewModelScope.launch {
            pubkyRingBridge.ensureIdentitySynced()
        }
        loadSubscriptions()
        loadIncomingProposals()
        loadSentProposals()
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
                Logger.info("loadIncomingProposals: No owner pubkey, skipping", context = TAG)
                _uiState.update { it.copy(isLoadingProposals = false) }
                return@launch
            }
            // Invalidate cache to ensure fresh data
            proposalStorage.invalidateCache()
            Logger.info("loadIncomingProposals: Starting discovery for $ownerPubkey", context = TAG)
            runCatching {
                // Discover proposals from peers (contacts/follows)
                val discoveredProposals = mutableListOf<DiscoveredSubscriptionProposal>()
                val peers = directoryService.fetchFollows()
                Logger.info("loadIncomingProposals: Found ${peers.size} peers to poll", context = TAG)
                for (peerPubkey in peers) {
                    val proposals = directoryService.discoverSubscriptionProposalsFromPeer(peerPubkey, ownerPubkey)
                    Logger.info(
                        "loadIncomingProposals: Found ${proposals.size} proposals from ${peerPubkey.take(12)}...",
                        context = TAG
                    )
                    discoveredProposals.addAll(proposals)
                }

                // Persist newly discovered proposals
                for (proposal in discoveredProposals) {
                    proposalStorage.saveProposal(ownerPubkey, proposal)
                }

                // Load all pending proposals (includes newly discovered + previously stored)
                val allProposals = proposalStorage.pendingProposals(ownerPubkey).map { stored ->
                    DiscoveredSubscriptionProposal(
                        subscriptionId = stored.id,
                        providerPubkey = stored.providerPubkey,
                        amountSats = stored.amountSats,
                        description = stored.description,
                        frequency = stored.frequency,
                        createdAt = stored.createdAt,
                    )
                }
                Logger.info("loadIncomingProposals: Total ${allProposals.size} pending proposals", context = TAG)
                allProposals
            }.onSuccess { proposals ->
                _uiState.update { it.copy(incomingProposals = proposals, isLoadingProposals = false) }
            }.onFailure { e ->
                Logger.error("Failed to load incoming proposals", e, context = TAG)
                _uiState.update { it.copy(isLoadingProposals = false, error = e.message) }
            }
        }
    }

    fun loadSentProposals() {
        viewModelScope.launch {
            val ownerPubkey = keyManager.getCurrentPublicKeyZ32() ?: return@launch
            val sentProposals = proposalStorage.listSentProposals(ownerPubkey)
            _uiState.update { it.copy(sentProposals = sentProposals) }
        }
    }

    /**
     * Cancel a sent proposal (delete from homeserver and local storage).
     */
    fun cancelSentProposal(proposal: SentProposal) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingSentProposal = true, deleteSentProposalError = null) }
            val ownerPubkey = keyManager.getCurrentPublicKeyZ32()
            if (ownerPubkey == null) {
                _uiState.update { it.copy(isDeletingSentProposal = false, deleteSentProposalError = "No identity") }
                return@launch
            }

            runCatching {
                // Delete from homeserver
                directoryService.deleteSubscriptionProposal(proposal.id, proposal.recipientPubkey)
                // Delete from local storage
                proposalStorage.deleteSentProposal(ownerPubkey, proposal.id)
            }.onSuccess {
                Logger.info("Cancelled sent proposal: ${proposal.id}", context = TAG)
                loadSentProposals()
                _uiState.update { it.copy(isDeletingSentProposal = false) }
            }.onFailure { e ->
                Logger.error("Failed to cancel sent proposal", e, context = TAG)
                _uiState.update { it.copy(isDeletingSentProposal = false, deleteSentProposalError = e.message) }
            }
        }
    }

    /**
     * Dismiss a received proposal locally (remove from pending list).
     */
    fun dismissProposal(proposal: DiscoveredSubscriptionProposal) {
        viewModelScope.launch {
            val ownerPubkey = keyManager.getCurrentPublicKeyZ32() ?: return@launch
            proposalStorage.markAsSeen(ownerPubkey, proposal.subscriptionId)
            loadIncomingProposals()
        }
    }

    /**
     * Clean up orphaned proposals from the homeserver.
     *
     * Finds proposals on the homeserver that aren't tracked locally (from previous
     * sessions or failed deletions) and removes them.
     */
    fun cleanupOrphanedProposals() {
        viewModelScope.launch {
            val ownerPubkey = keyManager.getCurrentPublicKeyZ32()
            if (ownerPubkey == null) {
                _uiState.update { it.copy(cleanupResult = "No identity configured") }
                return@launch
            }

            _uiState.update { it.copy(isCleaningUp = true, cleanupResult = null) }

            runCatching {
                val sentProposals = proposalStorage.listSentProposals(ownerPubkey)
                val trackedIds = sentProposals.map { it.id }.toSet()
                val recipientPubkeys = sentProposals.map { it.recipientPubkey }.toSet()

                var totalDeleted = 0
                for (recipientPubkey in recipientPubkeys) {
                    val homeserverIds = directoryService.listProposalsOnHomeserver(recipientPubkey)
                    val orphanedIds = homeserverIds.filter { it !in trackedIds }
                    if (orphanedIds.isNotEmpty()) {
                        totalDeleted += directoryService.deleteProposalsBatch(orphanedIds, recipientPubkey)
                    }
                }
                totalDeleted
            }.onSuccess { count ->
                val message = if (count > 0) "Cleaned up $count orphaned proposals" else "No orphaned proposals found"
                Logger.info(message, context = TAG)
                _uiState.update { it.copy(isCleaningUp = false, cleanupResult = message) }
            }.onFailure { e ->
                Logger.error("Failed to cleanup orphaned proposals", e, context = TAG)
                _uiState.update { it.copy(isCleaningUp = false, cleanupResult = "Failed: ${e.message}") }
            }
        }
    }

    fun clearCleanupResult() {
        _uiState.update { it.copy(cleanupResult = null) }
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
                // Save sent proposal locally for tracking
                proposalStorage.saveSentProposal(
                    identityPubkey = providerPubkey,
                    proposalId = proposal.id,
                    recipientPubkey = recipientPubkey,
                    amountSats = amountSats,
                    frequency = frequency,
                    description = description,
                )
                loadSentProposals()
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

            val ownerPubkey = keyManager.getCurrentPublicKeyZ32()
            if (ownerPubkey == null) {
                _uiState.update { it.copy(isAccepting = false, error = "No identity configured") }
                return@launch
            }

            val subscription = Subscription.create(
                providerName = proposal.providerPubkey.take(8),
                providerPubkey = proposal.providerPubkey,
                amountSats = proposal.amountSats,
                frequency = proposal.frequency,
                description = proposal.description ?: "",
            )

            runCatching {
                subscriptionStorage.saveSubscription(subscription)

                // Mark proposal as accepted in proposal storage
                proposalStorage.markAccepted(ownerPubkey, proposal.subscriptionId)

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

            runCatching {
                // Mark as declined in proposal storage (local-only; no remote delete)
                proposalStorage.markDeclined(ownerPubkey, proposal.subscriptionId)
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
    val sentProposals: List<SentProposal> = emptyList(),
    val isLoadingProposals: Boolean = false,
    val isSending: Boolean = false,
    val isAccepting: Boolean = false,
    val isDeclining: Boolean = false,
    val isDeletingSentProposal: Boolean = false,
    val isCleaningUp: Boolean = false,
    val sendSuccess: Boolean = false,
    val acceptSuccess: Boolean = false,
    val error: String? = null,
    val deleteSentProposalError: String? = null,
    val cleanupResult: String? = null,
)
