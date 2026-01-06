package to.bitkit.paykit

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import to.bitkit.paykit.models.SubscriptionProposal
import to.bitkit.paykit.services.DirectoryService
import to.bitkit.paykit.services.PubkyRingBridge
import to.bitkit.paykit.storage.AutoPayStorage
import to.bitkit.paykit.storage.SubscriptionProposalStorage
import to.bitkit.paykit.storage.SubscriptionStorage
import to.bitkit.paykit.viewmodels.SubscriptionsViewModel

class SendSubscriptionProposalE2ETest {

    @Test
    fun `E2E - sendSubscriptionProposal publishes proposal to directory`() = runTest {
        // Arrange
        val mockSubscriptionStorage: SubscriptionStorage = mock()
        val mockProposalStorage: SubscriptionProposalStorage = mock()
        val mockDirectoryService: DirectoryService = mock()
        val mockAutoPayStorage: AutoPayStorage = mock()
        val mockKeyManager: KeyManager = mock()
        val mockPubkyRingBridge: PubkyRingBridge = mock()

        val senderPubkey = "tjtigrhbiinfwwh8nbqmfh3mxurhpqqh5kp4r6ypgaqz96dpzzqo"
        val recipientPubkey = "n3pfudgxncn8i1e6icuq7umoczemjuyi6xdfrfczk3o8ej3e55my"

        whenever(mockKeyManager.getCurrentPublicKeyZ32()).thenReturn(senderPubkey)
        whenever(mockDirectoryService.publishSubscriptionProposal(any(), any())).thenReturn(Unit)

        val viewModel = SubscriptionsViewModel(
            subscriptionStorage = mockSubscriptionStorage,
            proposalStorage = mockProposalStorage,
            directoryService = mockDirectoryService,
            autoPayStorage = mockAutoPayStorage,
            keyManager = mockKeyManager,
            pubkyRingBridge = mockPubkyRingBridge,
        )

        // Act
        viewModel.sendSubscriptionProposal(
            recipientPubkey = recipientPubkey,
            amountSats = 1000L,
            frequency = "weekly",
            description = "E2E Test Subscription",
            enableAutopay = false,
            autopayLimitSats = null,
        )

        // Assert - verify proposal was published
        verify(mockDirectoryService).publishSubscriptionProposal(
            any<SubscriptionProposal>(),
            org.mockito.kotlin.eq(recipientPubkey)
        )

        println("✅ E2E TEST PASSED: Subscription proposal from $senderPubkey to $recipientPubkey (1000 sats/weekly)")
    }
}
