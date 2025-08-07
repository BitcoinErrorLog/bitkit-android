package to.bitkit.ui.screens.wallets.send

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import to.bitkit.ext.getSatsPerVByteFor
import to.bitkit.models.FeeRate
import to.bitkit.models.TransactionSpeed
import to.bitkit.repositories.LightningRepo
import to.bitkit.viewmodels.SendUiState
import javax.inject.Inject

@HiltViewModel
class SendFeeViewModel @Inject constructor(
    private val lightningRepo: LightningRepo,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SendFeeUiState())
    val uiState = _uiState.asStateFlow()

    private lateinit var sendUiState: SendUiState

    fun init(sendUiState: SendUiState) {
        this.sendUiState = sendUiState
        val selected = FeeRate.fromSpeed(sendUiState.speed)
        val fees = sendUiState.fees

        val custom = when (val speed = sendUiState.speed) {
            is TransactionSpeed.Custom -> speed
            else -> {
                val satsPerVByte = sendUiState.feeRates?.getSatsPerVByteFor(speed) ?: 0u
                TransactionSpeed.Custom(satsPerVByte)
            }
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
        val parsedValue = value.toUIntOrNull() ?: 0u
        _uiState.update {
            it.copy(custom = TransactionSpeed.Custom(parsedValue))
        }
    }
    
    suspend fun calculateTotalFee(satsPerVByte: UInt): ULong {
        return lightningRepo.calculateTotalFee(
            amountSats = sendUiState.amount,
            address = sendUiState.address.takeIf { it.isNotEmpty() },
            speed = TransactionSpeed.Custom(satsPerVByte),
            utxosToSpend = sendUiState.selectedUtxos,
            feeRates = sendUiState.feeRates,
        ).getOrDefault(0u)
    }
}

data class SendFeeUiState(
    val fees: Map<FeeRate, Long> = emptyMap(),
    val selected: FeeRate? = null,
    val custom: TransactionSpeed.Custom? = null,
    val totalFee: ULong = 0u,
)
