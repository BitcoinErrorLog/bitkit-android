package to.bitkit.ui.screens.wallets.send

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import to.bitkit.models.FeeRate
import to.bitkit.viewmodels.SendUiState
import javax.inject.Inject

@HiltViewModel
class SendFeeViewModel @Inject constructor(
) : ViewModel() {
    private val _uiState = MutableStateFlow(SendFeeUiState())
    val uiState = _uiState.asStateFlow()

    private lateinit var sendUiState: SendUiState

    fun init(sendUiState: SendUiState) {
        this.sendUiState = sendUiState
        _uiState.update {
            it.copy(
                selected = FeeRate.fromSpeed(sendUiState.speed),
                fees = sendUiState.fees
            )
        }
    }
}

data class SendFeeUiState(
    val fees: Map<FeeRate, Long> = emptyMap(),
    val selected: FeeRate? = null,
)
