package to.bitkit.paykit.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import to.bitkit.paykit.models.Contact
import to.bitkit.paykit.services.DirectoryService
import to.bitkit.paykit.storage.ContactStorage
import to.bitkit.utils.Logger
import javax.inject.Inject

/**
 * ViewModel for Contacts management
 */
class ContactsViewModel @Inject constructor(
    private val contactStorage: ContactStorage,
    private val directoryService: DirectoryService
) : ViewModel() {

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showingAddContact = MutableStateFlow(false)
    val showingAddContact: StateFlow<Boolean> = _showingAddContact.asStateFlow()

    private val _showingDiscovery = MutableStateFlow(false)
    val showingDiscovery: StateFlow<Boolean> = _showingDiscovery.asStateFlow()

    private val _discoveredContacts = MutableStateFlow<List<Contact>>(emptyList())
    val discoveredContacts: StateFlow<List<Contact>> = _discoveredContacts.asStateFlow()

    private val _showingDiscoveryResults = MutableStateFlow(false)
    val showingDiscoveryResults: StateFlow<Boolean> = _showingDiscoveryResults.asStateFlow()

    fun loadContacts() {
        viewModelScope.launch {
            _isLoading.value = true
            _contacts.value = contactStorage.listContacts()
            _isLoading.value = false
        }
    }

    fun searchContacts() {
        viewModelScope.launch {
            if (_searchQuery.value.isEmpty()) {
                _contacts.value = contactStorage.listContacts()
            } else {
                _contacts.value = contactStorage.searchContacts(_searchQuery.value)
            }
        }
    }

    fun addContact(contact: Contact) {
        viewModelScope.launch {
            try {
                contactStorage.saveContact(contact)
                loadContacts()
            } catch (e: Exception) {
                Logger.error("ContactsViewModel: Failed to add contact", e, context = "ContactsViewModel")
            }
        }
    }

    fun updateContact(contact: Contact) {
        viewModelScope.launch {
            try {
                contactStorage.saveContact(contact)
                loadContacts()
            } catch (e: Exception) {
                Logger.error("ContactsViewModel: Failed to update contact", e, context = "ContactsViewModel")
            }
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            try {
                contactStorage.deleteContact(contact.id)
                loadContacts()
            } catch (e: Exception) {
                Logger.error("ContactsViewModel: Failed to delete contact", e, context = "ContactsViewModel")
            }
        }
    }

    fun discoverContacts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // In production, would fetch from directory
                // For now, return empty
                _discoveredContacts.value = emptyList()
                _showingDiscoveryResults.value = true
            } catch (e: Exception) {
                Logger.error("ContactsViewModel: Failed to discover contacts", e, context = "ContactsViewModel")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun importDiscovered(contacts: List<Contact>) {
        viewModelScope.launch {
            try {
                contactStorage.importContacts(contacts)
                loadContacts()
            } catch (e: Exception) {
                Logger.error("ContactsViewModel: Failed to import contacts", e, context = "ContactsViewModel")
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchContacts()
    }

    fun setShowingAddContact(showing: Boolean) {
        _showingAddContact.value = showing
    }

    fun setShowingDiscovery(showing: Boolean) {
        _showingDiscovery.value = showing
    }

    fun setShowingDiscoveryResults(showing: Boolean) {
        _showingDiscoveryResults.value = showing
    }
}
