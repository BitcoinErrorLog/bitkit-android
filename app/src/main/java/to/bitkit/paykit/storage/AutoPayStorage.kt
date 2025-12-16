package to.bitkit.paykit.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.bitkit.paykit.models.AutoPayRule
import to.bitkit.paykit.models.AutoPaySettings
import to.bitkit.paykit.models.PeerSpendingLimit
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage for auto-pay rules and settings with persistent keychain storage
 */
@Singleton
class AutoPayStorage @Inject constructor(
    private val keychain: PaykitKeychainStorage,
) {
    companion object {
        private const val TAG = "AutoPayStorage"
        private const val SETTINGS_KEY = "autopay.settings"
        private const val PEER_LIMITS_KEY = "autopay.peer_limits"
        private const val RULES_KEY = "autopay.rules"
    }

    private var settingsCache: AutoPaySettings? = null
    private var peerLimitsCache: MutableMap<String, PeerSpendingLimit>? = null
    private var rulesCache: MutableMap<String, AutoPayRule>? = null
    private var cacheInitialized = false

    private fun ensureCacheLoaded() {
        if (cacheInitialized) return
        loadFromStorage()
        cacheInitialized = true
    }

    private fun loadFromStorage() {
        // Load settings
        try {
            val data = keychain.retrieve(SETTINGS_KEY)
            if (data != null) {
                val json = String(data)
                settingsCache = Json.decodeFromString<AutoPaySettings>(json)
                Logger.debug("Loaded auto-pay settings from storage", context = TAG)
            } else {
                settingsCache = AutoPaySettings()
            }
        } catch (e: Exception) {
            Logger.error("Failed to load auto-pay settings from storage", e, context = TAG)
            settingsCache = AutoPaySettings()
        }

        // Load peer limits
        try {
            val data = keychain.retrieve(PEER_LIMITS_KEY)
            if (data != null) {
                val json = String(data)
                val wrapper = Json.decodeFromString<PeerLimitsListWrapper>(json)
                peerLimitsCache = wrapper.limits.associateBy { it.id }.toMutableMap()
                Logger.debug("Loaded ${peerLimitsCache?.size ?: 0} peer limits from storage", context = TAG)
            } else {
                peerLimitsCache = mutableMapOf()
            }
        } catch (e: Exception) {
            Logger.error("Failed to load peer limits from storage", e, context = TAG)
            peerLimitsCache = mutableMapOf()
        }

        // Load rules
        try {
            val data = keychain.retrieve(RULES_KEY)
            if (data != null) {
                val json = String(data)
                val wrapper = Json.decodeFromString<RulesListWrapper>(json)
                rulesCache = wrapper.rules.associateBy { it.id }.toMutableMap()
                Logger.debug("Loaded ${rulesCache?.size ?: 0} rules from storage", context = TAG)
            } else {
                rulesCache = mutableMapOf()
            }
        } catch (e: Exception) {
            Logger.error("Failed to load rules from storage", e, context = TAG)
            rulesCache = mutableMapOf()
        }
    }

    private suspend fun persistSettings() {
        try {
            val settings = settingsCache ?: AutoPaySettings()
            val json = Json.encodeToString(settings)
            keychain.store(SETTINGS_KEY, json.toByteArray())
            Logger.debug("Persisted auto-pay settings", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to persist auto-pay settings", e, context = TAG)
            throw PaykitStorageException.SaveFailed(SETTINGS_KEY)
        }
    }

    private suspend fun persistPeerLimits() {
        try {
            val wrapper = PeerLimitsListWrapper(peerLimitsCache?.values?.toList() ?: emptyList())
            val json = Json.encodeToString(wrapper)
            keychain.store(PEER_LIMITS_KEY, json.toByteArray())
            Logger.debug("Persisted ${peerLimitsCache?.size ?: 0} peer limits", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to persist peer limits", e, context = TAG)
            throw PaykitStorageException.SaveFailed(PEER_LIMITS_KEY)
        }
    }

    private suspend fun persistRules() {
        try {
            val wrapper = RulesListWrapper(rulesCache?.values?.toList() ?: emptyList())
            val json = Json.encodeToString(wrapper)
            keychain.store(RULES_KEY, json.toByteArray())
            Logger.debug("Persisted ${rulesCache?.size ?: 0} rules", context = TAG)
        } catch (e: Exception) {
            Logger.error("Failed to persist rules", e, context = TAG)
            throw PaykitStorageException.SaveFailed(RULES_KEY)
        }
    }

    /**
     * Check if auto-pay is globally enabled
     */
    fun isAutoPayEnabled(): Boolean {
        ensureCacheLoaded()
        return settingsCache?.isEnabled ?: false
    }

    /**
     * Enable or disable auto-pay globally
     */
    suspend fun setAutoPayEnabled(enabled: Boolean) {
        ensureCacheLoaded()
        settingsCache = (settingsCache ?: AutoPaySettings()).copy(isEnabled = enabled)
        persistSettings()
    }

    /**
     * Get current auto-pay settings
     */
    suspend fun getSettings(): AutoPaySettings {
        ensureCacheLoaded()
        return settingsCache ?: AutoPaySettings()
    }

    /**
     * Save auto-pay settings
     */
    suspend fun saveSettings(settings: AutoPaySettings) {
        ensureCacheLoaded()
        settingsCache = settings
        persistSettings()
    }

    /**
     * Get all peer spending limits
     */
    suspend fun getPeerLimits(): List<PeerSpendingLimit> {
        ensureCacheLoaded()
        return peerLimitsCache?.values?.toList() ?: emptyList()
    }

    /**
     * Get peer spending limit by peer pubkey
     */
    fun getSpendingLimitForPeer(peerPubkey: String): PeerSpendingLimit? {
        ensureCacheLoaded()
        return peerLimitsCache?.values?.firstOrNull { it.peerPubkey == peerPubkey }
    }

    /**
     * Save a peer spending limit
     */
    suspend fun savePeerLimit(limit: PeerSpendingLimit) {
        ensureCacheLoaded()
        peerLimitsCache?.set(limit.id, limit)
        persistPeerLimits()
    }

    /**
     * Delete a peer spending limit by ID
     */
    suspend fun deletePeerLimit(id: String) {
        ensureCacheLoaded()
        peerLimitsCache?.remove(id)
        persistPeerLimits()
    }

    /**
     * Get auto-pay rule for a specific peer (for AutoPayEvaluator compatibility)
     */
    fun getAutoPayRuleForPeer(peerPubkey: String): ServiceAutoPayRule? {
        ensureCacheLoaded()
        val rule = rulesCache?.values?.firstOrNull { peerPubkey in it.allowedPeers || it.peerPubkey == peerPubkey }
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
    suspend fun getRules(): List<AutoPayRule> {
        ensureCacheLoaded()
        return rulesCache?.values?.toList() ?: emptyList()
    }

    /**
     * Save an auto-pay rule
     */
    suspend fun saveRule(rule: AutoPayRule) {
        ensureCacheLoaded()
        rulesCache?.set(rule.id, rule)
        persistRules()
    }

    /**
     * Delete an auto-pay rule by ID
     */
    suspend fun deleteRule(id: String) {
        ensureCacheLoaded()
        rulesCache?.remove(id)
        persistRules()
    }

    /**
     * Set auto-pay rule for a peer (legacy API for AutoPayEvaluator)
     */
    suspend fun setAutoPayRuleForPeer(rule: ServiceAutoPayRule) {
        ensureCacheLoaded()
        val newRule = AutoPayRule(
            id = rule.peerPubkey,
            name = rule.name,
            peerPubkey = rule.peerPubkey,
            isEnabled = rule.enabled,
            maxAmountSats = if (rule.maxAmountPerTransaction > 0) rule.maxAmountPerTransaction else null,
            allowedPeers = listOf(rule.peerPubkey),
            requireConfirmation = rule.requireConfirmation,
        )
        rulesCache?.set(newRule.id, newRule)
        persistRules()
    }

    /**
     * Remove auto-pay rule for a peer
     */
    suspend fun removeAutoPayRuleForPeer(peerPubkey: String) {
        ensureCacheLoaded()
        val toRemove = rulesCache?.values?.firstOrNull { peerPubkey in it.allowedPeers || it.peerPubkey == peerPubkey }
        toRemove?.let { rulesCache?.remove(it.id) }
        persistRules()
    }

    /**
     * Get all peer rules (legacy API)
     */
    fun getAllPeerRules(): List<ServiceAutoPayRule> {
        ensureCacheLoaded()
        return rulesCache?.values?.flatMap { rule ->
            val peers = if (rule.peerPubkey != null) {
                listOf(rule.peerPubkey!!)
            } else {
                rule.allowedPeers
            }
            peers.map { pubkey ->
                ServiceAutoPayRule(
                    peerPubkey = pubkey,
                    name = rule.name,
                    enabled = rule.isEnabled,
                    maxAmountPerTransaction = rule.maxAmountSats ?: 0L,
                    requireConfirmation = rule.requireConfirmation,
                )
            }
        } ?: emptyList()
    }
}

/**
 * Service-layer auto-pay rule type alias for compatibility with AutoPayEvaluator.
 * This is the same as to.bitkit.paykit.services.AutoPayRule but we define it here
 * to avoid circular dependencies.
 */
typealias ServiceAutoPayRule = to.bitkit.paykit.services.AutoPayRule

@Serializable
private data class PeerLimitsListWrapper(
    val limits: List<PeerSpendingLimit>,
)

@Serializable
private data class RulesListWrapper(
    val rules: List<AutoPayRule>,
)
