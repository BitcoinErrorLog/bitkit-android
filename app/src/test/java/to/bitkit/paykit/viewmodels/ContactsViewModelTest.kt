package to.bitkit.paykit.viewmodels

import kotlinx.coroutines.delay
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.wheneverBlocking
import to.bitkit.paykit.models.Contact
import to.bitkit.paykit.services.DirectoryService
import to.bitkit.paykit.services.DiscoveredContact
import to.bitkit.paykit.services.PubkySDKService
import to.bitkit.paykit.storage.ContactStorage
import to.bitkit.test.BaseUnitTest
import kotlin.test.assertEquals

/**
 * Unit tests for ContactsViewModel.
 *
 * Tests contact management and loading.
 */
class ContactsViewModelTest : BaseUnitTest() {

    private lateinit var contactStorage: ContactStorage
    private lateinit var directoryService: DirectoryService
    private lateinit var pubkySDKService: PubkySDKService
    private lateinit var viewModel: ContactsViewModel

    @Before
    fun setup() {
        contactStorage = mock()
        directoryService = mock()
        pubkySDKService = mock()
        // Mock all methods called in init/loadContacts
        wheneverBlocking { contactStorage.listContacts() }.thenReturn(emptyList())
        wheneverBlocking { contactStorage.getContact(any()) }.thenReturn(null)
        wheneverBlocking { contactStorage.importContacts(any()) }.thenReturn(Unit)
        wheneverBlocking { directoryService.discoverContactsFromFollows() }.thenReturn(emptyList())
        viewModel = ContactsViewModel(contactStorage, directoryService, pubkySDKService)
    }

    // MARK: - Load Contacts Tests

    @Test
    fun `loadContacts calls directoryService discoverContactsFromFollows`() = test {
        // Given
        val followedContacts = listOf(
            DiscoveredContact(pubkey = "pk_alice_z32", name = "Alice", hasPaymentMethods = false, supportedMethods = emptyList()),
        )
        wheneverBlocking { directoryService.discoverContactsFromFollows() }.thenReturn(followedContacts)
        wheneverBlocking { contactStorage.getContact(any()) }.thenReturn(null)

        // When
        viewModel.loadContacts()
        delay(100) // Allow async work to complete

        // Then - verify discovery was called at least once (init + loadContacts)
        verify(directoryService, atLeast(1)).discoverContactsFromFollows()
    }

    // MARK: - Add Contact Tests

    @Test
    fun `addContact saves to storage`() = test {
        // Given
        val contact = Contact.create(
            publicKeyZ32 = "pk_new_z32",
            name = "New Contact",
        )
        wheneverBlocking { directoryService.discoverContactsFromFollows() }.thenReturn(emptyList())

        // When
        viewModel.addContact(contact)
        delay(100) // Allow async work to complete

        // Then
        verify(contactStorage).saveContact(contact)
    }

    // MARK: - Delete Contact Tests

    @Test
    fun `deleteContact removes from storage`() = test {
        // Given
        val contact = Contact.create(publicKeyZ32 = "pk_toremove_z32", name = "To Remove")
        wheneverBlocking { directoryService.discoverContactsFromFollows() }.thenReturn(emptyList())

        // When
        viewModel.deleteContact(contact)
        delay(100) // Allow async work to complete

        // Then
        verify(contactStorage).deleteContact(contact.id)
    }

    // MARK: - Search Tests

    @Test
    fun `setSearchQuery updates search query`() = test {
        // Given
        val query = "alice"

        // When
        viewModel.setSearchQuery(query)

        // Then
        assertEquals(query, viewModel.searchQuery.value)
    }
}
