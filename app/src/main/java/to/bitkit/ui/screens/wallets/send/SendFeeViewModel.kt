package to.bitkit.ui.screens.wallets.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import to.bitkit.data.SettingsStore
import to.bitkit.di.BgDispatcher
import to.bitkit.ext.getSatsPerVByteFor
import to.bitkit.models.FeeRate
import to.bitkit.models.TransactionSpeed
import to.bitkit.repositories.BlocktankRepo
import javax.inject.Inject

@HiltViewModel
class SendFeeViewModel @Inject constructor(
    @BgDispatcher private val bgDispatcher: CoroutineDispatcher,
    private val blocktankRepo: BlocktankRepo,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SendFeeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        collectState()
    }

    private fun collectState() {
        viewModelScope.launch(bgDispatcher) {
            blocktankRepo.blocktankState.map { it.info?.onchain?.feeRates }.collect { feeRates ->
                _uiState.update {
                    it.copy(
                        items = TransactionSpeed.entries().associate { speed ->
                            val rate = FeeRate.fromSpeed(speed)
                            val sats = feeRates?.getSatsPerVByteFor(speed) ?: 0u
                            rate to sats.toLong()
                        }
                    )
                }
            }
        }
    }

    fun load(speed: TransactionSpeed?) {
        viewModelScope.launch(bgDispatcher) { blocktankRepo.refreshInfo() }
        viewModelScope.launch {
            val selectedSpeed = speed ?: settingsStore.data.first().defaultTransactionSpeed
            _uiState.update {
                it.copy(
                    selected = FeeRate.fromSpeed(selectedSpeed),
                )
            }
        }
    }
}

data class SendFeeUiState(
    val items: Map<FeeRate, Long> = emptyMap(),
    val selected: FeeRate? = null,
)
