package to.bitkit.paykit.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents a subscription proposal to be sent to a subscriber.
 *
 * In the v0 provider-storage model, proposals are published to the **provider's** Pubky directory
 * in a subscriber-scoped subdirectory. Subscribers poll known providers to discover proposals.
 *
 * Path format: `/pub/paykit.app/v0/subscriptions/proposals/{subscriber_scope}/{proposalId}`
 * where `subscriber_scope = hex(sha256(normalized_subscriber_pubkey_z32))`
 *
 * Stored on: provider's homeserver (sender-storage model)
 * Encryption: Sealed Blob v1 to subscriber's Noise public key
 */
@Serializable
data class SubscriptionProposal(
    val id: String = UUID.randomUUID().toString(),
    @SerialName("provider_pubkey")
    val providerPubkey: String,
    @SerialName("provider_name")
    val providerName: String? = null,
    @SerialName("amount_sats")
    val amountSats: Long,
    val currency: String = "SAT",
    val frequency: String,
    val description: String? = null,
    @SerialName("method_id")
    val methodId: String = "lightning",
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        fun create(
            providerPubkey: String,
            providerName: String?,
            amountSats: Long,
            frequency: String,
            description: String?,
            methodId: String = "lightning",
        ): SubscriptionProposal = SubscriptionProposal(
            id = UUID.randomUUID().toString(),
            providerPubkey = providerPubkey,
            providerName = providerName,
            amountSats = amountSats,
            frequency = frequency,
            description = description,
            methodId = methodId,
            createdAt = System.currentTimeMillis(),
        )
    }

    fun toSubscription(): Subscription = Subscription.create(
        providerName = providerName ?: providerPubkey.take(8),
        providerPubkey = providerPubkey,
        amountSats = amountSats,
        currency = currency,
        frequency = frequency,
        description = description ?: "",
        methodId = methodId,
    )
}

