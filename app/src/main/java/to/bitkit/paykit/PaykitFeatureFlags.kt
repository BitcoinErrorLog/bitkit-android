package to.bitkit.paykit

import android.content.Context
import android.content.SharedPreferences
import to.bitkit.utils.Logger

/**
 * Feature flags for Paykit integration.
 *
 * Use these flags to control the rollout of Paykit features
 * and enable quick rollback if issues arise.
 */
object PaykitFeatureFlags {

    private const val TAG = "PaykitFeatureFlags"
    private const val PREFS_NAME = "paykit_feature_flags"

    private const val ENABLED_KEY = "paykit_enabled"
    private const val LIGHTNING_ENABLED_KEY = "paykit_lightning_enabled"
    private const val ONCHAIN_ENABLED_KEY = "paykit_onchain_enabled"
    private const val RECEIPT_STORAGE_ENABLED_KEY = "paykit_receipt_storage_enabled"

    private var prefs: SharedPreferences? = null

    /**
     * Initialize feature flags with application context.
     * Call this during Application.onCreate().
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        setDefaults()
    }

    // MARK: - Main Feature Flag

    /** Whether Paykit integration is enabled. */
    var isEnabled: Boolean
        get() = prefs?.getBoolean(ENABLED_KEY, false) ?: false
        set(value) = prefs?.edit()?.putBoolean(ENABLED_KEY, value)?.apply() ?: Unit

    /** Whether Lightning payments via Paykit are enabled. */
    var isLightningEnabled: Boolean
        get() = prefs?.getBoolean(LIGHTNING_ENABLED_KEY, true) ?: true
        set(value) = prefs?.edit()?.putBoolean(LIGHTNING_ENABLED_KEY, value)?.apply() ?: Unit

    /** Whether on-chain payments via Paykit are enabled. */
    var isOnchainEnabled: Boolean
        get() = prefs?.getBoolean(ONCHAIN_ENABLED_KEY, true) ?: true
        set(value) = prefs?.edit()?.putBoolean(ONCHAIN_ENABLED_KEY, value)?.apply() ?: Unit

    /** Whether receipt storage is enabled. */
    var isReceiptStorageEnabled: Boolean
        get() = prefs?.getBoolean(RECEIPT_STORAGE_ENABLED_KEY, true) ?: true
        set(value) = prefs?.edit()?.putBoolean(RECEIPT_STORAGE_ENABLED_KEY, value)?.apply() ?: Unit

    // MARK: - Remote Config

    /**
     * Update flags from remote config.
     * Call this during app startup to sync with server-side configuration.
     *
     * @param config Map from remote config service (Firebase Remote Config, etc.)
     */
    fun updateFromRemoteConfig(config: Map<String, Any>) {
        (config["paykit_enabled"] as? Boolean)?.let { isEnabled = it }
        (config["paykit_lightning_enabled"] as? Boolean)?.let { isLightningEnabled = it }
        (config["paykit_onchain_enabled"] as? Boolean)?.let { isOnchainEnabled = it }
        (config["paykit_receipt_storage_enabled"] as? Boolean)?.let { isReceiptStorageEnabled = it }
    }

    // MARK: - Defaults

    /** Set default values for all flags. */
    private fun setDefaults() {
        prefs?.let { p ->
            if (!p.contains(ENABLED_KEY)) {
                p.edit()
                    .putBoolean(ENABLED_KEY, false) // Disabled by default until ready
                    .putBoolean(LIGHTNING_ENABLED_KEY, true)
                    .putBoolean(ONCHAIN_ENABLED_KEY, true)
                    .putBoolean(RECEIPT_STORAGE_ENABLED_KEY, true)
                    .apply()
            }
        }
    }

    // MARK: - Rollback

    /**
     * Emergency rollback - disable all Paykit features.
     * Call this if critical issues are detected.
     */
    fun emergencyRollback() {
        isEnabled = false
        Logger.warn("Paykit emergency rollback triggered", context = TAG)

        // Reset manager state
        PaykitManager.getSharedInstance()?.reset()
    }
}

/**
 * Manages Paykit configuration for production deployment.
 */
object PaykitConfigManager {

    private const val TAG = "PaykitConfigManager"

    // MARK: - Environment

    /** Current environment configuration. */
    val environment: PaykitEnvironment
        get() = if (to.bitkit.BuildConfig.DEBUG) {
            PaykitEnvironment.DEVELOPMENT
        } else {
            PaykitEnvironment.PRODUCTION
        }

    // MARK: - Logging

    /** Log level for Paykit operations. */
    var logLevel: PaykitLogLevel = PaykitLogLevel.INFO

    /** Whether to log payment details (disable in production for privacy). */
    val logPaymentDetails: Boolean
        get() = to.bitkit.BuildConfig.DEBUG

    // MARK: - Timeouts

    /** Default payment timeout in milliseconds. */
    var defaultPaymentTimeoutMs: Long = 60_000L

    /** Lightning payment polling interval in milliseconds. */
    var lightningPollingIntervalMs: Long = 500L

    // MARK: - Retry Configuration

    /** Maximum number of retry attempts for failed payments. */
    var maxRetryAttempts: Int = 3

    /** Base delay between retries in milliseconds. */
    var retryBaseDelayMs: Long = 1000L

    // MARK: - Monitoring

    /**
     * Error reporting callback.
     * Set this to integrate with your error monitoring service.
     */
    var errorReporter: ((Throwable, Map<String, Any>?) -> Unit)? = null

    /** Report an error to the configured monitoring service. */
    fun reportError(error: Throwable, context: Map<String, Any>? = null) {
        errorReporter?.invoke(error, context)
    }
}

/** Paykit environment configuration. */
enum class PaykitEnvironment {
    DEVELOPMENT,
    STAGING,
    PRODUCTION
}

/** Log level for Paykit operations. */
enum class PaykitLogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    NONE
}
