package to.bitkit.paykit.viewmodels

import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.wheneverBlocking
import to.bitkit.paykit.models.Contact
import to.bitkit.paykit.services.DirectoryService
import to.bitkit.paykit.storage.ContactStorage
import to.bitkit.test.BaseUnitTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Unit tests for ContactsViewModel.
 *
 * Tests contact management and loading.
 */
class ContactsViewModelTest : BaseUnitTest() {

    private lateinit var contactStorage: ContactStorage
    private lateinit var directoryService: DirectoryService
    private lateinit var viewModel: ContactsViewModel

    @Before
    fun setup() {
        contactStorage = mock()
        directoryService = mock()
        viewModel = ContactsViewModel(contactStorage, directoryService)
    }

    // MARK: - Load Contacts Tests

    @Test
    fun `loadContacts loads from storage`() = test {
        // Given
        val contacts = listOf(
            Contact.create(publicKeyZ32 = "pk_alice_z32", name = "Alice"),
            Contact.create(publicKeyZ32 = "pk_bob_z32", name = "Bob")
        )
        wheneverBlocking { contactStorage.listContacts() }.thenReturn(contacts)

        // When
        viewModel.loadContacts()

        // Then
        val loadedContacts = viewModel.contacts.value
        assertEquals(2, loadedContacts.size)
        assertEquals("Alice", loadedContacts[0].name)
    }

    @Test
    fun `loadContacts sets loading state`() = test {
        // Given
        wheneverBlocking { contactStorage.listContacts() }.thenReturn(emptyList())

        // When - check initial state
        assertFalse(viewModel.isLoading.value)

        // Then - after load
        viewModel.loadContacts()
        assertFalse(viewModel.isLoading.value) // Should be false after load completes
    }

    // MARK: - Add Contact Tests

    @Test
    fun `addContact saves to storage`() = test {
        // Given
        val contact = Contact.create(
            publicKeyZ32 = "pk_new_z32",
            name = "New Contact"
        )
        wheneverBlocking { contactStorage.listContacts() }.thenReturn(listOf(contact))

        // When
        viewModel.addContact(contact)

        // Then
        verify(contactStorage).saveContact(contact)
    }

    @Test
    fun `addContact updates contacts list`() = test {
        // Given
        val contact = Contact.create(
            publicKeyZ32 = "pk_new_z32",
            name = "New Contact"
        )
        wheneverBlocking { contactStorage.listContacts() }.thenReturn(listOf(contact))

        // When
        viewModel.addContact(contact)

        // Then
        val contacts = viewModel.contacts.value
        assertEquals(1, contacts.size)
    }

    // MARK: - Delete Contact Tests

    @Test
    fun `deleteContact removes from storage`() = test {
        // Given
        val contact = Contact.create(publicKeyZ32 = "pk_toremove_z32", name = "To Remove")
        wheneverBlocking { contactStorage.listContacts() }.thenReturn(emptyList())

        // When
        viewModel.deleteContact(contact)

        // Then
        verify(contactStorage).deleteContact(contact.id)
    }

    // MARK: - Search Tests

    @Test
    fun `setSearchQuery updates search query`() = test {
        // Given
        val query = "alice"
        wheneverBlocking { contactStorage.searchContacts(any()) }.thenReturn(emptyList())

        // When
        viewModel.setSearchQuery(query)

        // Then
        assertEquals(query, viewModel.searchQuery.value)
    }

    @Test
    fun `searchContacts with empty query loads all contacts`() = test {
        // Given
        val contacts = listOf(
            Contact.create(publicKeyZ32 = "pk_alice_z32", name = "Alice"),
            Contact.create(publicKeyZ32 = "pk_bob_z32", name = "Bob")
        )
        wheneverBlocking { contactStorage.listContacts() }.thenReturn(contacts)

        // When
        viewModel.setSearchQuery("")

        // Then
        verify(contactStorage).listContacts()
    }

    @Test
    fun `searchContacts with query searches storage`() = test {
        // Given
        val contacts = listOf(
            Contact.create(publicKeyZ32 = "pk_alice_z32", name = "Alice")
        )
        wheneverBlocking { contactStorage.searchContacts("alice") }.thenReturn(contacts)

        // When
        viewModel.setSearchQuery("alice")

        // Then
        verify(contactStorage).searchContacts("alice")
    }
}
