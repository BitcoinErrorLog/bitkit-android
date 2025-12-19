package to.bitkit.paykit.models

import kotlinx.serialization.Serializable

/**
 * Status of a payment request
 */
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
    val direction: RequestDirection,
    /** Optional invoice number for cross-referencing with receipts */
    val invoiceNumber: String? = null,
    /** ID of the receipt that fulfilled this request (if paid) */
    var receiptId: String? = null
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

    /** Display invoice number - returns invoiceNumber if set, otherwise request id */
    val displayInvoiceNumber: String
        get() = invoiceNumber ?: id

    /** Check if this request has been fulfilled */
    val isFulfilled: Boolean
        get() = status == PaymentRequestStatus.PAID && receiptId != null
}
