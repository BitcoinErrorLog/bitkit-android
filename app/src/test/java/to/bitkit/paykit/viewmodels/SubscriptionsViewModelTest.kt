package to.bitkit.paykit.viewmodels

import kotlinx.coroutines.flow.first
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import to.bitkit.paykit.KeyManager
import to.bitkit.paykit.models.Subscription
import to.bitkit.paykit.services.DirectoryService
import to.bitkit.paykit.services.PubkyRingBridge
import to.bitkit.paykit.storage.AutoPayStorage
import to.bitkit.paykit.storage.SubscriptionProposalStorage
import to.bitkit.paykit.storage.SubscriptionStorage
import to.bitkit.paykit.workers.DiscoveredSubscriptionProposal
import to.bitkit.test.BaseUnitTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionsViewModelTest : BaseUnitTest() {

    private lateinit var subscriptionStorage: SubscriptionStorage
    private lateinit var proposalStorage: SubscriptionProposalStorage
    private lateinit var directoryService: DirectoryService
    private lateinit var autoPayStorage: AutoPayStorage
    private lateinit var keyManager: KeyManager
    private lateinit var pubkyRingBridge: PubkyRingBridge
    private lateinit var viewModel: SubscriptionsViewModel

    @Before
    fun setup() {
        subscriptionStorage = mock()
        proposalStorage = mock()
        directoryService = mock()
        autoPayStorage = mock()
        keyManager = mock()
        pubkyRingBridge = mock()

        whenever(subscriptionStorage.listSubscriptions()).thenReturn(emptyList())
        whenever(keyManager.getCurrentPublicKeyZ32()).thenReturn("pk:owner")
        whenever(proposalStorage.pendingProposals(any())).thenReturn(emptyList())

        viewModel = SubscriptionsViewModel(
            subscriptionStorage,
            proposalStorage,
            directoryService,
            autoPayStorage,
            keyManager,
            pubkyRingBridge,
        )
    }

    @Test
    fun `loadSubscriptions updates state with subscriptions from storage`() = test {
        val subscriptions = listOf(
            Subscription.create(
                providerName = "Provider1",
                providerPubkey = "pk:p1",
                amountSats = 1000,
                frequency = "monthly",
                description = "Test1",
            ),
            Subscription.create(
                providerName = "Provider2",
                providerPubkey = "pk:p2",
                amountSats = 2000,
                frequency = "weekly",
                description = "Test2",
            ),
        )
        whenever(subscriptionStorage.listSubscriptions()).thenReturn(subscriptions)

        viewModel.loadSubscriptions()

        val result = viewModel.subscriptions.first()
        assertEquals(2, result.size)
    }

    @Test
    fun `acceptProposal creates subscription in storage`() = test {
        val proposal = DiscoveredSubscriptionProposal(
            subscriptionId = "prop-1",
            providerPubkey = "pk:provider",
            amountSats = 5000,
            description = "Test proposal",
            frequency = "monthly",
            createdAt = System.currentTimeMillis(),
        )
        wheneverBlocking { subscriptionStorage.saveSubscription(any()) }.thenAnswer { }
        wheneverBlocking { directoryService.removeSubscriptionProposal(any(), any()) }.thenAnswer { }

        viewModel.acceptProposal(proposal, enableAutopay = false)

        verify(subscriptionStorage).saveSubscription(argThat { subscription ->
            subscription.providerPubkey == "pk:provider" &&
                subscription.amountSats == 5000L &&
                subscription.frequency == "monthly"
        })
    }

    @Test
    fun `acceptProposal with autopay creates autopay rule`() = test {
        val proposal = DiscoveredSubscriptionProposal(
            subscriptionId = "prop-1",
            providerPubkey = "pk:provider",
            amountSats = 3000,
            description = "With autopay",
            frequency = "weekly",
            createdAt = System.currentTimeMillis(),
        )
        wheneverBlocking { subscriptionStorage.saveSubscription(any()) }.thenAnswer { }
        wheneverBlocking { autoPayStorage.saveRule(any()) }.thenAnswer { }
        wheneverBlocking { autoPayStorage.savePeerLimit(any()) }.thenAnswer { }
        wheneverBlocking { directoryService.removeSubscriptionProposal(any(), any()) }.thenAnswer { }

        viewModel.acceptProposal(proposal, enableAutopay = true, autopayLimitSats = 10000)

        verify(autoPayStorage).saveRule(argThat { rule ->
            rule.peerPubkey == "pk:provider" &&
                rule.isEnabled &&
                rule.maxAmountSats == 10000L
        })
        verify(autoPayStorage).savePeerLimit(argThat { limit ->
            limit.peerPubkey == "pk:provider" &&
                limit.limitSats == 10000L
        })
    }

    @Test
    fun `acceptProposal without autopay does not create rules`() = test {
        val proposal = DiscoveredSubscriptionProposal(
            subscriptionId = "prop-1",
            providerPubkey = "pk:provider",
            amountSats = 1000,
            description = "No autopay",
            frequency = "monthly",
            createdAt = System.currentTimeMillis(),
        )
        wheneverBlocking { subscriptionStorage.saveSubscription(any()) }.thenAnswer { }
        wheneverBlocking { directoryService.removeSubscriptionProposal(any(), any()) }.thenAnswer { }

        viewModel.acceptProposal(proposal, enableAutopay = false)

        verify(autoPayStorage, never()).saveRule(any())
        verify(autoPayStorage, never()).savePeerLimit(any())
    }

    @Test
    fun `acceptProposal does not remove proposal from directory after accepting`() = test {
        val proposal = DiscoveredSubscriptionProposal(
            subscriptionId = "prop-123",
            providerPubkey = "pk:provider",
            amountSats = 1000,
            description = null,
            frequency = "monthly",
            createdAt = System.currentTimeMillis(),
        )
        wheneverBlocking { subscriptionStorage.saveSubscription(any()) }.thenAnswer { }

        viewModel.acceptProposal(proposal, enableAutopay = false)

        verify(directoryService, never()).removeSubscriptionProposal(any(), any())
    }

    @Test
    fun `declineProposal does not remove proposal from directory`() = test {
        val proposal = DiscoveredSubscriptionProposal(
            subscriptionId = "prop-decline",
            providerPubkey = "pk:provider",
            amountSats = 500,
            description = null,
            frequency = "daily",
            createdAt = System.currentTimeMillis(),
        )

        viewModel.declineProposal(proposal)

        verify(directoryService, never()).removeSubscriptionProposal(any(), any())
    }

    @Test
    fun `initial uiState has empty proposals`() = test {
        val state = viewModel.uiState.first()

        assertTrue(state.incomingProposals.isEmpty())
        assertFalse(state.isLoadingProposals)
        assertFalse(state.isSending)
    }
}

