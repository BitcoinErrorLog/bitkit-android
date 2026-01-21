package to.bitkit.paykit.services

import com.pubky.noise.sealedBlobEncryptSigned
import com.pubky.noise.sealedBlobVerifySignature
import com.pubky.noise.sb2Sign
import to.bitkit.paykit.storage.PaykitKeychainStorage
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility for creating and verifying signatures on Paykit messages.
 *
 * Per PUBKY_CRYPTO_SPEC v2.5 Section 7.4, signed SB2 envelopes include:
 * - `sender`: The sender's PKARR pubkey in z-base-32
 * - `sig`: Ed25519 signature over envelope contents
 *
 * Supports two signing modes:
 * 1. Direct signing with owner's Ed25519 key
 * 2. Delegated signing with AppKey (includes cert_id in SB2 header)
 *
 * Signatures prove message authenticity and prevent relay attacks.
 */
@Singleton
class PaykitSigner @Inject constructor(
    private val keychainStorage: PaykitKeychainStorage,
) {
    companion object {
        private const val TAG = "PaykitSigner"
        private const val PUBKEY_LOG_LEN = 12
    }

    /**
     * Encrypt and sign a payload for a recipient.
     *
     * Uses the sender's Ed25519 key to create a signed SB2 envelope.
     *
     * @param recipientPk Recipient's X25519 public key (32 bytes)
     * @param plaintext Data to encrypt
     * @param aad Associated authenticated data
     * @param purpose Optional purpose hint (e.g., "request", "proposal")
     * @param senderEd25519Sk Sender's Ed25519 secret key (32 bytes)
     * @param senderPeeridZ32 Sender's PKARR pubkey in z-base-32
     * @return JSON-encoded signed SB2 envelope
     */
    fun encryptSigned(
        recipientPk: ByteArray,
        plaintext: ByteArray,
        aad: String,
        purpose: String?,
        senderEd25519Sk: ByteArray,
        senderPeeridZ32: String,
    ): String {
        Logger.debug(
            "Creating signed SB2 envelope for ${senderPeeridZ32.take(PUBKEY_LOG_LEN)}...",
            context = TAG,
        )

        return sealedBlobEncryptSigned(
            recipientPk = recipientPk,
            plaintext = plaintext,
            aad = aad,
            purpose = purpose,
            senderEd25519Sk = senderEd25519Sk,
            senderPeeridZ32 = senderPeeridZ32,
        )
    }

    /**
     * Verify the signature on a signed SB2 envelope.
     *
     * @param envelopeJson JSON-encoded SB2 envelope
     * @param senderEd25519Pk Sender's Ed25519 public key (32 bytes)
     * @return true if signature is valid, false if no signature present
     * @throws Exception if signature is present but invalid
     */
    fun verifySignature(envelopeJson: String, senderEd25519Pk: ByteArray): Boolean {
        return try {
            sealedBlobVerifySignature(envelopeJson, senderEd25519Pk)
        } catch (e: Exception) {
            Logger.error("Signature verification failed", e, context = TAG)
            throw e
        }
    }

    /**
     * Check if an envelope has a signature.
     *
     * @param envelopeJson JSON-encoded SB2 envelope
     * @return true if envelope contains a `sig` field
     */
    fun hasSignature(envelopeJson: String): Boolean {
        return envelopeJson.contains("\"sig\":")
    }

    /**
     * Extract the sender pubkey from a signed envelope.
     *
     * @param envelopeJson JSON-encoded SB2 envelope
     * @return Sender's z-base-32 pubkey if present, null otherwise
     */
    fun extractSender(envelopeJson: String): String? {
        return try {
            val json = org.json.JSONObject(envelopeJson)
            if (json.has("sender")) json.getString("sender") else null
        } catch (e: Exception) {
            null
        }
    }

    // MARK: - SB2 Binary Signing with AppKey (Delegated Signing)

    /**
     * Sign an SB2 envelope using AppKey for delegated signing.
     *
     * Per PUBKY_UNIFIED_KEY_DELEGATION_SPEC, delegated signatures include:
     * - cert_id: The AppCert ID in the SB2 header (set during encryption)
     * - sig: Ed25519 signature from the AppKey
     *
     * @param sb2EnvelopeBytes Raw SB2 binary envelope
     * @param ownerPeeridHex Owner's Ed25519 public key (hex)
     * @param canonicalPath Path for AAD binding
     * @return Signed SB2 envelope bytes
     * @throws AppKeyNotAvailableException if AppKey is not configured
     */
    fun signWithAppKey(
        sb2EnvelopeBytes: ByteArray,
        ownerPeeridHex: String,
        canonicalPath: String,
    ): ByteArray {
        val appSk = keychainStorage.getAppSecretKey()
            ?: throw AppKeyNotAvailableException()
        val certId = keychainStorage.getAppCertId()
            ?: throw AppKeyNotAvailableException()

        Logger.debug(
            "Signing SB2 with AppKey (cert_id=${certId.take(PUBKEY_LOG_LEN)}...)",
            context = TAG,
        )

        val appSkBytes = hexStringToByteArray(appSk)
        val ownerPeeridBytes = hexStringToByteArray(ownerPeeridHex)

        return sb2Sign(sb2EnvelopeBytes, appSkBytes, ownerPeeridBytes, canonicalPath)
    }

    /**
     * Check if AppKey is available for delegated signing.
     */
    fun hasAppKey(): Boolean {
        return keychainStorage.hasAppKey()
    }

    /**
     * Get the AppCert ID if available (for embedding in SB2 headers during encryption).
     */
    fun getAppCertIdHex(): String? {
        return keychainStorage.getAppCertId()
    }

    /**
     * Get the AppCert ID as bytes (for SB2 encryption cert_id parameter).
     */
    fun getAppCertIdBytes(): ByteArray? {
        val certIdHex = keychainStorage.getAppCertId() ?: return null
        return hexStringToByteArray(certIdHex)
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

/**
 * Exception thrown when AppKey is not available for delegated signing.
 */
class AppKeyNotAvailableException : Exception("AppKey not configured. Complete handoff from Ring first.")
