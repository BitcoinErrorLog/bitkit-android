package to.bitkit.paykit.protocol

import java.security.MessageDigest

/**
 * Canonical Paykit v0 protocol conventions.
 *
 * This object provides the single source of truth for:
 * - Pubkey normalization and ContextId derivation
 * - Storage path construction using ContextId
 * - AAD (Additional Authenticated Data) formats for Sealed Blob v2 with owner binding
 *
 * All implementations must match paykit-lib/src/protocol exactly.
 *
 * @see <a href="https://github.com/synonymdev/paykit-rs/blob/main/paykit-lib/src/protocol/mod.rs">Rust implementation</a>
 */
object PaykitV0Protocol {

    // ============================================================================
    // Constants
    // ============================================================================

    /** Protocol version string. */
    const val PROTOCOL_VERSION = "v0"

    /** Base path prefix for all Paykit v0 data. */
    const val PAYKIT_V0_PREFIX = "/pub/paykit.app/v0"

    /** Path suffix for payment requests directory. */
    const val REQUESTS_SUBPATH = "requests"

    /** Path suffix for subscription proposals directory. */
    const val SUBSCRIPTION_PROPOSALS_SUBPATH = "subscriptions/proposals"

    /** Path for Noise endpoint. */
    const val NOISE_ENDPOINT_SUBPATH = "noise"

    /** Path suffix for secure handoff directory. */
    const val HANDOFF_SUBPATH = "handoff"

    /** Path suffix for ACKs directory. */
    const val ACKS_SUBPATH = "acks"

    /** AAD prefix for all Paykit v0 sealed blobs. */
    const val AAD_PREFIX = "paykit:v0"

    /** Purpose label for ACKs. */
    const val PURPOSE_ACK = "ack"

    /** Purpose label for payment requests. */
    const val PURPOSE_REQUEST = "request"

    /** Purpose label for subscription proposals. */
    const val PURPOSE_SUBSCRIPTION_PROPOSAL = "subscription_proposal"

    /** Purpose label for secure handoff payloads. */
    const val PURPOSE_HANDOFF = "handoff"

    /** Valid characters in z-base-32 encoding (lowercase only). */
    private const val Z32_ALPHABET = "ybndrfg8ejkmcpqxot1uwisza345h769"

    /** Expected length of a z-base-32 encoded Ed25519 public key (256 bits / 5 bits per char). */
    private const val Z32_PUBKEY_LENGTH = 52

    // ============================================================================
    // Scope Derivation
    // ============================================================================

    /**
     * Normalize a z-base-32 pubkey string.
     *
     * Performs:
     * 1. Trim whitespace
     * 2. Strip `pubky://` or `pk:` prefix if present
     * 3. Lowercase
     * 4. Validate length (52 chars) and alphabet
     *
     * @param pubkey The pubkey string to normalize
     * @return The normalized pubkey (52 lowercase z32 chars)
     * @throws IllegalArgumentException if the pubkey is malformed
     */
    fun normalizePubkeyZ32(pubkey: String): String {
        val trimmed = pubkey.trim()

        // Strip pubky:// or pk: prefix if present
        val withoutPrefix = when {
            trimmed.startsWith("pubky://") -> trimmed.substring(8)
            trimmed.startsWith("pk:") -> trimmed.substring(3)
            else -> trimmed
        }

        // Lowercase
        val lowercased = withoutPrefix.lowercase()

        // Validate length
        require(lowercased.length == Z32_PUBKEY_LENGTH) {
            "z32 pubkey must be $Z32_PUBKEY_LENGTH chars, got ${lowercased.length}"
        }

        // Validate alphabet
        for (c in lowercased) {
            require(c in Z32_ALPHABET) {
                "invalid z32 character: '$c'"
            }
        }

        return lowercased
    }

