package to.bitkit.ui.screens.wallets.send

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import to.bitkit.models.FeeRate
import to.bitkit.models.TransactionSpeed
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
        val selected = FeeRate.fromSpeed(sendUiState.speed)
        val fees = sendUiState.fees

        // TODO use actual feeRate instead of the estimated fee amount for the selected speed
        val custom = when (val speed = sendUiState.speed) {
            is TransactionSpeed.Custom -> speed
            else -> TransactionSpeed.Custom(fees.getOrDefault(selected, 0).toUInt())
        }
        _uiState.update {
            it.copy(
                selected = selected,
                fees = fees,
                custom = custom,
            )
        }
    }

    fun onInputChange(value: String) {
        // TODO handle uiState.custom = TransactionSpeed.Custom(parsedValue)
    }
}

data class SendFeeUiState(
    val fees: Map<FeeRate, Long> = emptyMap(),
    val selected: FeeRate? = null,
    val custom: TransactionSpeed.Custom? = null,
)
