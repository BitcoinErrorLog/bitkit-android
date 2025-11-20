package to.bitkit.ui.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synonym.bitkitcore.Activity
import com.synonym.bitkitcore.PaymentType
import com.synonym.bitkitcore.giftOrder
import com.synonym.bitkitcore.giftPay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import to.bitkit.async.ServiceQueue
import to.bitkit.di.BgDispatcher
import to.bitkit.ext.calculateRemoteBalance
import to.bitkit.models.NewTransactionSheetDetails
import to.bitkit.models.NewTransactionSheetDirection
import to.bitkit.models.NewTransactionSheetType
import to.bitkit.repositories.ActivityRepo
import to.bitkit.repositories.BlocktankRepo
import to.bitkit.repositories.LightningRepo
import to.bitkit.services.LightningService
import to.bitkit.utils.Logger
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class GiftViewModel @Inject constructor(
    @BgDispatcher private val bgDispatcher: CoroutineDispatcher,
    private val lightningRepo: LightningRepo,
    private val lightningService: LightningService,
    private val blocktankRepo: BlocktankRepo,
    private val activityRepo: ActivityRepo,
) : ViewModel() {

    private val _navigationEvent = MutableSharedFlow<GiftRoute>(extraBufferCapacity = 1)
    val navigationEvent = _navigationEvent.asSharedFlow()

    private val _successEvent = MutableSharedFlow<NewTransactionSheetDetails>(extraBufferCapacity = 1)
    val successEvent = _successEvent.asSharedFlow()

    private var code: String = ""
    var amount: ULong = 0uL
        private set
    private var isClaiming: Boolean = false

    fun initialize(code: String, amount: ULong) {
        if (!isClaiming) {
            viewModelScope.launch {
                _navigationEvent.emit(GiftRoute.Loading)
            }
        }
        this.code = code
        this.amount = amount
        claimGift()
    }

    private fun claimGift() = viewModelScope.launch(bgDispatcher) {
        if (isClaiming) {
            return@launch
        }

        isClaiming = true
        runCatching {
            lightningRepo.executeWhenNodeRunning(
                operationName = "waitForNodeRunning",
                waitTimeout = NODE_STARTUP_TIMEOUT_MS.milliseconds,
            ) {
                Result.success(Unit)
            }.getOrThrow()

            delay(PEER_CONNECTION_DELAY_MS)

            val channels = lightningRepo.lightningState.value.channels
            val maxInboundCapacity = channels.calculateRemoteBalance()

            if (maxInboundCapacity >= amount) {
                claimWithLiquidity()
            } else {
                claimWithoutLiquidity()
            }
        }.onFailure { e ->
            isClaiming = false
            handleGiftClaimError(e)
        }
    }

    private suspend fun claimWithLiquidity() {
        runCatching {
            val invoice = lightningService.receive(
                sat = null,
                description = "blocktank-gift-code:$code",
                expirySecs = 3600u,
            )

            ServiceQueue.CORE.background {
                giftPay(invoice = invoice)
            }

            isClaiming = false
            _navigationEvent.emit(GiftRoute.Success)
        }.onFailure { e ->
            isClaiming = false
            handleGiftClaimError(e)
        }
    }

    private suspend fun claimWithoutLiquidity() {
        runCatching {
            check(lightningService.nodeId != null) { "Node not started" }
            val nodeId = lightningService.nodeId!!

            val order = ServiceQueue.CORE.background {
                giftOrder(clientNodeId = nodeId, code = "blocktank-gift-code:$code")
            }

            check(order.orderId != null) { "Order ID is nil" }
            val orderId = order.orderId!!

            val openedOrder = blocktankRepo.openChannel(orderId).getOrThrow()

            val nowTimestamp = (System.currentTimeMillis() / 1000).toULong()

            val lightningActivity = com.synonym.bitkitcore.LightningActivity(
                id = openedOrder.channel?.fundingTx?.id ?: orderId,
                txType = PaymentType.RECEIVED,
                status = com.synonym.bitkitcore.PaymentState.SUCCEEDED,
                value = amount,
                fee = 0u,
                invoice = openedOrder.payment?.bolt11Invoice?.request ?: "",
                message = code,
                timestamp = nowTimestamp,
                preimage = null,
                createdAt = nowTimestamp,
                updatedAt = null,
            )

            activityRepo.insertActivity(Activity.Lightning(lightningActivity)).getOrThrow()

            isClaiming = false
            _successEvent.emit(
                NewTransactionSheetDetails(
                    type = NewTransactionSheetType.LIGHTNING,
                    direction = NewTransactionSheetDirection.RECEIVED,
                    paymentHashOrTxId = openedOrder.channel?.fundingTx?.id ?: orderId,
                    sats = amount.toLong(),
                )
            )
            _navigationEvent.emit(GiftRoute.Success)
        }.onFailure { e ->
            isClaiming = false
            handleGiftClaimError(e)
        }
    }

    private suspend fun handleGiftClaimError(error: Throwable) {
        Logger.error("Gift claim failed: $error", error, context = TAG)

        val route = when {
            errorContains(error, "GIFT_CODE_ALREADY_USED") -> GiftRoute.Used
            errorContains(error, "GIFT_CODE_USED_UP") -> GiftRoute.UsedUp
            else -> GiftRoute.Error
        }

        _navigationEvent.emit(route)
    }

    private fun errorContains(error: Throwable, text: String): Boolean {
        var currentError: Throwable? = error
        while (currentError != null) {
            val errorText = currentError.toString() + (currentError.message ?: "")
            if (errorText.contains(text, ignoreCase = true)) {
                return true
            }
            currentError = currentError.cause
        }
        return false
    }

    companion object {
        private const val TAG = "GiftViewModel"
        private const val NODE_STARTUP_TIMEOUT_MS = 30_000L
        private const val PEER_CONNECTION_DELAY_MS = 2_000L
    }
}
