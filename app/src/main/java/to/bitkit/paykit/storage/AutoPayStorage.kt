package to.bitkit.paykit.storage

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import to.bitkit.paykit.models.AutoPaySettings
import to.bitkit.paykit.models.PeerSpendingLimit
import to.bitkit.paykit.models.AutoPayRule
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages persistent storage of auto-pay settings using Keychain.
 */
@Singleton
class AutoPayStorage @Inject constructor(
    private val keychain: PaykitKeychainStorage
) {
    companion object {
        private const val TAG = "AutoPayStorage"
    }
    
    private var settingsCache: AutoPaySettings? = null
    private var limitsCache: List<PeerSpendingLimit>? = null
    private var rulesCache: List<AutoPayRule>? = null
    private val identityName: String = "default"
    
    private val settingsKey: String
        get() = "autopay.$identityName.settings"
    
    private val limitsKey: String
        get() = "autopay.$identityName.limits"
    
    private val rulesKey: String
        get() = "autopay.$identityName.rules"
    
    // MARK: - Settings
    
    fun getSettings(): AutoPaySettings {
        settingsCache?.let { return it.resetIfNeeded() }
        
        return try {
            val data = keychain.retrieve(settingsKey) ?: return AutoPaySettings()
            val json = String(data)
            val settings = Json.decodeFromString<AutoPaySettings>(json).resetIfNeeded()
            settingsCache = settings
            settings
        } catch (e: Exception) {
            Logger.error("AutoPayStorage: Failed to load settings", e, context = TAG)
            AutoPaySettings()
        }
    }
    
    suspend fun saveSettings(settings: AutoPaySettings) {
        try {
            val json = Json.encodeToString(settings)
            keychain.store(settingsKey, json.toByteArray())
            settingsCache = settings
        } catch (e: Exception) {
            Logger.error("AutoPayStorage: Failed to save settings", e, context = TAG)
            throw PaykitStorageException.SaveFailed(settingsKey)
        }
    }
    
    // MARK: - Peer Limits
    
    fun getPeerLimits(): List<PeerSpendingLimit> {
        limitsCache?.let { cached ->
            return cached.map { limit -> limit.resetIfNeeded() }
        }
        
        return try {
            val data = keychain.retrieve(limitsKey) ?: return emptyList()
            val json = String(data)
            val decoded: List<PeerSpendingLimit> = Json.decodeFromString(json)
            val limits: List<PeerSpendingLimit> = decoded.map { limit -> limit.resetIfNeeded() }
            limitsCache = limits
            limits
        } catch (e: Exception) {
            Logger.error("AutoPayStorage: Failed to load limits", e, context = TAG)
            emptyList()
        }
    }
    
    suspend fun savePeerLimit(limit: PeerSpendingLimit) {
        val limits = getPeerLimits().toMutableList()
        val index = limits.indexOfFirst { it.id == limit.id }
        
        if (index >= 0) {
            limits[index] = limit
        } else {
            limits.add(limit)
        }
        
        persistLimits(limits)
    }
    
    suspend fun deletePeerLimit(id: String) {
        val limits = getPeerLimits().toMutableList()
        limits.removeAll { it.id == id }
        persistLimits(limits)
    }
    
    // MARK: - Rules
    
    fun getRules(): List<AutoPayRule> {
        rulesCache?.let { return it }
        
        return try {
            val data = keychain.retrieve(rulesKey) ?: return emptyList()
            val json = String(data)
            val rules = Json.decodeFromString<List<AutoPayRule>>(json)
            rulesCache = rules
            rules
        } catch (e: Exception) {
            Logger.error("AutoPayStorage: Failed to load rules", e, context = TAG)
            emptyList()
        }
    }
    
    suspend fun saveRule(rule: AutoPayRule) {
        val rules = getRules().toMutableList()
        val index = rules.indexOfFirst { it.id == rule.id }
        
        if (index >= 0) {
            rules[index] = rule
        } else {
            rules.add(rule)
        }
        
        persistRules(rules)
    }
    
    suspend fun deleteRule(id: String) {
        val rules = getRules().toMutableList()
        rules.removeAll { it.id == id }
        persistRules(rules)
    }
    
    // MARK: - Private
    
    private suspend fun persistLimits(limits: List<PeerSpendingLimit>) {
        try {
            val json = Json.encodeToString(limits)
            keychain.store(limitsKey, json.toByteArray())
            limitsCache = limits
        } catch (e: Exception) {
            Logger.error("AutoPayStorage: Failed to save limits", e, context = TAG)
            throw PaykitStorageException.SaveFailed(limitsKey)
        }
    }
    
    private suspend fun persistRules(rules: List<AutoPayRule>) {
        try {
            val json = Json.encodeToString(rules)
            keychain.store(rulesKey, json.toByteArray())
            rulesCache = rules
        } catch (e: Exception) {
            Logger.error("AutoPayStorage: Failed to save rules", e, context = TAG)
            throw PaykitStorageException.SaveFailed(rulesKey)
        }
    }
}

