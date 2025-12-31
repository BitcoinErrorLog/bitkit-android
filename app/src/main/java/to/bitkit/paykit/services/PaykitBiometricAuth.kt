package to.bitkit.paykit.services

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import to.bitkit.App
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Biometric authentication for Paykit payment operations.
 *
 * Provides suspending functions to authenticate users before executing
 * high-value payments or spending limit changes.
 *
 * ## Security Behavior
 *
 * When the FragmentActivity is unavailable (e.g., background operation),
 * the behavior depends on `requireBiometricForPayment`:
 * - `true` (default): Returns failure, preventing the payment.
 * - `false`: Allows the payment to proceed without authentication.
 *
 * Set `requireBiometricForPayment = false` only for automated payments
 * where the user has explicitly opted-in (e.g., auto-pay with spending limits).
 *
 * ## Background Payment Policy
 *
 * Background workers (e.g., [SubscriptionCheckWorker]) cannot show biometric prompts.
 * For these scenarios:
 *
 * 1. **With spending limits**: Use `allowWithoutPrompt = true` since the user has
 *    already authorized the spending limit as a form of pre-approval.
 * 2. **Without spending limits**: Do NOT use `allowWithoutPrompt = true`. Instead,
 *    queue the payment and notify the user to open the app.
 *
 * Example for background auto-pay:
 * ```kotlin
 * // User has configured a spending limit for this peer - implicit approval
 * biometricAuth.authenticateForPayment(
 *     amountSats = amount,
 *     allowWithoutPrompt = true // Safe: spending limit is the authorization
 * )
 * ```
 */
@Singleton
class PaykitBiometricAuth @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "PaykitBiometricAuth"
    }

    /**
     * Whether biometric authentication is strictly required for payments.
     * When true, payments will fail if biometric prompt cannot be shown.
     * When false, payments may proceed without authentication in certain edge cases.
     *
     * Default is true for security. Set to false only for automated payments
     * where the user has explicitly opted-in via spending limits/auto-pay.
     */
    var requireBiometricForPayment: Boolean = true

    /**
     * Check if biometric authentication is available on this device.
     */
    fun isAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Authenticate user before a payment operation.
     *
     * @param amountSats Payment amount in satoshis
     * @param description Optional payment description
     * @param allowWithoutPrompt If true, allows payment to proceed when biometric prompt
     *        cannot be shown (e.g., background operation). Use with caution.
     * @return Result indicating success or failure
     */
    suspend fun authenticateForPayment(
        amountSats: ULong,
        description: String? = null,
        allowWithoutPrompt: Boolean = false,
    ): Result<Unit> {
        if (!isAvailable()) {
            Logger.debug("Biometric not available, skipping authentication", context = TAG)
            return Result.success(Unit)
        }

        val activity = App.currentActivity?.value as? FragmentActivity
        if (activity == null) {
            val shouldAllowPayment = allowWithoutPrompt || !requireBiometricForPayment
            if (shouldAllowPayment) {
                Logger.warn("No FragmentActivity available - allowing payment (allowWithoutPrompt=$allowWithoutPrompt)", context = TAG)
                return Result.success(Unit)
            } else {
                Logger.warn("No FragmentActivity available - blocking payment (requireBiometricForPayment=true)", context = TAG)
                return Result.failure(BiometricAuthError.Failed("Authentication required but UI unavailable"))
            }
        }

        val title = "Confirm Payment"
        val subtitle = formatAmount(amountSats)
        val desc = description ?: "Authenticate to confirm this payment"

        return authenticate(activity, title, subtitle, desc)
    }

    /**
     * Authenticate user before changing spending limits.
     *
     * @return Result indicating success or failure
     */
    suspend fun authenticateForSpendingLimitChange(): Result<Unit> {
        if (!isAvailable()) {
            return Result.success(Unit)
        }

        val activity = App.currentActivity?.value as? FragmentActivity
            ?: return Result.success(Unit)

        return authenticate(
            activity = activity,
            title = "Change Spending Limit",
            subtitle = null,
            description = "Authenticate to modify your spending limits",
        )
    }

    private suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        description: String,
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(activity)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { subtitle?.let { setSubtitle(it) } }
            .setDescription(description)
            .setNegativeButtonText("Cancel")
            .setConfirmationRequired(true)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Logger.debug("Biometric auth succeeded for payment", context = TAG)
                if (continuation.isActive) {
                    continuation.resume(Result.success(Unit))
                }
            }

            override fun onAuthenticationFailed() {
                Logger.debug("Biometric auth failed", context = TAG)
                // Don't resume here - wait for error or success
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Logger.debug("Biometric auth error: $errorCode - $errString", context = TAG)
                if (continuation.isActive) {
                    val error = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> BiometricAuthError.Cancelled
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricAuthError.LockedOut
                        else -> BiometricAuthError.Failed(errString.toString())
                    }
                    continuation.resume(Result.failure(error))
                }
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        continuation.invokeOnCancellation {
            biometricPrompt.cancelAuthentication()
        }

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Logger.error("Failed to launch biometric prompt", e, context = TAG)
            if (continuation.isActive) {
                continuation.resume(Result.failure(BiometricAuthError.Failed(e.message ?: "Unknown error")))
            }
        }
    }

    private fun formatAmount(sats: ULong): String {
        return when {
            sats >= 100_000_000uL -> "${sats / 100_000_000uL}.${(sats % 100_000_000uL) / 1_000_000uL} BTC"
            sats >= 1_000_000uL -> "${sats / 1_000_000uL}.${(sats % 1_000_000uL) / 10_000uL}M sats"
            sats >= 1_000uL -> "${sats / 1_000uL}.${(sats % 1_000uL) / 10uL}K sats"
            else -> "$sats sats"
        }
    }
}

/**
 * Errors that can occur during biometric authentication.
 */
sealed class BiometricAuthError(message: String) : Exception(message) {
    object Cancelled : BiometricAuthError("User cancelled authentication")
    object LockedOut : BiometricAuthError("Biometric authentication locked out")
    class Failed(message: String) : BiometricAuthError(message)
}

