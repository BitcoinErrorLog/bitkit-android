package to.bitkit.paykit.storage

import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import to.bitkit.paykit.KeyManager
import to.bitkit.paykit.models.Subscription
import to.bitkit.test.BaseUnitTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionStorageTest : BaseUnitTest() {

    private lateinit var keychain: PaykitKeychainStorage
    private lateinit var keyManager: KeyManager
    private lateinit var storage: SubscriptionStorage

    @Before
    fun setup() {
        keychain = mock()
        keyManager = mock()
        whenever(keyManager.getCurrentPublicKeyZ32()).thenReturn("pk:test_identity")
        storage = SubscriptionStorage(keychain, keyManager)
    }

    @Test
    fun `listSubscriptions returns empty list when no data stored`() = test {
        whenever(keychain.retrieve(any())).thenReturn(null)

        val result = storage.listSubscriptions()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listSubscriptions parses stored JSON correctly`() = test {
        val json = """[{"id":"sub1","providerName":"Provider","providerPubkey":"pk:test","amountSats":1000,"currency":"SAT","frequency":"monthly","description":"Test","methodId":"lightning","isActive":true,"createdAt":1704067200000,"paymentCount":0}]"""
        whenever(keychain.retrieve(any())).thenReturn(json.toByteArray())

        val result = storage.listSubscriptions()

        assertEquals(1, result.size)
        assertEquals("sub1", result[0].id)
        assertEquals("Provider", result[0].providerName)
        assertEquals("pk:test", result[0].providerPubkey)
        assertEquals(1000L, result[0].amountSats)
        assertEquals("monthly", result[0].frequency)
        assertTrue(result[0].isActive)
    }

    @Test
    fun `getSubscription returns matching subscription`() = test {
        val json = """[{"id":"sub1","providerName":"Provider","providerPubkey":"pk:test","amountSats":1000,"currency":"SAT","frequency":"monthly","description":"Test","methodId":"lightning","isActive":true,"createdAt":1704067200000,"paymentCount":0}]"""
        whenever(keychain.retrieve(any())).thenReturn(json.toByteArray())

        val result = storage.getSubscription("sub1")

        assertNotNull(result)
        assertEquals("sub1", result.id)
    }

    @Test
    fun `getSubscription returns null for non-existent id`() = test {
        val json = """[{"id":"sub1","providerName":"Provider","providerPubkey":"pk:test","amountSats":1000,"currency":"SAT","frequency":"monthly","description":"Test","methodId":"lightning","isActive":true,"createdAt":1704067200000,"paymentCount":0}]"""
        whenever(keychain.retrieve(any())).thenReturn(json.toByteArray())

        val result = storage.getSubscription("non-existent")

        assertNull(result)
    }

    @Test
    fun `activeSubscriptions filters inactive subscriptions`() = test {
        val json = """[
            {"id":"sub1","providerName":"Active","providerPubkey":"pk:test","amountSats":1000,"currency":"SAT","frequency":"monthly","description":"Test","methodId":"lightning","isActive":true,"createdAt":1704067200000,"paymentCount":0},
            {"id":"sub2","providerName":"Inactive","providerPubkey":"pk:test2","amountSats":2000,"currency":"SAT","frequency":"weekly","description":"Test","methodId":"lightning","isActive":false,"createdAt":1704067200000,"paymentCount":0}
        ]"""
        whenever(keychain.retrieve(any())).thenReturn(json.toByteArray())

        val result = storage.activeSubscriptions()

        assertEquals(1, result.size)
        assertEquals("sub1", result[0].id)
        assertTrue(result[0].isActive)
    }

    @Test
    fun `Subscription create generates id and calculates next payment`() {
        val subscription = Subscription.create(
            providerName = "Test Provider",
            providerPubkey = "pk:test",
            amountSats = 5000,
            frequency = "monthly",
            description = "Test subscription",
        )

        assertTrue(subscription.id.isNotEmpty())
        assertEquals("Test Provider", subscription.providerName)
        assertEquals("pk:test", subscription.providerPubkey)
        assertEquals(5000L, subscription.amountSats)
        assertEquals("monthly", subscription.frequency)
        assertTrue(subscription.isActive)
        assertEquals(0, subscription.paymentCount)
        assertNotNull(subscription.nextPaymentAt)
        assertTrue(subscription.nextPaymentAt!! > System.currentTimeMillis())
    }

    @Test
    fun `Subscription recordPayment updates count and next payment`() {
        val subscription = Subscription.create(
            providerName = "Test",
            providerPubkey = "pk:test",
            amountSats = 1000,
            frequency = "daily",
            description = "",
        )

        val updated = subscription.recordPayment(
            paymentHash = "hash123",
            preimage = "preimage456",
            feeSats = 10u,
        )

        assertEquals(1, updated.paymentCount)
        assertNotNull(updated.lastPaymentAt)
        assertEquals("hash123", updated.lastPaymentHash)
        assertEquals("preimage456", updated.lastPreimage)
        assertEquals(10uL, updated.lastFeeSats)
        assertTrue(updated.nextPaymentAt!! > updated.lastPaymentAt!!)
    }
}

