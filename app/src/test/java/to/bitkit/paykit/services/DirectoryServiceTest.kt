package to.bitkit.paykit.services

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.doAnswer
import to.bitkit.paykit.KeyManager
import to.bitkit.paykit.storage.PaykitKeychainStorage
import to.bitkit.test.BaseUnitTest
import uniffi.paykit_mobile.StorageOperationResult
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for DirectoryService.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DirectoryServiceTest : BaseUnitTest() {

    private lateinit var context: Context
    private lateinit var keyManager: KeyManager
    private lateinit var pubkyStorage: PubkyStorageAdapter
    private lateinit var pubkySDKService: PubkySDKService
    private lateinit var pubkyRingBridge: PubkyRingBridge
    private lateinit var keychainStorage: PaykitKeychainStorage
    private lateinit var directoryService: DirectoryService

    private lateinit var mockUnauthAdapter: PubkyUnauthenticatedStorageAdapter
    private lateinit var mockAuthAdapter: PubkyAuthenticatedStorageAdapter

    companion object {
        // Valid 52-character z32 pubkeys for testing
        private const val VALID_OWNER_PUBKEY = "8um71us3fyw6h8wbcxb5ar3rwusy1a6u49956ikzojg3gcwd1dty"
        private const val VALID_SUBSCRIBER_PUBKEY = "ufibwbmed6jeq9k4p583go95wofakh9fwpp4k734trq79pd9u1uy"
    }

    @Before
    fun setup() {
        context = mock()
        keyManager = mock()
        pubkyStorage = mock()
        pubkySDKService = mock()
        pubkyRingBridge = mock()
        keychainStorage = mock()

        mockUnauthAdapter = mock()
        mockAuthAdapter = mock()

        whenever(keyManager.getCurrentPublicKeyZ32()).thenReturn(VALID_OWNER_PUBKEY)
        whenever(pubkyStorage.createUnauthenticatedAdapter(any())).thenReturn(mockUnauthAdapter)

        directoryService = DirectoryService(
            context,
            keyManager,
            pubkyStorage,
            pubkySDKService,
            pubkyRingBridge,
            keychainStorage,
        )
    }

    @Test
    fun `listProposalsOnHomeserver returns empty list when directory does not exist`() = test {
        whenever(pubkyStorage.listDirectory(any(), eq(mockUnauthAdapter), any()))
            .thenThrow(RuntimeException("Not found"))

        val result = directoryService.listProposalsOnHomeserver(VALID_SUBSCRIBER_PUBKEY)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listProposalsOnHomeserver returns empty list when directory is empty`() = test {
        whenever(pubkyStorage.listDirectory(any(), eq(mockUnauthAdapter), any()))
            .thenReturn(emptyList())

        val result = directoryService.listProposalsOnHomeserver(VALID_SUBSCRIBER_PUBKEY)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listProposalsOnHomeserver returns proposal IDs from homeserver`() = test {
        val proposalIds = listOf("prop-1", "prop-2", "prop-3")
        whenever(pubkyStorage.listDirectory(any(), eq(mockUnauthAdapter), any()))
            .thenReturn(proposalIds)

        val result = directoryService.listProposalsOnHomeserver(VALID_SUBSCRIBER_PUBKEY)

        assertEquals(3, result.size)
        assertTrue(result.containsAll(proposalIds))
    }

    @Test
    fun `listProposalsOnHomeserver throws when no identity configured`() = test {
        whenever(keyManager.getCurrentPublicKeyZ32()).thenReturn(null)

        var thrownError: Throwable? = null
        try {
            directoryService.listProposalsOnHomeserver(VALID_SUBSCRIBER_PUBKEY)
        } catch (e: Throwable) {
            thrownError = e
        }

        assertTrue(thrownError is DirectoryError.NotConfigured)
    }

    @Test
    fun `deleteProposalsBatch returns 0 when list is empty`() = test {
        configureServiceWithAuthAdapter()

        val result = directoryService.deleteProposalsBatch(emptyList(), VALID_SUBSCRIBER_PUBKEY)

        assertEquals(0, result)
    }

    @Test
    fun `deleteProposalsBatch attempts delete for each proposal`() = test {
        configureServiceWithAuthAdapter()
        whenever(mockAuthAdapter.delete(any())).thenReturn(
            StorageOperationResult(success = true, error = null)
        )

        val proposalIds = listOf("prop-1", "prop-2", "prop-3")
        val result = directoryService.deleteProposalsBatch(proposalIds, VALID_SUBSCRIBER_PUBKEY)

        assertEquals(3, result)
        verify(mockAuthAdapter, times(3)).delete(any())
    }

    @Test
    fun `deleteProposalsBatch returns count of successful deletes only`() = test {
        configureServiceWithAuthAdapter()
        var callCount = 0
        whenever(mockAuthAdapter.delete(any())).doAnswer {
            callCount++
            when (callCount) {
                1 -> StorageOperationResult(success = true, error = null)
                2 -> StorageOperationResult(success = false, error = "Failed")
                else -> StorageOperationResult(success = true, error = null)
            }
        }

        val proposalIds = listOf("prop-1", "prop-fail", "prop-3")
        val result = directoryService.deleteProposalsBatch(proposalIds, VALID_SUBSCRIBER_PUBKEY)

        assertEquals(2, result)
    }

    @Test
    fun `deleteProposalsBatch handles exceptions gracefully`() = test {
        configureServiceWithAuthAdapter()
        var callCount = 0
        whenever(mockAuthAdapter.delete(any())).doAnswer {
            callCount++
            when (callCount) {
                1 -> StorageOperationResult(success = true, error = null)
                2 -> throw RuntimeException("Network error")
                else -> StorageOperationResult(success = true, error = null)
            }
        }

        val proposalIds = listOf("prop-1", "prop-error", "prop-3")
        val result = directoryService.deleteProposalsBatch(proposalIds, VALID_SUBSCRIBER_PUBKEY)

        assertEquals(2, result)
    }

    @Test
    fun `deleteProposalsBatch throws when not configured`() = test {
        var thrownError: Throwable? = null
        try {
            directoryService.deleteProposalsBatch(listOf("prop-1"), VALID_SUBSCRIBER_PUBKEY)
        } catch (e: Throwable) {
            thrownError = e
        }

        assertTrue(thrownError is DirectoryError.NotConfigured)
    }

    private fun configureServiceWithAuthAdapter() {
        val adapterField = DirectoryService::class.java.getDeclaredField("authenticatedAdapter")
        adapterField.isAccessible = true
        adapterField.set(directoryService, mockAuthAdapter)

        val transportField = DirectoryService::class.java.getDeclaredField("authenticatedTransport")
        transportField.isAccessible = true
        transportField.set(directoryService, mock<uniffi.paykit_mobile.AuthenticatedTransportFfi>())
    }
}
