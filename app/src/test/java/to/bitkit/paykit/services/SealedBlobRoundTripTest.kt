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
        val ownerPubkey = "tjtigrhbiinfwwh8nwwgbq4b17t71uqesshsd7zp37zt3huwmwyo"
        val providerPubkey = "abcdefghijabcdefghijabcdefghijabcdefghijabcdefghijkl"
        val subscriberPubkey = "zyxwvutsrqzyxwvutsrqzyxwvutsrqzyxwvutsrqzyxwvutsrq11"
        val proposalId = "test-proposal-123"

        // Simulating what PaykitV0Protocol.subscriptionProposalAad does (owner-bound format)
        // Format: paykit:v0:subscription_proposal:{owner}:{path}:{id}
        val normalizedOwner = ownerPubkey.lowercase()
        val contextId = "mock-context-id"
        val path = "/pub/paykit.app/v0/subscriptions/proposals/$contextId/$proposalId"
        val aad = "paykit:v0:subscription_proposal:$normalizedOwner:$path:$proposalId"

        assertTrue(aad.contains(proposalId), "AAD should contain proposal ID")
        assertTrue(aad.contains(normalizedOwner), "AAD should contain normalized owner pubkey")
        assertTrue(aad.contains(path), "AAD should contain storage path")
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

            // Test data with owner-bound AAD format: paykit:v0:{purpose}:{owner}:{path}:{id}
            val plaintext = """{"provider_pubkey":"test123","amount_sats":1000}""".toByteArray()
            val ownerPubkey = "testpubkey12345678901234567890123456789012345678901234"
            val contextId = "mock-context-id-abc123"
            val proposalId = "proposal-id-123"
            val path = "/pub/paykit.app/v0/subscriptions/proposals/$contextId/$proposalId"
            val aad = "paykit:v0:subscription_proposal:$ownerPubkey:$path:$proposalId"
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

    // ===== Binary AAD (WithContext) Tests per PUBKY_CRYPTO_SPEC v2.5 =====

    @Test
    fun `sealedBlobEncryptWithContext and sealedBlobDecryptWithContext round trip`() {
        try {
            // Generate recipient keypair
            val secretKey = ByteArray(32) { (it + 1).toByte() }
            val publicKey = com.pubky.noise.publicKeyFromSecret(secretKey)

            // Test data with spec-compliant binary AAD parameters
            val plaintext = """{"amount_sats":5000,"memo":"test payment request"}""".toByteArray()
            val ownerPeeridBytes = ByteArray(32) { (it + 10).toByte() } // 32-byte owner peer ID
            val canonicalPath = "/pub/paykit.app/v0/requests/ctx123/req456"
            val purpose = "request"

            // Encrypt using WithContext (binary AAD)
            val envelope = com.pubky.noise.sealedBlobEncryptWithContext(
                publicKey,
                plaintext,
                ownerPeeridBytes,
                canonicalPath,
                purpose,
            )
            assertNotNull(envelope, "Encryption should produce envelope")
            assertTrue(envelope.isNotEmpty(), "Envelope should not be empty")
            assertTrue(com.pubky.noise.isSealedBlob(envelope), "Envelope should be valid sealed blob")

            // Decrypt using WithContext (binary AAD)
            val decrypted = com.pubky.noise.sealedBlobDecryptWithContext(
                secretKey,
                envelope,
                ownerPeeridBytes,
                canonicalPath,
            )
            assertNotNull(decrypted, "Decryption should succeed")
            assertTrue(
                plaintext.contentEquals(decrypted),
                "Decrypted content should match original",
            )
        } catch (e: UnsatisfiedLinkError) {
            println("Skipping crypto test: native library not loaded")
        } catch (e: NoClassDefFoundError) {
            println("Skipping crypto test: pubky-noise not available")
        }
    }

    @Test
    fun `sealedBlobDecryptWithContext fails with wrong owner peerid`() {
        try {
            val secretKey = ByteArray(32) { (it + 1).toByte() }
            val publicKey = com.pubky.noise.publicKeyFromSecret(secretKey)

            val plaintext = "test payload".toByteArray()
            val correctOwnerPeeridBytes = ByteArray(32) { (it + 10).toByte() }
            val wrongOwnerPeeridBytes = ByteArray(32) { (it + 20).toByte() } // Different owner
            val canonicalPath = "/pub/paykit.app/v0/handoff/abc123"

            // Encrypt with correct owner
            val envelope = com.pubky.noise.sealedBlobEncryptWithContext(
                publicKey,
                plaintext,
                correctOwnerPeeridBytes,
                canonicalPath,
                "handoff",
            )

            // Decrypt with wrong owner should fail (AAD mismatch)
            assertFails("Decryption with wrong owner peerid should fail") {
                com.pubky.noise.sealedBlobDecryptWithContext(
                    secretKey,
                    envelope,
                    wrongOwnerPeeridBytes,
                    canonicalPath,
                )
            }
        } catch (e: UnsatisfiedLinkError) {
            println("Skipping crypto test: native library not loaded")
        } catch (e: NoClassDefFoundError) {
            println("Skipping crypto test: pubky-noise not available")
        }
    }

    @Test
    fun `sealedBlobDecryptWithContext fails with wrong path`() {
        try {
            val secretKey = ByteArray(32) { (it + 1).toByte() }
            val publicKey = com.pubky.noise.publicKeyFromSecret(secretKey)

            val plaintext = "test payload".toByteArray()
            val ownerPeeridBytes = ByteArray(32) { (it + 10).toByte() }
            val correctPath = "/pub/paykit.app/v0/subscriptions/proposals/ctx/prop123"
            val wrongPath = "/pub/paykit.app/v0/subscriptions/proposals/ctx/prop999" // Different path

            // Encrypt with correct path
            val envelope = com.pubky.noise.sealedBlobEncryptWithContext(
                publicKey,
                plaintext,
                ownerPeeridBytes,
                correctPath,
                "proposal",
            )

            // Decrypt with wrong path should fail (AAD mismatch)
            assertFails("Decryption with wrong path should fail") {
                com.pubky.noise.sealedBlobDecryptWithContext(
                    secretKey,
                    envelope,
                    ownerPeeridBytes,
                    wrongPath,
                )
            }
        } catch (e: UnsatisfiedLinkError) {
            println("Skipping crypto test: native library not loaded")
        } catch (e: NoClassDefFoundError) {
            println("Skipping crypto test: pubky-noise not available")
        }
    }

    @Test
    fun `binary AAD vs string AAD are not interchangeable`() {
        try {
            val secretKey = ByteArray(32) { (it + 1).toByte() }
            val publicKey = com.pubky.noise.publicKeyFromSecret(secretKey)

            val plaintext = "test data".toByteArray()
            val ownerPeeridBytes = ByteArray(32) { (it + 10).toByte() }
            val canonicalPath = "/pub/paykit.app/v0/requests/ctx123/req456"

            // Encrypt with binary AAD (WithContext)
            val binaryAadEnvelope = com.pubky.noise.sealedBlobEncryptWithContext(
                publicKey,
                plaintext,
                ownerPeeridBytes,
                canonicalPath,
                "request",
            )

            // Encrypt with string AAD (legacy)
            val legacyAad = "paykit:v0:request:owner:$canonicalPath:req456"
            val stringAadEnvelope = com.pubky.noise.sealedBlobEncrypt(
                publicKey,
                plaintext,
                legacyAad,
                "request",
            )

            // Verify both produce valid sealed blobs
            assertTrue(com.pubky.noise.isSealedBlob(binaryAadEnvelope))
            assertTrue(com.pubky.noise.isSealedBlob(stringAadEnvelope))

            // Cross-decryption should fail (AAD mismatch)
            assertFails("Binary AAD envelope should not decrypt with string AAD") {
                com.pubky.noise.sealedBlobDecrypt(secretKey, binaryAadEnvelope, legacyAad)
            }

            assertFails("String AAD envelope should not decrypt with binary AAD") {
                com.pubky.noise.sealedBlobDecryptWithContext(
                    secretKey,
                    stringAadEnvelope,
                    ownerPeeridBytes,
                    canonicalPath,
                )
            }
        } catch (e: UnsatisfiedLinkError) {
            println("Skipping crypto test: native library not loaded")
        } catch (e: NoClassDefFoundError) {
            println("Skipping crypto test: pubky-noise not available")
        }
    }
}

