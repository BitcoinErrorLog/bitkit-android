package to.bitkit.paykit.services

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import to.bitkit.utils.Logger
import uniffi.paykit_mobile.PeerSpendingLimitFfi
import uniffi.paykit_mobile.SpendingCheckResultFfi
import uniffi.paykit_mobile.SpendingManagerFfi
import uniffi.paykit_mobile.SpendingReservationFfi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages spending limits with atomic reserve/commit/rollback operations.
 * Wraps the Rust FFI for secure, thread-safe spending limit management.
 */
@Singleton
class SpendingLimitManager @Inject constructor() {

    companion object {
        private const val TAG = "SpendingLimitManager"
    }

    @Volatile
    private var ffiManager: SpendingManagerFfi? = null
    private val mutex = Mutex()

    /**
     * Initialize the spending limit manager with a storage path
     */
    suspend fun initialize(basePath: String) {
        mutex.withLock {
            ffiManager = SpendingManagerFfi(basePath)
            Logger.info("SpendingLimitManager initialized at $basePath", context = TAG)
        }
    }

    /**
     * Check if the manager is initialized (thread-safe read via @Volatile).
     * For operations that depend on initialization state, prefer using the suspend
     * methods directly which will throw [SpendingLimitException.NotInitialized] if not ready.
     */
    val isInitialized: Boolean
        get() = ffiManager != null

    /**
     * Set a spending limit for a peer
     * @param peerPubkey The peer's public key (z-base32 encoded)
     * @param limitSats Maximum amount in satoshis
     * @param period Reset period ("daily", "weekly", or "monthly")
     * @return The created spending limit
     */
    suspend fun setSpendingLimit(
        peerPubkey: String,
        limitSats: Long,
        period: String = "daily",
    ): PeerSpendingLimit = mutex.withLock {
        val manager = ffiManager ?: throw SpendingLimitException.NotInitialized

        val ffiLimit = manager.setPeerSpendingLimit(
            peerPubkey = peerPubkey,
            limitSats = limitSats,
            period = period,
        )
        Logger.info("Set spending limit for $peerPubkey: $limitSats sats ($period)", context = TAG)

        ffiLimit.toDomain()
    }

    /**
     * Get the spending limit for a peer
     * @param peerPubkey The peer's public key
     * @return The spending limit if set
     */
    suspend fun getSpendingLimit(peerPubkey: String): PeerSpendingLimit? = mutex.withLock {
        val manager = ffiManager ?: throw SpendingLimitException.NotInitialized

        val ffiLimit = manager.getPeerSpendingLimit(peerPubkey = peerPubkey) ?: return@withLock null
        ffiLimit.toDomain()
    }

    /**
     * List all spending limits
     * @return List of all configured spending limits
     */
    suspend fun listSpendingLimits(): List<PeerSpendingLimit> = mutex.withLock {
        val manager = ffiManager ?: throw SpendingLimitException.NotInitialized

        manager.listSpendingLimits().map { it.toDomain() }
    }

    /**
     * Remove the spending limit for a peer
     */
    suspend fun removeSpendingLimit(peerPubkey: String) = mutex.withLock {
        val manager = ffiManager ?: throw SpendingLimitException.NotInitialized

        manager.removePeerSpendingLimit(peerPubkey = peerPubkey)
        Logger.info("Removed spending limit for $peerPubkey", context = TAG)
    }

    /**
     * Try to reserve spending against a peer's limit atomically
     * @param peerPubkey The peer's public key
     * @param amountSats Amount to reserve
     * @return A reservation if successful
     * @throws SpendingLimitException.WouldExceedLimit if the amount would exceed the limit
     */
    suspend fun tryReserveSpending(peerPubkey: String, amountSats: Long): SpendingReservation = mutex.withLock {
        val manager = ffiManager ?: throw SpendingLimitException.NotInitialized

        val ffiReservation = manager.tryReserveSpending(peerPubkey = peerPubkey, amountSats = amountSats)
        Logger.debug("Reserved $amountSats sats for $peerPubkey, id: ${ffiReservation.reservationId}", context = TAG)

        ffiReservation.toDomain()
    }

    /**
     * Commit a spending reservation (marks the spending as final)
     * This operation is idempotent.
     */
    suspend fun commitSpending(reservationId: String) = mutex.withLock {
        val manager = ffiManager ?: throw SpendingLimitException.NotInitialized

        manager.commitSpending(reservationId = reservationId)
        Logger.info("Committed spending for reservation: $reservationId", context = TAG)
    }

