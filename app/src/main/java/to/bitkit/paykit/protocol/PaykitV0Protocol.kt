package to.bitkit.paykit.protocol

import java.security.MessageDigest

/**
 * Canonical Paykit v0 protocol conventions.
 *
 * This object provides the single source of truth for:
 * - Pubkey normalization and scope hashing
 * - Storage path construction
 * - AAD (Additional Authenticated Data) formats for Sealed Blob v1
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

    /** AAD prefix for all Paykit v0 sealed blobs. */
    const val AAD_PREFIX = "paykit:v0"

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
     * 2. Strip `pk:` prefix if present
     * 3. Lowercase
     * 4. Validate length (52 chars) and alphabet
     *
     * @param pubkey The pubkey string to normalize
     * @return The normalized pubkey (52 lowercase z32 chars)
     * @throws IllegalArgumentException if the pubkey is malformed
     */
    fun normalizePubkeyZ32(pubkey: String): String {
        val trimmed = pubkey.trim()

        // Strip pk: prefix if present
        val withoutPrefix = if (trimmed.startsWith("pk:")) {
            trimmed.substring(3)
        } else {
            trimmed
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
     * Compute the scope hash for a pubkey.
     *
     * `scope = hex(sha256(utf8(normalized_pubkey_z32)))`
     *
     * The scope is used as a per-recipient directory name in storage paths.
     *
     * @param pubkeyZ32 A z-base-32 encoded pubkey (will be normalized)
     * @return Lowercase hex string (64 chars) representing the SHA-256 hash
     * @throws IllegalArgumentException if the pubkey is malformed
     */
    fun recipientScope(pubkeyZ32: String): String {
        val normalized = normalizePubkeyZ32(pubkeyZ32)
        return computeScopeHash(normalized)
    }

    /**
     * Alias for [recipientScope] - used for subscription proposals.
     *
     * Semantically identical, but named for clarity when dealing with subscriptions.
     */
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
     * Path format: `/pub/paykit.app/v0/requests/{recipient_scope}/{request_id}`
     *
     * This path is used on the **sender's** storage to store an encrypted
     * payment request addressed to the recipient.
     *
     * @param recipientPubkeyZ32 The recipient's z-base-32 encoded pubkey
     * @param requestId Unique identifier for this request
     * @return The full storage path (without the `pubky://owner` prefix)
     */
    fun paymentRequestPath(recipientPubkeyZ32: String, requestId: String): String {
        val scope = recipientScope(recipientPubkeyZ32)
        return "$PAYKIT_V0_PREFIX/$REQUESTS_SUBPATH/$scope/$requestId"
    }

    /**
     * Build the directory path for listing payment requests for a recipient.
     *
     * Path format: `/pub/paykit.app/v0/requests/{recipient_scope}/`
     *
     * Used when polling a contact's storage to discover pending requests.
     *
     * @param recipientPubkeyZ32 The recipient's z-base-32 encoded pubkey
     * @return The directory path (with trailing slash for listing)
     */
    fun paymentRequestsDir(recipientPubkeyZ32: String): String {
        val scope = recipientScope(recipientPubkeyZ32)
        return "$PAYKIT_V0_PREFIX/$REQUESTS_SUBPATH/$scope/"
    }

    /**
     * Build the storage path for a subscription proposal.
     *
     * Path format: `/pub/paykit.app/v0/subscriptions/proposals/{subscriber_scope}/{proposal_id}`
     *
     * This path is used on the **provider's** storage to store an encrypted
     * subscription proposal addressed to the subscriber.
     *
     * @param subscriberPubkeyZ32 The subscriber's z-base-32 encoded pubkey
     * @param proposalId Unique identifier for this proposal
     * @return The full storage path (without the `pubky://owner` prefix)
     */
    fun subscriptionProposalPath(subscriberPubkeyZ32: String, proposalId: String): String {
        val scope = subscriberScope(subscriberPubkeyZ32)
        return "$PAYKIT_V0_PREFIX/$SUBSCRIPTION_PROPOSALS_SUBPATH/$scope/$proposalId"
    }

    /**
     * Build the directory path for listing subscription proposals for a subscriber.
     *
     * Path format: `/pub/paykit.app/v0/subscriptions/proposals/{subscriber_scope}/`
     *
     * Used when polling a provider's storage to discover pending proposals.
     *
     * @param subscriberPubkeyZ32 The subscriber's z-base-32 encoded pubkey
     * @return The directory path (with trailing slash for listing)
     */
    fun subscriptionProposalsDir(subscriberPubkeyZ32: String): String {
        val scope = subscriberScope(subscriberPubkeyZ32)
        return "$PAYKIT_V0_PREFIX/$SUBSCRIPTION_PROPOSALS_SUBPATH/$scope/"
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
     * Format: `paykit:v0:request:{path}:{request_id}`
     *
     * @param recipientPubkeyZ32 The recipient's z-base-32 encoded pubkey
     * @param requestId Unique identifier for this request
     * @return The AAD string to use with Sealed Blob v1 encryption
     */
    fun paymentRequestAad(recipientPubkeyZ32: String, requestId: String): String {
        val path = paymentRequestPath(recipientPubkeyZ32, requestId)
        return "$AAD_PREFIX:$PURPOSE_REQUEST:$path:$requestId"
    }

    /**
     * Build AAD for a subscription proposal.
     *
     * Format: `paykit:v0:subscription_proposal:{path}:{proposal_id}`
     *
     * @param subscriberPubkeyZ32 The subscriber's z-base-32 encoded pubkey
     * @param proposalId Unique identifier for this proposal
     * @return The AAD string to use with Sealed Blob v1 encryption
     */
    fun subscriptionProposalAad(subscriberPubkeyZ32: String, proposalId: String): String {
        val path = subscriptionProposalPath(subscriberPubkeyZ32, proposalId)
        return "$AAD_PREFIX:$PURPOSE_SUBSCRIPTION_PROPOSAL:$path:$proposalId"
    }

    /**
     * Build AAD for a secure handoff payload.
     *
     * Format: `paykit:v0:handoff:{owner_pubkey}:{path}:{request_id}`
     *
     * @param ownerPubkeyZ32 The Ring user's z-base-32 encoded pubkey
     * @param requestId Unique identifier for this handoff
     * @return The AAD string to use with Sealed Blob v1 encryption
     */
    fun secureHandoffAad(ownerPubkeyZ32: String, requestId: String): String {
        val path = secureHandoffPath(requestId)
        return "$AAD_PREFIX:$PURPOSE_HANDOFF:$ownerPubkeyZ32:$path:$requestId"
    }

    /**
     * Build AAD from explicit path and ID.
     *
     * Format: `paykit:v0:{purpose}:{path}:{id}`
     *
     * @param purpose The object type (use constants like [PURPOSE_REQUEST])
     * @param path The full storage path
     * @param id The object identifier
     */
    fun buildAad(purpose: String, path: String, id: String): String =
        "$AAD_PREFIX:$purpose:$path:$id"
}

