package to.bitkit.paykit.services

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.bitkit.paykit.models.PaymentStatus
import to.bitkit.paykit.models.Receipt
import to.bitkit.paykit.storage.PaykitKeychainStorage
import to.bitkit.paykit.storage.PaykitStorageException
import to.bitkit.paykit.storage.PaymentRequestStorage
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service to manage receipts and their association with payment requests.
 *
 * This service provides:
 * - Receipt storage and retrieval
 * - Cross-referencing between receipts and payment requests
 * - Invoice number lookups
 */
@Singleton
class ReceiptService @Inject constructor(
    private val keychain: PaykitKeychainStorage,
    private val paymentRequestStorage: PaymentRequestStorage
) {
    companion object {
        private const val TAG = "ReceiptService"
        private const val RECEIPTS_KEY = "paykit.receipts"
        private const val MAX_RECEIPTS_TO_KEEP = 500
    }

    private var receiptsCache: List<Receipt>? = null

    // MARK: - Receipt CRUD Operations

    /**
     * Get all receipts (newest first)
     */
    fun listReceipts(): List<Receipt> {
        if (receiptsCache != null) {
            return receiptsCache!!
        }

        return try {
            val data = keychain.retrieve(RECEIPTS_KEY) ?: return emptyList()
            val json = String(data)
            val receipts = Json.decodeFromString<List<Receipt>>(json)
                .sortedByDescending { it.createdAt }
            receiptsCache = receipts
            receipts
        } catch (e: Exception) {
            Logger.error("ReceiptService: Failed to load receipts", e, context = TAG)
            emptyList()
        }
    }

    /**
     * Get a specific receipt by ID
     */
    fun getReceipt(id: String): Receipt? {
        return listReceipts().firstOrNull { it.id == id }
    }

    /**
     * Get receipts for a specific request ID
     */
    fun getReceiptsForRequest(requestId: String): List<Receipt> {
        return listReceipts().filter { it.requestId == requestId }
    }

    /**
     * Get receipts by invoice number
     */
    fun getReceiptsByInvoiceNumber(invoiceNumber: String): List<Receipt> {
        return listReceipts().filter { it.invoiceNumber == invoiceNumber }
    }

    /**
     * Get the request associated with a receipt
     */
    fun getRequestForReceipt(receiptId: String): to.bitkit.paykit.models.PaymentRequest? {
        val receipt = getReceipt(receiptId) ?: return null

        // Try by requestId first
        receipt.requestId?.let { requestId ->
            paymentRequestStorage.getRequest(requestId)?.let { return it }
        }

        // Fall back to invoice number lookup
        receipt.invoiceNumber?.let { invoiceNumber ->
            paymentRequestStorage.getRequestByInvoiceNumber(invoiceNumber)?.let { return it }
        }

        return null
    }

    /**
     * Add a new receipt
     */
    suspend fun addReceipt(receipt: Receipt) {
        val receipts = listReceipts().toMutableList()
        receipts.add(0, receipt)

        // Trim to max size
        val trimmed = if (receipts.size > MAX_RECEIPTS_TO_KEEP) {
            receipts.take(MAX_RECEIPTS_TO_KEEP)
        } else {
            receipts
        }

        persistReceipts(trimmed)
        Logger.info("ReceiptService: Added receipt ${receipt.id}", context = TAG)
    }

    /**
     * Update an existing receipt
     */
    suspend fun updateReceipt(receipt: Receipt) {
        val receipts = listReceipts().toMutableList()
        val index = receipts.indexOfFirst { it.id == receipt.id }

        if (index < 0) {
            throw PaykitStorageException.LoadFailed(receipt.id)
        }

        receipts[index] = receipt
        persistReceipts(receipts)
    }

    /**
     * Create a receipt and link it to a payment request
     */
    suspend fun createReceiptForRequest(
        requestId: String,
        receipt: Receipt
    ): Receipt {
        // Create receipt with request linkage
        val linkedReceipt = receipt.copy(
            requestId = requestId,
            invoiceNumber = paymentRequestStorage.getRequest(requestId)?.invoiceNumber
        )

        // Add the receipt
        addReceipt(linkedReceipt)

        // Update the request to link to this receipt
        paymentRequestStorage.fulfillRequest(requestId, linkedReceipt.id)

        Logger.info("ReceiptService: Created receipt ${linkedReceipt.id} for request $requestId", context = TAG)
        return linkedReceipt
    }

    /**
     * Create a receipt and link it to a payment request by invoice number
     */
    suspend fun createReceiptForInvoice(
        invoiceNumber: String,
        receipt: Receipt
    ): Receipt {
        val request = paymentRequestStorage.getRequestByInvoiceNumber(invoiceNumber)

        // Create receipt with invoice number linkage
        val linkedReceipt = receipt.copy(
            requestId = request?.id,
            invoiceNumber = invoiceNumber
        )

        // Add the receipt
        addReceipt(linkedReceipt)

        // Update the request if found
        request?.let {
            paymentRequestStorage.fulfillRequest(it.id, linkedReceipt.id)
        }

        Logger.info("ReceiptService: Created receipt ${linkedReceipt.id} for invoice $invoiceNumber", context = TAG)
        return linkedReceipt
    }

    /**
     * Link an existing receipt to a request
     */
    suspend fun linkReceiptToRequest(receiptId: String, requestId: String) {
        val receipt = getReceipt(receiptId)
            ?: throw PaykitStorageException.LoadFailed(receiptId)
        val request = paymentRequestStorage.getRequest(requestId)
            ?: throw PaykitStorageException.LoadFailed(requestId)

        // Update receipt with request linkage
        val linkedReceipt = receipt.copy(
            requestId = requestId,
            invoiceNumber = request.invoiceNumber
        )
        updateReceipt(linkedReceipt)

        // Update request with receipt linkage
        paymentRequestStorage.fulfillRequest(requestId, receiptId)

        Logger.info("ReceiptService: Linked receipt $receiptId to request $requestId", context = TAG)
    }

    /**
     * Mark a receipt as completed
     */
    suspend fun completeReceipt(receiptId: String, txId: String? = null) {
        val receipt = getReceipt(receiptId)
            ?: throw PaykitStorageException.LoadFailed(receiptId)

        val completedReceipt = receipt.complete(txId)
        updateReceipt(completedReceipt)
        Logger.info("ReceiptService: Completed receipt $receiptId", context = TAG)
    }

    /**
     * Mark a receipt as failed
     */
    suspend fun failReceipt(receiptId: String) {
        val receipt = getReceipt(receiptId)
            ?: throw PaykitStorageException.LoadFailed(receiptId)

        val failedReceipt = receipt.fail()
        updateReceipt(failedReceipt)
        Logger.info("ReceiptService: Failed receipt $receiptId", context = TAG)
    }

    /**
     * Clear all receipts
     */
    suspend fun clearAll() {
        persistReceipts(emptyList())
    }

    // MARK: - Statistics

    /**
     * Count of completed receipts
     */
    fun completedCount(): Int {
        return listReceipts().filter { it.status == PaymentStatus.COMPLETED }.size
    }

    /**
     * Count of pending receipts
     */
    fun pendingCount(): Int {
        return listReceipts().filter { it.status == PaymentStatus.PENDING }.size
    }

    // MARK: - Private

    private suspend fun persistReceipts(receipts: List<Receipt>) {
        try {
            val json = Json.encodeToString(receipts)
            keychain.store(RECEIPTS_KEY, json.toByteArray())
            receiptsCache = receipts
        } catch (e: Exception) {
            Logger.error("ReceiptService: Failed to persist receipts", e, context = TAG)
            throw PaykitStorageException.SaveFailed(RECEIPTS_KEY)
        }
    }
}