    /**
     * Commit a spending reservation (marks the spending as final)
     */
    suspend fun commitSpending(reservation: SpendingReservation) {
        commitSpending(reservation.reservationId)
    }

    /**
     * Rollback a spending reservation (releases the reserved amount)
     * This operation is idempotent.
     */
    suspend fun rollbackSpending(reservationId: String) = mutex.withLock {
        val manager = ffiManager ?: throw SpendingLimitException.NotInitialized

        manager.rollbackSpending(reservationId = reservationId)
        Logger.debug("Rolled back spending for reservation: $reservationId", context = TAG)
    }

    /**
     * Rollback a spending reservation (releases the reserved amount)
     */
    suspend fun rollbackSpending(reservation: SpendingReservation) {
        rollbackSpending(reservation.reservationId)
    }

    /**
     * Check if spending an amount would exceed the limit (non-blocking check)
     * @return Result containing whether the limit would be exceeded and remaining details
     */
    suspend fun wouldExceedLimit(peerPubkey: String, amountSats: Long): SpendingCheckResult = mutex.withLock {
        val manager = ffiManager ?: throw SpendingLimitException.NotInitialized

        val ffiResult = manager.wouldExceedSpendingLimit(peerPubkey = peerPubkey, amountSats = amountSats)
        ffiResult.toDomain()
    }

    /**
     * Get the number of active (in-flight) reservations
     */
    suspend fun activeReservationsCount(): UInt = mutex.withLock {
        val manager = ffiManager ?: throw SpendingLimitException.NotInitialized
        manager.activeReservationsCount()
    }

    /**
     * Execute a payment with automatic reserve/commit/rollback
     * @param peerPubkey The peer's public key
     * @param amountSats Amount to spend
     * @param payment The suspend payment operation to execute
     * @return The result of the payment
     */
    suspend fun <T> executeWithSpendingLimit(
        peerPubkey: String,
        amountSats: Long,
        payment: suspend () -> T,
    ): T {
        val reservation = tryReserveSpending(peerPubkey, amountSats)

        return runCatching {
            val result = payment()
            commitSpending(reservation)
            result
        }.onFailure {
            runCatching { rollbackSpending(reservation) }
        }.getOrThrow()
    }
}

/**
 * Peer spending limit configuration
 */
data class PeerSpendingLimit(
    val peerPubkey: String,
    val totalLimitSats: Long,
    val currentSpentSats: Long,
    val period: String,
    val remainingSats: Long,
    val lastReset: Long,
) {
    val usagePercent: Double
        get() = if (totalLimitSats > 0) {
            currentSpentSats.toDouble() / totalLimitSats.toDouble() * 100.0
        } else {
            0.0
        }
}

/**
 * Spending reservation token
 */
data class SpendingReservation(
    val reservationId: String,
    val peerPubkey: String,
    val amountSats: Long,
    val createdAt: Long,
)

/**
 * Result of a spending limit check
 */
data class SpendingCheckResult(
    val wouldExceed: Boolean,
    val currentSpentSats: Long,
    val remainingSats: Long,
    val checkAmountSats: Long,
)

/**
 * Spending limit errors
 */
sealed class SpendingLimitException(message: String) : Exception(message) {
    object NotInitialized : SpendingLimitException("SpendingLimitManager is not initialized")
    data class WouldExceedLimit(val remaining: Long) : SpendingLimitException(
        "Would exceed spending limit ($remaining sats remaining)"
    )
    object ReservationNotFound : SpendingLimitException("Spending reservation not found")
    object InvalidReservation : SpendingLimitException("Invalid reservation")
    data class StorageFailed(val reason: String) : SpendingLimitException("Storage operation failed: $reason")
}

private fun PeerSpendingLimitFfi.toDomain() = PeerSpendingLimit(
    peerPubkey = peerPubkey,
    totalLimitSats = totalLimitSats,
    currentSpentSats = currentSpentSats,
    period = period,
    remainingSats = remainingSats,
    lastReset = lastReset,
)

private fun SpendingReservationFfi.toDomain() = SpendingReservation(
    reservationId = reservationId,
    peerPubkey = peerPubkey,
    amountSats = amountSats,
    createdAt = createdAt,
)

private fun SpendingCheckResultFfi.toDomain() = SpendingCheckResult(
    wouldExceed = wouldExceed,
    currentSpentSats = currentSpentSats,
    remainingSats = remainingSats,
    checkAmountSats = checkAmountSats,
)
