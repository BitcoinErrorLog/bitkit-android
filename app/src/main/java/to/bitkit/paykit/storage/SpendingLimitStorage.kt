package to.bitkit.paykit.storage

import to.bitkit.paykit.services.SpendingLimit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage for spending limits
 */
@Singleton
class SpendingLimitStorage @Inject constructor() {

    // In-memory storage - TODO: persist to disk
    private val peerLimits = mutableMapOf<String, SpendingLimit>()
    private var globalLimit: SpendingLimit? = null

    /**
     * Get spending limit for a specific peer
     */
    fun getSpendingLimitForPeer(peerPubkey: String): SpendingLimit? = peerLimits[peerPubkey]

    /**
     * Set spending limit for a peer
     */
    fun setSpendingLimitForPeer(peerPubkey: String, limit: SpendingLimit) {
        peerLimits[peerPubkey] = limit
    }

    /**
     * Remove spending limit for a peer
     */
    fun removeSpendingLimitForPeer(peerPubkey: String) {
        peerLimits.remove(peerPubkey)
    }

    /**
     * Get global spending limit
     */
    fun getGlobalSpendingLimit(): SpendingLimit? = globalLimit

    /**
     * Set global spending limit
     */
    fun setGlobalSpendingLimit(limit: SpendingLimit) {
        globalLimit = limit
    }

    /**
     * Remove global spending limit
     */
    fun removeGlobalSpendingLimit() {
        globalLimit = null
    }

    /**
     * Get all peer limits
     */
    fun getAllPeerLimits(): Map<String, SpendingLimit> = peerLimits.toMap()

    /**
     * Record spending against a peer's limit
     */
    fun recordSpending(peerPubkey: String, amountSats: Long) {
        peerLimits[peerPubkey]?.let { limit ->
            peerLimits[peerPubkey] = limit.copy(
                currentSpentSats = limit.currentSpentSats + amountSats,
            )
        }

        // Also record against global limit
        globalLimit?.let { limit ->
            globalLimit = limit.copy(
                currentSpentSats = limit.currentSpentSats + amountSats,
            )
        }
    }

    /**
     * Reset spending counters for a period
     */
    fun resetSpending(peerPubkey: String) {
        peerLimits[peerPubkey]?.let { limit ->
            peerLimits[peerPubkey] = limit.copy(
                currentSpentSats = 0,
                lastResetTimestamp = System.currentTimeMillis(),
            )
        }
    }

    /**
     * Reset global spending counter
     */
    fun resetGlobalSpending() {
        globalLimit?.let { limit ->
            globalLimit = limit.copy(
                currentSpentSats = 0,
                lastResetTimestamp = System.currentTimeMillis(),
            )
        }
    }
}

