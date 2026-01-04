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
import to.bitkit.paykit.services.PubkySDKService
import to.bitkit.paykit.storage.ContactStorage
import to.bitkit.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for Contacts management.
 * Contacts are synchronized with Pubky follows - the homeserver is the source of truth.
 */
@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactStorage: ContactStorage,
    private val directoryService: DirectoryService,
    private val pubkySDKService: PubkySDKService,
) : ViewModel() {

    companion object {
        private const val TAG = "ContactsViewModel"
    }

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    /** Unfiltered list of all contacts (used for searching) */
    private var allContacts: List<Contact> = emptyList()

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
            runCatching {
                // Sync contacts from Pubky follows (source of truth)
                val follows = withContext(Dispatchers.IO) {
                    directoryService.discoverContactsFromFollows()
                }
                
                // Convert discovered contacts to Contact model and merge with local data
                val followContacts = follows.map { discovered ->
                    val existing = contactStorage.getContact(discovered.pubkey)
                    Contact(
                        id = discovered.pubkey,
                        publicKeyZ32 = discovered.pubkey,
                        name = discovered.name ?: existing?.name ?: "",
                        notes = existing?.notes,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        lastPaymentAt = existing?.lastPaymentAt,
                        paymentCount = existing?.paymentCount ?: 0,
                    )
                }
                
                // Persist synced contacts locally for offline access
                contactStorage.importContacts(followContacts)
                
                // Store unfiltered list for searching
                allContacts = followContacts
                
                _contacts.value = if (_searchQuery.value.isEmpty()) {
                    followContacts
                } else {
                    followContacts.filter { contact ->
                        contact.name.contains(_searchQuery.value, ignoreCase = true) ||
                            contact.publicKeyZ32.contains(_searchQuery.value, ignoreCase = true)
                    }
                }
                
                // Also update discovered contacts for the discovery screen
                _discoveredContacts.value = follows
                
                Logger.debug("Loaded ${followContacts.size} contacts from Pubky follows", context = TAG)
            }.onFailure { e ->
                Logger.error("Failed to load contacts from follows", e, context = TAG)
                // Fallback to local storage
                val localContacts = contactStorage.listContacts()
                allContacts = localContacts
                _contacts.value = localContacts
            }
            _isLoading.value = false
        }
    }

    fun searchContacts() {
        // Filter from the in-memory list (not storage) to match visible list after network sync
        val source = allContacts.ifEmpty { contactStorage.listContacts() }
        _contacts.value = if (_searchQuery.value.isEmpty()) {
            source
        } else {
            source.filter { contact ->
                contact.name.contains(_searchQuery.value, ignoreCase = true) ||
                    contact.publicKeyZ32.contains(_searchQuery.value, ignoreCase = true)
            }
        }
    }

    fun addContact(contact: Contact) {
        viewModelScope.launch {
            runCatching {
                // Save locally first for immediate feedback
                contactStorage.saveContact(contact)
                loadContacts()
            }.onFailure { e ->
                _errorMessage.update { "Failed to add contact: ${e.message}" }
            }
            
            // Then create Pubky follow in background
            runCatching {
                withContext(Dispatchers.IO) {
                    directoryService.addFollow(contact.publicKeyZ32)
                }
                Logger.info("Added follow for contact: ${contact.publicKeyZ32.take(12)}...", context = TAG)
            }.onFailure { e ->
                _errorMessage.update { "Failed to sync follow to Pubky: ${e.message}" }
                Logger.error("Failed to add follow: ${e.message}", context = TAG)
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
                // Delete locally first for immediate feedback
                contactStorage.deleteContact(contact.id)
                loadContacts()
            }.onFailure { e ->
                _errorMessage.update { "Failed to delete contact: ${e.message}" }
            }
            
            // Then remove Pubky follow in background
            runCatching {
                withContext(Dispatchers.IO) {
                    directoryService.removeFollow(contact.publicKeyZ32)
                }
                Logger.info("Removed follow for contact: ${contact.publicKeyZ32.take(12)}...", context = TAG)
            }.onFailure { e ->
                _errorMessage.update { "Failed to sync unfollow to Pubky: ${e.message}" }
                Logger.error("Failed to remove follow: ${e.message}", context = TAG)
            }
        }
    }

    fun discoverContacts() {
        // Discovery is now unified with loadContacts - just refresh from follows
        loadContacts()
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
            _isLoading.value = true
            runCatching {
                withContext(Dispatchers.IO) {
                    // 1. Add follow to Pubky homeserver
                    directoryService.addFollow(pubkey)
                    
                    // 2. Fetch profile for the followed user
                    val profile = runCatching { pubkySDKService.fetchProfile(pubkey) }.getOrNull()
                    
                    // 3. Add to local contacts
                    val contact = Contact(
                        id = pubkey,
                        publicKeyZ32 = pubkey,
                        name = profile?.name ?: "",
                        notes = null,
                        createdAt = System.currentTimeMillis(),
                        lastPaymentAt = null,
                        paymentCount = 0,
                    )
                    contactStorage.saveContact(contact)
                    
                    Logger.info("Followed and added contact: $pubkey", context = TAG)
                }
                // 4. Refresh contacts list
                loadContacts()
            }.onFailure { e ->
                Logger.error("Failed to follow: ${e.message}", context = TAG)
                _errorMessage.update { "Failed to follow: ${e.message}" }
                _isLoading.value = false
            }
        }
    }

    fun unfollowContact(pubkey: String) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                withContext(Dispatchers.IO) {
                    // Remove follow from Pubky homeserver
                    directoryService.removeFollow(pubkey)
                    // Remove from local contacts
                    contactStorage.deleteContact(pubkey)
                    Logger.info("Unfollowed and removed contact: $pubkey", context = TAG)
                }
                // Refresh contacts list
                loadContacts()
            }.onFailure { e ->
                Logger.error("Failed to unfollow: ${e.message}", context = TAG)
                _errorMessage.update { "Failed to unfollow: ${e.message}" }
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
