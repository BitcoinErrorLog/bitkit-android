package to.bitkit.paykit.storage

import to.bitkit.data.keychain.Keychain
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for storing Paykit-specific data in Keychain
 * Uses generic password items with custom account names
 */
@Singleton
class PaykitKeychainStorage @Inject constructor(
    private val keychain: Keychain
) {
    companion object {
        private const val TAG = "PaykitKeychainStorage"
        private const val SERVICE_PREFIX = "paykit."
    }
    
    suspend fun store(key: String, data: ByteArray) {
        try {
            val fullKey = "$SERVICE_PREFIX$key"
            keychain.upsertString(fullKey, String(data))
            Logger.debug("Stored Paykit keychain item: $key", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to store Paykit keychain item: $key", e, context = TAG)
            throw PaykitStorageException.SaveFailed(key)
        }
    }
    
    fun retrieve(key: String): ByteArray? {
        return try {
            val fullKey = "$SERVICE_PREFIX$key"
            keychain.loadString(fullKey)?.toByteArray()
        } catch (e: Exception) {
            Logger.debug("Paykit keychain item not found: $key", context = TAG)
            null
        }
    }
    
    suspend fun delete(key: String) {
        try {
            val fullKey = "$SERVICE_PREFIX$key"
            keychain.delete(fullKey)
            Logger.debug("Deleted Paykit keychain item: $key", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to delete Paykit keychain item: $key", e, context = TAG)
            throw PaykitStorageException.DeleteFailed(key)
        }
    }
    
    fun exists(key: String): Boolean {
        return try {
            val fullKey = "$SERVICE_PREFIX$key"
            keychain.exists(fullKey)
        } catch (e: Exception) {
            false
        }
    }
}

sealed class PaykitStorageException(message: String) : Exception(message) {
    class SaveFailed(key: String) : PaykitStorageException("Failed to save Paykit data: $key")
    class LoadFailed(key: String) : PaykitStorageException("Failed to load Paykit data: $key")
    class DeleteFailed(key: String) : PaykitStorageException("Failed to delete Paykit data: $key")
    object EncodingFailed : PaykitStorageException("Failed to encode Paykit data")
    object DecodingFailed : PaykitStorageException("Failed to decode Paykit data")
}

