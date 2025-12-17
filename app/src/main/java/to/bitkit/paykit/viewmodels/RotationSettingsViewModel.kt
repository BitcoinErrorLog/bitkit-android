package to.bitkit.paykit.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import to.bitkit.paykit.storage.RotationSettings
import to.bitkit.paykit.storage.RotationSettingsStorage
import to.bitkit.utils.Logger
import javax.inject.Inject

@HiltViewModel
class RotationSettingsViewModel @Inject constructor(
    private val rotationSettingsStorage: RotationSettingsStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(RotationSettingsUiState())
    val uiState: StateFlow<RotationSettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val settings = rotationSettingsStorage.loadSettings()
                _uiState.update { it.copy(settings = settings, isLoading = false) }
            } catch (e: Exception) {
                Logger.error("Failed to load rotation settings", e, context = TAG)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateAutoRotateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val updated = _uiState.value.settings?.copy(autoRotateEnabled = enabled)
                    ?: RotationSettings(autoRotateEnabled = enabled)
                rotationSettingsStorage.saveSettings(updated)
                _uiState.update { it.copy(settings = updated) }
            } catch (e: Exception) {
                Logger.error("Failed to update auto-rotate setting", e, context = TAG)
            }
        }
    }

    fun updateDefaultPolicy(policy: String) {
        viewModelScope.launch {
            try {
                val updated = _uiState.value.settings?.copy(defaultPolicy = policy)
                    ?: RotationSettings(defaultPolicy = policy)
                rotationSettingsStorage.saveSettings(updated)
                _uiState.update { it.copy(settings = updated) }
            } catch (e: Exception) {
                Logger.error("Failed to update default policy", e, context = TAG)
            }
        }
    }

    companion object {
        private const val TAG = "RotationSettingsViewModel"
    }
}

data class RotationSettingsUiState(
    val settings: RotationSettings? = null,
    val isLoading: Boolean = false,
)

