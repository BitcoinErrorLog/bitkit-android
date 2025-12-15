package to.bitkit.paykit.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import to.bitkit.paykit.models.AutoPaySettings
import to.bitkit.paykit.models.PeerSpendingLimit
import to.bitkit.paykit.models.AutoPayRule
import to.bitkit.paykit.services.AutopayEvaluationResult
import to.bitkit.paykit.services.AutopayEvaluator
import to.bitkit.paykit.storage.AutoPayStorage
import to.bitkit.utils.Logger
import javax.inject.Inject

/**
 * ViewModel for Auto-Pay settings
 */
class AutoPayViewModel @Inject constructor(
    private val autoPayStorage: AutoPayStorage
) : ViewModel() {
    
    private val _settings = MutableStateFlow(AutoPaySettings())
    val settings: StateFlow<AutoPaySettings> = _settings.asStateFlow()
    
    private val _peerLimits = MutableStateFlow<List<PeerSpendingLimit>>(emptyList())
    val peerLimits: StateFlow<List<PeerSpendingLimit>> = _peerLimits.asStateFlow()
    
    private val _rules = MutableStateFlow<List<AutoPayRule>>(emptyList())
    val rules: StateFlow<List<AutoPayRule>> = _rules.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        loadSettings()
    }
    
    fun loadSettings() {
        viewModelScope.launch {
            _isLoading.value = true
            _settings.value = autoPayStorage.getSettings()
            _peerLimits.value = autoPayStorage.getPeerLimits()
            _rules.value = autoPayStorage.getRules()
            _isLoading.value = false
        }
    }
    
    fun saveSettings() {
        viewModelScope.launch {
            try {
                autoPayStorage.saveSettings(_settings.value)
            } catch (e: Exception) {
                Logger.error("AutoPayViewModel: Failed to save settings", e, context = "AutoPayViewModel")
            }
        }
    }
    
    fun addPeerLimit(limit: PeerSpendingLimit) {
        viewModelScope.launch {
            try {
                autoPayStorage.savePeerLimit(limit)
                loadSettings()
            } catch (e: Exception) {
                Logger.error("AutoPayViewModel: Failed to add peer limit", e, context = "AutoPayViewModel")
            }
        }
    }
    
    fun deletePeerLimit(limit: PeerSpendingLimit) {
        viewModelScope.launch {
            try {
                autoPayStorage.deletePeerLimit(limit.id)
                loadSettings()
            } catch (e: Exception) {
                Logger.error("AutoPayViewModel: Failed to delete peer limit", e, context = "AutoPayViewModel")
            }
        }
    }
    
    fun addRule(rule: AutoPayRule) {
        viewModelScope.launch {
            try {
                autoPayStorage.saveRule(rule)
                loadSettings()
            } catch (e: Exception) {
                Logger.error("AutoPayViewModel: Failed to add rule", e, context = "AutoPayViewModel")
            }
        }
    }
    
    fun deleteRule(rule: AutoPayRule) {
        viewModelScope.launch {
            try {
                autoPayStorage.deleteRule(rule.id)
                loadSettings()
            } catch (e: Exception) {
                Logger.error("AutoPayViewModel: Failed to delete rule", e, context = "AutoPayViewModel")
            }
        }
    }
    
    fun updateSettings(settings: AutoPaySettings) {
        _settings.value = settings
        saveSettings()
    }
    
    /**
     * Evaluate if a payment should be auto-approved
     * Implements AutopayEvaluator interface for PaymentRequestService
     */
    fun evaluate(peerPubkey: String, amount: Long, methodId: String): AutopayEvaluationResult {
        val settings = _settings.value
        
        // Check if autopay is enabled
        if (!settings.isEnabled) {
            return AutopayEvaluationResult.Denied("Auto-pay is disabled")
        }
        
        // Reset daily limits if needed
        val resetSettings = settings.resetIfNeeded()
        if (resetSettings != settings) {
            _settings.value = resetSettings
        }
        
        // Check global daily limit
        val globalDailyLimit = resetSettings.globalDailyLimitSats
        val currentDailySpent = resetSettings.currentDailySpentSats
        if (currentDailySpent + amount > globalDailyLimit) {
            return AutopayEvaluationResult.Denied("Would exceed daily limit")
        }
        
        // Check peer-specific limit
        val peerLimit = _peerLimits.value.firstOrNull { it.peerPubkey == peerPubkey }
        peerLimit?.let { limit ->
            val resetLimit = limit.resetIfNeeded()
            if (resetLimit != limit) {
                // Update the limit in storage (launch coroutine for suspend function)
                viewModelScope.launch {
                    autoPayStorage.savePeerLimit(resetLimit)
                }
                // Note: We don't reload here to avoid blocking, but the reset is saved
            }
            
            val limitToCheck = if (resetLimit != limit) resetLimit else limit
            if (limitToCheck.spentSats + amount > limitToCheck.limitSats) {
                return AutopayEvaluationResult.Denied("Would exceed peer limit")
            }
        }
        
        // Check auto-pay rules
        val matchingRule = _rules.value.firstOrNull { rule ->
            rule.matches(amount, methodId, peerPubkey)
        }
        
        if (matchingRule != null) {
            return AutopayEvaluationResult.Approved(
                ruleId = matchingRule.id,
                ruleName = matchingRule.name
            )
        }
        
        return AutopayEvaluationResult.NeedsApproval
    }
}

// Make AutoPayViewModel implement AutopayEvaluator
fun AutoPayViewModel.asAutopayEvaluator(): AutopayEvaluator {
    return object : AutopayEvaluator {
        override fun evaluate(peerPubkey: String, amount: Long, methodId: String): AutopayEvaluationResult {
            return this@asAutopayEvaluator.evaluate(peerPubkey, amount, methodId)
        }
    }
}

