package to.bitkit.paykit.services

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Unit tests for PaykitBiometricAuth.
 *
 * Note: Most biometric functionality requires Android framework components
 * (BiometricManager, BiometricPrompt, FragmentActivity) which cannot be easily
 * unit tested. These tests cover the error types and related logic that can be
 * tested without Android dependencies.
 *
 * For full biometric testing, use instrumentation tests on a real device.
 */
class PaykitBiometricAuthTest {

    @Test
    fun `BiometricAuthError Cancelled has correct message`() {
        val error = BiometricAuthError.Cancelled
        assertEquals("User cancelled authentication", error.message)
    }

    @Test
    fun `BiometricAuthError LockedOut has correct message`() {
        val error = BiometricAuthError.LockedOut
        assertEquals("Biometric authentication locked out", error.message)
    }

    @Test
    fun `BiometricAuthError Failed preserves custom message`() {
        val customMessage = "Fingerprint sensor unavailable"
        val error = BiometricAuthError.Failed(customMessage)
        assertEquals(customMessage, error.message)
    }

    @Test
    fun `BiometricAuthError types are distinct`() {
        val cancelled = BiometricAuthError.Cancelled
        val lockedOut = BiometricAuthError.LockedOut
        val failed = BiometricAuthError.Failed("test")

        assertIs<BiometricAuthError.Cancelled>(cancelled)
        assertIs<BiometricAuthError.LockedOut>(lockedOut)
        assertIs<BiometricAuthError.Failed>(failed)
    }

    @Test
    fun `BiometricAuthError is throwable as Exception`() {
        val error: Exception = BiometricAuthError.Cancelled
        assertEquals("User cancelled authentication", error.message)
    }

    @Test
    fun `BiometricAuthError Failed can be pattern matched`() {
        val error: BiometricAuthError = BiometricAuthError.Failed("Custom error")

        val result = when (error) {
            BiometricAuthError.Cancelled -> "cancelled"
            BiometricAuthError.LockedOut -> "locked"
            is BiometricAuthError.Failed -> "failed: ${error.message}"
        }

        assertEquals("failed: Custom error", result)
    }
}

