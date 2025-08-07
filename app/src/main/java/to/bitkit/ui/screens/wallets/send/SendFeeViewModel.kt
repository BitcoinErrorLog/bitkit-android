package to.bitkit.ui.screens.wallets.send

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import to.bitkit.R
import to.bitkit.ext.getSatsPerVByteFor
import to.bitkit.models.FeeRate
import to.bitkit.models.TransactionSpeed
import to.bitkit.repositories.CurrencyRepo
import to.bitkit.repositories.LightningRepo
import to.bitkit.ui.components.KEY_DELETE
import to.bitkit.viewmodels.SendUiState
import javax.inject.Inject

@HiltViewModel
class SendFeeViewModel @Inject constructor(
    private val lightningRepo: LightningRepo,
    private val currencyRepo: CurrencyRepo,
    @ApplicationContext private val context: Context,
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
                input = custom.satsPerVByte.toString().takeIf { custom.satsPerVByte > 0u } ?: "",
            )
        }
        recalculateFee()
    }

    fun onKeyPress(key: String) {
        val currentInput = _uiState.value.input
        val newInput = when (key) {
            KEY_DELETE -> if (currentInput.isNotEmpty()) currentInput.dropLast(1) else ""
            else -> if (currentInput.length < 3) (currentInput + key).trimStart('0') else currentInput
        }

        _uiState.update {
            it.copy(
                input = newInput,
                custom = TransactionSpeed.Custom(newInput.toUIntOrNull() ?: 0u)
            )
        }
        recalculateFee()
    }

    private fun recalculateFee() {
        viewModelScope.launch {
            val satsPerVByte = _uiState.value.custom?.satsPerVByte ?: 0u
            val totalFee = if (satsPerVByte > 0u) calculateTotalFee(satsPerVByte).toLong() else 0L
            val fiat = if (totalFee != 0L) currencyRepo.convertSatsToFiat(totalFee).getOrNull() else null

            val totalFeeText = fiat?.let {
                context.getString(R.string.wallet__send_fee_total_fiat)
                    .replace("{feeSats}", "$totalFee")
                    .replace("{fiatSymbol}", it.symbol)
                    .replace("{fiatFormatted}", it.formatted)
            } ?: context.getString(R.string.wallet__send_fee_total).replace("{feeSats}", "$totalFee")

            _uiState.update {
                it.copy(
                    totalFeeText = totalFeeText,
                )
            }
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
    val input: String = "",
    val totalFeeText: String = "",
)
