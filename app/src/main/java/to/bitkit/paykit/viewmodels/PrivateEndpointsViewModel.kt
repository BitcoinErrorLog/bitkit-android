package to.bitkit.paykit.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import to.bitkit.paykit.storage.PrivateEndpointStorage
import to.bitkit.utils.Logger
import javax.inject.Inject

@HiltViewModel
class PrivateEndpointsViewModel @Inject constructor(
    private val privateEndpointStorage: PrivateEndpointStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivateEndpointsUiState())
    val uiState: StateFlow<PrivateEndpointsUiState> = _uiState.asStateFlow()

    init {
        loadPeers()
    }

    fun loadPeers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val peers = privateEndpointStorage.listPeers()
                _uiState.update { it.copy(peers = peers, isLoading = false) }
            } catch (e: Exception) {
                Logger.error("Failed to load private endpoint peers", e, context = TAG)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    companion object {
        private const val TAG = "PrivateEndpointsViewModel"
    }
}

data class PrivateEndpointsUiState(
    val peers: List<String> = emptyList(),
    val isLoading: Boolean = false,
)
