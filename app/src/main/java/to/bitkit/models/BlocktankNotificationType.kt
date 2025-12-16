package to.bitkit.models

import kotlinx.serialization.Serializable

@Suppress("EnumEntryName")
@Serializable
enum class BlocktankNotificationType {
    incomingHtlc,
    mutualClose,
    orderPaymentConfirmed,
    cjitPaymentArrived,
    wakeToTimeout,
    
    // Paykit notification types
    paykitPaymentRequest,
    paykitSubscriptionDue,
    paykitAutoPayExecuted,
    paykitSubscriptionFailed;

    override fun toString(): String = when {
        name.startsWith("paykit") -> "paykit.$name"
        else -> "blocktank.$name"
    }
}
