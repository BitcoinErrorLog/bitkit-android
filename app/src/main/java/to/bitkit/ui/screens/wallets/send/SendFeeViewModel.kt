package to.bitkit.ui.screens.wallets.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synonym.bitkitcore.FeeRates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.bitkit.di.BgDispatcher
import to.bitkit.ext.getSatsPerVByteFor
import to.bitkit.models.FeeRate
import to.bitkit.models.TransactionSpeed
import to.bitkit.repositories.BlocktankRepo
import to.bitkit.repositories.LightningRepo
import to.bitkit.viewmodels.SendUiState
import javax.inject.Inject

@HiltViewModel
class SendFeeViewModel @Inject constructor(
    @BgDispatcher private val bgDispatcher: CoroutineDispatcher,
    private val lightningRepo: LightningRepo,
    private val blocktankRepo: BlocktankRepo,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SendFeeUiState())
    val uiState = _uiState.asStateFlow()

    private lateinit var sendUiState: SendUiState

    fun init(sendUiState: SendUiState) {
        this.sendUiState = sendUiState
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selected = FeeRate.fromSpeed(sendUiState.speed),
                )
            }
        }
        viewModelScope.launch(bgDispatcher) { blocktankRepo.refreshInfo() }
        collectState()
    }

    private fun collectState() {
        viewModelScope.launch(bgDispatcher) {
            blocktankRepo.blocktankState.map { it.info?.onchain?.feeRates }.collect { feeRates ->
                // init ui first
                _uiState.update {
                    it.copy(
                        fees = speedEntries().associate { speed ->
                            val rate = FeeRate.fromSpeed(speed)
                            val sats = feeRates?.getSatsPerVByteFor(speed)?.toLong() ?: 0
                            rate to sats
                        }
                    )
                }
                // TODO try move to appViewModel and trigger load in bg onAmountContinue + store in sendUiState
                loadFees(feeRates)
            }
        }
    }

    private suspend fun loadFees(feeRates: FeeRates?) {
        val newFees = getFees(feeRates)
        _uiState.update { it.copy(fees = newFees) }
    }

    private suspend fun getFees(feeRates: FeeRates?): Map<FeeRate, Long> {
        return withContext(bgDispatcher) {
            return@withContext coroutineScope {
                speedEntries()
                    .map { speed ->
                        async {
                            val rate = FeeRate.fromSpeed(speed)
                            val fee = getFeeSatsForSpeed(speed, feeRates)
                            rate to fee
                        }
                    }.awaitAll()
                    .toMap()
            }
        }
    }

    private suspend fun getFeeSatsForSpeed(speed: TransactionSpeed, feeRates: FeeRates?): Long {
        if (feeRates?.getSatsPerVByteFor(speed)?.toLong() == 0L) return 0L

        return lightningRepo.calculateTotalFee(
            amountSats = sendUiState.amount,
            utxosToSpend = sendUiState.selectedUtxos,
            speed = speed,
        ).getOrDefault(0u).toLong()
    }

    private fun speedEntries(): List<TransactionSpeed> {
        return listOf(
            TransactionSpeed.Fast,
            TransactionSpeed.Medium,
            TransactionSpeed.Slow,
            when (val speed = sendUiState.speed) {
                is TransactionSpeed.Custom -> speed
                else -> TransactionSpeed.Custom(0u)
            }
        )
    }
}

data class SendFeeUiState(
    val fees: Map<FeeRate, Long> = emptyMap(),
    val selected: FeeRate? = null,
)
