package to.bitkit.paykit.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import to.bitkit.paykit.KeyManager
import to.bitkit.paykit.models.PaymentRequest
import to.bitkit.paykit.models.PaymentRequestStatus
import to.bitkit.paykit.models.RequestDirection
import to.bitkit.paykit.services.DirectoryService
import to.bitkit.paykit.services.PubkyRingBridge
import to.bitkit.paykit.storage.PaymentRequestStorage
import to.bitkit.paykit.storage.SentPaymentRequest
import to.bitkit.utils.Logger
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PaymentRequestsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val paymentRequestStorage: PaymentRequestStorage,
    private val directoryService: DirectoryService,
    private val keyManager: KeyManager,
    private val pubkyRingBridge: PubkyRingBridge,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentRequestsUiState())
    val uiState: StateFlow<PaymentRequestsUiState> = _uiState.asStateFlow()

    init {
        loadRequests()
        loadSentRequests()
    }

    fun loadRequests() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                val requests = paymentRequestStorage.listRequests()
                val incoming = requests.filter { it.direction == RequestDirection.INCOMING }
                val outgoing = requests.filter { it.direction == RequestDirection.OUTGOING }
                _uiState.update {
                    it.copy(
                        requests = requests,
                        incomingRequests = incoming,
                        outgoingRequests = outgoing,
                        isLoading = false,
                    )
                }
            }.onFailure { e ->
                Logger.error("Failed to load payment requests", e, context = TAG)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadSentRequests() {
        viewModelScope.launch {
            val sentRequests = paymentRequestStorage.listSentRequests()
            _uiState.update { it.copy(sentRequests = sentRequests) }
        }
    }

    /**
     * Manually discover payment requests from followed peers.
     * This polls each followed peer's homeserver for requests addressed to us.
     */
    fun discoverRequests() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDiscovering = true, discoveryResult = null) }

            val ownerPubkey = keyManager.getCurrentPublicKeyZ32()
            if (ownerPubkey == null) {
                _uiState.update {
                    it.copy(isDiscovering = false, discoveryResult = "No identity configured")
                }
                return@launch
            }

            runCatching {
                // Fetch follows from network (not local contacts)
                val follows = directoryService.fetchFollows(context)
                Logger.info("discoverRequests: Found ${follows.size} follows to poll", context = TAG)

                var discovered = 0
                for (peerPubkey in follows) {
                    runCatching {
                        val discoveredRequests = directoryService.discoverPendingRequestsFromPeer(
                            peerPubkey,
                            ownerPubkey,
                        )
                        Logger.info(
                            "Found ${discoveredRequests.size} requests from ${peerPubkey.take(12)}...",
                            context = TAG,
                        )

                        for (discoveredRequest in discoveredRequests) {
                            // Check if we already have this request
                            if (paymentRequestStorage.getRequest(discoveredRequest.requestId) == null) {
                                // Convert DiscoveredRequest to PaymentRequest
                                val request = PaymentRequest(
                                    id = discoveredRequest.requestId,
                                    fromPubkey = discoveredRequest.fromPubkey,
                                    toPubkey = ownerPubkey,
                                    amountSats = discoveredRequest.amountSats,
                                    currency = "SAT",
                                    methodId = "lightning",
                                    description = discoveredRequest.description ?: "",
                                    createdAt = discoveredRequest.createdAt,
                                    expiresAt = null,
                                    status = PaymentRequestStatus.PENDING,
                                    direction = RequestDirection.INCOMING,
                                )
                                paymentRequestStorage.addRequest(request)
                                discovered++
                            }
                        }
                    }.onFailure { e ->
                        Logger.debug(
                            "Failed to discover requests from ${peerPubkey.take(12)}: ${e.message}",
                            context = TAG,
                        )
                    }
                }

                // Check for key sync issues if nothing found
                val result = if (discovered > 0) {
                    "Found $discovered new request(s)"
                } else {
                    checkKeySyncIssue(ownerPubkey, follows.size)
                }

                loadRequests()
                _uiState.update { it.copy(isDiscovering = false, discoveryResult = result) }
            }.onFailure { e ->
                Logger.error("discoverRequests failed", e, context = TAG)
                _uiState.update {
                    it.copy(isDiscovering = false, discoveryResult = "Error: ${e.message}")
                }
            }
        }
    }

    /**
     * Check for Noise key sync issues.
     * Returns a descriptive message for the UI.
     */
    private suspend fun checkKeySyncIssue(ownerPubkey: String, followsCount: Int): String {
        // Check if we have a noise key
        val noiseKeypair = runCatching {
            pubkyRingBridge.requestNoiseKeypair(context, epoch = 0uL)
        }.getOrNull()

        if (noiseKeypair == null) {
            return "⚠️ No Noise key!\nReconnect to Pubky Ring"
        }

        // Check if local key matches published endpoint
        val publishedEndpoint = runCatching {
            directoryService.discoverNoiseEndpoint(ownerPubkey)
        }.getOrNull()

        if (publishedEndpoint != null && publishedEndpoint.serverNoisePubkey != noiseKeypair.publicKey) {
            Logger.error("Key sync issue: local key doesn't match published endpoint", context = TAG)
            return "⚠️ Key sync issue!\nReconnect to Pubky Ring to fix"
        }

        return "0 requests found.\n$followsCount follows checked"
    }

    fun clearDiscoveryResult() {
        _uiState.update { it.copy(discoveryResult = null) }
    }

    fun selectTab(tab: RequestTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun acceptRequest(request: PaymentRequest) {
        viewModelScope.launch {
            runCatching {
                paymentRequestStorage.updateStatus(request.id, PaymentRequestStatus.ACCEPTED)
                loadRequests()
            }.onFailure { e ->
                Logger.error("Failed to accept request", e, context = TAG)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun declineRequest(request: PaymentRequest) {
        viewModelScope.launch {
            runCatching {
                paymentRequestStorage.updateStatus(request.id, PaymentRequestStatus.DECLINED)
                loadRequests()
            }.onFailure { e ->
                Logger.error("Failed to decline request", e, context = TAG)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteRequest(request: PaymentRequest) {
        viewModelScope.launch {
            runCatching {
                paymentRequestStorage.deleteRequest(request.id)
                loadRequests()
            }.onFailure { e ->
                Logger.error("Failed to delete request", e, context = TAG)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun sendPaymentRequest(
        recipientPubkey: String,
        amountSats: Long,
        methodId: String,
        description: String,
        expiresInDays: Int = 7,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            val senderPubkey = keyManager.getCurrentPublicKeyZ32()
            if (senderPubkey == null) {
                _uiState.update { it.copy(isSending = false, error = "No identity configured") }
                return@launch
            }

            val requestId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val expiresAt = now + (expiresInDays * 24 * 60 * 60 * 1000L)

            val request = PaymentRequest(
                id = requestId,
                fromPubkey = senderPubkey,
                toPubkey = recipientPubkey,
                amountSats = amountSats,
                currency = "SAT",
                methodId = methodId,
                description = description,
                createdAt = now,
                expiresAt = expiresAt,
                status = PaymentRequestStatus.PENDING,
                direction = RequestDirection.OUTGOING,
            )

            runCatching {
                // Publish encrypted request to homeserver
                directoryService.publishPaymentRequest(request, recipientPubkey)

                // Save to local storage for display
                paymentRequestStorage.addRequest(request)

                // Track sent request for cleanup purposes
                paymentRequestStorage.saveSentRequest(
                    requestId = requestId,
                    recipientPubkey = recipientPubkey,
                    amountSats = amountSats,
                    methodId = methodId,
                    description = description,
                )

                loadRequests()
                loadSentRequests()
            }.onSuccess {
                Logger.info("Sent payment request $requestId to $recipientPubkey", context = TAG)
                _uiState.update { it.copy(isSending = false, sendSuccess = true) }
            }.onFailure { e ->
                Logger.error("Failed to send payment request", e, context = TAG)
                _uiState.update { it.copy(isSending = false, error = e.message) }
            }
        }
    }

    fun cancelSentRequest(request: SentPaymentRequest) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingSentRequest = true, deleteSentRequestError = null) }
            runCatching {
                // Delete from homeserver
                directoryService.deletePaymentRequest(request.id, request.recipientPubkey)
                // Delete from local tracking
                paymentRequestStorage.deleteSentRequest(request.id)
                // Also remove from main requests list if exists
                paymentRequestStorage.deleteRequest(request.id)
            }.onSuccess {
                Logger.info("Cancelled sent request: ${request.id}", context = TAG)
                loadRequests()
                loadSentRequests()
                _uiState.update { it.copy(isDeletingSentRequest = false) }
            }.onFailure { e ->
                Logger.error("Failed to cancel sent request", e, context = TAG)
                _uiState.update { it.copy(isDeletingSentRequest = false, deleteSentRequestError = e.message) }
            }
        }
    }

    fun cleanupOrphanedRequests() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCleaningUp = true, cleanupResult = null) }

            runCatching {
                val trackedIdsByRecipient = paymentRequestStorage.getSentRequestsByRecipient()

                var totalDeleted = 0
                for ((recipientPubkey, trackedIds) in trackedIdsByRecipient) {
                    val homeserverIds = directoryService.listRequestsOnHomeserver(recipientPubkey)
                    val orphanedIds = homeserverIds.filter { it !in trackedIds }
                    if (orphanedIds.isNotEmpty()) {
                        totalDeleted += directoryService.deleteRequestsBatch(orphanedIds, recipientPubkey)
                    }
                }
                totalDeleted
            }.onSuccess { count ->
                val message = if (count > 0) "Cleaned up $count orphaned requests" else "No orphaned requests found"
                Logger.info(message, context = TAG)
                _uiState.update { it.copy(isCleaningUp = false, cleanupResult = message) }
            }.onFailure { e ->
                Logger.error("Failed to cleanup orphaned requests", e, context = TAG)
                _uiState.update { it.copy(isCleaningUp = false, cleanupResult = "Failed: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSendSuccess() {
        _uiState.update { it.copy(sendSuccess = false) }
    }

    fun clearCleanupResult() {
        _uiState.update { it.copy(cleanupResult = null) }
    }

    companion object {
        private const val TAG = "PaymentRequestsViewModel"
    }
}

enum class RequestTab {
    INCOMING,
    SENT,
}

data class PaymentRequestsUiState(
    val requests: List<PaymentRequest> = emptyList(),
    val incomingRequests: List<PaymentRequest> = emptyList(),
    val outgoingRequests: List<PaymentRequest> = emptyList(),
    val sentRequests: List<SentPaymentRequest> = emptyList(),
    val selectedTab: RequestTab = RequestTab.INCOMING,
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isDiscovering: Boolean = false,
    val isDeletingSentRequest: Boolean = false,
    val isCleaningUp: Boolean = false,
    val sendSuccess: Boolean = false,
    val error: String? = null,
    val deleteSentRequestError: String? = null,
    val cleanupResult: String? = null,
    val discoveryResult: String? = null,
)
