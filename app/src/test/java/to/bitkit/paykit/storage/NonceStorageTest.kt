package to.bitkit.paykit.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NonceStorageTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var storage: NonceStorage

    // In-memory storage for testing
    private val storedNonces = mutableMapOf<String, Long>()

    @Before
    fun setup() {
        context = mock()
        prefs = mock()
        editor = mock()

        // Mock SharedPreferences behavior
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(prefs.all).thenAnswer { storedNonces.toMap() }

        // Mock contains()
        whenever(prefs.contains(any())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            storedNonces.containsKey(key)
        }

        // Mock putLong in editor (returns editor for chaining)
        whenever(editor.putLong(any(), any())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val value = invocation.getArgument<Long>(1)
            storedNonces[key] = value
            editor
        }

        // Mock remove in editor (returns editor for chaining)
        whenever(editor.remove(any())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            storedNonces.remove(key)
            editor
        }

        // Mock apply() - no-op but needs to be defined
        doAnswer { }.whenever(editor).apply()

        storage = NonceStorage(context)
        storedNonces.clear()
    }

    @Test
    fun `fresh nonce is accepted`() = runTest {
        val nonce = "abc123def456abc123def456abc123def456abc123def456abc123def456abcd"
        val expiresAt = System.currentTimeMillis() / 1000 + 3600

        val result = storage.checkAndMark(nonce, expiresAt)

        assertTrue(result, "Fresh nonce should be accepted")
    }

    @Test
    fun `duplicate nonce is rejected`() = runTest {
        val nonce = "abc123def456abc123def456abc123def456abc123def456abc123def456abcd"
        val expiresAt = System.currentTimeMillis() / 1000 + 3600

        // First use - should succeed
        val first = storage.checkAndMark(nonce, expiresAt)
        assertTrue(first, "First use should succeed")

        // Second use - should fail (replay attack)
        val second = storage.checkAndMark(nonce, expiresAt)
        assertFalse(second, "Duplicate nonce should be rejected")
    }

    @Test
    fun `different nonces are both accepted`() = runTest {
        val nonce1 = "1111111111111111111111111111111111111111111111111111111111111111"
        val nonce2 = "2222222222222222222222222222222222222222222222222222222222222222"
        val expiresAt = System.currentTimeMillis() / 1000 + 3600

        val first = storage.checkAndMark(nonce1, expiresAt)
        val second = storage.checkAndMark(nonce2, expiresAt)

        assertTrue(first, "First nonce should be accepted")
        assertTrue(second, "Second nonce should be accepted")
    }

    @Test
    fun `isUsed returns true for used nonce`() = runTest {
        val nonce = "abc123def456abc123def456abc123def456abc123def456abc123def456abcd"
        val expiresAt = System.currentTimeMillis() / 1000 + 3600

        assertFalse(storage.isUsed(nonce), "Should not be used initially")

        storage.checkAndMark(nonce, expiresAt)

        assertTrue(storage.isUsed(nonce), "Should be used after marking")
    }

    @Test
    fun `cleanupExpired removes old nonces`() = runTest {
        val now = System.currentTimeMillis() / 1000

        // Add an old nonce (expired 1000 seconds ago)
        val oldNonce = "old_nonce_1111111111111111111111111111111111111111111111111111"
        storedNonces["nonce_$oldNonce"] = now - 1000

        // Add a recent nonce (expires in 1000 seconds)
        val recentNonce = "recent_nonce_2222222222222222222222222222222222222222222222222"
        storedNonces["nonce_$recentNonce"] = now + 1000

        assertEquals(2, storage.count())

        val removed = storage.cleanupExpired(now)

        assertEquals(1, removed, "Should remove 1 expired nonce")
        assertFalse(storedNonces.containsKey("nonce_$oldNonce"), "Old nonce should be removed")
        assertTrue(storedNonces.containsKey("nonce_$recentNonce"), "Recent nonce should remain")
    }

    @Test
    fun `count returns correct number of nonces`() = runTest {
        assertEquals(0, storage.count(), "Should start empty")

        val nonce1 = "1111111111111111111111111111111111111111111111111111111111111111"
        val nonce2 = "2222222222222222222222222222222222222222222222222222222222222222"
        val expiresAt = System.currentTimeMillis() / 1000 + 3600

        storage.checkAndMark(nonce1, expiresAt)
        assertEquals(1, storage.count())

        storage.checkAndMark(nonce2, expiresAt)
        assertEquals(2, storage.count())
    }
}
