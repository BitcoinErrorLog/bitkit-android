package to.bitkit.paykit.services

import android.content.Context
import android.os.Build
import com.paykit.mobile.*
import com.pubky.noise.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import to.bitkit.paykit.KeyManager
import to.bitkit.utils.Logger
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A payment request to send over Noise channel
 */
data class NoisePaymentRequest(
    val receiptId: String = "rcpt_${UUID.randomUUID()}",
    val payerPubkey: String,
    val payeePubkey: String,
    val methodId: String,
    val amount: String? = null,
    val currency: String? = null,
    val description: String? = null
)

/**
 * Response from a payment request
 */
data class NoisePaymentResponse(
    val success: Boolean,
    val receiptId: String? = null,
    val confirmedAt: Long? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null
)

/**
 * Service errors
 */
sealed class NoisePaymentError(message: String) : Exception(message) {
    object NoIdentity : NoisePaymentError("No identity configured")
    class KeyDerivationFailed(msg: String) : NoisePaymentError("Failed to derive encryption keys: $msg")
    object EndpointNotFound : NoisePaymentError("Recipient has no Noise endpoint published")
    class InvalidEndpoint(msg: String) : NoisePaymentError("Invalid endpoint format: $msg")
    class ConnectionFailed(msg: String) : NoisePaymentError("Connection failed: $msg")
    class HandshakeFailed(msg: String) : NoisePaymentError("Secure handshake failed: $msg")
    class EncryptionFailed(msg: String) : NoisePaymentError("Encryption failed: $msg")
    class DecryptionFailed(msg: String) : NoisePaymentError("Decryption failed: $msg")
    class InvalidResponse(msg: String) : NoisePaymentError("Invalid response: $msg")
    object Timeout : NoisePaymentError("Operation timed out")
    object Cancelled : NoisePaymentError("Operation cancelled")
    class ServerError(val code: String, message: String) : NoisePaymentError("Server error [$code]: $message")
}

/**
 * Service for coordinating Noise protocol payments
 * Uses FfiNoiseManager from pubky-noise for encrypted communication
 */
