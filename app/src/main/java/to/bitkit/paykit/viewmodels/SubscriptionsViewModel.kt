package to.bitkit.paykit.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import to.bitkit.paykit.models.Subscription
import to.bitkit.paykit.storage.SubscriptionStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import to.bitkit.utils.Logger
import javax.inject.Inject

/**
 * ViewModel for Subscriptions management
 */
@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val subscriptionStorage: SubscriptionStorage
) : ViewModel() {

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showingAddSubscription = MutableStateFlow(false)
    val showingAddSubscription: StateFlow<Boolean> = _showingAddSubscription.asStateFlow()

    fun loadSubscriptions() {
        viewModelScope.launch {
            _isLoading.value = true
            _subscriptions.value = subscriptionStorage.listSubscriptions()
            _isLoading.value = false
        }
    }

    fun addSubscription(subscription: Subscription) {
        viewModelScope.launch {
            try {
                subscriptionStorage.saveSubscription(subscription)
                loadSubscriptions()
            } catch (e: Exception) {
                Logger.error(
                    "SubscriptionsViewModel: Failed to add subscription",
                    e,
                    context = "SubscriptionsViewModel"
                )
            }
        }
    }

    fun updateSubscription(subscription: Subscription) {
        viewModelScope.launch {
            try {
                subscriptionStorage.saveSubscription(subscription)
                loadSubscriptions()
            } catch (e: Exception) {
                Logger.error(
                    "SubscriptionsViewModel: Failed to update subscription",
                    e,
                    context = "SubscriptionsViewModel"
                )
            }
        }
    }

    fun deleteSubscription(subscription: Subscription) {
        viewModelScope.launch {
            try {
                subscriptionStorage.deleteSubscription(subscription.id)
                loadSubscriptions()
            } catch (e: Exception) {
                Logger.error(
                    "SubscriptionsViewModel: Failed to delete subscription",
                    e,
                    context = "SubscriptionsViewModel"
                )
            }
        }
    }

    fun toggleActive(subscription: Subscription) {
        viewModelScope.launch {
            try {
                subscriptionStorage.toggleActive(subscription.id)
                loadSubscriptions()
            } catch (e: Exception) {
                Logger.error(
                    "SubscriptionsViewModel: Failed to toggle subscription",
                    e,
                    context = "SubscriptionsViewModel"
                )
            }
        }
    }

    fun recordPayment(subscription: Subscription) {
        viewModelScope.launch {
            try {
                subscriptionStorage.recordPayment(subscription.id)
                loadSubscriptions()
            } catch (e: Exception) {
                Logger.error("SubscriptionsViewModel: Failed to record payment", e, context = "SubscriptionsViewModel")
            }
        }
    }

    val activeSubscriptions: List<Subscription>
        get() = subscriptionStorage.activeSubscriptions()

    fun setShowingAddSubscription(showing: Boolean) {
        _showingAddSubscription.value = showing
    }
}
