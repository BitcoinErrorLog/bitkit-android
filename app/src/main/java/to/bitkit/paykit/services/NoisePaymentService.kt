package to.bitkit.paykit.services

import android.content.Context
import uniffi.paykit_mobile.*
import com.pubky.noise.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.bitkit.paykit.KeyManager
import to.bitkit.utils.Logger
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A payment request to send over Noise channel
 */
@Serializable
data class NoisePaymentRequest(
    @SerialName("receipt_id")
    val receiptId: String = "rcpt_${UUID.randomUUID()}",
    @SerialName("payer")
    val payerPubkey: String,
    @SerialName("payee")
    val payeePubkey: String,
    @SerialName("method_id")
    val methodId: String,
    val amount: String? = null,
    val currency: String? = null,
    val description: String? = null,
    /** Invoice number for cross-referencing */
    @SerialName("invoice_number")
    val invoiceNumber: String? = null,
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000
)

/**
 * Response from a payment request
 */
@Serializable
data class NoisePaymentResponse(
    val success: Boolean,
    @SerialName("receipt_id")
    val receiptId: String? = null,
    @SerialName("confirmed_at")
    val confirmedAt: Long? = null,
    @SerialName("error_code")
    val errorCode: String? = null,
    @SerialName("error_message")
    val errorMessage: String? = null
)

/**
 * Base class for Noise messages
 */
@Serializable
data class NoiseMessage(
    val type: String,
    @SerialName("receipt_id")
    val receiptId: String,
    val payer: String? = null,
    val payee: String? = null,
    @SerialName("method_id")
    val methodId: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val description: String? = null,
    @SerialName("invoice_number")
    val invoiceNumber: String? = null,
    @SerialName("created_at")
    val createdAt: Long? = null,
    @SerialName("confirmed_at")
    val confirmedAt: Long? = null,
    val success: Boolean? = null,
    @SerialName("error_code")
    val errorCode: String? = null,
    @SerialName("error_message")
    val errorMessage: String? = null
)

/**
 * Service errors
 */
sealed class NoisePaymentError(message: String) : Exception(message) {
    data object NoIdentity : NoisePaymentError("No identity configured")
    class NoKeypairCached(msg: String) : NoisePaymentError(msg)
    class KeyDerivationFailed(msg: String) : NoisePaymentError("Failed to derive encryption keys: $msg")
    data object EndpointNotFound : NoisePaymentError("Recipient has no Noise endpoint published")
    class InvalidEndpoint(msg: String) : NoisePaymentError("Invalid endpoint format: $msg")
    class ConnectionFailed(msg: String) : NoisePaymentError("Connection failed: $msg")
    class HandshakeFailed(msg: String) : NoisePaymentError("Secure handshake failed: $msg")
    class EncryptionFailed(msg: String) : NoisePaymentError("Encryption failed: $msg")
    class DecryptionFailed(msg: String) : NoisePaymentError("Decryption failed: $msg")
    class InvalidResponse(msg: String) : NoisePaymentError("Invalid response: $msg")
    data object Timeout : NoisePaymentError("Operation timed out")
    data object Cancelled : NoisePaymentError("Operation cancelled")
    class ServerError(val code: String, message: String) : NoisePaymentError("Server error [$code]: $message")
}

/**
 * Service for coordinating Noise protocol payments
 * Uses FfiNoiseManager from pubky-noise for encrypted communication
 */
