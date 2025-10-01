package to.bitkit.models

import kotlinx.serialization.Serializable
import org.lightningdevkit.ldknode.BalanceSource
import org.lightningdevkit.ldknode.BlockHash
import org.lightningdevkit.ldknode.ChannelId
import org.lightningdevkit.ldknode.PaymentHash
import org.lightningdevkit.ldknode.PaymentPreimage
import org.lightningdevkit.ldknode.PublicKey
import org.lightningdevkit.ldknode.Txid
import org.lightningdevkit.ldknode.BalanceDetails as LdkBalanceDetails
import org.lightningdevkit.ldknode.LightningBalance as LdkLightningBalance
import org.lightningdevkit.ldknode.PendingSweepBalance as LdkPendingSweepBalance

@Serializable
data class BalanceState(
    val totalOnchainSats: ULong = 0uL,
    val totalLightningSats: ULong = 0uL,
    val maxSendLightningSats: ULong = 0uL, // TODO use where applicable
    val maxSendOnchainSats: ULong = 0uL,
    val balanceInTransferToSavings: ULong = 0uL,
    val balanceInTransferToSpending: ULong = 0uL,
) {
    val totalSats get() = totalOnchainSats + totalLightningSats
}

// region BalanceDetails mapping

// TODO replace when ldk-node exports uniffi kotlin bindings with serializable records
@Serializable
data class BalanceDetails(
    var totalOnchainBalanceSats: ULong,
    var spendableOnchainBalanceSats: ULong,
    var totalAnchorChannelsReserveSats: ULong,
    var totalLightningBalanceSats: ULong,
    var lightningBalances: List<LightningBalance>,
    var pendingBalancesFromChannelClosures: List<PendingSweepBalance>,
)

@Serializable
sealed class LightningBalance {

    @Serializable
    data class ClaimableOnChannelClose(
        val channelId: ChannelId,
        val counterpartyNodeId: PublicKey,
        val amountSatoshis: ULong,
        val transactionFeeSatoshis: ULong,
        val outboundPaymentHtlcRoundedMsat: ULong,
        val outboundForwardedHtlcRoundedMsat: ULong,
        val inboundClaimingHtlcRoundedMsat: ULong,
        val inboundHtlcRoundedMsat: ULong,
    ) : LightningBalance()

    @Serializable
    data class ClaimableAwaitingConfirmations(
        val channelId: ChannelId,
        val counterpartyNodeId: PublicKey,
        val amountSatoshis: ULong,
        val confirmationHeight: UInt,
        val source: BalanceSource,
    ) : LightningBalance()

    @Serializable
    data class ContentiousClaimable(
        val channelId: ChannelId,
        val counterpartyNodeId: PublicKey,
        val amountSatoshis: ULong,
        val timeoutHeight: UInt,
        val paymentHash: PaymentHash,
        val paymentPreimage: PaymentPreimage,
    ) : LightningBalance()

    @Serializable
    data class MaybeTimeoutClaimableHtlc(
        val channelId: ChannelId,
        val counterpartyNodeId: PublicKey,
        val amountSatoshis: ULong,
        val claimableHeight: UInt,
        val paymentHash: PaymentHash,
        val outboundPayment: Boolean,
    ) : LightningBalance()

    @Serializable
    data class MaybePreimageClaimableHtlc(
        val channelId: ChannelId,
        val counterpartyNodeId: PublicKey,
        val amountSatoshis: ULong,
        val expiryHeight: UInt,
        val paymentHash: PaymentHash,
    ) : LightningBalance()

    @Serializable
    data class CounterpartyRevokedOutputClaimable(
        val channelId: ChannelId,
        val counterpartyNodeId: PublicKey,
        val amountSatoshis: ULong,
    ) : LightningBalance()
}

@Serializable
sealed class PendingSweepBalance {

    @Serializable
    data class PendingBroadcast(
        val channelId: ChannelId?,
        val amountSatoshis: ULong,
    ) : PendingSweepBalance()

    @Serializable
    data class BroadcastAwaitingConfirmation(
        val channelId: ChannelId?,
        val latestBroadcastHeight: UInt,
        val latestSpendingTxid: Txid,
        val amountSatoshis: ULong,
    ) : PendingSweepBalance()

