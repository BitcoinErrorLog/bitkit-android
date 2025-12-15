package to.bitkit.paykit.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class PaymentStatus {
    PENDING, COMPLETED, FAILED, REFUNDED
}

@Serializable
enum class PaymentDirection {
    SENT, RECEIVED
}

@Serializable
data class Receipt(
    val id: String,
    val direction: PaymentDirection,
    val counterpartyKey: String,
    var counterpartyName: String? = null,
    val amountSats: Long,
    var status: PaymentStatus = PaymentStatus.PENDING,
    val paymentMethod: String,
    val createdAt: Long = System.currentTimeMillis(),
    var completedAt: Long? = null,
    var memo: String? = null,
    var txId: String? = null,
    var proof: String? = null,
    var proofVerified: Boolean = false,
    var proofVerifiedAt: Long? = null
) {
    companion object {
        fun create(
            direction: PaymentDirection,
            counterpartyKey: String,
            counterpartyName: String? = null,
            amountSats: Long,
            paymentMethod: String,
            memo: String? = null
        ): Receipt {
            return Receipt(
                id = UUID.randomUUID().toString(),
                direction = direction,
                counterpartyKey = counterpartyKey,
                counterpartyName = counterpartyName,
                amountSats = amountSats,
                paymentMethod = paymentMethod,
                memo = memo
            )
        }
    }
    
    fun complete(txId: String? = null): Receipt {
        return copy(
            status = PaymentStatus.COMPLETED,
            completedAt = System.currentTimeMillis(),
            txId = txId
        )
    }
    
    fun fail(): Receipt {
        return copy(status = PaymentStatus.FAILED)
    }
    
    fun markProofVerified(): Receipt {
        return copy(
            proofVerified = true,
            proofVerifiedAt = System.currentTimeMillis()
        )
    }
    
    val abbreviatedCounterparty: String
        get() {
            if (counterpartyKey.length <= 16) return counterpartyKey
            return "${counterpartyKey.take(8)}...${counterpartyKey.takeLast(8)}"
        }
    
    val displayName: String
        get() = counterpartyName ?: abbreviatedCounterparty
}
