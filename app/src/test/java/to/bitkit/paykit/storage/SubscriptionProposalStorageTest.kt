package to.bitkit.paykit.storage

import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import to.bitkit.test.BaseUnitTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionProposalStorageTest : BaseUnitTest() {

    private lateinit var keychain: PaykitKeychainStorage
    private lateinit var storage: SubscriptionProposalStorage

    @Before
    fun setup() {
        keychain = mock()
        storage = SubscriptionProposalStorage(keychain)
    }

    @Test
    fun `listProposals returns empty list when no data stored`() = test {
        whenever(keychain.retrieve(any())).thenReturn(null)

        val result = storage.listProposals("pk:test_identity")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listProposals parses stored JSON correctly`() = test {
        val json = """[{"id":"prop1","providerPubkey":"pk:provider","amountSats":1000,"description":"Test","frequency":"monthly","createdAt":1704067200000,"status":"PENDING"}]"""
        whenever(keychain.retrieve("proposals.pk:test_identity")).thenReturn(json.toByteArray())

        val result = storage.listProposals("pk:test_identity")

        assertEquals(1, result.size)
        assertEquals("prop1", result[0].id)
        assertEquals("pk:provider", result[0].providerPubkey)
        assertEquals(1000L, result[0].amountSats)
        assertEquals("monthly", result[0].frequency)
        assertEquals(ProposalStatus.PENDING, result[0].status)
    }

    @Test
    fun `listProposals uses cache on subsequent calls`() = test {
        val json = """[{"id":"prop1","providerPubkey":"pk:provider","amountSats":1000,"description":"Test","frequency":"monthly","createdAt":1704067200000,"status":"PENDING"}]"""
        whenever(keychain.retrieve("proposals.pk:test_identity")).thenReturn(json.toByteArray())

        val result1 = storage.listProposals("pk:test_identity")
        whenever(keychain.retrieve(any())).thenReturn(null)
        val result2 = storage.listProposals("pk:test_identity")

        assertEquals(result1, result2)
        assertEquals(1, result2.size)
    }

    @Test
    fun `listSentProposals returns empty list when no data stored`() = test {
        whenever(keychain.retrieve(any())).thenReturn(null)

        val result = storage.listSentProposals("pk:test_identity")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listSentProposals parses stored JSON correctly`() = test {
        val json = """[{"id":"sent1","recipientPubkey":"pk:recipient","amountSats":2000,"frequency":"weekly","description":"Sent proposal","sentAt":1704067200000,"status":"PENDING"}]"""
        whenever(keychain.retrieve("proposals.sent.pk:test_identity")).thenReturn(json.toByteArray())

        val result = storage.listSentProposals("pk:test_identity")

        assertEquals(1, result.size)
        assertEquals("sent1", result[0].id)
        assertEquals("pk:recipient", result[0].recipientPubkey)
        assertEquals(2000L, result[0].amountSats)
        assertEquals("weekly", result[0].frequency)
        assertEquals(SentProposalStatus.PENDING, result[0].status)
    }

    @Test
    fun `invalidateCache clears all caches and forces fresh read`() = test {
        val proposalsJson = """[{"id":"prop1","providerPubkey":"pk:provider","amountSats":1000,"description":"Test","frequency":"monthly","createdAt":1704067200000,"status":"PENDING"}]"""
        val sentJson = """[{"id":"sent1","recipientPubkey":"pk:recipient","amountSats":2000,"frequency":"weekly","description":"Sent","sentAt":1704067200000,"status":"PENDING"}]"""
        val seenJson = """["seen1","seen2"]"""
        val declinedJson = """["declined1"]"""

        whenever(keychain.retrieve("proposals.pk:test")).thenReturn(proposalsJson.toByteArray())
        whenever(keychain.retrieve("proposals.sent.pk:test")).thenReturn(sentJson.toByteArray())
        whenever(keychain.retrieve("proposals.seen.pk:test")).thenReturn(seenJson.toByteArray())
        whenever(keychain.retrieve("proposals.declined.pk:test")).thenReturn(declinedJson.toByteArray())

        storage.listProposals("pk:test")
        storage.listSentProposals("pk:test")
        storage.hasSeen("pk:test", "seen1")
        storage.pendingProposals("pk:test")

        val updatedProposalsJson = """[{"id":"prop2","providerPubkey":"pk:newprovider","amountSats":3000,"description":"New","frequency":"daily","createdAt":1704070000000,"status":"PENDING"}]"""
        whenever(keychain.retrieve("proposals.pk:test")).thenReturn(updatedProposalsJson.toByteArray())

        storage.invalidateCache()

        val result = storage.listProposals("pk:test")
        assertEquals(1, result.size)
        assertEquals("prop2", result[0].id)
        assertEquals("pk:newprovider", result[0].providerPubkey)
    }

    @Test
    fun `invalidateCache allows fresh read of sent proposals`() = test {
        val sentJson = """[{"id":"sent1","recipientPubkey":"pk:recipient","amountSats":2000,"frequency":"weekly","description":"Sent","sentAt":1704067200000,"status":"PENDING"}]"""
        whenever(keychain.retrieve("proposals.sent.pk:test")).thenReturn(sentJson.toByteArray())
        storage.listSentProposals("pk:test")

        val updatedSentJson = """[{"id":"sent2","recipientPubkey":"pk:newrecipient","amountSats":5000,"frequency":"monthly","description":"Updated","sentAt":1704080000000,"status":"ACCEPTED"}]"""
        whenever(keychain.retrieve("proposals.sent.pk:test")).thenReturn(updatedSentJson.toByteArray())

        storage.invalidateCache()

        val result = storage.listSentProposals("pk:test")
        assertEquals(1, result.size)
        assertEquals("sent2", result[0].id)
        assertEquals("pk:newrecipient", result[0].recipientPubkey)
        assertEquals(SentProposalStatus.ACCEPTED, result[0].status)
    }

    @Test
    fun `pendingProposals filters out declined proposals`() = test {
        val proposalsJson = """[
            {"id":"prop1","providerPubkey":"pk:p1","amountSats":1000,"description":"Test1","frequency":"monthly","createdAt":1704067200000,"status":"PENDING"},
            {"id":"prop2","providerPubkey":"pk:p2","amountSats":2000,"description":"Test2","frequency":"weekly","createdAt":1704067200000,"status":"PENDING"}
        ]"""
        val declinedJson = """["prop2"]"""
        whenever(keychain.retrieve("proposals.pk:test")).thenReturn(proposalsJson.toByteArray())
        whenever(keychain.retrieve("proposals.declined.pk:test")).thenReturn(declinedJson.toByteArray())

        val result = storage.pendingProposals("pk:test")

        assertEquals(1, result.size)
        assertEquals("prop1", result[0].id)
    }

    @Test
    fun `hasSeen returns true for seen proposal`() = test {
        val seenJson = """["seen1","seen2"]"""
        whenever(keychain.retrieve("proposals.seen.pk:test")).thenReturn(seenJson.toByteArray())

        val result = storage.hasSeen("pk:test", "seen1")

        assertTrue(result)
    }

    @Test
    fun `hasSeen returns false for unseen proposal`() = test {
        val seenJson = """["seen1","seen2"]"""
        whenever(keychain.retrieve("proposals.seen.pk:test")).thenReturn(seenJson.toByteArray())

        val result = storage.hasSeen("pk:test", "seen3")

        assertFalse(result)
    }
}

