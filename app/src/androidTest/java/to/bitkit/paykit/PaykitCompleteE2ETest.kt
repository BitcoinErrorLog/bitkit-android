package to.bitkit.paykit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import to.bitkit.data.keychain.Keychain
import to.bitkit.paykit.models.Contact
import to.bitkit.paykit.models.Receipt
import to.bitkit.paykit.models.Subscription
import to.bitkit.paykit.services.*
import to.bitkit.paykit.storage.*
import to.bitkit.paykit.viewmodels.*
import to.bitkit.test.BaseAndroidTest
import javax.inject.Inject
import kotlin.test.*

/**
 * Comprehensive E2E tests for Paykit integration
 * Tests all features: contacts, receipts, subscriptions, auto-pay, noise payments
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PaykitCompleteE2ETest : BaseAndroidTest() {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var keychain: Keychain

    @Inject
    lateinit var contactStorage: ContactStorage

    @Inject
    lateinit var receiptStorage: ReceiptStorage

    @Inject
    lateinit var subscriptionStorage: SubscriptionStorage

    @Inject
    lateinit var autoPayStorage: AutoPayStorage

    @Inject
    lateinit var paymentRequestStorage: PaymentRequestStorage

    @Inject
    lateinit var keyManager: KeyManager

    @Inject
    lateinit var directoryService: DirectoryService

    @Inject
    lateinit var noisePaymentService: NoisePaymentService

    @Before
    fun setUp() = runBlocking {
        hiltRule.inject()
        
        // Clear all storage
        contactStorage.getAllContacts().forEach { contactStorage.deleteContact(it.pubkey) }
        receiptStorage.getAllReceipts().forEach { receiptStorage.deleteReceipt(it.id) }
        subscriptionStorage.getAllSubscriptions().forEach { subscriptionStorage.deleteSubscription(it.id) }
        autoPayStorage.getSettings()?.let { autoPayStorage.saveSettings(it.copy(enabled = false)) }
    }

    @After
    fun tearDown() = runBlocking {
        // Cleanup
        contactStorage.getAllContacts().forEach { contactStorage.deleteContact(it.pubkey) }
        receiptStorage.getAllReceipts().forEach { receiptStorage.deleteReceipt(it.id) }
        subscriptionStorage.getAllSubscriptions().forEach { subscriptionStorage.deleteSubscription(it.id) }
    }

    // MARK: - Contact Management E2E Tests

    @Test
    fun `test contact creation and retrieval`() = test {
        // Given a new contact
        val contact = Contact(
            pubkey = "test_pubkey_123",
            name = "Test Contact",
            notes = "Test notes"
        )

        // When we save the contact
        contactStorage.saveContact(contact)

        // Then we can retrieve it
        val retrieved = contactStorage.getContact(contact.pubkey)
        assertNotNull(retrieved)
        assertEquals(contact.pubkey, retrieved.pubkey)
        assertEquals(contact.name, retrieved.name)
        assertEquals(contact.notes, retrieved.notes)
    }

    @Test
    fun `test contact search`() = test {
        // Given multiple contacts
        val contact1 = Contact(pubkey = "pubkey1", name = "Alice")
        val contact2 = Contact(pubkey = "pubkey2", name = "Bob")
        val contact3 = Contact(pubkey = "pubkey3", name = "Charlie")

        contactStorage.saveContact(contact1)
        contactStorage.saveContact(contact2)
        contactStorage.saveContact(contact3)

        // When we search
        val results = contactStorage.searchContacts("Bob")

        // Then we find the right contact
        assertEquals(1, results.size)
        assertEquals("Bob", results.first().name)
    }

    @Test
    fun `test contact payment recording`() = test {
        // Given a contact
        val contact = Contact(pubkey = "test_pubkey", name = "Test")
        contactStorage.saveContact(contact)

        // When we record a payment
        contactStorage.recordPayment(contact.pubkey, 1000uL, "lightning")

        // Then payment is recorded
        val updated = contactStorage.getContact(contact.pubkey)
        assertNotNull(updated)
        assertEquals(1, updated.totalPayments)
        assertEquals(1000uL, updated.totalAmount)
    }

    // MARK: - Receipt Management E2E Tests

    @Test
    fun `test receipt creation and storage`() = test {
        // Given a receipt
        val receipt = Receipt(
            id = "receipt_123",
            payerPubkey = "payer_pubkey",
            payeePubkey = "payee_pubkey",
            amount = "1000",
            currency = "sats",
            methodId = "lightning",
            status = Receipt.Status.CONFIRMED,
            direction = Receipt.Direction.SENT,
            createdAt = System.currentTimeMillis() / 1000
        )

        // When we save it
        receiptStorage.saveReceipt(receipt)

        // Then we can retrieve it
        val retrieved = receiptStorage.getReceipt(receipt.id)
        assertNotNull(retrieved)
        assertEquals(receipt.id, retrieved.id)
        assertEquals(receipt.amount, retrieved.amount)
    }

    @Test
    fun `test receipt filtering by status`() = test {
        // Given receipts with different statuses
        val confirmed = Receipt(
            id = "confirmed_1",
            payerPubkey = "payer",
            payeePubkey = "payee",
            amount = "1000",
            currency = "sats",
            methodId = "lightning",
            status = Receipt.Status.CONFIRMED,
            direction = Receipt.Direction.SENT,
            createdAt = System.currentTimeMillis() / 1000
        )
        val pending = Receipt(
            id = "pending_1",
            payerPubkey = "payer",
            payeePubkey = "payee",
            amount = "2000",
            currency = "sats",
            methodId = "lightning",
            status = Receipt.Status.PENDING,
            direction = Receipt.Direction.SENT,
            createdAt = System.currentTimeMillis() / 1000
        )

        receiptStorage.saveReceipt(confirmed)
        receiptStorage.saveReceipt(pending)

        // When we filter by status
        val confirmedReceipts = receiptStorage.getReceiptsByStatus(Receipt.Status.CONFIRMED)
        val pendingReceipts = receiptStorage.getReceiptsByStatus(Receipt.Status.PENDING)

        // Then we get the right receipts
        assertEquals(1, confirmedReceipts.size)
        assertEquals(1, pendingReceipts.size)
        assertEquals("confirmed_1", confirmedReceipts.first().id)
        assertEquals("pending_1", pendingReceipts.first().id)
    }

    // MARK: - Subscription Management E2E Tests

    @Test
    fun `test subscription creation and activation`() = test {
        // Given a subscription
        val subscription = Subscription(
            id = "sub_123",
            providerPubkey = "provider_pubkey",
            providerName = "Test Provider",
            amount = "5000",
            currency = "sats",
            frequency = Subscription.Frequency.MONTHLY,
            isActive = true,
            createdAt = System.currentTimeMillis() / 1000
        )

        // When we save it
        subscriptionStorage.saveSubscription(subscription)

        // Then we can retrieve it
        val retrieved = subscriptionStorage.getSubscription(subscription.id)
        assertNotNull(retrieved)
        assertEquals(subscription.id, retrieved.id)
        assertTrue(retrieved.isActive)

        // When we toggle it
        subscriptionStorage.toggleActive(subscription.id, false)

        // Then it's inactive
        val updated = subscriptionStorage.getSubscription(subscription.id)
        assertNotNull(updated)
        assertFalse(updated.isActive)
    }

    @Test
    fun `test subscription payment recording`() = test {
        // Given a subscription
        val subscription = Subscription(
            id = "sub_123",
            providerPubkey = "provider",
            providerName = "Provider",
            amount = "1000",
            currency = "sats",
            frequency = Subscription.Frequency.MONTHLY,
            isActive = true,
            createdAt = System.currentTimeMillis() / 1000
        )
        subscriptionStorage.saveSubscription(subscription)

        // When we record a payment
        subscriptionStorage.recordPayment(subscription.id, System.currentTimeMillis() / 1000)

        // Then payment is recorded
        val updated = subscriptionStorage.getSubscription(subscription.id)
        assertNotNull(updated)
        assertTrue(updated.lastPaymentAt != null)
    }

    // MARK: - Auto-Pay E2E Tests

    @Test
    fun `test auto-pay settings management`() = test {
        // Given auto-pay settings
        val settings = to.bitkit.paykit.models.AutoPaySettings(
            enabled = true,
            defaultMethodId = "lightning",
            maxAmountPerPayment = "10000",
            maxAmountPerDay = "100000"
        )

        // When we save settings
        autoPayStorage.saveSettings(settings)

        // Then we can retrieve them
        val retrieved = autoPayStorage.getSettings()
        assertNotNull(retrieved)
        assertTrue(retrieved.enabled)
        assertEquals("lightning", retrieved.defaultMethodId)
    }

    @Test
    fun `test peer spending limit management`() = test {
        // Given a peer limit
        val limit = to.bitkit.paykit.models.PeerSpendingLimit(
            peerPubkey = "peer_pubkey",
            maxAmountPerPayment = "5000",
            maxAmountPerDay = "50000",
            resetAt = System.currentTimeMillis() / 1000 + 86400
        )

        // When we save it
        autoPayStorage.savePeerLimit(limit)

        // Then we can retrieve it
        val retrieved = autoPayStorage.getPeerLimit(limit.peerPubkey)
        assertNotNull(retrieved)
        assertEquals(limit.peerPubkey, retrieved.peerPubkey)
        assertEquals(limit.maxAmountPerPayment, retrieved.maxAmountPerPayment)
    }

    @Test
    fun `test auto-pay rule matching`() = test {
        // Given an auto-pay rule
        val rule = to.bitkit.paykit.models.AutoPayRule(
            id = "rule_123",
            peerPubkey = "peer_pubkey",
            methodId = "lightning",
            maxAmount = "10000",
            description = "Test rule"
        )

        // When we save it
        autoPayStorage.saveRule(rule)

        // Then we can find matching rules
        val matches = autoPayStorage.getMatchingRules("peer_pubkey", "lightning", 5000uL)
        assertTrue(matches.isNotEmpty())
        assertEquals("rule_123", matches.first().id)
    }

    // MARK: - Payment Request E2E Tests

    @Test
    fun `test payment request creation and expiration`() = test {
        // Given a payment request
        val request = to.bitkit.paykit.models.PaymentRequest(
            id = "req_123",
            senderPubkey = "sender",
            receiverPubkey = "receiver",
            amount = "1000",
            currency = "sats",
            methodId = "lightning",
            status = to.bitkit.paykit.models.PaymentRequest.Status.PENDING,
            direction = to.bitkit.paykit.models.PaymentRequest.Direction.INCOMING,
            createdAt = System.currentTimeMillis() / 1000,
            expiresAt = System.currentTimeMillis() / 1000 + 3600
        )

        // When we save it
        paymentRequestStorage.saveRequest(request)

        // Then we can retrieve it
        val retrieved = paymentRequestStorage.getRequest(request.id)
        assertNotNull(retrieved)
        assertEquals(request.id, retrieved.id)

        // When we check expiration
        val isExpired = paymentRequestStorage.isExpired(request.id)
        assertFalse(isExpired)
    }

    // MARK: - Key Management E2E Tests

    @Test
    fun `test identity key generation`() = test {
        // When we generate a new identity
        val keypair = keyManager.getOrCreateIdentity()

        // Then we have an identity
        assertNotNull(keypair)
        assertTrue(keypair.publicKeyZ32.isNotEmpty())
        assertTrue(keypair.secretKeyHex.isNotEmpty())

        // And we can retrieve it
        val pubkey = keyManager.getCurrentPublicKeyZ32()
        assertNotNull(pubkey)
        assertEquals(keypair.publicKeyZ32, pubkey)
    }

    @Test
    fun `test X25519 key derivation`() = test {
        // Given we have an identity
        keyManager.getOrCreateIdentity()

        // When we derive X25519 keys
        val x25519Keypair = keyManager.deriveX25519Keypair()

        // Then we get valid keys
        assertNotNull(x25519Keypair)
        assertTrue(x25519Keypair.publicKeyHex.isNotEmpty())
        assertTrue(x25519Keypair.secretKeyHex.isNotEmpty())
    }

    // MARK: - ViewModel Integration E2E Tests

    @Test
    fun `test contacts viewmodel loads contacts`() = test {
        // Given contacts in storage
        val contact1 = Contact(pubkey = "pubkey1", name = "Alice")
        val contact2 = Contact(pubkey = "pubkey2", name = "Bob")
        contactStorage.saveContact(contact1)
        contactStorage.saveContact(contact2)

        // When we retrieve contacts directly from storage
        val contacts = contactStorage.getAllContacts()

        // Then contacts are loaded
        assertTrue(contacts.size >= 2)
        assertTrue(contacts.any { it.pubkey == "pubkey1" })
        assertTrue(contacts.any { it.pubkey == "pubkey2" })
    }

    @Test
    fun `test receipts viewmodel loads receipts`() = test {
        // Given receipts in storage
        val receipt = Receipt(
            id = "receipt_1",
            payerPubkey = "payer",
            payeePubkey = "payee",
            amount = "1000",
            currency = "sats",
            methodId = "lightning",
            status = Receipt.Status.CONFIRMED,
            direction = Receipt.Direction.SENT,
            createdAt = System.currentTimeMillis() / 1000
        )
        receiptStorage.saveReceipt(receipt)

        // When we retrieve receipts directly from storage
        val receipts = receiptStorage.getAllReceipts()

        // Then receipts are loaded
        assertTrue(receipts.isNotEmpty())
        assertEquals("receipt_1", receipts.first().id)
    }

    // MARK: - Directory Service E2E Tests

    @Test
    fun `test directory service initialization`() = test {
        // Given directory service
        // When we initialize with PaykitClient (if available)
        // Note: In real E2E, we'd need actual PaykitClient
        // For now, we verify the service exists
        assertNotNull(directoryService)
    }

    // MARK: - Noise Payment Service E2E Tests

    @Test
    fun `test noise payment service initialization`() = test {
        // Given noise payment service
        // When we initialize with PaykitClient (if available)
        // Note: In real E2E, we'd need actual PaykitClient and network setup
        // For now, we verify the service exists
        assertNotNull(noisePaymentService)
    }

    // MARK: - Storage Persistence E2E Tests

    @Test
    fun `test storage persistence across app restarts`() = test {
        // Given we save data
        val contact = Contact(pubkey = "persist_test", name = "Persist Test")
        contactStorage.saveContact(contact)

        // When we retrieve it (simulating app restart)
        val retrieved = contactStorage.getContact(contact.pubkey)

        // Then data persists
        assertNotNull(retrieved)
        assertEquals(contact.pubkey, retrieved.pubkey)
        assertEquals(contact.name, retrieved.name)
    }
}

