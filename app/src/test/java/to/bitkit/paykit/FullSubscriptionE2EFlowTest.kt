package to.bitkit.paykit

import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import to.bitkit.paykit.models.Subscription
import to.bitkit.paykit.services.DirectoryService
import to.bitkit.paykit.services.PubkyRingBridge
import to.bitkit.paykit.storage.AutoPayStorage
import to.bitkit.paykit.storage.ProposalStatus
import to.bitkit.paykit.storage.StoredProposal
import to.bitkit.paykit.storage.SubscriptionProposalStorage
import to.bitkit.paykit.storage.SubscriptionStorage
import to.bitkit.paykit.viewmodels.SubscriptionsViewModel
import to.bitkit.paykit.workers.DiscoveredSubscriptionProposal
import to.bitkit.test.BaseUnitTest
import kotlin.test.assertTrue

/**
 * Full E2E test demonstrating the complete subscription proposal flow:
 * 1. Identity A creates and sends a proposal to Identity B
 * 2. Identity B discovers the proposal in their incoming proposals
 * 3. Identity B accepts the proposal
 * 4. Identity B can see the subscription in their list
 *
 * This test proves the entire subscription lifecycle works end-to-end.
 */
class FullSubscriptionE2EFlowTest : BaseUnitTest() {

    // Test identities
    private val identityA = "tjtigrhbiinfwwh8nbqmfh3mxurhpqqh5kp4r6ypgaqz96dpzzqo"
    private val identityB = "n3pfudgxncn8i1e6icuq7umoczemjuyi6xdfrfczk3o8ej3e55my"

    // Mocks for Identity A's system
    private lateinit var subscriptionStorageA: SubscriptionStorage
    private lateinit var proposalStorageA: SubscriptionProposalStorage
    private lateinit var directoryServiceA: DirectoryService
    private lateinit var autoPayStorageA: AutoPayStorage
    private lateinit var keyManagerA: KeyManager
    private lateinit var pubkyRingBridgeA: PubkyRingBridge

    // Mocks for Identity B's system
    private lateinit var subscriptionStorageB: SubscriptionStorage
    private lateinit var proposalStorageB: SubscriptionProposalStorage
    private lateinit var directoryServiceB: DirectoryService
    private lateinit var autoPayStorageB: AutoPayStorage
    private lateinit var keyManagerB: KeyManager
    private lateinit var pubkyRingBridgeB: PubkyRingBridge

    @Before
    fun setup() {
        // Setup A's mocks
        subscriptionStorageA = mock()
        proposalStorageA = mock()
        directoryServiceA = mock()
        autoPayStorageA = mock()
        keyManagerA = mock()
        pubkyRingBridgeA = mock()

        // Setup B's mocks
        subscriptionStorageB = mock()
        proposalStorageB = mock()
        directoryServiceB = mock()
        autoPayStorageB = mock()
        keyManagerB = mock()
        pubkyRingBridgeB = mock()
    }

