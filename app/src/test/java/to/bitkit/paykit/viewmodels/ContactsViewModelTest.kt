package to.bitkit.paykit.viewmodels

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import to.bitkit.paykit.models.Contact
import to.bitkit.paykit.services.DirectoryService
import to.bitkit.paykit.services.DiscoveredContact
import to.bitkit.paykit.storage.ContactsStorage
import to.bitkit.test.BaseUnitTest

/**
 * Unit tests for ContactsViewModel.
 *
 * Tests contact management, discovery, and synchronization.
 */
class ContactsViewModelTest : BaseUnitTest() {

    private lateinit var contactsStorage: ContactsStorage
    private lateinit var directoryService: DirectoryService
    private lateinit var viewModel: ContactsViewModel

    @Before
    fun setup() {
        contactsStorage = mockk(relaxed = true)
        directoryService = mockk(relaxed = true)
        viewModel = ContactsViewModel(contactsStorage, directoryService)
    }

    // MARK: - Load Contacts Tests

    @Test
    fun `loadContacts loads from storage`() = test {
        // Given
        val contacts = listOf(
            Contact(
                pubkey = "pk:alice",
                name = "Alice",
                avatarUrl = null,
                supportedMethods = listOf("lightning")
            ),
            Contact(
                pubkey = "pk:bob",
                name = "Bob",
                avatarUrl = "https://example.com/bob.png",
                supportedMethods = listOf("lightning", "onchain")
            )
        )
        coEvery { contactsStorage.listContacts() } returns contacts

        // When
        viewModel.loadContacts()

        // Then
        val state = viewModel.uiState.value
        assertEquals(2, state.contacts.size)
        assertEquals("Alice", state.contacts[0].name)
    }

    @Test
    fun `loadContacts sets loading state`() = test {
        // Given
        coEvery { contactsStorage.listContacts() } returns emptyList()

        // When - check initial state
        val initialState = viewModel.uiState.value
        assertFalse(initialState.isLoading)

        // Then - after load
        viewModel.loadContacts()
        assertFalse(viewModel.uiState.value.isLoading) // Should be false after load completes
    }

    // MARK: - Add Contact Tests

    @Test
    fun `addContact saves to storage`() = test {
        // Given
        val contact = Contact(
            pubkey = "pk:new",
            name = "New Contact",
            supportedMethods = listOf("lightning")
        )

        // When
        viewModel.addContact(contact)

        // Then
        coVerify { contactsStorage.saveContact(contact) }
    }

    @Test
    fun `addContact updates UI state`() = test {
        // Given
        val contact = Contact(
            pubkey = "pk:new",
            name = "New Contact",
            supportedMethods = listOf("lightning")
        )
        coEvery { contactsStorage.listContacts() } returns listOf(contact)

        // When
        viewModel.addContact(contact)
        viewModel.loadContacts()

        // Then
        val state = viewModel.uiState.value
        assertEquals(1, state.contacts.size)
    }

    // MARK: - Remove Contact Tests

    @Test
    fun `removeContact deletes from storage`() = test {
        // Given
        val pubkey = "pk:toremove"

        // When
        viewModel.removeContact(pubkey)

        // Then
        coVerify { contactsStorage.deleteContact(pubkey) }
    }

    // MARK: - Contact Discovery Tests

    @Test
    fun `discoverContacts fetches from directory`() = test {
        // Given
        val userPubkey = "pk:user"
        val discovered = listOf(
            DiscoveredContact(
                pubkey = "pk:found1",
                name = "Found User 1",
                avatarUrl = null,
                supportedMethods = listOf("lightning")
            )
        )
        coEvery { directoryService.discoverContactsFromFollows(userPubkey) } returns discovered

        // When
        viewModel.discoverContacts(userPubkey)

        // Then
        val state = viewModel.uiState.value
        assertEquals(1, state.discoveredContacts.size)
    }

    @Test
    fun `addDiscoveredContact converts and saves`() = test {
        // Given
        val discovered = DiscoveredContact(
            pubkey = "pk:discovered",
            name = "Discovered User",
            avatarUrl = "https://example.com/avatar.png",
            supportedMethods = listOf("lightning")
        )

        // When
        viewModel.addDiscoveredContact(discovered)

        // Then
        coVerify {
            contactsStorage.saveContact(match {
                it.pubkey == "pk:discovered" && it.name == "Discovered User"
            })
        }
    }

    // MARK: - Search Tests

    @Test
    fun `searchContacts filters by name`() = test {
        // Given
        val contacts = listOf(
            Contact(pubkey = "pk:alice", name = "Alice", supportedMethods = emptyList()),
            Contact(pubkey = "pk:bob", name = "Bob", supportedMethods = emptyList()),
            Contact(pubkey = "pk:charlie", name = "Charlie", supportedMethods = emptyList())
        )
        coEvery { contactsStorage.listContacts() } returns contacts
        viewModel.loadContacts()

        // When
        viewModel.searchContacts("ali")

        // Then
        val state = viewModel.uiState.value
        assertEquals(1, state.filteredContacts.size)
        assertEquals("Alice", state.filteredContacts[0].name)
    }

    @Test
    fun `searchContacts with empty query shows all`() = test {
        // Given
        val contacts = listOf(
            Contact(pubkey = "pk:alice", name = "Alice", supportedMethods = emptyList()),
            Contact(pubkey = "pk:bob", name = "Bob", supportedMethods = emptyList())
        )
        coEvery { contactsStorage.listContacts() } returns contacts
        viewModel.loadContacts()

        // When
        viewModel.searchContacts("")

        // Then
        val state = viewModel.uiState.value
        assertEquals(2, state.filteredContacts.size)
    }

    // MARK: - Sync Tests

    @Test
    fun `syncContact refreshes supported methods`() = test {
        // Given
        val contact = Contact(
            pubkey = "pk:tosync",
            name = "Sync Me",
            supportedMethods = listOf("lightning")
        )
        val updatedMethods = listOf("lightning", "onchain", "noise")
        coEvery { directoryService.getSupportedMethods("pk:tosync") } returns updatedMethods

        // When
        viewModel.syncContact(contact)

        // Then
        coVerify {
            contactsStorage.saveContact(match {
                it.pubkey == "pk:tosync" && it.supportedMethods.size == 3
            })
        }
    }
}

