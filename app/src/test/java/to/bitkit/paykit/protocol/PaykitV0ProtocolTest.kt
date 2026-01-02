package to.bitkit.paykit.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for PaykitV0Protocol.
 *
 * These tests verify that the Kotlin implementation produces identical outputs
 * to the Rust paykit-lib/src/protocol module.
 */
class PaykitV0ProtocolTest {

    companion object {
        // Test vectors - must match Rust tests in paykit-lib/src/protocol/scope.rs
        private const val TEST_PUBKEY_1 = "ybndrfg8ejkmcpqxot1uwisza345h769ybndrfg8ejkmcpqxot1u"
        private const val TEST_PUBKEY_2 = "8pinxxgqs41n4aididenw5apqp1urfmzdztr8jt4abrkdn435ewo"

        // Expected scope hashes - must match Rust test vectors
        private const val EXPECTED_SCOPE_1 = "55340b54f918470e1f025a80bb3347934fad3f57189eef303d620e65468cde80"
        private const val EXPECTED_SCOPE_2 = "04dc3323da61313c6f5404cf7921af2432ef867afe6cc4c32553858b8ac07f12"
    }

    // ============================================================================
    // Normalization Tests
    // ============================================================================

    @Test
    fun `normalizePubkeyZ32 handles already normalized input`() {
        val result = PaykitV0Protocol.normalizePubkeyZ32(TEST_PUBKEY_1)
        assertEquals(TEST_PUBKEY_1, result)
    }

    @Test
    fun `normalizePubkeyZ32 strips pk prefix and lowercases`() {
        val input = "pk:YBNDRFG8EJKMCPQXOT1UWISZA345H769YBNDRFG8EJKMCPQXOT1U"
        val result = PaykitV0Protocol.normalizePubkeyZ32(input)
        assertEquals(TEST_PUBKEY_1, result)
    }

    @Test
    fun `normalizePubkeyZ32 trims whitespace`() {
        val input = "  pk:ybndrfg8ejkmcpqxot1uwisza345h769ybndrfg8ejkmcpqxot1u  "
        val result = PaykitV0Protocol.normalizePubkeyZ32(input)
        assertEquals(TEST_PUBKEY_1, result)
    }

