package to.bitkit.paykit.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import to.bitkit.paykit.models.AutoPayRule
import to.bitkit.paykit.models.AutoPaySettings
import to.bitkit.paykit.models.PeerSpendingLimit
import to.bitkit.paykit.services.AutoPayEvaluatorService
import to.bitkit.paykit.services.AutopayEvaluationResult
import to.bitkit.paykit.services.IAutopayEvaluator
import to.bitkit.paykit.storage.AutoPayStorage
import to.bitkit.utils.Logger
import javax.inject.Inject

/**
 * ViewModel for Auto-Pay settings
 */
@HiltViewModel
class AutoPayViewModel @Inject constructor(
    private val autoPayStorage: AutoPayStorage,
    private val autoPayEvaluatorService: AutoPayEvaluatorService,
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

            // Keep evaluator service in sync
            autoPayEvaluatorService.loadSettings()

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

    fun updateRule(rule: AutoPayRule) {
        viewModelScope.launch {
            try {
                autoPayStorage.saveRule(rule)
                loadSettings()
            } catch (e: Exception) {
                Logger.error("AutoPayViewModel: Failed to update rule", e, context = "AutoPayViewModel")
            }
        }
    }

    fun updateSettings(settings: AutoPaySettings) {
        _settings.value = settings
        saveSettings()
    }

    /**
     * Evaluate if a payment should be auto-approved.
     * Delegates to AutoPayEvaluatorService for consistent evaluation logic.
     */
    fun evaluate(peerPubkey: String, amount: Long, methodId: String): AutopayEvaluationResult {
        return autoPayEvaluatorService.evaluate(peerPubkey, amount, methodId)
    }
}

// Make AutoPayViewModel implement IAutopayEvaluator
fun AutoPayViewModel.asAutopayEvaluator(): IAutopayEvaluator {
    return object : IAutopayEvaluator {
        override fun evaluate(peerPubkey: String, amount: Long, methodId: String): AutopayEvaluationResult {
            return this@asAutopayEvaluator.evaluate(peerPubkey, amount, methodId)
        }
    }
}
