package to.bitkit.paykit.utils

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for PaykitValidation utilities.
 */
class PaykitValidationTest {

    // MARK: - UUID Validation

    @Test
    fun `isValidUUID returns true for valid UUID`() {
        assertTrue(PaykitValidation.isValidUUID("550e8400-e29b-41d4-a716-446655440000"))
        assertTrue(PaykitValidation.isValidUUID("123e4567-e89b-12d3-a456-426614174000"))
    }

    @Test
    fun `isValidUUID returns false for invalid UUID`() {
        assertFalse(PaykitValidation.isValidUUID(null))
        assertFalse(PaykitValidation.isValidUUID(""))
        assertFalse(PaykitValidation.isValidUUID("not-a-uuid"))
        assertFalse(PaykitValidation.isValidUUID("550e8400-e29b-41d4-a716")) // Too short
        assertFalse(PaykitValidation.isValidUUID("550e8400-e29b-41d4-a716-446655440000-extra")) // Too long
        assertFalse(PaykitValidation.isValidUUID("550e8400e29b41d4a716446655440000")) // No dashes
    }

    // MARK: - Z-Base32 Pubkey Validation

    @Test
    fun `isValidZBase32Pubkey returns true for valid 52-char pubkey`() {
        // Valid Z-Base32 characters: ybndrfg8ejkmcpqxot1uwisza345h769
        assertTrue(PaykitValidation.isValidZBase32Pubkey("ybndrfg8ejkmcpqxot1uwisza345h769ybndrfg8ejkmcpqxot1u"))
    }

    @Test
    fun `isValidZBase32Pubkey returns false for invalid pubkey`() {
        assertFalse(PaykitValidation.isValidZBase32Pubkey(null))
        assertFalse(PaykitValidation.isValidZBase32Pubkey(""))
        assertFalse(PaykitValidation.isValidZBase32Pubkey("tooshort"))
        // Contains invalid characters (l, 0, 2, v)
        assertFalse(PaykitValidation.isValidZBase32Pubkey("0000000000000000000000000000000000000000000000000000"))
    }

    // MARK: - Hex Pubkey Validation

    @Test
    fun `isValidHexPubkey returns true for valid 64-char hex`() {
        assertTrue(
            PaykitValidation.isValidHexPubkey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        )
        assertTrue(
            PaykitValidation.isValidHexPubkey("ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789")
        )
    }

    @Test
    fun `isValidHexPubkey returns false for invalid hex`() {
        assertFalse(PaykitValidation.isValidHexPubkey(null))
        assertFalse(PaykitValidation.isValidHexPubkey(""))
        assertFalse(PaykitValidation.isValidHexPubkey("0123456789abcdef")) // Too short
        assertFalse(PaykitValidation.isValidHexPubkey("ghijklmnop")) // Invalid hex chars
    }

    // MARK: - BOLT11 Invoice Validation

    @Test
    fun `isValidBolt11Invoice returns true for valid invoices`() {
        assertTrue(PaykitValidation.isValidBolt11Invoice("lnbc1pvjluezpp5qqqsyqcyq5rqwzqf"))
        assertTrue(PaykitValidation.isValidBolt11Invoice("lntb1pvjluezpp5qqqsyqcyq5rqwzqf"))
        assertTrue(PaykitValidation.isValidBolt11Invoice("lnbcrt1pvjluezpp5qqqsyqcyq5rqwzqf"))
        assertTrue(PaykitValidation.isValidBolt11Invoice("LNBC1PVJLUEZPP5QQQSYQCYQ5RQWZQF")) // Uppercase
    }

    @Test
    fun `isValidBolt11Invoice returns false for invalid invoices`() {
        assertFalse(PaykitValidation.isValidBolt11Invoice(null))
        assertFalse(PaykitValidation.isValidBolt11Invoice(""))
        assertFalse(PaykitValidation.isValidBolt11Invoice("bitcoin:1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"))
        assertFalse(PaykitValidation.isValidBolt11Invoice("not-an-invoice"))
    }

    // MARK: - Bitcoin Address Validation

    @Test
    fun `isValidBitcoinAddress returns true for valid addresses`() {
        assertTrue(PaykitValidation.isValidBitcoinAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")) // Legacy
        assertTrue(PaykitValidation.isValidBitcoinAddress("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy")) // P2SH
        assertTrue(
            PaykitValidation.isValidBitcoinAddress("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq")
        ) // Native SegWit
        assertTrue(PaykitValidation.isValidBitcoinAddress("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx")) // Testnet
        assertTrue(PaykitValidation.isValidBitcoinAddress("bcrt1qw508d6qejxtdg4y5r3zarvary0c5xw7kygt080")) // Regtest
    }

    @Test
    fun `isValidBitcoinAddress returns false for invalid addresses`() {
        assertFalse(PaykitValidation.isValidBitcoinAddress(null))
        assertFalse(PaykitValidation.isValidBitcoinAddress(""))
        assertFalse(PaykitValidation.isValidBitcoinAddress("short"))
        assertFalse(PaykitValidation.isValidBitcoinAddress("not-a-bitcoin-address"))
    }

    // MARK: - Description Sanitization

    @Test
    fun `sanitizeDescription removes dangerous characters`() {
        assertEquals("Hello World", PaykitValidation.sanitizeDescription("Hello World"))
        // Removes < > " ' characters
        assertEquals("scriptHello/script World", PaykitValidation.sanitizeDescription("<script>Hello</script> World"))
        assertEquals("Payment for order", PaykitValidation.sanitizeDescription("Payment for \"order\""))
        assertEquals("Test", PaykitValidation.sanitizeDescription("  Test  ")) // Trimmed
    }

    @Test
    fun `sanitizeDescription returns null for null input`() {
        assertNull(PaykitValidation.sanitizeDescription(null))
    }

    @Test
    fun `sanitizeDescription truncates long strings`() {
        val longString = "a".repeat(1000)
        val result = PaykitValidation.sanitizeDescription(longString)
        assertEquals(500, result?.length)
    }

    // MARK: - Satoshi Amount Validation

    @Test
    fun `isValidSatoshiAmount returns true for valid amounts`() {
        assertTrue(PaykitValidation.isValidSatoshiAmount(1L))
        assertTrue(PaykitValidation.isValidSatoshiAmount(21_000_000_00_000_000L)) // 21M BTC
        assertTrue(PaykitValidation.isValidSatoshiAmount(1uL))
    }

    @Test
    fun `isValidSatoshiAmount returns false for invalid amounts`() {
        assertFalse(PaykitValidation.isValidSatoshiAmount(null as Long?))
        assertFalse(PaykitValidation.isValidSatoshiAmount(0L))
        assertFalse(PaykitValidation.isValidSatoshiAmount(-1L))
        assertFalse(PaykitValidation.isValidSatoshiAmount(0uL))
    }
}
