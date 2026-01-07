package to.bitkit.paykit.storage

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.bitkit.paykit.KeyManager
import to.bitkit.paykit.models.PaymentRequest
import to.bitkit.paykit.models.PaymentRequestStatus
import to.bitkit.paykit.models.RequestDirection
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages persistent storage of payment requests using Keychain.
 *
 * Storage is scoped by the current identity pubkey.
 */
@Singleton
class PaymentRequestStorage @Inject constructor(
    private val keychain: PaykitKeychainStorage,
    private val keyManager: KeyManager,
) {
    companion object {
        private const val TAG = "PaymentRequestStorage"
        private const val MAX_REQUESTS_TO_KEEP = 200
    }

    private var requestsCache: MutableMap<String, List<PaymentRequest>> = mutableMapOf()

    private val currentIdentity: String
        get() = keyManager.getCurrentPublicKeyZ32() ?: "default"

    private val requestsKey: String
        get() = "payment_requests.$currentIdentity"

    /**
     * Get all requests (newest first)
     */
    fun listRequests(): List<PaymentRequest> {
        val identity = currentIdentity
        requestsCache[identity]?.let { return it }

        return try {
            val data = keychain.retrieve(requestsKey) ?: return emptyList()
            val json = String(data)
            val requests = Json.decodeFromString<List<PaymentRequest>>(json)
                .sortedByDescending { it.createdAt }
            requestsCache[identity] = requests
            requests
        } catch (e: Exception) {
            Logger.error("PaymentRequestStorage: Failed to load requests", e, context = TAG)
            emptyList()
        }
    }

    /**
     * Get pending requests only
     */
    fun pendingRequests(): List<PaymentRequest> {
        return listRequests().filter { it.status == PaymentRequestStatus.PENDING }
    }

    /**
     * Get requests filtered by status
     */
    fun listRequests(status: PaymentRequestStatus): List<PaymentRequest> {
        return listRequests().filter { it.status == status }
    }

    /**
     * Get requests filtered by direction
     */
    fun listRequests(direction: RequestDirection): List<PaymentRequest> {
        return listRequests().filter { it.direction == direction }
    }

    /**
     * Get recent requests (limited count)
     */
    fun recentRequests(limit: Int = 10): List<PaymentRequest> {
        return listRequests().take(limit)
    }

    /**
     * Get a specific request
     */
    fun getRequest(id: String): PaymentRequest? {
        return listRequests().firstOrNull { it.id == id }
    }

    /**
     * Get a request by invoice number
     */
    fun getRequestByInvoiceNumber(invoiceNumber: String): PaymentRequest? {
        return listRequests().firstOrNull { it.invoiceNumber == invoiceNumber }
    }

    /**
     * Get a request that was fulfilled by a specific receipt
     */
    fun getRequestByReceiptId(receiptId: String): PaymentRequest? {
        return listRequests().firstOrNull { it.receiptId == receiptId }
    }

    /**
     * Add a new request
     */
    suspend fun addRequest(request: PaymentRequest) {
        val requests = listRequests().toMutableList()

        // Add new request at the beginning (newest first)
        requests.add(0, request)

        // Trim to max size
        val trimmed = if (requests.size > MAX_REQUESTS_TO_KEEP) {
            requests.take(MAX_REQUESTS_TO_KEEP)
        } else {
            requests
        }

        persistRequests(trimmed)
    }

    /**
     * Update an existing request
     */
    suspend fun updateRequest(request: PaymentRequest) {
        val requests = listRequests().toMutableList()
        val index = requests.indexOfFirst { it.id == request.id }

        if (index < 0) {
            throw PaykitStorageException.LoadFailed(request.id)
        }

        requests[index] = request
        persistRequests(requests)
    }

    /**
     * Update request status
     */
    suspend fun updateStatus(id: String, status: PaymentRequestStatus) {
        val request = getRequest(id) ?: throw PaykitStorageException.LoadFailed(id)
        val updatedRequest = request.copy(status = status)
        updateRequest(updatedRequest)
    }

    /**
     * Fulfill a request with a receipt (marks as paid and links receipt)
     */
    suspend fun fulfillRequest(id: String, receiptId: String) {
        val request = getRequest(id) ?: throw PaykitStorageException.LoadFailed(id)
        val updatedRequest = request.copy(status = PaymentRequestStatus.PAID, receiptId = receiptId)
        updateRequest(updatedRequest)
        Logger.info("PaymentRequestStorage: Fulfilled request $id with receipt $receiptId", context = TAG)
    }

    /**
     * Fulfill a request by invoice number with a receipt
     */
    suspend fun fulfillRequestByInvoiceNumber(invoiceNumber: String, receiptId: String) {
        val request = getRequestByInvoiceNumber(invoiceNumber)
            ?: throw PaykitStorageException.LoadFailed("invoiceNumber:$invoiceNumber")
        val updatedRequest = request.copy(status = PaymentRequestStatus.PAID, receiptId = receiptId)
        updateRequest(updatedRequest)
        Logger.info(
            "PaymentRequestStorage: Fulfilled request by invoice $invoiceNumber with receipt $receiptId",
            context = TAG
        )
    }

    /**
     * Delete a request
     */
    suspend fun deleteRequest(id: String) {
        val requests = listRequests().toMutableList()
        requests.removeAll { it.id == id }
        persistRequests(requests)
    }

    /**
     * Check and mark expired requests
     */
    suspend fun checkExpirations() {
        val now = System.currentTimeMillis()
        val requests = listRequests().toMutableList()
        var hasChanges = false

        for (i in requests.indices) {
            if (requests[i].status == PaymentRequestStatus.PENDING) {
                requests[i].expiresAt?.let { expiresAt ->
                    if (expiresAt < now) {
                        requests[i] = requests[i].copy(status = PaymentRequestStatus.EXPIRED)
                        hasChanges = true
                    }
                }
            }
        }

        if (hasChanges) {
            persistRequests(requests)
        }
    }

    /**
     * Clear all requests
     */
    suspend fun clearAll() {
        val identity = currentIdentity
        persistRequests(emptyList())
        requestsCache.remove(identity)
    }

    // MARK: - Sent Request Tracking

    private val sentRequestsKey: String
        get() = "payment_requests.sent.$currentIdentity"

    private var sentRequestsCache: MutableMap<String, List<SentPaymentRequest>> = mutableMapOf()

    /**
     * List all sent payment requests for tracking.
     */
    fun listSentRequests(): List<SentPaymentRequest> {
        val identity = currentIdentity
        sentRequestsCache[identity]?.let { return it }

        return try {
            val data = keychain.retrieve(sentRequestsKey) ?: return emptyList()
            val json = String(data)
            val requests = Json.decodeFromString<List<SentPaymentRequest>>(json)
                .sortedByDescending { it.sentAt }
            sentRequestsCache[identity] = requests
            requests
        } catch (e: Exception) {
            Logger.error("PaymentRequestStorage: Failed to load sent requests", e, context = TAG)
            emptyList()
        }
    }

    /**
     * Save a sent payment request for tracking.
     */
    suspend fun saveSentRequest(
        requestId: String,
        recipientPubkey: String,
        amountSats: Long,
        methodId: String,
        description: String?,
    ) {
        val requests = listSentRequests().toMutableList()
        if (requests.any { it.id == requestId }) return

        requests.add(
            0,
            SentPaymentRequest(
                id = requestId,
                recipientPubkey = recipientPubkey,
                amountSats = amountSats,
                methodId = methodId,
                description = description,
                sentAt = System.currentTimeMillis(),
                status = SentRequestStatus.PENDING,
            ),
        )
        persistSentRequests(requests)
    }

    /**
     * Delete a sent request from tracking.
     */
    suspend fun deleteSentRequest(requestId: String) {
        val requests = listSentRequests().toMutableList()
        requests.removeAll { it.id == requestId }
        persistSentRequests(requests)
        Logger.debug("Deleted sent request tracking: $requestId", context = TAG)
    }

    /**
     * Mark a sent request as paid.
     */
    suspend fun markSentRequestPaid(requestId: String) {
        val requests = listSentRequests().toMutableList()
        val index = requests.indexOfFirst { it.id == requestId }
        if (index >= 0) {
            requests[index] = requests[index].copy(status = SentRequestStatus.PAID)
            persistSentRequests(requests)
        }
    }

    /**
     * Get all sent requests grouped by recipient.
     */
    fun getSentRequestsByRecipient(): Map<String, Set<String>> {
        return listSentRequests().groupBy(
            keySelector = { it.recipientPubkey },
            valueTransform = { it.id },
        ).mapValues { it.value.toSet() }
    }

    private suspend fun persistSentRequests(requests: List<SentPaymentRequest>) {
        val identity = currentIdentity
        try {
            val json = Json.encodeToString(requests)
            keychain.store(sentRequestsKey, json.toByteArray())
            sentRequestsCache[identity] = requests
        } catch (e: Exception) {
            Logger.error("PaymentRequestStorage: Failed to persist sent requests", e, context = TAG)
            throw PaykitStorageException.SaveFailed(sentRequestsKey)
        }
    }

    // MARK: - Statistics

    /**
     * Count of pending requests
     */
    fun pendingCount(): Int {
        return listRequests(PaymentRequestStatus.PENDING).size
    }

    /**
     * Count of incoming pending requests
     */
    fun incomingPendingCount(): Int {
        return listRequests(RequestDirection.INCOMING)
            .filter { it.status == PaymentRequestStatus.PENDING }
            .size
    }

    /**
     * Count of outgoing pending requests
     */
    fun outgoingPendingCount(): Int {
        return listRequests(RequestDirection.OUTGOING)
            .filter { it.status == PaymentRequestStatus.PENDING }
            .size
    }

    // MARK: - Private

    private suspend fun persistRequests(requests: List<PaymentRequest>) {
        val identity = currentIdentity
        try {
            val json = Json.encodeToString(requests)
            keychain.store(requestsKey, json.toByteArray())
            requestsCache[identity] = requests
        } catch (e: Exception) {
            Logger.error("PaymentRequestStorage: Failed to persist requests", e, context = TAG)
            throw PaykitStorageException.SaveFailed(requestsKey)
        }
    }
}

/**
 * A sent (outgoing) payment request for tracking.
 */
@kotlinx.serialization.Serializable
data class SentPaymentRequest(
    val id: String,
    val recipientPubkey: String,
    val amountSats: Long,
    val methodId: String,
    val description: String?,
    val sentAt: Long,
    val status: SentRequestStatus,
)

@kotlinx.serialization.Serializable
enum class SentRequestStatus {
    PENDING,
    PAID,
    EXPIRED,
}
