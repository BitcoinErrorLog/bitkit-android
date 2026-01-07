package to.bitkit.paykit.viewmodels

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
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
import to.bitkit.paykit.models.PaymentRequest
import to.bitkit.paykit.models.PaymentRequestStatus
import to.bitkit.paykit.models.RequestDirection
import to.bitkit.paykit.services.DirectoryService
import to.bitkit.paykit.services.PubkyRingBridge
import to.bitkit.paykit.storage.PaymentRequestStorage
import to.bitkit.paykit.storage.SentPaymentRequest
import to.bitkit.paykit.storage.SentRequestStatus
import to.bitkit.test.BaseUnitTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentRequestsViewModelTest : BaseUnitTest() {

    private lateinit var context: Context
    private lateinit var paymentRequestStorage: PaymentRequestStorage
    private lateinit var directoryService: DirectoryService
    private lateinit var keyManager: KeyManager
    private lateinit var pubkyRingBridge: PubkyRingBridge
    private lateinit var viewModel: PaymentRequestsViewModel

    @Before
    fun setup() {
        context = mock()
        paymentRequestStorage = mock()
        directoryService = mock()
        keyManager = mock()
        pubkyRingBridge = mock()

        whenever(paymentRequestStorage.listRequests()).thenReturn(emptyList())
        whenever(paymentRequestStorage.listSentRequests()).thenReturn(emptyList())
        whenever(keyManager.getCurrentPublicKeyZ32()).thenReturn("pk:owner")

        viewModel = PaymentRequestsViewModel(
            context,
            paymentRequestStorage,
            directoryService,
            keyManager,
            pubkyRingBridge,
        )
    }

    @Test
    fun `initial uiState has empty requests and defaults`() = test {
        val state = viewModel.uiState.first()

        assertTrue(state.requests.isEmpty())
        assertTrue(state.incomingRequests.isEmpty())
        assertTrue(state.outgoingRequests.isEmpty())
        assertTrue(state.sentRequests.isEmpty())
        assertEquals(RequestTab.INCOMING, state.selectedTab)
        assertFalse(state.isLoading)
        assertFalse(state.isSending)
        assertFalse(state.isCleaningUp)
        assertNull(state.error)
    }

    @Test
    fun `loadRequests updates state with requests from storage`() = test {
        val requests = listOf(
            PaymentRequest(
                id = "req-1",
                fromPubkey = "pk:sender",
                toPubkey = "pk:owner",
                amountSats = 1000,
                currency = "SAT",
                methodId = "lightning",
                description = "Test incoming",
                createdAt = System.currentTimeMillis(),
                expiresAt = null,
                status = PaymentRequestStatus.PENDING,
                direction = RequestDirection.INCOMING,
            ),
            PaymentRequest(
                id = "req-2",
                fromPubkey = "pk:owner",
                toPubkey = "pk:recipient",
                amountSats = 2000,
                currency = "SAT",
                methodId = "lightning",
                description = "Test outgoing",
                createdAt = System.currentTimeMillis(),
                expiresAt = null,
                status = PaymentRequestStatus.PENDING,
                direction = RequestDirection.OUTGOING,
            ),
        )
        whenever(paymentRequestStorage.listRequests()).thenReturn(requests)

        viewModel.loadRequests()
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals(2, state.requests.size)
        assertEquals(1, state.incomingRequests.size)
        assertEquals(1, state.outgoingRequests.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun `loadSentRequests updates state with sent requests from storage`() = test {
        val sentRequests = listOf(
            SentPaymentRequest(
                id = "sent-1",
                recipientPubkey = "pk:recipient",
                amountSats = 5000,
                methodId = "lightning",
                description = "Sent request",
                sentAt = System.currentTimeMillis(),
                status = SentRequestStatus.PENDING,
            ),
        )
        whenever(paymentRequestStorage.listSentRequests()).thenReturn(sentRequests)

        viewModel.loadSentRequests()
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals(1, state.sentRequests.size)
        assertEquals("sent-1", state.sentRequests[0].id)
    }

    @Test
    fun `selectTab updates selected tab in state`() = test {
        viewModel.selectTab(RequestTab.SENT)

        val state = viewModel.uiState.first()
        assertEquals(RequestTab.SENT, state.selectedTab)
    }

    @Test
    fun `sendPaymentRequest publishes to directory and saves locally`() = test {
        wheneverBlocking { directoryService.publishPaymentRequest(any(), any()) }.thenAnswer { }
        wheneverBlocking { paymentRequestStorage.addRequest(any()) }.thenAnswer { }
        wheneverBlocking { paymentRequestStorage.saveSentRequest(any(), any(), any(), any(), any()) }.thenAnswer { }

        viewModel.sendPaymentRequest(
            recipientPubkey = "pk:recipient",
            amountSats = 10000,
            methodId = "lightning",
            description = "Payment for services",
            expiresInDays = 7,
        )
        advanceUntilIdle()

        verify(directoryService).publishPaymentRequest(
            argThat { request ->
                request.toPubkey == "pk:recipient" &&
                    request.amountSats == 10000L &&
                    request.methodId == "lightning" &&
                    request.direction == RequestDirection.OUTGOING
            },
            argThat { pubkey -> pubkey == "pk:recipient" },
        )
        verify(paymentRequestStorage).addRequest(any())
        verify(paymentRequestStorage).saveSentRequest(
            any(),
            argThat { pubkey -> pubkey == "pk:recipient" },
            argThat { amount -> amount == 10000L },
            argThat { method -> method == "lightning" },
            any(),
        )

        val state = viewModel.uiState.first()
        assertFalse(state.isSending)
        assertTrue(state.sendSuccess)
    }

    @Test
    fun `sendPaymentRequest with no identity returns error`() = test {
        whenever(keyManager.getCurrentPublicKeyZ32()).thenReturn(null)

        viewModel.sendPaymentRequest(
            recipientPubkey = "pk:recipient",
            amountSats = 5000,
            methodId = "lightning",
            description = "Test",
        )
        advanceUntilIdle()

        verify(directoryService, never()).publishPaymentRequest(any(), any())
        val state = viewModel.uiState.first()
        assertEquals("No identity configured", state.error)
        assertFalse(state.isSending)
    }

    @Test
    fun `sendPaymentRequest handles directory service failure`() = test {
        wheneverBlocking { directoryService.publishPaymentRequest(any(), any()) }
            .thenThrow(RuntimeException("Network error"))

        viewModel.sendPaymentRequest(
            recipientPubkey = "pk:recipient",
            amountSats = 5000,
            methodId = "lightning",
            description = "Test",
        )
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertTrue(state.error?.contains("Network error") == true)
        assertFalse(state.isSending)
        assertFalse(state.sendSuccess)
    }

    @Test
    fun `cancelSentRequest deletes from homeserver and local storage`() = test {
        val sentRequest = SentPaymentRequest(
            id = "req-to-cancel",
            recipientPubkey = "pk:recipient",
            amountSats = 3000,
            methodId = "lightning",
            description = "To cancel",
            sentAt = System.currentTimeMillis(),
            status = SentRequestStatus.PENDING,
        )
        wheneverBlocking { directoryService.deletePaymentRequest(any(), any()) }.thenAnswer { }
        wheneverBlocking { paymentRequestStorage.deleteSentRequest(any()) }.thenAnswer { }
        wheneverBlocking { paymentRequestStorage.deleteRequest(any()) }.thenAnswer { }

        viewModel.cancelSentRequest(sentRequest)
        advanceUntilIdle()

        verify(directoryService).deletePaymentRequest("req-to-cancel", "pk:recipient")
        verify(paymentRequestStorage).deleteSentRequest("req-to-cancel")
        verify(paymentRequestStorage).deleteRequest("req-to-cancel")
    }

    @Test
    fun `acceptRequest updates status in storage`() = test {
        val request = PaymentRequest(
            id = "req-1",
            fromPubkey = "pk:sender",
            toPubkey = "pk:owner",
            amountSats = 1000,
            currency = "SAT",
            methodId = "lightning",
            description = "Test",
            createdAt = System.currentTimeMillis(),
            expiresAt = null,
            status = PaymentRequestStatus.PENDING,
            direction = RequestDirection.INCOMING,
        )
        wheneverBlocking { paymentRequestStorage.updateStatus(any(), any()) }.thenAnswer { }

        viewModel.acceptRequest(request)
        advanceUntilIdle()

        verify(paymentRequestStorage).updateStatus("req-1", PaymentRequestStatus.ACCEPTED)
    }

    @Test
    fun `declineRequest updates status in storage`() = test {
        val request = PaymentRequest(
            id = "req-1",
            fromPubkey = "pk:sender",
            toPubkey = "pk:owner",
            amountSats = 1000,
            currency = "SAT",
            methodId = "lightning",
            description = "Test",
            createdAt = System.currentTimeMillis(),
            expiresAt = null,
            status = PaymentRequestStatus.PENDING,
            direction = RequestDirection.INCOMING,
        )
        wheneverBlocking { paymentRequestStorage.updateStatus(any(), any()) }.thenAnswer { }

        viewModel.declineRequest(request)
        advanceUntilIdle()

        verify(paymentRequestStorage).updateStatus("req-1", PaymentRequestStatus.DECLINED)
    }

    @Test
    fun `deleteRequest removes from storage`() = test {
        val request = PaymentRequest(
            id = "req-to-delete",
            fromPubkey = "pk:sender",
            toPubkey = "pk:owner",
            amountSats = 1000,
            currency = "SAT",
            methodId = "lightning",
            description = "Test",
            createdAt = System.currentTimeMillis(),
            expiresAt = null,
            status = PaymentRequestStatus.PENDING,
            direction = RequestDirection.INCOMING,
        )
        wheneverBlocking { paymentRequestStorage.deleteRequest(any()) }.thenAnswer { }

        viewModel.deleteRequest(request)
        advanceUntilIdle()

        verify(paymentRequestStorage).deleteRequest("req-to-delete")
    }

    @Test
    fun `cleanupOrphanedRequests reports no orphans when homeserver matches tracked`() = test {
        val trackedByRecipient = mapOf("pk:recipient" to setOf("req-1", "req-2"))
        whenever(paymentRequestStorage.getSentRequestsByRecipient()).thenReturn(trackedByRecipient)
        wheneverBlocking { directoryService.listRequestsOnHomeserver("pk:recipient") }
            .thenReturn(listOf("req-1", "req-2"))

        viewModel.cleanupOrphanedRequests()
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals("No orphaned requests found", state.cleanupResult)
        verify(directoryService, never()).deleteRequestsBatch(any(), any())
    }

    @Test
    fun `cleanupOrphanedRequests deletes orphaned requests from homeserver`() = test {
        val trackedByRecipient = mapOf("pk:recipient" to setOf("req-tracked"))
        whenever(paymentRequestStorage.getSentRequestsByRecipient()).thenReturn(trackedByRecipient)
        wheneverBlocking { directoryService.listRequestsOnHomeserver("pk:recipient") }
            .thenReturn(listOf("req-tracked", "req-orphan-1", "req-orphan-2"))
        wheneverBlocking { directoryService.deleteRequestsBatch(any(), any()) }.thenReturn(2)

        viewModel.cleanupOrphanedRequests()
        advanceUntilIdle()

        verify(directoryService).deleteRequestsBatch(
            argThat { ids ->
                ids.containsAll(listOf("req-orphan-1", "req-orphan-2")) && !ids.contains("req-tracked")
            },
            argThat { pubkey -> pubkey == "pk:recipient" },
        )
        val state = viewModel.uiState.first()
        assertEquals("Cleaned up 2 orphaned requests", state.cleanupResult)
    }

    @Test
    fun `cleanupOrphanedRequests handles multiple recipients`() = test {
        val trackedByRecipient = mapOf(
            "pk:recipient1" to setOf("req-1"),
            "pk:recipient2" to setOf("req-2"),
        )
        whenever(paymentRequestStorage.getSentRequestsByRecipient()).thenReturn(trackedByRecipient)
        wheneverBlocking { directoryService.listRequestsOnHomeserver("pk:recipient1") }
            .thenReturn(listOf("req-1", "orphan-a"))
        wheneverBlocking { directoryService.listRequestsOnHomeserver("pk:recipient2") }
            .thenReturn(listOf("req-2"))
        wheneverBlocking { directoryService.deleteRequestsBatch(any(), any()) }.thenReturn(1)

        viewModel.cleanupOrphanedRequests()
        advanceUntilIdle()

        verify(directoryService).deleteRequestsBatch(
            argThat { ids -> ids == listOf("orphan-a") },
            argThat { pubkey -> pubkey == "pk:recipient1" },
        )
        verify(directoryService, never()).deleteRequestsBatch(any(), argThat { pubkey -> pubkey == "pk:recipient2" })
    }

    @Test
    fun `cleanupOrphanedRequests handles directory service failure gracefully`() = test {
        val trackedByRecipient = mapOf("pk:recipient" to setOf("req-1"))
        whenever(paymentRequestStorage.getSentRequestsByRecipient()).thenReturn(trackedByRecipient)
        wheneverBlocking { directoryService.listRequestsOnHomeserver("pk:recipient") }
            .thenThrow(RuntimeException("Network error"))

        viewModel.cleanupOrphanedRequests()
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertTrue(state.cleanupResult?.startsWith("Failed:") == true)
        assertFalse(state.isCleaningUp)
    }

    @Test
    fun `cleanupOrphanedRequests with no tracked requests finds no orphans`() = test {
        whenever(paymentRequestStorage.getSentRequestsByRecipient()).thenReturn(emptyMap())

        viewModel.cleanupOrphanedRequests()
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals("No orphaned requests found", state.cleanupResult)
    }

    @Test
    fun `clearError clears error in state`() = test {
        whenever(keyManager.getCurrentPublicKeyZ32()).thenReturn(null)
        viewModel.sendPaymentRequest("pk:r", 1000, "ln", "desc")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.first().error != null)

        viewModel.clearError()

        assertNull(viewModel.uiState.first().error)
    }

    @Test
    fun `clearSendSuccess clears sendSuccess in state`() = test {
        wheneverBlocking { directoryService.publishPaymentRequest(any(), any()) }.thenAnswer { }
        wheneverBlocking { paymentRequestStorage.addRequest(any()) }.thenAnswer { }
        wheneverBlocking { paymentRequestStorage.saveSentRequest(any(), any(), any(), any(), any()) }.thenAnswer { }

        viewModel.sendPaymentRequest("pk:r", 1000, "ln", "desc")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.first().sendSuccess)

        viewModel.clearSendSuccess()

        assertFalse(viewModel.uiState.first().sendSuccess)
    }

    @Test
    fun `clearCleanupResult clears cleanupResult in state`() = test {
        whenever(paymentRequestStorage.getSentRequestsByRecipient()).thenReturn(emptyMap())

        viewModel.cleanupOrphanedRequests()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.first().cleanupResult != null)

        viewModel.clearCleanupResult()

        assertNull(viewModel.uiState.first().cleanupResult)
    }
}

