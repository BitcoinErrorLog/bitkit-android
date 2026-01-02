package to.bitkit.paykit.services

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import to.bitkit.utils.Logger
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent receipt store using EncryptedSharedPreferences.
 *
 * Provides thread-safe storage and retrieval of payment receipts.
 * Receipts are automatically persisted to disk and survive app restarts.
 * Uses encryption to protect sensitive payment data.
 *
 * ## Usage
 *
 * Prefer dependency injection:
 * ```kotlin
 * @Inject constructor(private val receiptStore: PaykitReceiptStore)
 * ```
 *
 * Legacy `getInstance()` is deprecated and will be removed in a future release.
 */
@Singleton
class PaykitReceiptStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    companion object {
        private const val TAG = "PaykitReceiptStore"
        private const val PREFS_NAME = "paykit_receipts"
        private const val RECEIPTS_KEY = "receipts"
        private const val MAX_RECEIPTS = 1000 // Prevent unbounded growth
    }

    private val cache = ConcurrentHashMap<String, PaykitReceipt>()
    private val mutex = Mutex()
    private var prefs: SharedPreferences? = null
    private val gson: Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        .create()
    private var isLoaded = false

    init {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            loadFromDisk()
        } catch (e: Exception) {
            Logger.error("Failed to initialize encrypted prefs, falling back to memory-only", e, context = TAG)
        }
    }

    /**
     * Store a receipt (persisted to disk).
     */
    suspend fun store(receipt: PaykitReceipt) = mutex.withLock {
        cache[receipt.id] = receipt
        saveToDisk()
    }

    /**
     * Store a receipt synchronously (for non-coroutine contexts).
     */
    fun storeSync(receipt: PaykitReceipt) {
        cache[receipt.id] = receipt
        saveToDiskSync()
    }

    /**
     * Get receipt by ID.
     */
    fun get(id: String): PaykitReceipt? = cache[id]

    /**
     * Get all receipts, sorted by timestamp (newest first).
     */
    fun getAll(): List<PaykitReceipt> = cache.values.sortedByDescending { it.timestamp }

    /**
     * Get receipts filtered by type.
     */
    fun getByType(type: PaykitReceiptType): List<PaykitReceipt> =
        cache.values.filter { it.type == type }.sortedByDescending { it.timestamp }

    /**
     * Get receipts filtered by status.
     */
    fun getByStatus(status: PaykitReceiptStatus): List<PaykitReceipt> =
        cache.values.filter { it.status == status }.sortedByDescending { it.timestamp }

    /**
     * Update receipt status.
     */
    suspend fun updateStatus(id: String, status: PaykitReceiptStatus) = mutex.withLock {
        cache[id]?.let { receipt ->
            val updated = receipt.copy(status = status)
            cache[id] = updated
            saveToDisk()
        }
    }

    /**
     * Delete a receipt.
     */
    suspend fun delete(id: String) = mutex.withLock {
        cache.remove(id)
        saveToDisk()
    }

    /**
     * Clear all receipts.
     */
    suspend fun clear() = mutex.withLock {
        cache.clear()
        prefs?.edit()?.remove(RECEIPTS_KEY)?.apply()
    }

    /**
     * Get receipt count.
     */
    val count: Int get() = cache.size

    // MARK: - Persistence

    private fun loadFromDisk() {
        if (isLoaded) return

        try {
            val json = prefs?.getString(RECEIPTS_KEY, null)
            if (json != null) {
                val type = object : TypeToken<List<PaykitReceiptDto>>() {}.type
                val dtos: List<PaykitReceiptDto> = gson.fromJson(json, type)
                dtos.forEach { dto ->
                    cache[dto.id] = dto.toReceipt()
                }
                Logger.debug("Loaded ${dtos.size} receipts from disk", context = TAG)
            }
        } catch (e: Exception) {
            Logger.error("Failed to load receipts", e, context = TAG)
        }

        isLoaded = true
    }

    private fun saveToDisk() {
        saveToDiskSync()
    }

    private fun saveToDiskSync() {
        try {
            // Trim old receipts if we exceed max
            val receiptsToSave = if (cache.size > MAX_RECEIPTS) {
                cache.values.sortedByDescending { it.timestamp }.take(MAX_RECEIPTS)
            } else {
                cache.values.toList()
            }

            // Update cache if we trimmed
            if (receiptsToSave.size < cache.size) {
                cache.clear()
                receiptsToSave.forEach { cache[it.id] = it }
            }

            val dtos = receiptsToSave.map { PaykitReceiptDto.fromReceipt(it) }
            val json = gson.toJson(dtos)
            prefs?.edit()?.putString(RECEIPTS_KEY, json)?.apply()
            Logger.debug("Saved ${dtos.size} receipts to disk", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to save receipts", e, context = TAG)
        }
    }
}

/**
 * DTO for serializing receipts to JSON.
 */
private data class PaykitReceiptDto(
    val id: String,
    val type: String,
    val recipient: String,
    val amountSats: Long,
    val feeSats: Long,
    val paymentHash: String?,
    val preimage: String?,
    val txid: String?,
    val timestamp: Long,
    val status: String,
) {
    fun toReceipt(): PaykitReceipt = PaykitReceipt(
        id = id,
        type = PaykitReceiptType.valueOf(type),
        recipient = recipient,
        amountSats = amountSats.toULong(),
        feeSats = feeSats.toULong(),
        paymentHash = paymentHash,
        preimage = preimage,
        txid = txid,
        timestamp = Date(timestamp),
        status = PaykitReceiptStatus.valueOf(status),
    )

    companion object {
        fun fromReceipt(receipt: PaykitReceipt): PaykitReceiptDto = PaykitReceiptDto(
            id = receipt.id,
            type = receipt.type.name,
            recipient = receipt.recipient,
            amountSats = receipt.amountSats.toLong(),
            feeSats = receipt.feeSats.toLong(),
            paymentHash = receipt.paymentHash,
            preimage = receipt.preimage,
            txid = receipt.txid,
            timestamp = receipt.timestamp.time,
            status = receipt.status.name,
        )
    }
}
