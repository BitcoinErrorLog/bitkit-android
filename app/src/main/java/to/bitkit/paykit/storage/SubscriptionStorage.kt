package to.bitkit.paykit.storage

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import to.bitkit.paykit.models.Subscription
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages persistent storage of subscriptions using Keychain.
 */
@Singleton
class SubscriptionStorage @Inject constructor(
    private val keychain: PaykitKeychainStorage
) {
    companion object {
        private const val TAG = "SubscriptionStorage"
    }
    
    private var subscriptionsCache: List<Subscription>? = null
    private val identityName: String = "default"
    
    private val storageKey: String
        get() = "subscriptions.$identityName"
    
    fun listSubscriptions(): List<Subscription> {
        if (subscriptionsCache != null) {
            return subscriptionsCache!!
        }
        
        return try {
            val data = keychain.retrieve(storageKey) ?: return emptyList()
            val json = String(data)
            val subscriptions = Json.decodeFromString<List<Subscription>>(json)
            subscriptionsCache = subscriptions
            subscriptions
        } catch (e: Exception) {
            Logger.error("SubscriptionStorage: Failed to load subscriptions", e, context = TAG)
            emptyList()
        }
    }
    
    fun getSubscription(id: String): Subscription? {
        return listSubscriptions().firstOrNull { it.id == id }
    }
    
    suspend fun saveSubscription(subscription: Subscription) {
        val subscriptions = listSubscriptions().toMutableList()
        val index = subscriptions.indexOfFirst { it.id == subscription.id }
        if (index >= 0) {
            subscriptions[index] = subscription
        } else {
            subscriptions.add(subscription)
        }
        persistSubscriptions(subscriptions)
    }
    
    suspend fun deleteSubscription(id: String) {
        val subscriptions = listSubscriptions().toMutableList()
        subscriptions.removeAll { it.id == id }
        persistSubscriptions(subscriptions)
    }
    
    suspend fun toggleActive(id: String) {
        val subscriptions = listSubscriptions().toMutableList()
        val index = subscriptions.indexOfFirst { it.id == id }
        if (index >= 0) {
            subscriptions[index] = subscriptions[index].copy(isActive = !subscriptions[index].isActive)
            persistSubscriptions(subscriptions)
        }
    }
    
    suspend fun recordPayment(subscriptionId: String) {
        val subscriptions = listSubscriptions().toMutableList()
        val index = subscriptions.indexOfFirst { it.id == subscriptionId }
        if (index >= 0) {
            subscriptions[index] = subscriptions[index].recordPayment()
            persistSubscriptions(subscriptions)
        }
    }
    
    fun activeSubscriptions(): List<Subscription> {
        return listSubscriptions().filter { it.isActive }
    }
    
    suspend fun clearAll() {
        persistSubscriptions(emptyList())
    }
    
    private suspend fun persistSubscriptions(subscriptions: List<Subscription>) {
        try {
            val json = Json.encodeToString(subscriptions)
            keychain.store(storageKey, json.toByteArray())
            subscriptionsCache = subscriptions
        } catch (e: Exception) {
            Logger.error("SubscriptionStorage: Failed to persist subscriptions", e, context = TAG)
            throw PaykitStorageException.SaveFailed(storageKey)
        }
    }
}