    /**
     * Compute the ContextId for a peer pair.
     *
     * ContextId is a symmetric identifier for a pair of peers, used for routing
     * and correlation. The formula is:
     *
     * `context_id = hex(sha256("paykit:v0:context:" + first_z32 + ":" + second_z32))`
     *
     * where first_z32 and second_z32 are sorted lexicographically to ensure symmetry.
     *
     * @param pubkeyA First peer's z-base-32 encoded pubkey
     * @param pubkeyB Second peer's z-base-32 encoded pubkey
     * @return Lowercase hex string (64 chars) representing the ContextId
     * @throws IllegalArgumentException if either pubkey is malformed
     */
    fun contextId(pubkeyA: String, pubkeyB: String): String {
        val normA = normalizePubkeyZ32(pubkeyA)
        val normB = normalizePubkeyZ32(pubkeyB)
        
        // Sort lexicographically for symmetry
        val (first, second) = if (normA <= normB) Pair(normA, normB) else Pair(normB, normA)
        
        val preimage = "paykit:v0:context:$first:$second"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(preimage.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Compute the scope hash for a pubkey.
     *
     * `scope = hex(sha256(utf8(normalized_pubkey_z32)))`
     *
     * @deprecated Use [contextId] for ContextId-based routing instead.
     * This function is kept for backward compatibility with legacy data.
     *
     * @param pubkeyZ32 A z-base-32 encoded pubkey (will be normalized)
     * @return Lowercase hex string (64 chars) representing the SHA-256 hash
     * @throws IllegalArgumentException if the pubkey is malformed
     */
    @Deprecated(
        message = "Use contextId() for new code. recipientScope is legacy.",
        replaceWith = ReplaceWith("contextId(senderPubkeyZ32, pubkeyZ32)")
    )
    fun recipientScope(pubkeyZ32: String): String {
        val normalized = normalizePubkeyZ32(pubkeyZ32)
        return computeScopeHash(normalized)
    }

    /**
     * Alias for [recipientScope] - used for subscription proposals.
     *
     * @deprecated Use [contextId] for ContextId-based routing instead.
     */
    @Deprecated(
        message = "Use contextId() for new code. subscriberScope is legacy.",
        replaceWith = ReplaceWith("contextId(providerPubkeyZ32, pubkeyZ32)")
    )
    fun subscriberScope(pubkeyZ32: String): String = recipientScope(pubkeyZ32)

    /**
     * Internal: compute SHA-256 hash and return as lowercase hex.
     */
    private fun computeScopeHash(normalizedPubkey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(normalizedPubkey.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    // ============================================================================
    // Path Builders
    // ============================================================================

    /**
     * Build the storage path for a payment request.
     *
     * Path format: `/pub/paykit.app/v0/requests/{context_id}/{request_id}`
     *
     * This path is used on the **sender's** storage to store an encrypted
     * payment request addressed to the recipient. The context_id is computed
     * from both sender and recipient pubkeys for symmetric routing.
     *
     * @param senderPubkeyZ32 The sender's z-base-32 encoded pubkey
     * @param recipientPubkeyZ32 The recipient's z-base-32 encoded pubkey
     * @param requestId Unique identifier for this request
     * @return The full storage path (without the `pubky://owner` prefix)
     */
    fun paymentRequestPath(senderPubkeyZ32: String, recipientPubkeyZ32: String, requestId: String): String {
        val ctxId = contextId(senderPubkeyZ32, recipientPubkeyZ32)
        return "$PAYKIT_V0_PREFIX/$REQUESTS_SUBPATH/$ctxId/$requestId"
    }

    /**
     * Build the directory path for listing payment requests for a peer pair.
     *
     * Path format: `/pub/paykit.app/v0/requests/{context_id}/`
     *
     * Used when polling a contact's storage to discover pending requests.
     *
     * @param senderPubkeyZ32 The sender's z-base-32 encoded pubkey
     * @param recipientPubkeyZ32 The recipient's z-base-32 encoded pubkey
     * @return The directory path (with trailing slash for listing)
     */
    fun paymentRequestsDir(senderPubkeyZ32: String, recipientPubkeyZ32: String): String {
        val ctxId = contextId(senderPubkeyZ32, recipientPubkeyZ32)
        return "$PAYKIT_V0_PREFIX/$REQUESTS_SUBPATH/$ctxId/"
    }

    /**
     * Build the storage path for a subscription proposal.
     *
     * Path format: `/pub/paykit.app/v0/subscriptions/proposals/{context_id}/{proposal_id}`
     *
     * This path is used on the **provider's** storage to store an encrypted
     * subscription proposal addressed to the subscriber.
     *
     * @param providerPubkeyZ32 The provider's z-base-32 encoded pubkey
     * @param subscriberPubkeyZ32 The subscriber's z-base-32 encoded pubkey
     * @param proposalId Unique identifier for this proposal
     * @return The full storage path (without the `pubky://owner` prefix)
     */
    fun subscriptionProposalPath(
        providerPubkeyZ32: String,
        subscriberPubkeyZ32: String,
        proposalId: String,
    ): String {
        val ctxId = contextId(providerPubkeyZ32, subscriberPubkeyZ32)
        return "$PAYKIT_V0_PREFIX/$SUBSCRIPTION_PROPOSALS_SUBPATH/$ctxId/$proposalId"
    }

    /**
     * Build the directory path for listing subscription proposals for a peer pair.
     *
     * Path format: `/pub/paykit.app/v0/subscriptions/proposals/{context_id}/`
     *
     * Used when polling a provider's storage to discover pending proposals.
     *
     * @param providerPubkeyZ32 The provider's z-base-32 encoded pubkey
     * @param subscriberPubkeyZ32 The subscriber's z-base-32 encoded pubkey
     * @return The directory path (with trailing slash for listing)
     */
    fun subscriptionProposalsDir(providerPubkeyZ32: String, subscriberPubkeyZ32: String): String {
        val ctxId = contextId(providerPubkeyZ32, subscriberPubkeyZ32)
        return "$PAYKIT_V0_PREFIX/$SUBSCRIPTION_PROPOSALS_SUBPATH/$ctxId/"
    }

    /**
     * Build the storage path for an ACK.
     *
     * Path format: `/pub/paykit.app/v0/acks/{object_type}/{context_id}/{msg_id}`
     *
     * ACKs are written by the recipient to acknowledge receipt of a message.
     *
     * @param objectType The type of object being ACKed (e.g., "request", "subscription_proposal")
     * @param senderPubkeyZ32 The original sender's z-base-32 encoded pubkey
     * @param recipientPubkeyZ32 The recipient's z-base-32 encoded pubkey
     * @param msgId The message ID being ACKed
     * @return The full storage path
     */
    fun ackPath(
        objectType: String,
        senderPubkeyZ32: String,
        recipientPubkeyZ32: String,
        msgId: String,
    ): String {
        val ctxId = contextId(senderPubkeyZ32, recipientPubkeyZ32)
        return "$PAYKIT_V0_PREFIX/$ACKS_SUBPATH/$objectType/$ctxId/$msgId"
    }

    /**
     * Build the storage path for a Noise endpoint.
     *
     * Path format: `/pub/paykit.app/v0/noise`
     *
     * This is a fixed path on the user's own storage.
     */
    fun noiseEndpointPath(): String = "$PAYKIT_V0_PREFIX/$NOISE_ENDPOINT_SUBPATH"

    /**
     * Build the storage path for a secure handoff payload.
     *
     * Path format: `/pub/paykit.app/v0/handoff/{request_id}`
     *
     * @param requestId Unique identifier for this handoff request
     * @return The full storage path
     */
    fun secureHandoffPath(requestId: String): String =
        "$PAYKIT_V0_PREFIX/$HANDOFF_SUBPATH/$requestId"

    // ============================================================================
    // AAD Builders
    // ============================================================================

    /**
     * Build AAD for a payment request.
     *
     * Format: `paykit:v0:request:{owner_z32}:{path}:{request_id}`
     *
     * The owner binding ensures the AAD is tied to the storage owner's identity,
     * preventing relocation attacks.
     *
     * @param ownerPubkeyZ32 The storage owner's z-base-32 encoded pubkey (the sender)
     * @param senderPubkeyZ32 The sender's z-base-32 encoded pubkey
     * @param recipientPubkeyZ32 The recipient's z-base-32 encoded pubkey
     * @param requestId Unique identifier for this request
     * @return The AAD string to use with Sealed Blob v2 encryption
     */
    fun paymentRequestAad(
        ownerPubkeyZ32: String,
        senderPubkeyZ32: String,
        recipientPubkeyZ32: String,
        requestId: String,
    ): String {
        val owner = normalizePubkeyZ32(ownerPubkeyZ32)
        val path = paymentRequestPath(senderPubkeyZ32, recipientPubkeyZ32, requestId)
        return "$AAD_PREFIX:$PURPOSE_REQUEST:$owner:$path:$requestId"
    }

    /**
     * Build AAD for a subscription proposal.
     *
     * Format: `paykit:v0:subscription_proposal:{owner_z32}:{path}:{proposal_id}`
     *
     * @param ownerPubkeyZ32 The storage owner's z-base-32 encoded pubkey (the provider)
     * @param providerPubkeyZ32 The provider's z-base-32 encoded pubkey
     * @param subscriberPubkeyZ32 The subscriber's z-base-32 encoded pubkey
     * @param proposalId Unique identifier for this proposal
     * @return The AAD string to use with Sealed Blob v2 encryption
     */
    fun subscriptionProposalAad(
        ownerPubkeyZ32: String,
        providerPubkeyZ32: String,
        subscriberPubkeyZ32: String,
        proposalId: String,
    ): String {
        val owner = normalizePubkeyZ32(ownerPubkeyZ32)
        val path = subscriptionProposalPath(providerPubkeyZ32, subscriberPubkeyZ32, proposalId)
        return "$AAD_PREFIX:$PURPOSE_SUBSCRIPTION_PROPOSAL:$owner:$path:$proposalId"
    }

    /**
     * Build AAD for a secure handoff payload.
     *
     * Format: `paykit:v0:handoff:{owner_pubkey}:{path}:{request_id}`
     *
     * @param ownerPubkeyZ32 The Ring user's z-base-32 encoded pubkey
     * @param requestId Unique identifier for this handoff
     * @return The AAD string to use with Sealed Blob v2 encryption
     */
    fun secureHandoffAad(ownerPubkeyZ32: String, requestId: String): String {
        val owner = normalizePubkeyZ32(ownerPubkeyZ32)
        val path = secureHandoffPath(requestId)
        return "$AAD_PREFIX:$PURPOSE_HANDOFF:$owner:$path:$requestId"
    }

    /**
     * Build AAD for an ACK.
     *
     * Format: `paykit:v0:ack_{object_type}:{ack_writer_z32}:{path}:{msg_id}`
     *
     * @param objectType The type of object being ACKed (e.g., "request")
     * @param ackWriterPubkeyZ32 The ACK writer's z-base-32 encoded pubkey (recipient)
     * @param senderPubkeyZ32 The original sender's z-base-32 encoded pubkey
     * @param recipientPubkeyZ32 The recipient's z-base-32 encoded pubkey
     * @param msgId The message ID being ACKed
     * @return The AAD string to use with Sealed Blob v2 encryption
     */
    fun ackAad(
        objectType: String,
        ackWriterPubkeyZ32: String,
        senderPubkeyZ32: String,
        recipientPubkeyZ32: String,
        msgId: String,
    ): String {
        val ackWriter = normalizePubkeyZ32(ackWriterPubkeyZ32)
        val path = ackPath(objectType, senderPubkeyZ32, recipientPubkeyZ32, msgId)
        return "$AAD_PREFIX:${PURPOSE_ACK}_$objectType:$ackWriter:$path:$msgId"
    }

    /**
     * Build AAD for a cross-device relay session payload.
     *
     * Format: `paykit:v0:relay:session:{request_id}`
     *
     * @param requestId Unique identifier for this relay session request
     * @return The AAD string to use with Sealed Blob v2 encryption
     */
    fun relaySessionAad(requestId: String): String =
        "$AAD_PREFIX:relay:session:$requestId"

    /**
     * Build AAD from explicit owner, path, and ID.
     *
     * Format: `paykit:v0:{purpose}:{owner_z32}:{path}:{id}`
     *
     * @param purpose The object type (use constants like [PURPOSE_REQUEST])
     * @param ownerPubkeyZ32 The storage owner's z-base-32 encoded pubkey
     * @param path The full storage path
     * @param id The object identifier
     */
    fun buildAad(purpose: String, ownerPubkeyZ32: String, path: String, id: String): String {
        val owner = normalizePubkeyZ32(ownerPubkeyZ32)
        return "$AAD_PREFIX:$purpose:$owner:$path:$id"
    }
}
