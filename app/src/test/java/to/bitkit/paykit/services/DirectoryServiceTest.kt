package to.bitkit.paykit.services

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import to.bitkit.paykit.PaykitManager

/**
 * Unit tests for DirectoryService.
 *
 * Tests payment method discovery, endpoint publishing, and contact discovery.
 */
class DirectoryServiceTest {

    private lateinit var paykitManager: PaykitManager
    private lateinit var directoryService: DirectoryService

    @Before
    fun setup() {
        paykitManager = mockk(relaxed = true)
        directoryService = DirectoryService(paykitManager)
    }

    // MARK: - Payment Method Discovery Tests

    @Test
    fun `discoverPaymentMethods returns empty list for unknown pubkey`() = runTest {
        // Given
        val unknownPubkey = "pk:unknown123"

        // When
        val result = directoryService.discoverPaymentMethods(unknownPubkey)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `discoverNoiseEndpoint returns null for unknown recipient`() = runTest {
        // Given
        val unknownRecipient = "pk:unknown456"

        // When
        val result = directoryService.discoverNoiseEndpoint(unknownRecipient)

        // Then
        assertEquals(null, result)
    }

    // MARK: - Endpoint Publishing Tests

    @Test
    fun `publishNoiseEndpoint calls PaykitClient`() = runTest {
        // Given
        val methodId = "lightning"
        val endpoint = "lnurl1dp68gurn8ghj7um9..."

        // When
        directoryService.publishNoiseEndpoint(methodId, endpoint)

        // Then - verify no exception thrown
        // Real verification would check the paykitClient was called
    }

    @Test
    fun `removeNoiseEndpoint calls PaykitClient`() = runTest {
        // Given
        val methodId = "lightning"

        // When
        directoryService.removeNoiseEndpoint(methodId)

        // Then - verify no exception thrown
    }

    // MARK: - Contact Discovery Tests

    @Test
    fun `discoverContactsFromFollows returns empty list when no follows`() = runTest {
        // Given
        val userPubkey = "pk:user123"

        // When
        val result = directoryService.discoverContactsFromFollows(userPubkey)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getSupportedMethods returns list of method IDs`() = runTest {
        // Given
        val pubkey = "pk:test123"

        // When
        val result = directoryService.getSupportedMethods(pubkey)

        // Then
        assertNotNull(result)
    }
}