@Singleton
class NoisePaymentService @Inject constructor(
    private val context: Context,
    private val keyManager: KeyManager,
    private val directoryService: DirectoryService,
    private val pubkyRingIntegration: PubkyRingIntegration
) {
    companion object {
        private const val TAG = "NoisePaymentService"
    }

    private var paykitClient: PaykitClient? = null

    // Connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    // Noise manager and socket
    private var noiseManager: FfiNoiseManager? = null
    private var socket: Socket? = null

    // Configuration
    var connectionTimeoutMs: Int = 30000

    /**
     * Initialize with PaykitClient
     */
    fun initialize(client: PaykitClient) {
        this.paykitClient = client
    }

    /**
     * Send a payment request over Noise protocol
     */
    suspend fun sendPaymentRequest(request: NoisePaymentRequest): NoisePaymentResponse = withContext(Dispatchers.IO) {
        // Step 1: Discover Noise endpoint for recipient
        val endpoint = directoryService.discoverNoiseEndpoint(request.payeePubkey)
            ?: throw NoisePaymentError.EndpointNotFound

        // Step 2: Connect to endpoint
        connect(endpoint)

        // Step 3: Send payment request
        val sessionId = _currentSessionId.value
            ?: throw NoisePaymentError.ConnectionFailed("Not connected")
        val manager = noiseManager
            ?: throw NoisePaymentError.ConnectionFailed("Not connected")

        // Create message JSON
        val messageJson = JSONObject().apply {
            put("type", "request_receipt")
            put("receipt_id", request.receiptId)
            put("payer", request.payerPubkey)
            put("payee", request.payeePubkey)
            put("method_id", request.methodId)
            request.amount?.let { put("amount", it) }
            request.currency?.let { put("currency", it) }
            request.description?.let { put("description", it) }
            put("created_at", System.currentTimeMillis() / 1000)
        }

        val jsonData = messageJson.toString().toByteArray()

        // Encrypt
        val ciphertext = try {
            manager.encrypt(sessionId, jsonData)
        } catch (e: Exception) {
            throw NoisePaymentError.EncryptionFailed(e.message ?: "Unknown error")
        }

        // Send with length prefix
        sendLengthPrefixedData(ciphertext)

        // Receive response
        val responseCiphertext = receiveLengthPrefixedData()

        // Decrypt
        val responsePlaintext = try {
            manager.decrypt(sessionId, responseCiphertext)
        } catch (e: Exception) {
            throw NoisePaymentError.DecryptionFailed(e.message ?: "Unknown error")
        }

        // Parse response
        parsePaymentResponse(responsePlaintext, request.receiptId)
    }

    /**
     * Connect to a Noise endpoint
     */
    private suspend fun connect(endpoint: NoiseEndpointInfo) = withContext(Dispatchers.IO) {
        // Get Ed25519 seed from KeyManager
        val seedData = keyManager.getSecretKeyBytes()
            ?: throw NoisePaymentError.NoIdentity

        val deviceId = keyManager.getDeviceId()
        val deviceIdBytes = deviceId.toByteArray()

        // Create Noise manager configuration
        val config = FfiMobileConfig(
            autoReconnect = false,
            maxReconnectAttempts = 0u,
            reconnectDelayMs = 0u,
            batterySaver = false,
            chunkSize = 32768u
        )

        // Create Noise manager
        try {
            noiseManager = FfiNoiseManager.newClient(
                config = config,
                clientSeed = seedData,
                clientKid = "bitkit-android",
                deviceId = deviceIdBytes
            )
        } catch (e: Exception) {
            Logger.error("Failed to create Noise manager", e, context = TAG)
            throw NoisePaymentError.HandshakeFailed("Failed to create Noise manager: ${e.message}")
        }

        // Parse server pubkey from hex
        val serverPubkey = endpoint.serverNoisePubkey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        // Create socket connection
        try {
            socket = Socket().apply {
                soTimeout = connectionTimeoutMs
                connect(InetSocketAddress(endpoint.host, endpoint.port.toInt()), connectionTimeoutMs)
            }
        } catch (e: Exception) {
            throw NoisePaymentError.ConnectionFailed(e.message ?: "Unknown error")
        }

        // Perform Noise handshake
        performHandshake(serverPubkey)
        _isConnected.value = true
    }

    /**
     * Perform Noise IK handshake
     */
    private suspend fun performHandshake(serverPubkey: ByteArray) = withContext(Dispatchers.IO) {
        val manager = noiseManager ?: throw NoisePaymentError.HandshakeFailed("Noise manager not initialized")

        // Step 1: Initiate connection
        val initResult = try {
            manager.initiateConnection(serverPubkey, null)
        } catch (e: Exception) {
            Logger.error("Failed to initiate Noise connection", e, context = TAG)
            throw NoisePaymentError.HandshakeFailed("Failed to initiate: ${e.message}")
        }

        // Step 2: Send first message
        sendRawData(initResult.firstMessage)

        // Step 3: Receive server response
        val response = receiveRawData()

        // Step 4: Complete handshake
        val sessionId = try {
            manager.completeConnection(initResult.sessionId, response)
        } catch (e: Exception) {
            Logger.error("Failed to complete Noise connection", e, context = TAG)
            throw NoisePaymentError.HandshakeFailed("Failed to complete: ${e.message}")
        }

        _currentSessionId.value = sessionId
        Logger.info("Noise handshake completed, session: $sessionId", context = TAG)
    }

    /**
     * Disconnect from current peer
     */
    fun disconnect() {
        _currentSessionId.value?.let { sessionId ->
            noiseManager?.removeSession(sessionId)
        }

        try {
            socket?.close()
        } catch (e: Exception) {
            // Ignore close errors
        }

        socket = null
        noiseManager = null
        _isConnected.value = false
        _currentSessionId.value = null
    }

    /**
     * Receive a payment request (server mode)
     * Note: Server mode is implemented via startServer() and handleClientConnection()
     * This method is kept for backward compatibility but server mode should be used
     * via the server infrastructure (startServer, handleClientConnection, etc.)
     */
    suspend fun receivePaymentRequest(): NoisePaymentRequest? {
        // Server mode is implemented via:
        // 1. startServer() - starts ServerSocket listener
        // 2. handleClientConnection() - accepts incoming connections
        // 3. handleServerMessage() - performs server-side Noise handshake and processes messages
        // 
        // To receive payment requests, use the server mode:
        // - Call startServer() to begin listening
        // - Set onPendingPaymentRequest callback to receive requests
        // - Payment requests will be delivered via the callback
        
        // This method returns null as server mode uses callbacks instead
        return null
    }

    // MARK: - Network I/O

    private fun sendRawData(data: ByteArray) {
        val sock = socket ?: throw NoisePaymentError.ConnectionFailed("No connection")
        val output = DataOutputStream(sock.getOutputStream())
        output.writeInt(data.size)
        output.write(data)
        output.flush()
    }

    private fun receiveRawData(): ByteArray {
        val sock = socket ?: throw NoisePaymentError.ConnectionFailed("No connection")
        val input = DataInputStream(sock.getInputStream())
        val length = input.readInt()
        val data = ByteArray(length)
        input.readFully(data)
        return data
    }

    private fun sendLengthPrefixedData(data: ByteArray) {
        sendRawData(data)
    }

    private fun receiveLengthPrefixedData(): ByteArray {
        return receiveRawData()
    }

    /**
     * Parse payment response JSON
     */
    private fun parsePaymentResponse(data: ByteArray, expectedReceiptId: String): NoisePaymentResponse {
        val json = try {
            JSONObject(String(data))
        } catch (e: Exception) {
            throw NoisePaymentError.InvalidResponse("Invalid JSON structure")
        }

        return when (val msgType = json.optString("type")) {
            "confirm_receipt" -> {
                NoisePaymentResponse(
                    success = true,
                    receiptId = json.optString("receipt_id"),
                    confirmedAt = json.optLong("confirmed_at"),
                    errorCode = null,
                    errorMessage = null
                )
            }
            "error" -> {
                NoisePaymentResponse(
                    success = false,
                    receiptId = null,
                    confirmedAt = null,
                    errorCode = json.optString("code", "unknown"),
                    errorMessage = json.optString("message", "Unknown error")
                )
            }
            else -> throw NoisePaymentError.InvalidResponse("Unexpected message type: $msgType")
        }
    }
}
