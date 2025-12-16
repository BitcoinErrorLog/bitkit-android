package to.bitkit.paykit.storage

import to.bitkit.paykit.models.AutoPaySettings
import to.bitkit.paykit.models.AutoPayRule
import to.bitkit.paykit.models.PeerSpendingLimit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage for auto-pay rules and settings
 */
@Singleton
class AutoPayStorage @Inject constructor() {

    // In-memory storage - TODO: persist to disk
    private var settings = AutoPaySettings()
    private val peerLimits = mutableMapOf<String, PeerSpendingLimit>()
    private val rules = mutableMapOf<String, AutoPayRule>()

    /**
     * Check if auto-pay is globally enabled
     */
    fun isAutoPayEnabled(): Boolean = settings.isEnabled

    /**
     * Enable or disable auto-pay globally
     */
    fun setAutoPayEnabled(enabled: Boolean) {
        settings = settings.copy(isEnabled = enabled)
    }

    /**
     * Get current auto-pay settings
     */
    suspend fun getSettings(): AutoPaySettings = settings

    /**
     * Save auto-pay settings
     */
    suspend fun saveSettings(settings: AutoPaySettings) {
        this.settings = settings
    }

    /**
     * Get all peer spending limits
     */
    suspend fun getPeerLimits(): List<PeerSpendingLimit> = peerLimits.values.toList()

    /**
     * Get peer spending limit by peer pubkey
     */
    fun getSpendingLimitForPeer(peerPubkey: String): PeerSpendingLimit? =
        peerLimits.values.firstOrNull { it.peerPubkey == peerPubkey }

    /**
     * Save a peer spending limit
     */
    suspend fun savePeerLimit(limit: PeerSpendingLimit) {
        peerLimits[limit.id] = limit
    }

    /**
     * Delete a peer spending limit by ID
     */
    suspend fun deletePeerLimit(id: String) {
        peerLimits.remove(id)
    }

    /**
     * Get auto-pay rule for a specific peer (for AutoPayEvaluator compatibility)
     */
    fun getAutoPayRuleForPeer(peerPubkey: String): ServiceAutoPayRule? {
        val rule = rules.values.firstOrNull { peerPubkey in it.allowedPeers }
        return rule?.let {
            ServiceAutoPayRule(
                peerPubkey = peerPubkey,
                name = it.name,
                enabled = it.isEnabled,
                maxAmountPerTransaction = it.maxAmountSats ?: 0L,
                requireConfirmation = it.requireConfirmation,
            )
        }
    }

    /**
     * Get all auto-pay rules
     */
    suspend fun getRules(): List<AutoPayRule> = rules.values.toList()

    /**
     * Save an auto-pay rule
     */
    suspend fun saveRule(rule: AutoPayRule) {
        rules[rule.id] = rule
    }

    /**
     * Delete an auto-pay rule by ID
     */
    suspend fun deleteRule(id: String) {
        rules.remove(id)
    }

    /**
     * Set auto-pay rule for a peer (legacy API for AutoPayEvaluator)
     */
    fun setAutoPayRuleForPeer(rule: ServiceAutoPayRule) {
        val newRule = AutoPayRule(
            id = rule.peerPubkey,
            name = rule.name,
            isEnabled = rule.enabled,
            maxAmountSats = if (rule.maxAmountPerTransaction > 0) rule.maxAmountPerTransaction else null,
            allowedPeers = listOf(rule.peerPubkey),
            requireConfirmation = rule.requireConfirmation,
        )
        rules[newRule.id] = newRule
    }

    /**
     * Remove auto-pay rule for a peer
     */
    fun removeAutoPayRuleForPeer(peerPubkey: String) {
        val toRemove = rules.values.firstOrNull { peerPubkey in it.allowedPeers }
        toRemove?.let { rules.remove(it.id) }
    }

    /**
     * Get all peer rules (legacy API)
     */
    fun getAllPeerRules(): List<ServiceAutoPayRule> =
        rules.values.flatMap { rule ->
            rule.allowedPeers.map { pubkey ->
                ServiceAutoPayRule(
                    peerPubkey = pubkey,
                    name = rule.name,
                    enabled = rule.isEnabled,
                    maxAmountPerTransaction = rule.maxAmountSats ?: 0L,
                    requireConfirmation = rule.requireConfirmation,
                )
            }
        }
}

/**
 * Service-layer auto-pay rule type alias for compatibility with AutoPayEvaluator.
 * This is the same as to.bitkit.paykit.services.AutoPayRule but we define it here
 * to avoid circular dependencies.
 */
typealias ServiceAutoPayRule = to.bitkit.paykit.services.AutoPayRule
