package to.bitkit.paykit.services

import android.net.Uri
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for Pubky-ring callback URIs.
 * Extracts session, keypair, profile, and setup data from callback URIs.
 */
@Singleton
class PubkyRingCallbackParser @Inject constructor() {

    sealed class CallbackResult<out T> {
        data class Success<T>(val data: T) : CallbackResult<T>()
        data class Error(val exception: PubkyRingException) : CallbackResult<Nothing>()
    }

    data class SessionData(
        val pubkey: String,
        val sessionSecret: String,
        val capabilities: List<String>,
    )

    data class KeypairData(
        val publicKey: String,
        val secretKey: String,
        val deviceId: String,
        val epoch: ULong,
    )

    data class LegacySetupData(
        val session: SessionData,
        val deviceId: String,
        val keypair0: KeypairData?,
        val keypair1: KeypairData?,
    )

    data class SecureHandoffReference(
        val pubkey: String,
        val requestId: String,
    )

    fun parseSessionCallback(uri: Uri): CallbackResult<SessionData> {
        val pubkey = uri.getQueryParameter("pubky")
            ?: return CallbackResult.Error(PubkyRingException.MissingParameters)
        val sessionSecret = uri.getQueryParameter("session_secret")
            ?: return CallbackResult.Error(PubkyRingException.MissingParameters)

        val capabilities = uri.getQueryParameter("capabilities")
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        return CallbackResult.Success(SessionData(pubkey, sessionSecret, capabilities))
    }

    fun parseKeypairCallback(uri: Uri): CallbackResult<KeypairData> {
        val publicKey = uri.getQueryParameter("public_key")
            ?: return CallbackResult.Error(PubkyRingException.InvalidCallback)
        val secretKey = uri.getQueryParameter("secret_key")
            ?: return CallbackResult.Error(PubkyRingException.InvalidCallback)
        val deviceId = uri.getQueryParameter("device_id")
            ?: return CallbackResult.Error(PubkyRingException.InvalidCallback)
        val epochStr = uri.getQueryParameter("epoch")
            ?: return CallbackResult.Error(PubkyRingException.InvalidCallback)

        val epoch = epochStr.toULongOrNull()
            ?: return CallbackResult.Error(PubkyRingException.InvalidCallback)

        return CallbackResult.Success(KeypairData(publicKey, secretKey, deviceId, epoch))
    }

    fun parseSecureHandoffReference(uri: Uri): CallbackResult<SecureHandoffReference> {
        val pubkey = uri.getQueryParameter("pubky")
            ?: return CallbackResult.Error(PubkyRingException.MissingParameters)
        val requestId = uri.getQueryParameter("request_id")
            ?: return CallbackResult.Error(PubkyRingException.MissingParameters)

        return CallbackResult.Success(SecureHandoffReference(pubkey, requestId))
    }

    fun isSecureHandoffMode(uri: Uri): Boolean {
        return uri.getQueryParameter("mode") == "secure_handoff"
    }

    fun parseLegacySetupCallback(uri: Uri): CallbackResult<LegacySetupData> {
        val pubkey = uri.getQueryParameter("pubky")
            ?: return CallbackResult.Error(PubkyRingException.MissingParameters)
        val sessionSecret = uri.getQueryParameter("session_secret")
            ?: return CallbackResult.Error(PubkyRingException.MissingParameters)
        val deviceId = uri.getQueryParameter("device_id")
            ?: return CallbackResult.Error(PubkyRingException.MissingParameters)

        val capabilities = uri.getQueryParameter("capabilities")
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        val session = SessionData(pubkey, sessionSecret, capabilities)

        val keypair0 = parseOptionalKeypair(uri, deviceId, 0u)
        val keypair1 = parseOptionalKeypair(uri, deviceId, 1u)

        return CallbackResult.Success(LegacySetupData(session, deviceId, keypair0, keypair1))
    }

    private fun parseOptionalKeypair(uri: Uri, deviceId: String, epoch: ULong): KeypairData? {
        val publicKey = uri.getQueryParameter("noise_public_key_$epoch")
        val secretKey = uri.getQueryParameter("noise_secret_key_$epoch")
        return if (publicKey != null && secretKey != null) {
            KeypairData(publicKey, secretKey, deviceId, epoch)
        } else {
            null
        }
    }

    fun parseProfileCallback(uri: Uri): PubkyProfile? {
        val error = uri.getQueryParameter("error")
        if (error != null) return null

        return PubkyProfile(
            name = uri.getQueryParameter("name"),
            bio = uri.getQueryParameter("bio"),
            image = uri.getQueryParameter("image"),
            links = null,
        )
    }

    fun parseFollowsCallback(uri: Uri): List<String>? {
        val error = uri.getQueryParameter("error")
        if (error != null) return null

        val followsJson = uri.getQueryParameter("follows") ?: return emptyList()
        return followsJson.split(",").filter { it.isNotEmpty() }
    }
}

