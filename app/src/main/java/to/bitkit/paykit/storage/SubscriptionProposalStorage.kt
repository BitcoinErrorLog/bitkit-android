package to.bitkit.paykit.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.bitkit.paykit.workers.DiscoveredSubscriptionProposal
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages persistent storage of subscription proposals.
 *
 * Proposals are persisted per-identity to avoid leaking data across identities.
 * Seen IDs are tracked to prevent duplicate notifications.
 *
 * Accept/decline is local-only (no remote delete on provider storage).
 */
@Singleton
class SubscriptionProposalStorage @Inject constructor(
    private val keychain: PaykitKeychainStorage,
) {
    companion object {
        private const val TAG = "SubscriptionProposalStorage"
    }

    private var proposalsCache: MutableMap<String, List<StoredProposal>>? = null
    private var seenIdsCache: MutableMap<String, Set<String>>? = null
    private var declinedIdsCache: MutableMap<String, Set<String>>? = null
    private var sentProposalsCache: MutableMap<String, List<SentProposal>>? = null

    private fun proposalsKey(identityPubkey: String) = "proposals.$identityPubkey"
    private fun seenIdsKey(identityPubkey: String) = "proposals.seen.$identityPubkey"
    private fun declinedIdsKey(identityPubkey: String) = "proposals.declined.$identityPubkey"
    private fun sentProposalsKey(identityPubkey: String) = "proposals.sent.$identityPubkey"

    /**
     * List all stored proposals for the given identity.
     */
    fun listProposals(identityPubkey: String): List<StoredProposal> {
        if (proposalsCache?.containsKey(identityPubkey) == true) {
            return proposalsCache!![identityPubkey]!!
        }

        return try {
            val data = keychain.retrieve(proposalsKey(identityPubkey)) ?: return emptyList()
            val json = String(data)
            val proposals = Json.decodeFromString<List<StoredProposal>>(json)
            if (proposalsCache == null) proposalsCache = mutableMapOf()
            proposalsCache!![identityPubkey] = proposals
            proposals
        } catch (e: Exception) {
            Logger.error("Failed to load proposals", e, context = TAG)
            emptyList()
        }
    }

    /**
     * Get pending proposals (not accepted or declined).
     */
    fun pendingProposals(identityPubkey: String): List<StoredProposal> {
        val declined = getDeclinedIds(identityPubkey)
        return listProposals(identityPubkey).filter { it.status == ProposalStatus.PENDING && it.id !in declined }
    }

    /**
     * Save a discovered proposal. Returns true if this is a new proposal.
     */
    suspend fun saveProposal(
        identityPubkey: String,
        proposal: DiscoveredSubscriptionProposal,
    ): Boolean {
        val proposals = listProposals(identityPubkey).toMutableList()
        val existing = proposals.indexOfFirst { it.id == proposal.subscriptionId }

        if (existing >= 0) {
            return false
        }

        proposals.add(
            StoredProposal(
                id = proposal.subscriptionId,
                providerPubkey = proposal.providerPubkey,
                amountSats = proposal.amountSats,
                description = proposal.description,
                frequency = proposal.frequency,
                createdAt = proposal.createdAt,
                status = ProposalStatus.PENDING,
            ),
        )
        persistProposals(identityPubkey, proposals)
        return true
    }

    /**
     * Mark a proposal as accepted.
     */
    suspend fun markAccepted(identityPubkey: String, proposalId: String) {
        val proposals = listProposals(identityPubkey).toMutableList()
        val index = proposals.indexOfFirst { it.id == proposalId }
        if (index >= 0) {
            proposals[index] = proposals[index].copy(status = ProposalStatus.ACCEPTED)
            persistProposals(identityPubkey, proposals)
        }
    }

    /**
     * Mark a proposal as declined (local-only, hides from inbox).
     */
    suspend fun markDeclined(identityPubkey: String, proposalId: String) {
        val declined = getDeclinedIds(identityPubkey).toMutableSet()
        declined.add(proposalId)
        persistDeclinedIds(identityPubkey, declined)

        val proposals = listProposals(identityPubkey).toMutableList()
        val index = proposals.indexOfFirst { it.id == proposalId }
        if (index >= 0) {
            proposals[index] = proposals[index].copy(status = ProposalStatus.DECLINED)
            persistProposals(identityPubkey, proposals)
        }
    }

    /**
     * Check if a proposal has been seen (notified about).
     */
    fun hasSeen(identityPubkey: String, proposalId: String): Boolean {
        return getSeenIds(identityPubkey).contains(proposalId)
    }

    /**
     * Mark a proposal as seen (prevents duplicate notifications).
     */
    suspend fun markSeen(identityPubkey: String, proposalId: String) {
        val seen = getSeenIds(identityPubkey).toMutableSet()
        seen.add(proposalId)
        persistSeenIds(identityPubkey, seen)
    }

    /**
     * Clear all data for an identity.
     */
    suspend fun clearAll(identityPubkey: String) {
        keychain.delete(proposalsKey(identityPubkey))
        keychain.delete(seenIdsKey(identityPubkey))
        keychain.delete(declinedIdsKey(identityPubkey))
        keychain.delete(sentProposalsKey(identityPubkey))
        proposalsCache?.remove(identityPubkey)
        seenIdsCache?.remove(identityPubkey)
        declinedIdsCache?.remove(identityPubkey)
        sentProposalsCache?.remove(identityPubkey)
    }

    /**
     * Invalidate all in-memory caches to ensure fresh reads from storage.
     */
    fun invalidateCache() {
        proposalsCache = null
        seenIdsCache = null
        declinedIdsCache = null
        sentProposalsCache = null
    }

    /**
     * Save a sent (outgoing) proposal for tracking.
     */
    suspend fun saveSentProposal(
        identityPubkey: String,
        proposalId: String,
        recipientPubkey: String,
        amountSats: Long,
        frequency: String,
        description: String?,
    ) {
        val proposals = listSentProposals(identityPubkey).toMutableList()
        if (proposals.any { it.id == proposalId }) return

        proposals.add(
            SentProposal(
                id = proposalId,
                recipientPubkey = recipientPubkey,
                amountSats = amountSats,
                frequency = frequency,
                description = description,
                sentAt = System.currentTimeMillis(),
                status = SentProposalStatus.PENDING,
            ),
        )
        persistSentProposals(identityPubkey, proposals)
    }

    /**
     * List all sent proposals for the given identity.
     */
    fun listSentProposals(identityPubkey: String): List<SentProposal> {
        if (sentProposalsCache?.containsKey(identityPubkey) == true) {
            return sentProposalsCache!![identityPubkey]!!
        }

        return try {
            val data = keychain.retrieve(sentProposalsKey(identityPubkey)) ?: return emptyList()
            val json = String(data)
            val proposals = Json.decodeFromString<List<SentProposal>>(json)
            if (sentProposalsCache == null) sentProposalsCache = mutableMapOf()
            sentProposalsCache!![identityPubkey] = proposals
            proposals
        } catch (e: Exception) {
            Logger.error("Failed to load sent proposals", e, context = TAG)
            emptyList()
        }
    }

    /**
     * Mark a sent proposal as accepted (subscriber accepted).
     */
    suspend fun markSentAccepted(identityPubkey: String, proposalId: String) {
        val proposals = listSentProposals(identityPubkey).toMutableList()
        val index = proposals.indexOfFirst { it.id == proposalId }
        if (index >= 0) {
            proposals[index] = proposals[index].copy(status = SentProposalStatus.ACCEPTED)
            persistSentProposals(identityPubkey, proposals)
        }
    }

    /**
     * Delete a sent proposal from local storage.
     */
    suspend fun deleteSentProposal(identityPubkey: String, proposalId: String) {
        val proposals = listSentProposals(identityPubkey).toMutableList()
        proposals.removeAll { it.id == proposalId }
        persistSentProposals(identityPubkey, proposals)
        Logger.debug("Deleted sent proposal: $proposalId", context = TAG)
    }

    /**
     * Mark a received proposal as seen (for dismissing).
     */
    suspend fun markAsSeen(identityPubkey: String, proposalId: String) {
        val seenIds = getSeenIds(identityPubkey).toMutableSet()
        seenIds.add(proposalId)
        persistSeenIds(identityPubkey, seenIds)
    }

    private suspend fun persistSentProposals(identityPubkey: String, proposals: List<SentProposal>) {
        try {
            val json = Json.encodeToString(proposals)
            keychain.store(sentProposalsKey(identityPubkey), json.toByteArray())
            if (sentProposalsCache == null) sentProposalsCache = mutableMapOf()
            sentProposalsCache!![identityPubkey] = proposals
        } catch (e: Exception) {
            Logger.error("Failed to persist sent proposals", e, context = TAG)
            throw PaykitStorageException.SaveFailed(sentProposalsKey(identityPubkey))
        }
    }

    private fun getSeenIds(identityPubkey: String): Set<String> {
        if (seenIdsCache?.containsKey(identityPubkey) == true) {
            return seenIdsCache!![identityPubkey]!!
        }

        return try {
            val data = keychain.retrieve(seenIdsKey(identityPubkey)) ?: return emptySet()
            val json = String(data)
            val ids = Json.decodeFromString<Set<String>>(json)
            if (seenIdsCache == null) seenIdsCache = mutableMapOf()
            seenIdsCache!![identityPubkey] = ids
            ids
        } catch (e: Exception) {
            Logger.error("Failed to load seen proposal IDs", e, context = TAG)
            emptySet()
        }
    }

    private fun getDeclinedIds(identityPubkey: String): Set<String> {
        if (declinedIdsCache?.containsKey(identityPubkey) == true) {
            return declinedIdsCache!![identityPubkey]!!
        }

        return try {
            val data = keychain.retrieve(declinedIdsKey(identityPubkey)) ?: return emptySet()
            val json = String(data)
            val ids = Json.decodeFromString<Set<String>>(json)
            if (declinedIdsCache == null) declinedIdsCache = mutableMapOf()
            declinedIdsCache!![identityPubkey] = ids
            ids
        } catch (e: Exception) {
            Logger.error("Failed to load declined proposal IDs", e, context = TAG)
            emptySet()
        }
    }

    private suspend fun persistProposals(identityPubkey: String, proposals: List<StoredProposal>) {
        try {
            val json = Json.encodeToString(proposals)
            keychain.store(proposalsKey(identityPubkey), json.toByteArray())
            if (proposalsCache == null) proposalsCache = mutableMapOf()
            proposalsCache!![identityPubkey] = proposals
        } catch (e: Exception) {
            Logger.error("Failed to persist proposals", e, context = TAG)
            throw PaykitStorageException.SaveFailed(proposalsKey(identityPubkey))
        }
    }

    private suspend fun persistSeenIds(identityPubkey: String, ids: Set<String>) {
        try {
            val json = Json.encodeToString(ids)
            keychain.store(seenIdsKey(identityPubkey), json.toByteArray())
            if (seenIdsCache == null) seenIdsCache = mutableMapOf()
            seenIdsCache!![identityPubkey] = ids
        } catch (e: Exception) {
            Logger.error("Failed to persist seen proposal IDs", e, context = TAG)
        }
    }

    private suspend fun persistDeclinedIds(identityPubkey: String, ids: Set<String>) {
        try {
            val json = Json.encodeToString(ids)
            keychain.store(declinedIdsKey(identityPubkey), json.toByteArray())
            if (declinedIdsCache == null) declinedIdsCache = mutableMapOf()
            declinedIdsCache!![identityPubkey] = ids
        } catch (e: Exception) {
            Logger.error("Failed to persist declined proposal IDs", e, context = TAG)
        }
    }
}

/**
 * A stored subscription proposal with local status.
 */
@Serializable
data class StoredProposal(
    val id: String,
    val providerPubkey: String,
    val amountSats: Long,
    val description: String?,
    val frequency: String,
    val createdAt: Long,
    val status: ProposalStatus,
)

@Serializable
enum class ProposalStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
}

/**
 * A sent (outgoing) subscription proposal for tracking.
 */
@Serializable
data class SentProposal(
    val id: String,
    val recipientPubkey: String,
    val amountSats: Long,
    val frequency: String,
    val description: String?,
    val sentAt: Long,
    val status: SentProposalStatus,
)

@Serializable
enum class SentProposalStatus {
    PENDING,
    ACCEPTED,
    EXPIRED,
}
