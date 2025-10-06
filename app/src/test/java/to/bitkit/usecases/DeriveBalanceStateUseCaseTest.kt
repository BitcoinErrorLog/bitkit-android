package to.bitkit.usecases

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.lightningdevkit.ldknode.ChannelDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import to.bitkit.data.SettingsData
import to.bitkit.data.SettingsStore
import to.bitkit.data.dto.TransferType
import to.bitkit.data.entities.TransferEntity
import to.bitkit.models.BalanceDetails
import to.bitkit.models.LightningBalance
import to.bitkit.repositories.LightningRepo
import to.bitkit.repositories.LightningState
import to.bitkit.repositories.TransferRepo
import to.bitkit.test.BaseUnitTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeriveBalanceStateUseCaseTest : BaseUnitTest() {

    private val lightningRepo: LightningRepo = mock()
    private val transferRepo: TransferRepo = mock()
    private val settingsStore: SettingsStore = mock()

    private lateinit var sut: DeriveBalanceStateUseCase

    @Before
    fun setUp() {
        whenever(settingsStore.data).thenReturn(flowOf(SettingsData()))
        whenever(lightningRepo.lightningState).thenReturn(MutableStateFlow(LightningState()))
        whenever(transferRepo.activeTransfers).thenReturn(flowOf(emptyList()))
        wheneverBlocking { lightningRepo.listSpendableOutputs() }.thenReturn(Result.success(emptyList()))
        wheneverBlocking { lightningRepo.calculateTotalFee(any(), any(), any(), any(), anyOrNull()) }
            .thenReturn(Result.success(1000uL))

        sut = DeriveBalanceStateUseCase(
            lightningRepo = lightningRepo,
            transferRepo = transferRepo,
            settingsStore = settingsStore,
        )
    }

    @Test
    fun `should calculate LSP order transfer to spending using transfer amount`() = test {
        val balance = setupBalanceMock()
        val transfers = listOf(
            createTransferEntity(
                type = TransferType.TO_SPENDING,
                amountSats = 50000L,
                lspOrderId = "lsp-order-id",
                channelId = null
            )
        )

        whenever(lightningRepo.getChannels()).thenReturn(emptyList())
        whenever(transferRepo.activeTransfers).thenReturn(flowOf(transfers))

        val result = sut()

        assertTrue(result.isSuccess)
        val balanceState = result.getOrThrow()
        assertEquals(50000uL, balanceState.balanceInTransferToSpending)
        assertEquals(balance.totalLightningBalanceSats - 50000uL, balanceState.totalLightningSats)
    }

    @Test
    fun `should calculate manual channel transfer to spending using channel balance`() = test {
        val channelId = "manual-channel-id"
        val channelBalance = LightningBalance.ClaimableOnChannelClose(
            channelId = channelId,
            counterpartyNodeId = "node-id",
            amountSatoshis = 30000u,
            transactionFeeSatoshis = 0u,
            outboundPaymentHtlcRoundedMsat = 0u,
            outboundForwardedHtlcRoundedMsat = 0u,
            inboundClaimingHtlcRoundedMsat = 0u,
            inboundHtlcRoundedMsat = 0u,
        )

        val balance = BalanceDetails(
            totalOnchainBalanceSats = 100_000u,
            spendableOnchainBalanceSats = 0u,
            totalAnchorChannelsReserveSats = 10_000u,
            totalLightningBalanceSats = 80_000u,
            lightningBalances = listOf(channelBalance),
            pendingBalancesFromChannelClosures = emptyList(),
        )
        wheneverBlocking { lightningRepo.getBalancesAsync() }.thenReturn(Result.success(balance))

        val channel = mock<ChannelDetails> {
            on { this.channelId } doReturn channelId
            on { isChannelReady } doReturn false
        }

        val transfers = listOf(
            createTransferEntity(
                type = TransferType.MANUAL_SETUP,
                amountSats = 30000L,
                channelId = channelId,
                lspOrderId = null
            )
        )

        whenever(lightningRepo.getChannels()).thenReturn(listOf(channel))
        whenever(transferRepo.activeTransfers).thenReturn(flowOf(transfers))

        val result = sut()

        assertTrue(result.isSuccess)
        val balanceState = result.getOrThrow()
        assertEquals(30000uL, balanceState.balanceInTransferToSpending)
        assertEquals(balance.totalLightningBalanceSats - 30000uL, balanceState.totalLightningSats)
    }

    @Test
    fun `should not count manual channel as pending when ready`() = test {
        setupBalanceMock()
        val channelId = "ready-channel-id"
        val channel = mock<ChannelDetails> {
            on { this.channelId } doReturn channelId
            on { isChannelReady } doReturn true
        }

        val transfers = listOf(
            createTransferEntity(
                type = TransferType.MANUAL_SETUP,
                amountSats = 30000L,
                channelId = channelId,
                lspOrderId = null
            )
        )

        whenever(lightningRepo.getChannels()).thenReturn(listOf(channel))
        whenever(transferRepo.activeTransfers).thenReturn(flowOf(transfers))

        val result = sut()

        assertTrue(result.isSuccess)
        val balanceState = result.getOrThrow()
        assertEquals(0uL, balanceState.balanceInTransferToSpending)
    }

    @Test
    fun `should count closing channel balance for transfer to savings`() = test {
        val channelId = "closing-channel-id"
        val channelBalance = LightningBalance.ClaimableOnChannelClose(
            channelId = channelId,
            counterpartyNodeId = "node-id",
            amountSatoshis = 40000u,
            transactionFeeSatoshis = 0u,
            outboundPaymentHtlcRoundedMsat = 0u,
            outboundForwardedHtlcRoundedMsat = 0u,
            inboundClaimingHtlcRoundedMsat = 0u,
            inboundHtlcRoundedMsat = 0u,
        )

        val balance = BalanceDetails(
            totalOnchainBalanceSats = 100_000u,
            spendableOnchainBalanceSats = 0u,
            totalAnchorChannelsReserveSats = 10_000u,
            totalLightningBalanceSats = 90_000u,
            lightningBalances = listOf(channelBalance),
            pendingBalancesFromChannelClosures = emptyList(),
        )
        wheneverBlocking { lightningRepo.getBalancesAsync() }.thenReturn(Result.success(balance))

        val transfers = listOf(
            createTransferEntity(
                type = TransferType.COOP_CLOSE,
                amountSats = 40000L,
                channelId = channelId,
                lspOrderId = null
            )
        )

        whenever(lightningRepo.getChannels()).thenReturn(emptyList())
        whenever(transferRepo.activeTransfers).thenReturn(flowOf(transfers))

        val result = sut()

        assertTrue(result.isSuccess)
        val balanceState = result.getOrThrow()
        assertEquals(40000uL, balanceState.balanceInTransferToSavings)
        assertEquals(balance.totalLightningBalanceSats - 40000uL, balanceState.totalLightningSats)
    }

    @Test
    fun `should return zero max send onchain when spendable balance is zero`() = test {
        val balance = BalanceDetails(
            totalOnchainBalanceSats = 50_000u,
            spendableOnchainBalanceSats = 0u,
            totalAnchorChannelsReserveSats = 50_000u,
            totalLightningBalanceSats = 0u,
            lightningBalances = emptyList(),
            pendingBalancesFromChannelClosures = emptyList(),
        )
        wheneverBlocking { lightningRepo.getBalancesAsync() }.thenReturn(Result.success(balance))
        whenever(lightningRepo.getChannels()).thenReturn(emptyList())

        val result = sut()

        assertTrue(result.isSuccess)
        val balanceState = result.getOrThrow()
        assertEquals(0u, balanceState.maxSendOnchainSats)
    }

    @Test
    fun `should calculate max send onchain with successful fee calculation`() = test {
        val spendableAmount = 100_000uL
        val feeAmount = 2_000uL
        val expectedMaxSend = spendableAmount - feeAmount // 98_000

        val balance = BalanceDetails(
            totalOnchainBalanceSats = 100_000u,
            spendableOnchainBalanceSats = spendableAmount,
            totalAnchorChannelsReserveSats = 0u,
            totalLightningBalanceSats = 0u,
            lightningBalances = emptyList(),
            pendingBalancesFromChannelClosures = emptyList(),
        )

        wheneverBlocking { lightningRepo.getBalancesAsync() }.thenReturn(Result.success(balance))
        whenever(lightningRepo.getChannels()).thenReturn(emptyList())
        wheneverBlocking {
            lightningRepo.calculateTotalFee(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        }.thenReturn(Result.success(feeAmount))

        val result = sut()

        assertTrue(result.isSuccess)
        val balanceState = result.getOrThrow()
        assertEquals(expectedMaxSend, balanceState.maxSendOnchainSats)
    }

    @Test
    fun `should use 10 percent fallback when fee calculation fails`() = test {
        val spendableAmount = 100_000uL
        val expectedFallback = (spendableAmount.toDouble() * 0.1).toULong() // 10_000
        val expectedMaxSend = spendableAmount - expectedFallback // 90_000

        val balance = BalanceDetails(
            totalOnchainBalanceSats = 100_000u,
            spendableOnchainBalanceSats = spendableAmount,
            totalAnchorChannelsReserveSats = 0u,
            totalLightningBalanceSats = 0u,
            lightningBalances = emptyList(),
            pendingBalancesFromChannelClosures = emptyList(),
        )

        wheneverBlocking { lightningRepo.getBalancesAsync() }.thenReturn(Result.success(balance))
        whenever(lightningRepo.getChannels()).thenReturn(emptyList())
        wheneverBlocking {
            lightningRepo.calculateTotalFee(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        }.thenReturn(Result.failure(Exception("Fee calculation failed")))

        val result = sut()

        assertTrue(result.isSuccess)
        val balanceState = result.getOrThrow()
        assertEquals(expectedMaxSend, balanceState.maxSendOnchainSats)
    }

    @Test
    fun `should return zero max send when fee exceeds spendable balance`() = test {
        val spendableAmount = 100_000uL
        val excessiveFee = 150_000uL // Fee exceeds balance

        val balance = BalanceDetails(
            totalOnchainBalanceSats = 100_000u,
            spendableOnchainBalanceSats = spendableAmount,
            totalAnchorChannelsReserveSats = 0u,
            totalLightningBalanceSats = 0u,
            lightningBalances = emptyList(),
            pendingBalancesFromChannelClosures = emptyList(),
        )

        wheneverBlocking { lightningRepo.getBalancesAsync() }.thenReturn(Result.success(balance))
        whenever(lightningRepo.getChannels()).thenReturn(emptyList())
        wheneverBlocking {
            lightningRepo.calculateTotalFee(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        }.thenReturn(Result.success(excessiveFee))

        val result = sut()

        assertTrue(result.isSuccess)
        val balanceState = result.getOrThrow()
        assertEquals(0u, balanceState.maxSendOnchainSats)
    }

    @Test
    fun `should count both spending and savings transfers correctly`() = test {
        val spendingChannelId = "spending-channel-id"
        val savingsChannelId = "savings-channel-id"

        val spendingBalance = LightningBalance.ClaimableOnChannelClose(
            channelId = spendingChannelId,
            counterpartyNodeId = "node-id",
            amountSatoshis = 30000u,
            transactionFeeSatoshis = 0u,
            outboundPaymentHtlcRoundedMsat = 0u,
            outboundForwardedHtlcRoundedMsat = 0u,
            inboundClaimingHtlcRoundedMsat = 0u,
            inboundHtlcRoundedMsat = 0u,
        )

        val savingsBalance = LightningBalance.ClaimableOnChannelClose(
            channelId = savingsChannelId,
            counterpartyNodeId = "node-id",
            amountSatoshis = 20000u,
            transactionFeeSatoshis = 0u,
            outboundPaymentHtlcRoundedMsat = 0u,
            outboundForwardedHtlcRoundedMsat = 0u,
            inboundClaimingHtlcRoundedMsat = 0u,
            inboundHtlcRoundedMsat = 0u,
        )

        val balance = BalanceDetails(
            totalOnchainBalanceSats = 100_000u,
            spendableOnchainBalanceSats = 0u,
            totalAnchorChannelsReserveSats = 10_000u,
            totalLightningBalanceSats = 100_000u,
            lightningBalances = listOf(spendingBalance, savingsBalance),
            pendingBalancesFromChannelClosures = emptyList(),
        )
        wheneverBlocking { lightningRepo.getBalancesAsync() }.thenReturn(Result.success(balance))

        val spendingChannel = mock<ChannelDetails> {
            on { channelId } doReturn spendingChannelId
            on { isChannelReady } doReturn false
        }

        val transfers = listOf(
            createTransferEntity(
                type = TransferType.MANUAL_SETUP,
                amountSats = 30000L,
                channelId = spendingChannelId,
                lspOrderId = null
            ),
            createTransferEntity(
                type = TransferType.COOP_CLOSE,
                amountSats = 20000L,
                channelId = savingsChannelId,
                lspOrderId = null
            )
        )

        whenever(lightningRepo.getChannels()).thenReturn(listOf(spendingChannel))
        whenever(transferRepo.activeTransfers).thenReturn(flowOf(transfers))

        val result = sut()

        assertTrue(result.isSuccess)
        val balanceState = result.getOrThrow()
        assertEquals(30000uL, balanceState.balanceInTransferToSpending)
        assertEquals(20000uL, balanceState.balanceInTransferToSavings)
        assertEquals(balance.totalLightningBalanceSats - 30000uL - 20000uL, balanceState.totalLightningSats)
    }

    private fun setupBalanceMock(): BalanceDetails = BalanceDetails(
        totalOnchainBalanceSats = 100_000u,
        spendableOnchainBalanceSats = 0u,
        totalAnchorChannelsReserveSats = 10_000u,
        totalLightningBalanceSats = 50_000u,
        lightningBalances = emptyList(),
        pendingBalancesFromChannelClosures = emptyList(),
    ).also { wheneverBlocking { lightningRepo.getBalancesAsync() }.thenReturn(Result.success(it)) }
}

private fun createTransferEntity(
    type: TransferType,
    amountSats: Long,
    channelId: String? = null,
    lspOrderId: String? = null,
) = TransferEntity(
    id = "test-transfer-${System.currentTimeMillis()}",
    type = type,
    amountSats = amountSats,
    channelId = channelId,
    fundingTxId = null,
    lspOrderId = lspOrderId,
    isSettled = false,
    createdAt = System.currentTimeMillis(),
)