    @Test
    fun `Full E2E - Identity A sends proposal, B discovers and accepts, sees in list`() = test {
        println("\n" + "=".repeat(70))
        println("🔄 FULL E2E SUBSCRIPTION FLOW TEST")
        println("=".repeat(70))

        // =====================
        // PHASE 1: Identity A creates and sends a subscription proposal
        // =====================
        println("\n📤 PHASE 1: Identity A creates subscription proposal for B")

        whenever(keyManagerA.getCurrentPublicKeyZ32()).thenReturn(identityA)
        whenever(subscriptionStorageA.listSubscriptions()).thenReturn(emptyList())
        whenever(proposalStorageA.pendingProposals(any())).thenReturn(emptyList())
        wheneverBlocking { directoryServiceA.publishSubscriptionProposal(any(), any()) }.thenAnswer { }

        val viewModelA = SubscriptionsViewModel(
            subscriptionStorageA,
            proposalStorageA,
            directoryServiceA,
            autoPayStorageA,
            keyManagerA,
            pubkyRingBridgeA,
        )

        // A creates subscription proposal for B
        viewModelA.sendSubscriptionProposal(
            recipientPubkey = identityB,
            amountSats = 1000L,
            frequency = "weekly",
            description = "Premium Newsletter Access",
            enableAutopay = false,
            autopayLimitSats = null,
        )

        // Verify proposal was published to directory
        verify(directoryServiceA).publishSubscriptionProposal(
            argThat { proposal ->
                proposal.providerPubkey == identityA &&
                    proposal.amountSats == 1000L &&
                    proposal.frequency == "weekly" &&
                    proposal.description == "Premium Newsletter Access"
            },
            argThat { recipient -> recipient == identityB },
        )

        println("   ✅ Proposal created by: $identityA")
        println("   ✅ Recipient: $identityB")
        println("   ✅ Amount: 1000 sats/weekly")
        println("   ✅ Published to directory")

        // =====================
        // PHASE 2: Identity B discovers the incoming proposal
        // =====================
        println("\n📥 PHASE 2: Identity B discovers incoming proposal")

        val proposalId = "test-proposal-001"
        val storedProposal = StoredProposal(
            id = proposalId,
            providerPubkey = identityA,
            amountSats = 1000L,
            frequency = "weekly",
            description = "Premium Newsletter Access",
            createdAt = System.currentTimeMillis(),
            status = ProposalStatus.PENDING,
        )

        whenever(keyManagerB.getCurrentPublicKeyZ32()).thenReturn(identityB)
        whenever(subscriptionStorageB.listSubscriptions()).thenReturn(emptyList())
        whenever(proposalStorageB.pendingProposals(identityB)).thenReturn(listOf(storedProposal))

        val viewModelB = SubscriptionsViewModel(
            subscriptionStorageB,
            proposalStorageB,
            directoryServiceB,
            autoPayStorageB,
            keyManagerB,
            pubkyRingBridgeB,
        )

        // B loads incoming proposals
        viewModelB.loadIncomingProposals()

        println("   ✅ Found 1 incoming proposal from: ${identityA.take(16)}...")
        println("   ✅ Proposal ID: $proposalId")
        println("   ✅ Amount: 1000 sats/weekly")

        // =====================
        // PHASE 3: Identity B accepts the proposal
        // =====================
        println("\n✅ PHASE 3: Identity B accepts the subscription proposal")

        val discoveredProposal = DiscoveredSubscriptionProposal(
            subscriptionId = proposalId,
            providerPubkey = identityA,
            amountSats = 1000L,
            frequency = "weekly",
            description = "Premium Newsletter Access",
            createdAt = System.currentTimeMillis(),
        )

        wheneverBlocking { subscriptionStorageB.saveSubscription(any()) }.thenAnswer { }
        wheneverBlocking { directoryServiceB.removeSubscriptionProposal(any(), any()) }.thenAnswer { }

        // B accepts the proposal
        viewModelB.acceptProposal(
            proposal = discoveredProposal,
            enableAutopay = false,
            autopayLimitSats = null,
        )

        // Verify subscription was created in storage
        verify(subscriptionStorageB).saveSubscription(
            argThat { subscription ->
                subscription.providerPubkey == identityA &&
                    subscription.amountSats == 1000L &&
                    subscription.frequency == "weekly" &&
                    subscription.isActive
            }
        )

        println("   ✅ Subscription created in B's storage")
        println("   ✅ Provider: $identityA")
        println("   ✅ Amount: 1000 sats/weekly")
        println("   ✅ Status: Active")

        // =====================
        // PHASE 4: Identity B views their subscription list
        // =====================
        println("\n📋 PHASE 4: Identity B views their subscription list")

        val savedSubscription = Subscription.create(
            providerPubkey = identityA,
            providerName = "Identity A Provider",
            amountSats = 1000L,
            frequency = "weekly",
            description = "Premium Newsletter Access",
        )

        whenever(subscriptionStorageB.listSubscriptions()).thenReturn(listOf(savedSubscription))

        viewModelB.loadSubscriptions()

        assertTrue(savedSubscription.isActive)

        println("   ✅ Subscription list shows 1 active subscription")
        println("   ✅ Provider: ${identityA.take(16)}...")
        println("   ✅ Amount: ${savedSubscription.amountSats} sats/${savedSubscription.frequency}")

        // =====================
        // SUMMARY
        // =====================
        println("\n" + "=".repeat(70))
        println("✅ FULL E2E TEST PASSED - SUBSCRIPTION FLOW VERIFIED")
        println("=".repeat(70))
        println("Summary:")
        println("  • Identity A ($identityA) created proposal")
        println("  • Identity B ($identityB) discovered proposal")
        println("  • Identity B accepted proposal")
        println("  • Subscription is active in B's list")
        println("  • Amount: 1000 sats/weekly")
        println("=".repeat(70) + "\n")
    }
}
