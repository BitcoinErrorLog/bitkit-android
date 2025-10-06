package to.bitkit.usecases

import kotlinx.coroutines.flow.first
import org.lightningdevkit.ldknode.ChannelDetails
import to.bitkit.data.SettingsStore
import to.bitkit.data.entities.TransferEntity
import to.bitkit.ext.amountSats
import to.bitkit.ext.channelId
import to.bitkit.ext.minusOrZero
import to.bitkit.ext.totalNextOutboundHtlcLimitSats
import to.bitkit.models.BalanceDetails
import to.bitkit.models.BalanceState
import to.bitkit.repositories.LightningRepo
import to.bitkit.repositories.TransferRepo
import to.bitkit.utils.Logger
import to.bitkit.utils.jsonLogOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncBalancesUseCase @Inject constructor(
    private val lightningRepo: LightningRepo,
    private val transferRepo: TransferRepo,
    private val settingsStore: SettingsStore,
) {
    /**
     * Syncs balances from lightning node and calculates pending transfer amounts.
     * Returns the new balance state.
     */
    suspend operator fun invoke(): Result<BalanceState> = runCatching {
        val balanceDetails = lightningRepo.getBalancesAsync().getOrThrow()
        val channels = lightningRepo.getChannels() ?: emptyList()
        val activeTransfers = transferRepo.activeTransfers.first()

        val toSpendingAmount = calculateTransferToSpendingAmount(activeTransfers, channels, balanceDetails)
        val toSavingsAmount = calculateTransferToSavingsAmount(activeTransfers, channels, balanceDetails)
        val adjustedLightningSats = balanceDetails.totalLightningBalanceSats - toSpendingAmount - toSavingsAmount

        val balanceState = BalanceState(
            totalOnchainSats = balanceDetails.totalOnchainBalanceSats,
            totalLightningSats = adjustedLightningSats,
            maxSendLightningSats = lightningRepo.getChannels().totalNextOutboundHtlcLimitSats(),
            maxSendOnchainSats = getMaxSendAmount(balanceDetails),
            balanceInTransferToSavings = toSavingsAmount,
            balanceInTransferToSpending = toSpendingAmount,
        )

        val height = lightningRepo.lightningState.value.block()?.height
        Logger.verbose("Active transfers at block height=$height: ${jsonLogOf(activeTransfers)}", context = TAG)
        Logger.verbose("Balances in ldk-node at block height=$height: ${jsonLogOf(balanceDetails)}", context = TAG)
        Logger.verbose("Balances in state at block height=$height: ${jsonLogOf(balanceState)}", context = TAG)

        return@runCatching balanceState
    }

    private fun calculateTransferToSpendingAmount(
        transfers: List<TransferEntity>,
        channels: List<ChannelDetails>,
        balances: BalanceDetails,
    ): ULong {
        var toSpendingAmount = 0uL
        val toSpending = transfers.filter { it.type.isToSpending() }

        for (transfer in toSpending) {
            when {
                // LSP orders: use transfer amount directly (channel doesn't exist yet during PAID phase)
                transfer.lspOrderId != null -> {
                    toSpendingAmount += transfer.amountSats.toULong()
                }
                // Manual channels: find channel in LDK and get balance from lightningBalances
                transfer.channelId != null -> {
                    val channel = channels.find { it.channelId == transfer.channelId }
                    if (channel != null && !channel.isChannelReady) {
                        val channelBalance = balances.lightningBalances.find { it.channelId() == channel.channelId }
                        toSpendingAmount += channelBalance?.amountSats() ?: 0u
                    }
                }
            }
        }

        return toSpendingAmount
    }

    private suspend fun calculateTransferToSavingsAmount(
        transfers: List<TransferEntity>,
        channels: List<ChannelDetails>,
        balanceDetails: BalanceDetails,
    ): ULong {
        var toSavingsAmount = 0uL
        val toSavings = transfers.filter { it.type.isToSavings() }

        for (transfer in toSavings) {
            val channelId = when {
                // LSP orders: resolve via order
                transfer.lspOrderId != null -> transferRepo.resolveChannelIdForTransfer(transfer, channels)
                // Direct channelId for manual/coop/force close
                else -> transfer.channelId
            }

            // Find balance in lightning_balances by channel ID
            val channelBalance = balanceDetails.lightningBalances.find {
                it.channelId() == channelId
            }
            toSavingsAmount += channelBalance?.amountSats() ?: 0u
        }

        return toSavingsAmount
    }

    private suspend fun getMaxSendAmount(balanceDetails: BalanceDetails): ULong {
        val spendableOnchainSats = balanceDetails.spendableOnchainBalanceSats
        if (spendableOnchainSats == 0uL) return 0u

        val fallback = (spendableOnchainSats.toDouble() * FALLBACK_FEE_PERCENT).toULong()
        val speed = settingsStore.data.first().defaultTransactionSpeed

        val fee = lightningRepo.calculateTotalFee(
            amountSats = spendableOnchainSats,
            speed = speed,
            utxosToSpend = lightningRepo.listSpendableOutputs().getOrNull()
        ).onFailure {
            Logger.debug("Could not calculate max send amount, using fallback of 10% = $fallback", context = TAG)
        }.getOrDefault(fallback)

        val maxSendable = spendableOnchainSats.minusOrZero(fee)
        return maxSendable
    }

    private companion object {
        const val TAG = "SyncBalancesUseCase"
        const val FALLBACK_FEE_PERCENT = 0.1
    }
}
