package to.bitkit.paykit.models

import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.UUID

/**
 * Represents a recurring payment subscription
 */
@Serializable
data class Subscription(
    val id: String,
    var providerName: String,
    var providerPubkey: String,
    var amountSats: Long,
    var currency: String = "SAT",
    var frequency: String, // daily, weekly, monthly, yearly
    var description: String,
    var methodId: String = "lightning",
    var isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    var lastPaymentAt: Long? = null,
    var nextPaymentAt: Long? = null,
    var paymentCount: Int = 0,
    var lastInvoice: String? = null,
    var lastPaymentHash: String? = null,
    var lastPreimage: String? = null,
    var lastFeeSats: ULong? = null,
) {
    companion object {
        fun create(
            providerName: String,
            providerPubkey: String,
            amountSats: Long,
            currency: String = "SAT",
            frequency: String,
            description: String,
            methodId: String = "lightning"
        ): Subscription {
            return Subscription(
                id = UUID.randomUUID().toString(),
                providerName = providerName,
                providerPubkey = providerPubkey,
                amountSats = amountSats,
                currency = currency,
                frequency = frequency,
                description = description,
                methodId = methodId,
                nextPaymentAt = calculateNextPayment(frequency, System.currentTimeMillis())
            )
        }

        private fun calculateNextPayment(frequency: String, from: Long): Long {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = from
            when (frequency.lowercase()) {
                "daily" -> calendar.add(Calendar.DAY_OF_YEAR, 1)
                "weekly" -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
                "monthly" -> calendar.add(Calendar.MONTH, 1)
                "yearly" -> calendar.add(Calendar.YEAR, 1)
            }
            return calendar.timeInMillis
        }
    }

    fun recordPayment(): Subscription {
        val now = System.currentTimeMillis()
        return copy(
            lastPaymentAt = now,
            paymentCount = paymentCount + 1,
            nextPaymentAt = Companion.calculateNextPayment(frequency, now)
        )
    }

    fun recordPayment(
        paymentHash: String?,
        preimage: String?,
        feeSats: ULong?,
    ): Subscription {
        val now = System.currentTimeMillis()
        return copy(
            lastPaymentAt = now,
            paymentCount = paymentCount + 1,
            nextPaymentAt = Companion.calculateNextPayment(frequency, now),
            lastPaymentHash = paymentHash,
            lastPreimage = preimage,
            lastFeeSats = feeSats,
        )
    }
}
