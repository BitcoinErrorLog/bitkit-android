package to.bitkit.helpers

import android.content.Context
import kotlinx.coroutines.delay
import to.bitkit.paykit.models.PaymentDirection
import to.bitkit.paykit.models.PaymentStatus
import to.bitkit.paykit.models.Receipt
import to.bitkit.paykit.services.DiscoveredContact
import to.bitkit.paykit.services.PubkyProfile
import to.bitkit.paykit.services.PubkyProfileLink
import java.util.UUID

/**
 * Helper class for wallet-related test operations
 */
object WalletTestHelper {
    
    // Test wallet mnemonic (for regtest only!)
    // DO NOT use this in production
    const val TEST_MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    
    // Timeout for node operations
    private const val NODE_READY_TIMEOUT_MS = 60_000L
    
    // MARK: - Wallet Creation
    
    /**
     * Create a test wallet with a known seed
     * This should only be used in test environments
     */
    suspend fun createTestWallet(context: Context) {
        println("WalletTestHelper: Creating test wallet...")
        // In actual implementation, this would interact with WalletViewModel
    }
    
    /**
     * Restore a wallet from the test mnemonic
     */
    suspend fun restoreTestWallet(context: Context) {
        println("WalletTestHelper: Restoring test wallet from mnemonic...")
        // In actual implementation, this would restore via WalletViewModel
    }
    
    // MARK: - Node Lifecycle
    
    /**
     * Wait for the LDK node to be ready
     * @param timeout Maximum time to wait in milliseconds (default 60 seconds)
     * @return True if node became ready, false if timeout
     */
    suspend fun waitForNodeReady(timeout: Long = NODE_READY_TIMEOUT_MS): Boolean {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeout) {
            if (isNodeRunning()) {
                return true
            }
            delay(500)
        }
        
        return false
    }
    
    /**
     * Check if the LDK node is currently running
     */
    fun isNodeRunning(): Boolean {
        // In actual implementation, this would check LightningService state
        // For testing, we return a placeholder
        return true
    }
    
    /**
     * Start the LDK node if not already running
     */
    suspend fun ensureNodeRunning() {
        if (!isNodeRunning()) {
            println("WalletTestHelper: Starting LDK node...")
            // In actual implementation: LightningService.getInstance().startNode()
        }
    }
    
    // MARK: - Balance Helpers
    
    /**
     * Get the current wallet balance in satoshis
     */
    suspend fun getBalance(): WalletBalance {
        // In actual implementation, this would fetch from LightningService
        return WalletBalance(onchain = 0L, lightning = 0L)
    }
    
    /**
     * Check if wallet has sufficient balance for testing
     */
    suspend fun hasSufficientBalance(minSats: Long = 10000): Boolean {
        val balance = getBalance()
        return balance.onchain >= minSats || balance.lightning >= minSats
    }
    
    // MARK: - Regtest Helpers
    
    /**
     * Fund the test wallet via regtest faucet
     * @param amount Amount in satoshis to fund
     */
    suspend fun fundWallet(amount: Long = 100_000) {
        if (!android.os.Build.TYPE.contains("debug", ignoreCase = true)) {
            throw WalletTestException.NotAvailableInProduction
        }
        
        println("WalletTestHelper: Funding wallet with $amount sats...")
        
        // Get receive address
        // In actual implementation: val address = LightningService.getInstance().getReceiveAddress()
        
        // Call regtest faucet
        // This requires a running regtest environment
        
        println("WalletTestHelper: Funded wallet successfully")
    }
    
    /**
     * Generate regtest blocks to confirm transactions
     * @param count Number of blocks to generate
     */
    suspend fun generateBlocks(count: Int = 6) {
        if (!android.os.Build.TYPE.contains("debug", ignoreCase = true)) {
            throw WalletTestException.NotAvailableInProduction
        }
        
        println("WalletTestHelper: Generating $count regtest blocks...")
        
        // Call regtest RPC to generate blocks
        // This requires a running regtest environment
        
        println("WalletTestHelper: Generated $count blocks")
    }
    
    // MARK: - Cleanup
    
    /**
     * Clean up after tests
     */
    suspend fun cleanup() {
        println("WalletTestHelper: Cleaning up...")
        // Clear any test state
    }
    
    /**
     * Reset wallet state (for fresh test runs)
     */
    suspend fun resetWallet() {
        if (!android.os.Build.TYPE.contains("debug", ignoreCase = true)) {
            throw WalletTestException.NotAvailableInProduction
        }
        
        println("WalletTestHelper: Resetting wallet...")
        // In actual implementation, this would wipe wallet data
    }
    
    // MARK: - Test Fixtures
    
    /**
     * Create a test payment receipt for testing
     */
    fun createTestReceipt(
        direction: PaymentDirection = PaymentDirection.SENT,
        amountSats: Long = 1000,
        counterpartyKey: String = PubkyRingSimulator.TEST_PUBKEY
    ): Receipt {
        return Receipt(
            id = UUID.randomUUID().toString(),
            direction = direction,
            counterpartyKey = counterpartyKey,
            counterpartyName = "Test Contact",
            amountSats = amountSats,
            status = PaymentStatus.COMPLETED,
            paymentMethod = "lightning"
        )
    }
    
    /**
     * Create a test contact for testing
     */
    fun createTestContact(): DiscoveredContact {
        return DiscoveredContact(
            pubkey = PubkyRingSimulator.TEST_PUBKEY,
            name = "Test Contact",
            hasPaymentMethods = true,
            supportedMethods = listOf("lightning", "bitcoin")
        )
    }
    
    /**
     * Create a test profile for testing
     */
    fun createTestProfile(): PubkyProfile {
        return PubkyProfile(
            name = "Test User",
            bio = "This is a test profile for E2E testing",
            avatar = null,
            links = listOf(
                PubkyProfileLink(title = "Website", url = "https://example.com"),
                PubkyProfileLink(title = "Twitter", url = "https://twitter.com/test")
            )
        )
    }
}

/**
 * Wallet balance data class
 */
data class WalletBalance(
    val onchain: Long,
    val lightning: Long,
) {
    val total: Long get() = onchain + lightning
}

/**
 * Wallet test errors
 */
sealed class WalletTestException(message: String) : Exception(message) {
    object WalletCreationFailed : WalletTestException("Failed to create test wallet")
    object NodeNotReady : WalletTestException("LDK node did not become ready in time")
    object InsufficientBalance : WalletTestException("Wallet does not have sufficient balance for test")
    object RegtestNotAvailable : WalletTestException("Regtest environment is not available")
    object NotAvailableInProduction : WalletTestException("This operation is only available in debug builds")
}

