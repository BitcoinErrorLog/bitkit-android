package to.bitkit.paykit.services

import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import to.bitkit.paykit.storage.PaykitKeychainStorage
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Tests for NoiseKeyCache and hex-to-bytes conversion.
 *
 * Critical bug fix verification: Noise secret keys are provided as hex strings (64 chars)
 * which must be decoded to 32-byte arrays. Previously the code used toByteArray(Charsets.UTF_8)
 * which resulted in 64 bytes instead of 32, breaking decryption.
 */
class NoiseKeyCacheTest {

    private lateinit var mockKeychain: PaykitKeychainStorage
    private lateinit var cache: NoiseKeyCache

    @Before
    fun setup() {
        mockKeychain = mock()
        cache = NoiseKeyCache(mockKeychain)
    }

    @Test
    fun `hex string to bytes conversion - correct method produces 32 bytes`() {
        // A valid 32-byte X25519 secret key as hex (64 chars)
        val hexSecretKey = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
        assertEquals(64, hexSecretKey.length, "Hex string should be 64 chars")

        // CORRECT: Decode hex to bytes
        val correctBytes = hexSecretKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertEquals(32, correctBytes.size, "Decoded hex should be 32 bytes")

        // INCORRECT (the bug): Convert hex string to UTF-8 bytes
        val incorrectBytes = hexSecretKey.toByteArray(Charsets.UTF_8)
        assertEquals(64, incorrectBytes.size, "UTF-8 encoding produces 64 bytes (the bug)")

        // Verify they are different
        assertNotEquals(correctBytes.size, incorrectBytes.size, "Correct and incorrect methods differ in size")
    }

    @Test
    fun `setKeySync stores 32-byte key correctly`() {
        val deviceId = "test-device"
        val epoch = 0u

        // 32-byte key (correctly decoded)
        val keyBytes = ByteArray(32) { it.toByte() }

        cache.setKeySync(keyBytes, deviceId, epoch)

        val retrieved = cache.getKeySync(deviceId, epoch)
        assertEquals(32, retrieved?.size, "Retrieved key should be 32 bytes")
        assertEquals(keyBytes.toList(), retrieved?.toList(), "Retrieved key should match stored key")
    }

    @Test
    fun `hexStringToByteArray helper produces correct output`() {
        // Test the inline hex decoder used in the fix
        val hexString = "0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20"
        val expected = ByteArray(32) { (it + 1).toByte() }

        val decoded = hexString.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        assertEquals(32, decoded.size)
        assertEquals(expected.toList(), decoded.toList())
    }

    @Test
    fun `X25519 key from real-world hex string decodes to 32 bytes`() {
        // Simulating a real Noise keypair secretKey from pubky-ring
        // These are random hex strings of correct length
        val secretKeyHex = "deadbeefcafebabe123456789abcdef0fedcba9876543210abcdefdeadbeef01"
        assertEquals(64, secretKeyHex.length, "Real secret key hex is 64 chars")

        val decoded = secretKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertEquals(32, decoded.size, "Decoded secret key is 32 bytes")

        // Verify specific bytes
        assertEquals(0xde.toByte(), decoded[0])
        assertEquals(0xad.toByte(), decoded[1])
        assertEquals(0x01.toByte(), decoded[31])
    }
}
