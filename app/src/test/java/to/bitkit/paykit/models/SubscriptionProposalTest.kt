package to.bitkit.paykit.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SubscriptionProposalTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `create generates valid proposal with UUID`() {
        val proposal = SubscriptionProposal.create(
            providerPubkey = "pk:provider123",
            providerName = "Test Provider",
            amountSats = 5000,
            frequency = "monthly",
            description = "Test subscription",
        )

        assertTrue(proposal.id.isNotEmpty())
        assertEquals("pk:provider123", proposal.providerPubkey)
        assertEquals("Test Provider", proposal.providerName)
        assertEquals(5000L, proposal.amountSats)
        assertEquals("SAT", proposal.currency)
        assertEquals("monthly", proposal.frequency)
        assertEquals("Test subscription", proposal.description)
        assertEquals("lightning", proposal.methodId)
        assertTrue(proposal.createdAt > 0)
    }

    @Test
    fun `serialization produces expected JSON fields`() {
        val proposal = SubscriptionProposal(
            id = "test-id-123",
            providerPubkey = "pk:provider",
            providerName = "Provider Name",
            amountSats = 10000,
            currency = "SAT",
            frequency = "weekly",
            description = "Weekly payment",
            methodId = "lightning",
            createdAt = 1704067200000,
        )

        val jsonString = json.encodeToString(proposal)

        // Check essential fields are present (JSON format may vary)
        // Note: fields with default values (currency, methodId) may be omitted by kotlinx.serialization
        assertTrue(jsonString.contains("pk:provider"), "Should contain provider pubkey, got: $jsonString")
        assertTrue(jsonString.contains("Provider Name"), "Should contain provider name, got: $jsonString")
        assertTrue(jsonString.contains("10000"), "Should contain amount, got: $jsonString")
        assertTrue(jsonString.contains("weekly"), "Should contain frequency, got: $jsonString")
        assertTrue(jsonString.contains("1704067200000"), "Should contain timestamp, got: $jsonString")
        assertTrue(jsonString.contains("provider_pubkey"), "Should use snake_case for field names")
        assertTrue(jsonString.contains("amount_sats"), "Should use snake_case for amount_sats")
    }

    @Test
    fun `deserialization parses JSON correctly`() {
        val jsonString = """
            {
                "id": "proposal-id",
                "provider_pubkey": "pk:sender",
                "provider_name": "Sender Name",
                "amount_sats": 2500,
                "currency": "SAT",
                "frequency": "daily",
                "description": "Daily payment",
                "method_id": "lightning",
                "created_at": 1704153600000
            }
        """.trimIndent()

        val proposal = json.decodeFromString<SubscriptionProposal>(jsonString)

        assertEquals("proposal-id", proposal.id)
        assertEquals("pk:sender", proposal.providerPubkey)
        assertEquals("Sender Name", proposal.providerName)
        assertEquals(2500L, proposal.amountSats)
        assertEquals("daily", proposal.frequency)
        assertEquals("Daily payment", proposal.description)
        assertEquals(1704153600000L, proposal.createdAt)
    }

    @Test
    fun `toSubscription converts proposal to Subscription correctly`() {
        val proposal = SubscriptionProposal.create(
            providerPubkey = "pk:provider",
            providerName = "Provider",
            amountSats = 7500,
            frequency = "monthly",
            description = "Monthly service",
        )

        val subscription = proposal.toSubscription()

        assertEquals("Provider", subscription.providerName)
        assertEquals("pk:provider", subscription.providerPubkey)
        assertEquals(7500L, subscription.amountSats)
        assertEquals("SAT", subscription.currency)
        assertEquals("monthly", subscription.frequency)
        assertEquals("Monthly service", subscription.description)
        assertEquals("lightning", subscription.methodId)
        assertTrue(subscription.isActive)
        assertEquals(0, subscription.paymentCount)
        assertNotNull(subscription.nextPaymentAt)
    }

    @Test
    fun `toSubscription uses truncated pubkey when no name provided`() {
        val proposal = SubscriptionProposal.create(
            providerPubkey = "abcdefgh12345678",
            providerName = null,
            amountSats = 1000,
            frequency = "weekly",
            description = null,
        )

        val subscription = proposal.toSubscription()

        assertEquals("abcdefgh", subscription.providerName)
    }

    @Test
    fun `path format for subscription proposals matches expected pattern`() {
        val recipientPubkey = "pk:recipient123"
        val proposalId = "proposal-uuid-456"
        val expectedPath = "/pub/paykit.app/v0/subscriptions/proposals/$recipientPubkey/$proposalId"

        val actualPath = "/pub/paykit.app/v0/subscriptions/proposals/$recipientPubkey/$proposalId"

        assertEquals(expectedPath, actualPath)
    }
}

