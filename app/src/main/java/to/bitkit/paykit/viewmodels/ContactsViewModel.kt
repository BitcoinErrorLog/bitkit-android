package to.bitkit.paykit.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.bitkit.paykit.models.Contact
import to.bitkit.paykit.services.DirectoryService
import to.bitkit.paykit.services.DiscoveredContact
import to.bitkit.paykit.storage.ContactStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for Contacts management
 */
@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactStorage: ContactStorage,
    private val directoryService: DirectoryService,
) : ViewModel() {

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showingAddContact = MutableStateFlow(false)
    val showingAddContact: StateFlow<Boolean> = _showingAddContact.asStateFlow()

    private val _discoveredContacts = MutableStateFlow<List<DiscoveredContact>>(emptyList())
    val discoveredContacts: StateFlow<List<DiscoveredContact>> = _discoveredContacts.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadContacts()
    }

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
            runCatching {
                contactStorage.saveContact(contact)
                loadContacts()
            }.onFailure { e ->
                _errorMessage.update { "Failed to add contact: ${e.message}" }
            }
        }
    }

    fun updateContact(contact: Contact) {
        viewModelScope.launch {
            runCatching {
                contactStorage.saveContact(contact)
                loadContacts()
            }.onFailure { e ->
                _errorMessage.update { "Failed to update contact: ${e.message}" }
            }
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            runCatching {
                contactStorage.deleteContact(contact.id)
                loadContacts()
            }.onFailure { e ->
                _errorMessage.update { "Failed to delete contact: ${e.message}" }
            }
        }
    }

    fun discoverContacts() {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                _discoveredContacts.value = directoryService.discoverContactsFromFollows()
            }.onFailure { e ->
                _errorMessage.update { "Failed to discover contacts: ${e.message}" }
            }
            _isLoading.value = false
        }
    }

    fun importDiscovered(contacts: List<Contact>) {
        viewModelScope.launch {
            runCatching {
                contactStorage.importContacts(contacts)
                loadContacts()
            }.onFailure { e ->
                _errorMessage.update { "Failed to import contacts: ${e.message}" }
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

    fun followContact(pubkey: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    directoryService.addFollow(pubkey)
                }
            }.onFailure { e ->
                _errorMessage.update { "Failed to follow: ${e.message}" }
            }
        }
    }

    fun unfollowContact(pubkey: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    directoryService.removeFollow(pubkey)
                }
            }.onFailure { e ->
                _errorMessage.update { "Failed to unfollow: ${e.message}" }
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
