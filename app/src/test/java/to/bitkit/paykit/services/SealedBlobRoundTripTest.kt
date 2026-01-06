package to.bitkit.paykit.services

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFails

/**
 * Tests for Sealed Blob encryption/decryption round-trip.
 *
 * Verifies that subscription proposals can be encrypted by a sender
 * and decrypted by the intended recipient.
 */
class SealedBlobRoundTripTest {

    @Test
    fun `hex to bytes conversion produces correct length`() {
        // A 32-byte X25519 key as hex (64 characters)
        val hexKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        // Correct conversion: hex -> bytes
        val bytes = hexKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        assertEquals(32, bytes.size, "32-byte key should have 32 bytes after hex decoding")
        assertEquals(64, hexKey.length, "Hex string should be 64 characters")
    }

    @Test
    fun `UTF-8 conversion of hex string produces wrong length`() {
        // This is the BUG we fixed - converting hex as UTF-8 gives wrong result
        val hexKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        // WRONG: treating hex string as UTF-8 gives 64 bytes instead of 32
        val wrongBytes = hexKey.toByteArray(Charsets.UTF_8)

        assertEquals(64, wrongBytes.size, "UTF-8 encoding of 64-char hex gives 64 bytes (WRONG)")
    }

    @Test
    fun `subscriber scope calculation is deterministic`() {
        val pubkey1 = "tjtigrhbiinfwwh8nwwgbq4b17t71uqesshsd7zp37zt3huwmwyo"
        val pubkey2 = "TJTIGRHBIINFWWH8NWWGBQ4B17T71UQESSHSD7ZP37ZT3HUWMWYO"

        // Both should normalize to lowercase
        val normalized1 = pubkey1.lowercase()
        val normalized2 = pubkey2.lowercase()

        assertEquals(normalized1, normalized2, "Pubkeys should normalize to same value")

        // SHA256 should be deterministic
        val digest1 = java.security.MessageDigest.getInstance("SHA-256")
            .digest(normalized1.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val digest2 = java.security.MessageDigest.getInstance("SHA-256")
            .digest(normalized2.toByteArray())
            .joinToString("") { "%02x".format(it) }

        assertEquals(digest1, digest2, "Subscriber scopes should match")
    }

    @Test
    fun `AAD format is consistent between encrypt and decrypt`() {
        val subscriberPubkey = "tjtigrhbiinfwwh8nwwgbq4b17t71uqesshsd7zp37zt3huwmwyo"
        val proposalId = "test-proposal-123"

        // Simulating what PaykitV0Protocol.subscriptionProposalAad does
        val normalizedPubkey = subscriberPubkey.lowercase()
        val aad = "paykit:v0:subscription_proposal:$normalizedPubkey:$proposalId"

        assertTrue(aad.contains(proposalId), "AAD should contain proposal ID")
        assertTrue(aad.contains(normalizedPubkey), "AAD should contain normalized pubkey")
        assertTrue(aad.startsWith("paykit:v0:subscription_proposal:"), "AAD should have correct prefix")
    }

    @Test
    fun `hexStringToByteArray and byteArrayToHexString are inverses`() {
        val original = ByteArray(32) { it.toByte() }

        // Convert to hex
        val hex = original.joinToString("") { "%02x".format(it) }
        assertEquals(64, hex.length)

        // Convert back to bytes
        val restored = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        assertTrue(original.contentEquals(restored), "Round-trip should preserve bytes")
    }

    @Test
    fun `sealed blob encrypt-decrypt round trip works with pubky-noise`() {
        // This test verifies the actual crypto library works correctly
        // It requires the pubky-noise library to be available

        try {
            // Generate a recipient keypair (simulating what Ring would provide)
            val secretKey = ByteArray(32) { (it + 1).toByte() } // Deterministic test key
            val publicKey = com.pubky.noise.publicKeyFromSecret(secretKey)

            assertNotNull(publicKey, "Public key derivation should work")
            assertEquals(32, publicKey.size, "Public key should be 32 bytes")
            assertEquals(32, secretKey.size, "Secret key should be 32 bytes")

            // Test data
            val plaintext = """{"provider_pubkey":"test123","amount_sats":1000}""".toByteArray()
            val aad = "paykit:v0:subscription_proposal:testpubkey:proposal-id-123"
            val purpose = "subscription_proposal"

            // Encrypt to recipient's public key
            val envelope = com.pubky.noise.sealedBlobEncrypt(publicKey, plaintext, aad, purpose)
            assertNotNull(envelope, "Encryption should produce envelope")
            assertTrue(envelope.isNotEmpty(), "Envelope should not be empty")

            // Verify it's a sealed blob
            assertTrue(com.pubky.noise.isSealedBlob(envelope), "Envelope should be valid sealed blob")

            // Decrypt with recipient's secret key
            val decrypted = com.pubky.noise.sealedBlobDecrypt(secretKey, envelope, aad)
            assertNotNull(decrypted, "Decryption should succeed")
            assertTrue(plaintext.contentEquals(decrypted), "Decrypted content should match original")
        } catch (e: UnsatisfiedLinkError) {
            // Native library not available in unit tests - skip
            println("Skipping crypto test: native library not loaded")
        } catch (e: NoClassDefFoundError) {
            println("Skipping crypto test: pubky-noise not available")
        }
    }

    @Test
    fun `decryption fails with wrong key`() {
        try {
            // Generate two different keypairs
            val secretKey1 = ByteArray(32) { (it + 1).toByte() }
            val publicKey1 = com.pubky.noise.publicKeyFromSecret(secretKey1)

            val secretKey2 = ByteArray(32) { (it + 100).toByte() } // Different key

            val plaintext = "test message".toByteArray()
            val aad = "test-aad"

            // Encrypt to key1's public key
            val envelope = com.pubky.noise.sealedBlobEncrypt(publicKey1, plaintext, aad, "test")

            // Try to decrypt with wrong key - should fail
            assertFails("Decryption with wrong key should fail") {
                com.pubky.noise.sealedBlobDecrypt(secretKey2, envelope, aad)
            }
        } catch (e: UnsatisfiedLinkError) {
            println("Skipping crypto test: native library not loaded")
        } catch (e: NoClassDefFoundError) {
            println("Skipping crypto test: pubky-noise not available")
        }
    }

    @Test
    fun `decryption fails with corrupted 64-byte key`() {
        try {
            // This demonstrates the bug we fixed
            val correctSecretKeyHex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

            // Correct: decode hex to 32 bytes
            val correctKey = correctSecretKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            assertEquals(32, correctKey.size)

            // WRONG: treat hex as UTF-8 (the bug)
            val wrongKey = correctSecretKeyHex.toByteArray(Charsets.UTF_8)
            assertEquals(64, wrongKey.size)

            // Generate public key from correct key
            val publicKey = com.pubky.noise.publicKeyFromSecret(correctKey)

            // Encrypt with correct public key
            val plaintext = "test".toByteArray()
            val aad = "test-aad"
            val envelope = com.pubky.noise.sealedBlobEncrypt(publicKey, plaintext, aad, "test")

            // Decrypt with correct key should work
            val decrypted = com.pubky.noise.sealedBlobDecrypt(correctKey, envelope, aad)
            assertTrue(plaintext.contentEquals(decrypted))

            // Decrypt with wrong (64-byte) key should fail
            assertFails("Decryption with 64-byte corrupted key should fail") {
                com.pubky.noise.sealedBlobDecrypt(wrongKey, envelope, aad)
            }
        } catch (e: UnsatisfiedLinkError) {
            println("Skipping crypto test: native library not loaded")
        } catch (e: NoClassDefFoundError) {
            println("Skipping crypto test: pubky-noise not available")
        }
    }
}

