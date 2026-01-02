package to.bitkit.paykit.storage

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.bitkit.paykit.KeyManager
import to.bitkit.paykit.models.Subscription
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages persistent storage of subscriptions using Keychain.
 *
 * Storage is scoped by the current identity pubkey to prevent data leaks
 * between different identities.
 */
@Singleton
class SubscriptionStorage @Inject constructor(
    private val keychain: PaykitKeychainStorage,
    private val keyManager: KeyManager,
) {
    companion object {
        private const val TAG = "SubscriptionStorage"
    }

    private var subscriptionsCache: MutableMap<String, List<Subscription>> = mutableMapOf()

    private val currentIdentity: String
        get() = keyManager.getCurrentPublicKeyZ32() ?: "default"

    private val storageKey: String
        get() = "subscriptions.$currentIdentity"

    fun listSubscriptions(): List<Subscription> {
        val identity = currentIdentity
        subscriptionsCache[identity]?.let { return it }

        return try {
            val data = keychain.retrieve(storageKey) ?: return emptyList()
            val json = String(data)
            val subscriptions = Json.decodeFromString<List<Subscription>>(json)
            subscriptionsCache[identity] = subscriptions
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
        recordPayment(subscriptionId, paymentHash = null, preimage = null, feeSats = null)
    }

    suspend fun recordPayment(
        subscriptionId: String,
        paymentHash: String?,
        preimage: String?,
        feeSats: ULong?,
    ) {
        val subscriptions = listSubscriptions().toMutableList()
        val index = subscriptions.indexOfFirst { it.id == subscriptionId }
        if (index >= 0) {
            subscriptions[index] = subscriptions[index].recordPayment(
                paymentHash = paymentHash,
                preimage = preimage,
                feeSats = feeSats,
            )
            persistSubscriptions(subscriptions)
        }
    }

    fun activeSubscriptions(): List<Subscription> {
        return listSubscriptions().filter { it.isActive }
    }

    suspend fun clearAll() {
        val identity = currentIdentity
        persistSubscriptions(emptyList())
        subscriptionsCache.remove(identity)
    }

    private suspend fun persistSubscriptions(subscriptions: List<Subscription>) {
        val identity = currentIdentity
        try {
            val json = Json.encodeToString(subscriptions)
            keychain.store(storageKey, json.toByteArray())
            subscriptionsCache[identity] = subscriptions
        } catch (e: Exception) {
            Logger.error("SubscriptionStorage: Failed to persist subscriptions", e, context = TAG)
            throw PaykitStorageException.SaveFailed(storageKey)
        }
    }
}