    @Test
    fun `normalizePubkeyZ32 rejects wrong length`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            PaykitV0Protocol.normalizePubkeyZ32("tooshort")
        }
        assertTrue(exception.message!!.contains("must be 52 chars"))
    }

    @Test
    fun `normalizePubkeyZ32 rejects invalid z32 characters`() {
        // 'l' is not in z32 alphabet
        val invalidInput = "lbndrfg8ejkmcpqxot1uwisza345h769ybndrfg8ejkmcpqxot1u"
        val exception = assertThrows(IllegalArgumentException::class.java) {
            PaykitV0Protocol.normalizePubkeyZ32(invalidInput)
        }
        assertTrue(exception.message!!.contains("invalid z32 character"))
    }

    // ============================================================================
    // Scope Tests - Cross-platform test vectors
    // ============================================================================

    @Test
    fun `recipientScope produces correct hash for test vector 1`() {
        val scope = PaykitV0Protocol.recipientScope(TEST_PUBKEY_1)
        assertEquals(EXPECTED_SCOPE_1, scope)
    }

    @Test
    fun `recipientScope produces correct hash for test vector 2`() {
        val scope = PaykitV0Protocol.recipientScope(TEST_PUBKEY_2)
        assertEquals(EXPECTED_SCOPE_2, scope)
    }

    @Test
    fun `recipientScope with pk prefix normalizes to same scope`() {
        val scopeWithPrefix = PaykitV0Protocol.recipientScope("pk:$TEST_PUBKEY_2")
        assertEquals(EXPECTED_SCOPE_2, scopeWithPrefix)
    }

    @Test
    fun `recipientScope with uppercase normalizes to same scope`() {
        val scopeUppercase = PaykitV0Protocol.recipientScope(TEST_PUBKEY_1.uppercase())
        assertEquals(EXPECTED_SCOPE_1, scopeUppercase)
    }

    @Test
    fun `scope hash is deterministic`() {
        val scope1 = PaykitV0Protocol.recipientScope(TEST_PUBKEY_1)
        val scope2 = PaykitV0Protocol.recipientScope(TEST_PUBKEY_1)
        assertEquals(scope1, scope2)
        assertEquals(64, scope1.length)
    }

    @Test
    fun `scope hash differs for different pubkeys`() {
        val scope1 = PaykitV0Protocol.recipientScope(TEST_PUBKEY_1)
        val scope2 = PaykitV0Protocol.recipientScope(TEST_PUBKEY_2)
        assertTrue(scope1 != scope2)
    }

    @Test
    fun `subscriberScope is alias for recipientScope`() {
        val rScope = PaykitV0Protocol.recipientScope(TEST_PUBKEY_1)
        val sScope = PaykitV0Protocol.subscriberScope(TEST_PUBKEY_1)
        assertEquals(rScope, sScope)
    }

    // ============================================================================
    // Path Tests
    // ============================================================================

    @Test
    fun `paymentRequestPath has correct format`() {
        val path = PaykitV0Protocol.paymentRequestPath(TEST_PUBKEY_1, "req-123")
        assertTrue(path.startsWith("/pub/paykit.app/v0/requests/"))
        assertTrue(path.endsWith("/req-123"))
        // Should contain 64-char hex scope
        val parts = path.split("/")
        assertEquals(7, parts.size)
        assertEquals(64, parts[5].length)
    }

    @Test
    fun `paymentRequestsDir has correct format`() {
        val dir = PaykitV0Protocol.paymentRequestsDir(TEST_PUBKEY_1)
        assertTrue(dir.startsWith("/pub/paykit.app/v0/requests/"))
        assertTrue(dir.endsWith("/"))
    }

    @Test
    fun `subscriptionProposalPath has correct format`() {
        val path = PaykitV0Protocol.subscriptionProposalPath(TEST_PUBKEY_1, "prop-456")
        assertTrue(path.startsWith("/pub/paykit.app/v0/subscriptions/proposals/"))
        assertTrue(path.endsWith("/prop-456"))
        val parts = path.split("/")
        assertEquals(8, parts.size)
        assertEquals(64, parts[6].length)
    }

    @Test
    fun `subscriptionProposalsDir has correct format`() {
        val dir = PaykitV0Protocol.subscriptionProposalsDir(TEST_PUBKEY_1)
        assertTrue(dir.startsWith("/pub/paykit.app/v0/subscriptions/proposals/"))
        assertTrue(dir.endsWith("/"))
    }

    @Test
    fun `noiseEndpointPath returns fixed path`() {
        val path = PaykitV0Protocol.noiseEndpointPath()
        assertEquals("/pub/paykit.app/v0/noise", path)
    }

    @Test
    fun `secureHandoffPath has correct format`() {
        val path = PaykitV0Protocol.secureHandoffPath("handoff-789")
        assertEquals("/pub/paykit.app/v0/handoff/handoff-789", path)
    }

    @Test
    fun `paths are consistent for same pubkey`() {
        val path1 = PaykitV0Protocol.paymentRequestPath(TEST_PUBKEY_1, "req-1")
        val path2 = PaykitV0Protocol.paymentRequestPath(TEST_PUBKEY_1, "req-2")
        val scope1 = path1.split("/")[5]
        val scope2 = path2.split("/")[5]
        assertEquals(scope1, scope2)
    }

    @Test
    fun `paths differ for different pubkeys`() {
        val path1 = PaykitV0Protocol.paymentRequestPath(TEST_PUBKEY_1, "req-1")
        val path2 = PaykitV0Protocol.paymentRequestPath(TEST_PUBKEY_2, "req-1")
        val scope1 = path1.split("/")[5]
        val scope2 = path2.split("/")[5]
        assertTrue(scope1 != scope2)
    }

    // ============================================================================
    // AAD Tests
    // ============================================================================

    @Test
    fun `paymentRequestAad has correct format`() {
        val aad = PaykitV0Protocol.paymentRequestAad(TEST_PUBKEY_1, "req-123")
        assertTrue(aad.startsWith("paykit:v0:request:/pub/paykit.app/v0/requests/"))
        assertTrue(aad.endsWith(":req-123"))
    }

    @Test
    fun `subscriptionProposalAad has correct format`() {
        val aad = PaykitV0Protocol.subscriptionProposalAad(TEST_PUBKEY_1, "prop-456")
        assertTrue(aad.startsWith("paykit:v0:subscription_proposal:/pub/paykit.app/v0/subscriptions/proposals/"))
        assertTrue(aad.endsWith(":prop-456"))
    }

    @Test
    fun `secureHandoffAad has correct format`() {
        val aad = PaykitV0Protocol.secureHandoffAad(TEST_PUBKEY_1, "handoff-789")
        assertTrue(aad.startsWith("paykit:v0:handoff:"))
        assertTrue(aad.contains(TEST_PUBKEY_1))
        assertTrue(aad.contains("/pub/paykit.app/v0/handoff/handoff-789"))
        assertTrue(aad.endsWith(":handoff-789"))
    }

    @Test
    fun `buildAad produces correct format`() {
        val aad = PaykitV0Protocol.buildAad("custom", "/some/path", "id-123")
        assertEquals("paykit:v0:custom:/some/path:id-123", aad)
    }

    @Test
    fun `aad is deterministic`() {
        val aad1 = PaykitV0Protocol.paymentRequestAad(TEST_PUBKEY_1, "req-123")
        val aad2 = PaykitV0Protocol.paymentRequestAad(TEST_PUBKEY_1, "req-123")
        assertEquals(aad1, aad2)
    }

    @Test
    fun `aad differs for different ids`() {
        val aad1 = PaykitV0Protocol.paymentRequestAad(TEST_PUBKEY_1, "req-123")
        val aad2 = PaykitV0Protocol.paymentRequestAad(TEST_PUBKEY_1, "req-456")
        assertTrue(aad1 != aad2)
    }

    @Test
    fun `aad differs for different recipients`() {
        val aad1 = PaykitV0Protocol.paymentRequestAad(TEST_PUBKEY_1, "req-123")
        val aad2 = PaykitV0Protocol.paymentRequestAad(TEST_PUBKEY_2, "req-123")
        assertTrue(aad1 != aad2)
    }
}

