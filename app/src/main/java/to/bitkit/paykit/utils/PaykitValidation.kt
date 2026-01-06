package to.bitkit.paykit.utils

/**
 * Validation utilities for Paykit inputs.
 *
 * Provides format validation for pubkeys, request IDs, and other Paykit data
 * to prevent injection attacks and malformed inputs.
 */
object PaykitValidation {

    // Maximum lengths to prevent DoS via oversized inputs
    private const val MAX_UUID_LENGTH = 36
    private const val MAX_PUBKEY_LENGTH = 100
    private const val MAX_INVOICE_LENGTH = 2000
    private const val MAX_DESCRIPTION_LENGTH = 500

    // Z-Base32 alphabet (lowercase only)
    private val ZBASE32_REGEX = Regex("^[ybndrfg8ejkmcpqxot1uwisza345h769]+$")

    // UUID format: 8-4-4-4-12 hex digits
    private val UUID_REGEX = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        RegexOption.IGNORE_CASE,
    )

    // Hex string (for pubkeys in hex format)
    private val HEX_REGEX = Regex("^[0-9a-f]+$", RegexOption.IGNORE_CASE)

    /**
     * Validate a UUID format string (used for request IDs).
     */
    fun isValidUUID(value: String?): Boolean {
        if (value == null) return false
        if (value.length > MAX_UUID_LENGTH) return false
        return UUID_REGEX.matches(value)
    }

    /**
     * Validate a Z-Base32 encoded pubkey (52 chars for ed25519).
     */
    fun isValidZBase32Pubkey(value: String?): Boolean {
        if (value == null) return false
        if (value.length != 52) return false
        return ZBASE32_REGEX.matches(value)
    }

    /**
     * Validate a hex-encoded pubkey (64 chars for 32-byte key).
     */
    fun isValidHexPubkey(value: String?): Boolean {
        if (value == null) return false
        if (value.length != 64) return false
        return HEX_REGEX.matches(value)
    }

    /**
     * Validate any pubkey format (Z-Base32 or hex).
     */
    fun isValidPubkey(value: String?): Boolean {
        if (value == null) return false
        if (value.length > MAX_PUBKEY_LENGTH) return false
        return isValidZBase32Pubkey(value) || isValidHexPubkey(value)
    }

    /**
     * Validate a BOLT11 invoice format (basic check).
     */
    fun isValidBolt11Invoice(value: String?): Boolean {
        if (value == null) return false
        if (value.length > MAX_INVOICE_LENGTH) return false
        val lower = value.lowercase()
        // BOLT11 invoices start with lnbc (mainnet), lntb (testnet), lnbcrt (regtest)
        return lower.startsWith("lnbc") || lower.startsWith("lntb") || lower.startsWith("lnbcrt")
    }

    /**
     * Validate a Bitcoin address format (basic check).
     */
    fun isValidBitcoinAddress(value: String?): Boolean {
        if (value == null) return false
        if (value.length < 26 || value.length > 90) return false
        // Basic format checks - actual validation done by BitkitCore
        return value.startsWith("1") || // Legacy
            value.startsWith("3") || // P2SH
            value.startsWith("bc1") || // Native SegWit mainnet
            value.startsWith("tb1") || // Native SegWit testnet
            value.startsWith("bcrt1") // Native SegWit regtest
    }

    /**
     * Sanitize a description string (remove dangerous characters).
     */
    fun sanitizeDescription(value: String?): String? {
        if (value == null) return null
        return value
            .take(MAX_DESCRIPTION_LENGTH)
            .replace(Regex("[<>\"']"), "") // Remove HTML/script injection chars
            .trim()
    }

    /**
     * Validate an amount in satoshis (must be positive and reasonable).
     */
    fun isValidSatoshiAmount(sats: Long?): Boolean {
        if (sats == null) return false
        if (sats <= 0) return false
        // Max supply is 21M BTC = 2.1 quadrillion sats
        return sats <= 2_100_000_000_000_000L
    }

    /**
     * Validate an amount in satoshis (ULong version).
     */
    fun isValidSatoshiAmount(sats: ULong?): Boolean {
        if (sats == null) return false
        if (sats == 0uL) return false
        return sats <= 2_100_000_000_000_000uL
    }
}