    @Serializable
    data class AwaitingThresholdConfirmations(
        val channelId: ChannelId?,
        val latestSpendingTxid: Txid,
        val confirmationHash: BlockHash,
        val confirmationHeight: UInt,
        val amountSatoshis: ULong,
    ) : PendingSweepBalance()
}

fun LdkBalanceDetails.toDomainModel() = BalanceDetails(
    totalOnchainBalanceSats = totalOnchainBalanceSats,
    spendableOnchainBalanceSats = spendableOnchainBalanceSats,
    totalAnchorChannelsReserveSats = totalAnchorChannelsReserveSats,
    totalLightningBalanceSats = totalLightningBalanceSats,
    lightningBalances = lightningBalances.map { it.mapToLightningBalance() },
    pendingBalancesFromChannelClosures = pendingBalancesFromChannelClosures.map { it.mapToPendingSweepBalance() },
)

fun LdkLightningBalance.mapToLightningBalance() = when (this) {
    is LdkLightningBalance.ClaimableOnChannelClose ->
        LightningBalance.ClaimableOnChannelClose(
            channelId = channelId,
            counterpartyNodeId = counterpartyNodeId,
            amountSatoshis = amountSatoshis,
            transactionFeeSatoshis = transactionFeeSatoshis,
            outboundPaymentHtlcRoundedMsat = outboundPaymentHtlcRoundedMsat,
            outboundForwardedHtlcRoundedMsat = outboundForwardedHtlcRoundedMsat,
            inboundClaimingHtlcRoundedMsat = inboundClaimingHtlcRoundedMsat,
            inboundHtlcRoundedMsat = inboundHtlcRoundedMsat
        )

    is LdkLightningBalance.ClaimableAwaitingConfirmations -> LightningBalance.ClaimableAwaitingConfirmations(
        channelId = channelId,
        counterpartyNodeId = counterpartyNodeId,
        amountSatoshis = amountSatoshis,
        confirmationHeight = confirmationHeight,
        source = source
    )

    is LdkLightningBalance.ContentiousClaimable -> LightningBalance.ContentiousClaimable(
        channelId = channelId,
        counterpartyNodeId = counterpartyNodeId,
        amountSatoshis = amountSatoshis,
        timeoutHeight = timeoutHeight,
        paymentHash = paymentHash,
        paymentPreimage = paymentPreimage
    )

    is LdkLightningBalance.CounterpartyRevokedOutputClaimable -> LightningBalance.CounterpartyRevokedOutputClaimable(
        channelId = channelId,
        counterpartyNodeId = counterpartyNodeId,
        amountSatoshis = amountSatoshis
    )

    is LdkLightningBalance.MaybePreimageClaimableHtlc -> LightningBalance.MaybePreimageClaimableHtlc(
        channelId = channelId,
        counterpartyNodeId = counterpartyNodeId,
        amountSatoshis = amountSatoshis,
        expiryHeight = expiryHeight,
        paymentHash = paymentHash
    )

    is LdkLightningBalance.MaybeTimeoutClaimableHtlc -> LightningBalance.MaybeTimeoutClaimableHtlc(
        channelId = channelId,
        counterpartyNodeId = counterpartyNodeId,
        amountSatoshis = amountSatoshis,
        claimableHeight = claimableHeight,
        paymentHash = paymentHash,
        outboundPayment = outboundPayment
    )
}

fun LdkPendingSweepBalance.mapToPendingSweepBalance() = when (this) {
    is LdkPendingSweepBalance.PendingBroadcast -> PendingSweepBalance.PendingBroadcast(
        channelId = channelId,
        amountSatoshis = amountSatoshis
    )

    is LdkPendingSweepBalance.BroadcastAwaitingConfirmation -> PendingSweepBalance.BroadcastAwaitingConfirmation(
        channelId = channelId,
        latestBroadcastHeight = latestBroadcastHeight,
        latestSpendingTxid = latestSpendingTxid,
        amountSatoshis = amountSatoshis
    )

    is LdkPendingSweepBalance.AwaitingThresholdConfirmations -> PendingSweepBalance.AwaitingThresholdConfirmations(
        channelId = channelId,
        latestSpendingTxid = latestSpendingTxid,
        confirmationHash = confirmationHash,
        confirmationHeight = confirmationHeight,
        amountSatoshis = amountSatoshis
    )
}

// endregion
