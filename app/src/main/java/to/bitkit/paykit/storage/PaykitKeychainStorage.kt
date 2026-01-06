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
            // Encode binary data as hex to preserve byte values through string storage
            val hexString = data.joinToString("") { "%02x".format(it) }
            keychain.upsertString(fullKey, hexString)
            Logger.debug("Stored Paykit keychain item: $key (${data.size} bytes)", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to store Paykit keychain item: $key", e, context = TAG)
            throw PaykitStorageException.SaveFailed(key)
        }
    }

    fun retrieve(key: String): ByteArray? {
        return try {
            val fullKey = "$SERVICE_PREFIX$key"
            val hexString = keychain.loadString(fullKey) ?: return null
            // Decode hex string back to bytes
            hexString.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) {
            Logger.debug("Paykit keychain item not found or invalid: $key", context = TAG)
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

    // MARK: - Convenience Methods

    /**
     * Get string value
     */
    fun getString(key: String): String? {
        return try {
            val fullKey = "$SERVICE_PREFIX$key"
            keychain.loadString(fullKey)
        } catch (e: Exception) {
            Logger.debug("Paykit keychain item not found: $key", context = TAG)
            null
        }
    }

    /**
     * Set string value
     */
    suspend fun setString(key: String, value: String) {
        try {
            val fullKey = "$SERVICE_PREFIX$key"
            keychain.upsertString(fullKey, value)
            Logger.debug("Stored Paykit keychain string: $key", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to store Paykit keychain string: $key", e, context = TAG)
            throw PaykitStorageException.SaveFailed(key)
        }
    }

    /**
     * Set string value synchronously (for use in non-suspend contexts)
     * Note: Uses runBlocking - prefer suspend version where possible
     */
    fun setStringSync(key: String, value: String) {
        kotlinx.coroutines.runBlocking {
            setString(key, value)
        }
    }

    /**
     * Delete key synchronously (for use in non-suspend contexts)
     * Note: Uses runBlocking - prefer suspend version where possible
     */
    fun deleteSync(key: String) {
        kotlinx.coroutines.runBlocking {
            delete(key)
        }
    }

    /**
     * List all keys with a given prefix
     * Note: This requires the keychain to support listing, which may need implementation
     */
    fun listKeys(prefix: String): List<String> {
        return try {
            val fullPrefix = "$SERVICE_PREFIX$prefix"
            keychain.listKeys(fullPrefix)
        } catch (e: Exception) {
            Logger.error("Failed to list keychain keys with prefix: $prefix", e, context = TAG)
            emptyList()
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
