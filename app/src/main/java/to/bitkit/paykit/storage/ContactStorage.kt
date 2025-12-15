package to.bitkit.paykit.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import to.bitkit.paykit.models.Contact
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages persistent storage of contacts
 */
@Singleton
class ContactStorage @Inject constructor(
    private val keychain: PaykitKeychainStorage
) {
    companion object {
        private const val TAG = "ContactStorage"
    }
    
    private var contactsCache: List<Contact>? = null
    private val identityName: String = "default"
    
    private val contactsKey: String
        get() = "contacts.$identityName"
    
    fun listContacts(): List<Contact> {
        if (contactsCache != null) {
            return contactsCache!!
        }
        
        return try {
            val data = keychain.retrieve(contactsKey) ?: return emptyList()
            val json = String(data)
            val contacts = Json.decodeFromString<List<Contact>>(json)
            contactsCache = contacts
            contacts
        } catch (e: Exception) {
            Logger.error("ContactStorage: Failed to load contacts", e, context = TAG)
            emptyList()
        }
    }
    
    fun getContact(id: String): Contact? {
        return listContacts().firstOrNull { it.id == id }
    }
    
    suspend fun saveContact(contact: Contact) {
        val contacts = listContacts().toMutableList()
        val index = contacts.indexOfFirst { it.id == contact.id }
        if (index >= 0) {
            contacts[index] = contact
        } else {
            contacts.add(contact)
        }
        persistContacts(contacts)
    }
    
    suspend fun deleteContact(id: String) {
        val contacts = listContacts().toMutableList()
        contacts.removeAll { it.id == id }
        persistContacts(contacts)
    }
    
    fun searchContacts(query: String): List<Contact> {
        val lowerQuery = query.lowercase()
        return listContacts().filter { contact ->
            contact.name.lowercase().contains(lowerQuery) ||
            contact.publicKeyZ32.lowercase().contains(lowerQuery)
        }
    }
    
    suspend fun recordPayment(contactId: String) {
        val contacts = listContacts().toMutableList()
        val index = contacts.indexOfFirst { it.id == contactId }
        if (index >= 0) {
            val contact = contacts[index]
            contacts[index] = contact.recordPayment()
            persistContacts(contacts)
        }
    }
    
    suspend fun clearAll() {
        persistContacts(emptyList())
    }
    
    suspend fun importContacts(newContacts: List<Contact>) {
        val contacts = listContacts().toMutableList()
        for (newContact in newContacts) {
            if (!contacts.any { it.id == newContact.id }) {
                contacts.add(newContact)
            }
        }
        persistContacts(contacts)
    }
    
    suspend fun exportContacts(): String {
        val contacts = listContacts()
        return Json.encodeToString(contacts)
    }
    
    private suspend fun persistContacts(contacts: List<Contact>) {
        try {
            val json = Json.encodeToString(contacts)
            keychain.store(contactsKey, json.toByteArray())
            contactsCache = contacts
        } catch (e: Exception) {
            Logger.error("ContactStorage: Failed to persist contacts", e, context = TAG)
            throw PaykitStorageException.SaveFailed(contactsKey)
        }
    }
}

