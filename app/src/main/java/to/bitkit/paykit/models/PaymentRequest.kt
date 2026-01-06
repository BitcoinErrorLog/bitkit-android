package to.bitkit.paykit.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Serializer for timestamps that handles both Long and Double (from iOS Date.millisecondsSince1970)
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object FlexibleTimestampSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleTimestamp", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }

    override fun deserialize(decoder: Decoder): Long {
        return when (decoder) {
            is JsonDecoder -> {
                val element = decoder.decodeJsonElement()
                if (element is JsonPrimitive) {
                    element.longOrNull ?: element.doubleOrNull?.toLong() ?: 0L
                } else {
                    0L
                }
            }
            else -> decoder.decodeLong()
        }
    }
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object NullableFlexibleTimestampSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "NullableFlexibleTimestamp",
        PrimitiveKind.LONG
    )

    override fun serialize(encoder: Encoder, value: Long?) {
        if (value != null) encoder.encodeLong(value) else encoder.encodeNull()
    }

    override fun deserialize(decoder: Decoder): Long? {
        return when (decoder) {
            is JsonDecoder -> {
                val element = decoder.decodeJsonElement()
                if (element is JsonPrimitive && !element.isString) {
                    element.longOrNull ?: element.doubleOrNull?.toLong()
                } else {
                    null
                }
            }
            else -> try { decoder.decodeLong() } catch (_: Exception) { null }
        }
    }
}

/**
 * Status of a payment request
 */
@Serializable
enum class PaymentRequestStatus {
    @SerialName("Pending")
    PENDING,

    @SerialName("Accepted")
    ACCEPTED,

    @SerialName("Declined")
    DECLINED,

    @SerialName("Expired")
    EXPIRED,

    @SerialName("Paid")
    PAID
}

@Serializable
enum class RequestDirection {
    @SerialName("incoming")
    INCOMING,

    @SerialName("outgoing")
    OUTGOING
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
    @Serializable(with = FlexibleTimestampSerializer::class)
    val createdAt: Long = System.currentTimeMillis(),
    @Serializable(with = NullableFlexibleTimestampSerializer::class)
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