@Singleton
class NoisePaymentService @Inject constructor(
    @ApplicationContext private val context: Context,
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
    private val json = Json { ignoreUnknownKeys = true }

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
        val message = NoiseMessage(
            type = "request_receipt",
            receiptId = request.receiptId,
            payer = request.payerPubkey,
            payee = request.payeePubkey,
            methodId = request.methodId,
            amount = request.amount,
            currency = request.currency,
            description = request.description,
            invoiceNumber = request.invoiceNumber,
            createdAt = request.createdAt
        )

        val jsonData = json.encodeToString(message).toByteArray()

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
        // Get cached X25519 keypair from Ring (no local Ed25519 derivation)
        val keypair = keyManager.getCachedNoiseKeypair()
            ?: throw NoisePaymentError.NoKeypairCached("No noise keypair available. Please reconnect to Pubky Ring.")

        // Use X25519 secret key as seed for Noise manager
        val seedData = keypair.secretKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        val deviceId = keyManager.getDeviceId()
        val deviceIdBytes = deviceId.toByteArray()

        // Create Noise manager configuration
        val config = FfiMobileConfig(
            autoReconnect = false,
            maxReconnectAttempts = 0u,
            reconnectDelayMs = 0u,
            batterySaver = false,
            chunkSize = 32768u,
        )

        // Create Noise manager
        try {
            noiseManager = FfiNoiseManager.newClient(
                config = config,
                clientSeed = seedData,
                clientKid = "bitkit-android",
                deviceId = deviceIdBytes,
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

    // MARK: - Background Server Mode

    private var serverSocket: java.net.ServerSocket? = null
    private var isServerRunning = false
    private var onRequestCallback: ((NoisePaymentRequest) -> Unit)? = null

    /**
     * Start a background Noise server to receive incoming payment requests.
     * This is called when the app is woken by a push notification indicating
     * an incoming Noise connection.
     * 
     * @param port Port to listen on
     * @param onRequest Callback invoked when a payment request is received
     */
    suspend fun startBackgroundServer(
        port: Int,
        onRequest: (NoisePaymentRequest) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (isServerRunning) {
            Logger.warn("Background server already running", context = TAG)
            return@withContext
        }

        onRequestCallback = onRequest

        try {
            serverSocket = java.net.ServerSocket(port)
            serverSocket?.soTimeout = 30000 // 30 second timeout waiting for connection
            isServerRunning = true

            Logger.info("Noise server started on port $port", context = TAG)

            // Accept a single connection (push-wake mode)
            val clientSocket = serverSocket?.accept()
            if (clientSocket != null) {
                handleServerConnection(clientSocket)
            }

        } catch (e: java.net.SocketTimeoutException) {
            Logger.info("Server timeout - no incoming connection", context = TAG)
        } catch (e: Exception) {
            Logger.error("Server error", e, context = TAG)
            throw e
        } finally {
            stopBackgroundServer()
        }
    }

    /**
     * Stop the background server
     */
    fun stopBackgroundServer() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        isServerRunning = false
        onRequestCallback = null
        Logger.info("Background server stopped", context = TAG)
    }

    /**
     * Handle an incoming server connection
     */
    private suspend fun handleServerConnection(clientSocket: java.net.Socket) = withContext(Dispatchers.IO) {
        socket = clientSocket

        try {
            // Get cached X25519 keypair from Ring (no local Ed25519 derivation)
            val keypair = keyManager.getCachedNoiseKeypair()
                ?: throw NoisePaymentError.NoKeypairCached("No noise keypair available. Please reconnect to Pubky Ring.")

            // Use X25519 secret key as seed for Noise manager
            val seedData = keypair.secretKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

            val deviceId = keyManager.getDeviceId()
            val deviceIdBytes = deviceId.toByteArray()

            // Create Noise manager in server mode
            val config = FfiMobileConfig(
                autoReconnect = false,
                maxReconnectAttempts = 0u,
                reconnectDelayMs = 0u,
                batterySaver = false,
                chunkSize = 32768u,
            )

            noiseManager = FfiNoiseManager.newServer(
                config = config,
                serverSeed = seedData,
                serverKid = "bitkit-android-server",
                deviceId = deviceIdBytes,
            )

            // Perform server-side handshake
            performServerHandshake()

            // Receive encrypted message
            val ciphertext = receiveLengthPrefixedData()
            val sessionId = _currentSessionId.value
                ?: throw NoisePaymentError.ConnectionFailed("No session")

            // Decrypt
            val plaintext = noiseManager?.decrypt(sessionId, ciphertext)
                ?: throw NoisePaymentError.DecryptionFailed("Failed to decrypt")

            // Parse as payment request
            val request = parsePaymentRequest(plaintext)

            // Send confirmation response
            val response = NoiseMessage(
                type = "confirm_receipt",
                receiptId = request.receiptId,
                confirmedAt = System.currentTimeMillis() / 1000,
                success = true
            )
            val responseData = json.encodeToString(response).toByteArray()
            val encryptedResponse = noiseManager?.encrypt(sessionId, responseData)
                ?: throw NoisePaymentError.EncryptionFailed("Failed to encrypt response")
            sendLengthPrefixedData(encryptedResponse)

            // Notify callback
            onRequestCallback?.invoke(request)

            Logger.info("Successfully received payment request: ${request.receiptId}", context = TAG)

        } catch (e: Exception) {
            Logger.error("Error handling server connection", e, context = TAG)
            throw e
        } finally {
            disconnect()
        }
    }

    /**
     * Perform server-side Noise handshake
     */
    private suspend fun performServerHandshake() = withContext(Dispatchers.IO) {
        val manager = noiseManager ?: throw NoisePaymentError.HandshakeFailed("Noise manager not initialized")

        // Receive first message from client
        val firstMessage = receiveRawData()

        // Process handshake
        val result = try {
            manager.acceptConnection(firstMessage)
        } catch (e: Exception) {
            Logger.error("Failed to accept Noise connection", e, context = TAG)
            throw NoisePaymentError.HandshakeFailed("Failed to accept: ${e.message}")
        }

        // Send response
        sendRawData(result.responseMessage)

        _currentSessionId.value = result.sessionId
        _isConnected.value = true
        Logger.info("Server handshake completed, session: ${result.sessionId}", context = TAG)
    }

    /**
     * Parse incoming payment request JSON
     */
    private fun parsePaymentRequest(data: ByteArray): NoisePaymentRequest {
        val message = try {
            json.decodeFromString<NoiseMessage>(String(data))
        } catch (e: Exception) {
            throw NoisePaymentError.InvalidResponse("Invalid JSON structure: ${e.message}")
        }

        if (message.type != "request_receipt") {
            throw NoisePaymentError.InvalidResponse("Unexpected message type: ${message.type}")
        }

        return NoisePaymentRequest(
            receiptId = message.receiptId,
            payerPubkey = message.payer ?: "",
            payeePubkey = message.payee ?: "",
            methodId = message.methodId ?: "",
            amount = message.amount,
            currency = message.currency,
            description = message.description,
            invoiceNumber = message.invoiceNumber,
            createdAt = message.createdAt ?: (System.currentTimeMillis() / 1000)
        )
    }

    /**
     * Parse payment response JSON
     */
    private fun parsePaymentResponse(data: ByteArray, expectedReceiptId: String): NoisePaymentResponse {
        val message = try {
            json.decodeFromString<NoiseMessage>(String(data))
        } catch (e: Exception) {
            throw NoisePaymentError.InvalidResponse("Invalid JSON structure: ${e.message}")
        }

        return when (message.type) {
            "confirm_receipt" -> {
                NoisePaymentResponse(
                    success = message.success ?: true,
                    receiptId = message.receiptId,
                    confirmedAt = message.confirmedAt,
                    errorCode = null,
                    errorMessage = null
                )
            }
            "error" -> {
                NoisePaymentResponse(
                    success = false,
                    receiptId = message.receiptId,
                    confirmedAt = null,
                    errorCode = message.errorCode ?: "unknown",
                    errorMessage = message.errorMessage ?: "Unknown error"
                )
            }
            else -> throw NoisePaymentError.InvalidResponse("Unexpected message type: ${message.type}")
        }
    }
}
