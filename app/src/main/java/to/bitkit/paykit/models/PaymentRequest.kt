package to.bitkit.paykit.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class PaymentRequestStatus {
    PENDING, ACCEPTED, DECLINED, EXPIRED, PAID
}

@Serializable
enum class RequestDirection {
    INCOMING, OUTGOING
}

@Serializable
data class PaymentRequest(
    val id: String,
    val fromPubkey: String,
    val toPubkey: String,
    val amountSats: Long,
    val currency: String,
    val methodId: String,
    val description: String,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    var status: PaymentRequestStatus = PaymentRequestStatus.PENDING,
    val direction: RequestDirection
) {
    val counterpartyName: String
        get() {
            val key = if (direction == RequestDirection.INCOMING) fromPubkey else toPubkey
            return if (key.length > 12) {
                "${key.take(6)}...${key.takeLast(4)}"
            } else {
                key
            }
        }
}
