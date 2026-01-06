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
    paykitSubscriptionFailed,

    /** Incoming Noise protocol request - wake app to start Noise server */
    paykitNoiseRequest;

    override fun toString(): String = when {
        name.startsWith("paykit") -> "paykit.$name"
        else -> "blocktank.$name"
    }
}
