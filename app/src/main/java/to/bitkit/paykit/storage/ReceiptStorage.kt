package to.bitkit.paykit.storage

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.bitkit.paykit.models.PaymentDirection
import to.bitkit.paykit.models.PaymentStatus
import to.bitkit.paykit.models.Receipt
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages persistent storage of payment receipts
 */
@Singleton
class ReceiptStorage @Inject constructor(
    private val keychain: PaykitKeychainStorage
) {
    companion object {
        private const val TAG = "ReceiptStorage"
        private const val MAX_RECEIPTS_TO_KEEP = 500
    }

    private var receiptsCache: List<Receipt>? = null
    private val identityName: String = "default"

    private val receiptsKey: String
        get() = "receipts.$identityName"

    fun listReceipts(): List<Receipt> {
        if (receiptsCache != null) {
            return receiptsCache!!
        }

        return try {
            val data = keychain.retrieve(receiptsKey) ?: return emptyList()
            val json = String(data)
            val receipts = Json.decodeFromString<List<Receipt>>(json)
            val sorted = receipts.sortedByDescending { it.createdAt }
            receiptsCache = sorted
            sorted
        } catch (e: Exception) {
            Logger.error("ReceiptStorage: Failed to load receipts", e, context = TAG)
            emptyList()
        }
    }

    fun listReceipts(status: PaymentStatus): List<Receipt> {
        return listReceipts().filter { it.status == status }
    }

    fun listReceipts(direction: PaymentDirection): List<Receipt> {
        return listReceipts().filter { it.direction == direction }
    }

    fun recentReceipts(limit: Int = 10): List<Receipt> {
        return listReceipts().take(limit)
    }

    fun getReceipt(id: String): Receipt? {
        return listReceipts().firstOrNull { it.id == id }
    }

    suspend fun addReceipt(receipt: Receipt) {
        val receipts = listReceipts().toMutableList()
        receipts.add(0, receipt) // Add at beginning (newest first)

        // Trim to max size
        val trimmed = if (receipts.size > MAX_RECEIPTS_TO_KEEP) {
            receipts.take(MAX_RECEIPTS_TO_KEEP)
        } else {
            receipts
        }

        persistReceipts(trimmed)
    }

    suspend fun updateReceipt(receipt: Receipt) {
        val receipts = listReceipts().toMutableList()
        val index = receipts.indexOfFirst { it.id == receipt.id }
        if (index >= 0) {
            receipts[index] = receipt
            persistReceipts(receipts)
        }
    }

    suspend fun deleteReceipt(id: String) {
        val receipts = listReceipts().toMutableList()
        receipts.removeAll { it.id == id }
        persistReceipts(receipts)
    }

    fun searchReceipts(query: String): List<Receipt> {
        val lowerQuery = query.lowercase()
        return listReceipts().filter { receipt ->
            receipt.displayName.lowercase().contains(lowerQuery) ||
                receipt.counterpartyKey.lowercase().contains(lowerQuery) ||
                receipt.memo?.lowercase()?.contains(lowerQuery) == true
        }
    }

    fun receiptsForCounterparty(publicKey: String): List<Receipt> {
        return listReceipts().filter { it.counterpartyKey == publicKey }
    }

    suspend fun clearAll() {
        persistReceipts(emptyList())
    }

    fun totalSent(): Long {
        return listReceipts(PaymentDirection.SENT)
            .filter { it.status == PaymentStatus.COMPLETED }
            .sumOf { it.amountSats }
    }

    fun totalReceived(): Long {
        return listReceipts(PaymentDirection.RECEIVED)
            .filter { it.status == PaymentStatus.COMPLETED }
            .sumOf { it.amountSats }
    }

    fun completedCount(): Int {
        return listReceipts(PaymentStatus.COMPLETED).size
    }

    fun pendingCount(): Int {
        return listReceipts(PaymentStatus.PENDING).size
    }

    private suspend fun persistReceipts(receipts: List<Receipt>) {
        try {
            val json = Json.encodeToString(receipts)
            keychain.store(receiptsKey, json.toByteArray())
            receiptsCache = receipts
        } catch (e: Exception) {
            Logger.error("ReceiptStorage: Failed to persist receipts", e, context = TAG)
            throw PaykitStorageException.SaveFailed(receiptsKey)
        }
    }
}
