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

        // InboxKey storage keys
        private const val KEY_INBOX_SECRET_KEY = "inbox.secret_key"
        private const val KEY_INBOX_PUBLIC_KEY = "inbox.public_key"

        // AppKey storage keys
        private const val KEY_APP_ED25519_SK = "appkey.ed25519_sk"
        private const val KEY_APP_ED25519_PK = "appkey.ed25519_pk"
        private const val KEY_APP_CERT_ID = "appkey.cert_id"
        private const val KEY_APP_CERT_BODY = "appkey.cert_body"
        private const val KEY_APP_CERT_SIG = "appkey.cert_sig"
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

    // MARK: - InboxKey Storage (for SB2 stored delivery)

    /**
     * Store InboxKey keypair (from handoff payload)
     */
    suspend fun storeInboxKeypair(publicKeyHex: String, secretKeyHex: String) {
        setString(KEY_INBOX_PUBLIC_KEY, publicKeyHex)
        setString(KEY_INBOX_SECRET_KEY, secretKeyHex)
        Logger.info("Stored InboxKey keypair", context = TAG)
    }

    /**
     * Get InboxKey secret key (hex) for SB2 decryption
     */
    fun getInboxSecretKey(): String? {
        return getString(KEY_INBOX_SECRET_KEY)
    }

    /**
     * Get InboxKey public key (hex)
     */
    fun getInboxPublicKey(): String? {
        return getString(KEY_INBOX_PUBLIC_KEY)
    }

    /**
     * Check if InboxKey is available
     */
    fun hasInboxKey(): Boolean {
        return getInboxSecretKey() != null
    }

    // MARK: - AppKey Storage (for delegated signing)

    /**
     * Store AppKey from handoff payload
     */
    suspend fun storeAppKey(
        ed25519Sk: String,
        ed25519Pk: String,
        certId: String,
        certBody: String,
        certSig: String,
    ) {
        setString(KEY_APP_ED25519_SK, ed25519Sk)
        setString(KEY_APP_ED25519_PK, ed25519Pk)
        setString(KEY_APP_CERT_ID, certId)
        setString(KEY_APP_CERT_BODY, certBody)
        setString(KEY_APP_CERT_SIG, certSig)
        Logger.info("Stored AppKey with cert_id=${certId.take(16)}...", context = TAG)
    }

    /**
     * Get AppKey secret key (hex) for delegated signing
     */
    fun getAppSecretKey(): String? {
        return getString(KEY_APP_ED25519_SK)
    }

    /**
     * Get AppKey public key (hex)
     */
    fun getAppPublicKey(): String? {
        return getString(KEY_APP_ED25519_PK)
    }

    /**
     * Get AppCert ID (hex)
     */
    fun getAppCertId(): String? {
        return getString(KEY_APP_CERT_ID)
    }

    /**
     * Get AppCert body (hex-encoded CBOR)
     */
    fun getAppCertBody(): String? {
        return getString(KEY_APP_CERT_BODY)
    }

    /**
     * Get AppCert signature (hex)
     */
    fun getAppCertSig(): String? {
        return getString(KEY_APP_CERT_SIG)
    }

    /**
     * Check if AppKey is available
     */
    fun hasAppKey(): Boolean {
        return getAppSecretKey() != null && getAppCertId() != null
    }
}

sealed class PaykitStorageException(message: String) : Exception(message) {
    class SaveFailed(key: String) : PaykitStorageException("Failed to save Paykit data: $key")
    class LoadFailed(key: String) : PaykitStorageException("Failed to load Paykit data: $key")
    class DeleteFailed(key: String) : PaykitStorageException("Failed to delete Paykit data: $key")
    object EncodingFailed : PaykitStorageException("Failed to encode Paykit data")
    object DecodingFailed : PaykitStorageException("Failed to decode Paykit data")
}
