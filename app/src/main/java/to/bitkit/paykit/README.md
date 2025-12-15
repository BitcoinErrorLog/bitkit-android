# Paykit Integration for Bitkit Android

This module integrates the Paykit payment coordination protocol with Bitkit Android.

## Overview

Paykit enables Bitkit to execute payments through a standardized protocol that supports:
- Lightning Network payments
- On-chain Bitcoin transactions
- Payment discovery and routing
- Receipt generation and proof verification

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Bitkit Android App                       │
├─────────────────────────────────────────────────────────────┤
│  PaykitPaymentService                                        │
│  - High-level payment API                                    │
│  - Payment type detection                                    │
│  - StateFlow for UI observation                              │
│  - Receipt management                                        │
├─────────────────────────────────────────────────────────────┤
│  PaykitManager                                               │
│  - Client lifecycle management                               │
│  - Executor registration                                     │
│  - Network configuration                                     │
│  - Dagger/Hilt @Singleton                                   │
├─────────────────────────────────────────────────────────────┤
│  Executors                                                   │
│  ├── BitkitBitcoinExecutor (onchain payments)               │
│  └── BitkitLightningExecutor (Lightning payments)           │
├─────────────────────────────────────────────────────────────┤
│  LightningRepo / CoreService (Bitkit)                       │
└─────────────────────────────────────────────────────────────┘
```

## Setup

### Prerequisites

1. PaykitMobile UniFFI bindings must be generated and linked
2. Native libraries in `jniLibs/` directory
3. Bitkit wallet must be initialized
4. Lightning node must be running

### Gradle Configuration

Add native libraries to `app/src/main/jniLibs/`:
```
jniLibs/
├── arm64-v8a/
│   └── libpaykit_mobile.so
├── armeabi-v7a/
│   └── libpaykit_mobile.so
└── x86_64/
    └── libpaykit_mobile.so
```

### Initialization

```kotlin
// During app startup, after wallet is ready
lifecycleScope.launch {
    try {
        PaykitIntegrationHelper.setup(lightningRepo)
    } catch (e: Exception) {
        Logger.error("Paykit setup failed", e)
    }
}

// Or with callback
PaykitIntegrationHelper.setupAsync(lightningRepo) { result ->
    result.onSuccess { Logger.info("Paykit ready") }
    result.onFailure { Logger.error("Paykit setup failed", it) }
}
```

### Making Payments

```kotlin
// Using the high-level service
val service = PaykitPaymentService.getInstance()

// Lightning payment
val result = service.payLightning(lightningRepo, "lnbc10u1p0...", amountSats = null)

// On-chain payment
val result = service.payOnchain(lightningRepo, "bc1q...", amountSats = 50000uL, feeRate = 10.0)

// Check result
if (result.success) {
    println("Payment succeeded: ${result.receipt.id}")
} else {
    println("Payment failed: ${result.error?.userMessage}")
}
```

### Observing Payment State

```kotlin
// In ViewModel
val paymentState = PaykitPaymentService.getInstance().paymentState

// In Composable
val state by paymentState.collectAsState()

when (state) {
    is PaykitPaymentState.Idle -> { /* Show payment form */ }
    is PaykitPaymentState.Processing -> { /* Show loading */ }
    is PaykitPaymentState.Succeeded -> { /* Show success */ }
    is PaykitPaymentState.Failed -> { /* Show error */ }
}
```

## File Structure

```
app/src/main/java/to/bitkit/paykit/
├── PaykitManager.kt              # Client lifecycle management
├── PaykitIntegrationHelper.kt    # Convenience setup methods
├── PaykitException.kt            # Exception types (in PaykitManager.kt)
├── executors/
│   ├── BitkitBitcoinExecutor.kt  # On-chain payment execution
│   └── BitkitLightningExecutor.kt # Lightning payment execution
├── services/
│   └── PaykitPaymentService.kt   # High-level payment API
└── README.md                     # This file
```

## Configuration

### Network Configuration

Network is automatically mapped from `Env.network`:

| Bitkit Network | Paykit Bitcoin | Paykit Lightning |
|----------------|----------------|------------------|
| `BITCOIN`      | `MAINNET`      | `MAINNET`        |
| `TESTNET`      | `TESTNET`      | `TESTNET`        |
| `REGTEST`      | `REGTEST`      | `REGTEST`        |
| `SIGNET`       | `TESTNET`      | `TESTNET`        |

### Timeout Configuration

```kotlin
// Default: 60,000 ms (60 seconds)
PaykitPaymentService.getInstance().paymentTimeoutMs = 120_000L
```

### Receipt Storage

```kotlin
// Disable automatic receipt storage
PaykitPaymentService.getInstance().autoStoreReceipts = false
```

## Error Handling

All errors are mapped to user-friendly messages:

```kotlin
when (val error = result.error) {
    is PaykitPaymentError.NotInitialized -> showToast(error.userMessage)
    is PaykitPaymentError.PaymentFailed -> showToast(error.userMessage)
    // etc.
}
```

| Error | User Message |
|-------|--------------|
| `NotInitialized` | "Please wait for the app to initialize" |
| `InvalidRecipient` | "Please check the payment address or invoice" |
| `AmountRequired` | "Please enter an amount" |
| `InsufficientFunds` | "You don't have enough funds for this payment" |
| `PaymentFailed` | "Payment could not be completed. Please try again." |
| `Timeout` | "Payment is taking longer than expected" |

## Dependency Injection

PaykitManager is Hilt-compatible:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object PaykitModule {
    @Provides
    @Singleton
    fun providePaykitManager(): PaykitManager = PaykitManager()
}
```

## Testing

Run unit tests:
```bash
./gradlew testDebugUnitTest --tests "to.bitkit.paykit.*"
```

Test files:
- `app/src/test/java/to/bitkit/paykit/PaykitManagerTest.kt`
- `app/src/test/java/to/bitkit/paykit/executors/BitkitBitcoinExecutorTest.kt`
- `app/src/test/java/to/bitkit/paykit/executors/BitkitLightningExecutorTest.kt`
- `app/src/test/java/to/bitkit/paykit/services/PaykitPaymentServiceTest.kt`

## Production Checklist

- [ ] Build PaykitMobile for all Android targets (arm64-v8a, armeabi-v7a, x86_64)
- [ ] Add `.so` files to `jniLibs/`
- [ ] Add generated Kotlin bindings to source set
- [ ] Uncomment FFI binding code (search for `// TODO: Uncomment`)
- [ ] Test on testnet before mainnet
- [ ] Configure error monitoring (Firebase Crashlytics)
- [ ] Enable feature flag for gradual rollout
- [ ] Configure ProGuard rules if using R8

## Rollback Plan

If issues arise in production:

1. **Immediate**: Disable Paykit feature flag via remote config
2. **App Update**: Push update reverting to previous version
3. **Data**: Receipt data is stored locally and independent of Paykit

## Troubleshooting

### "PaykitManager has not been initialized"
Ensure `PaykitIntegrationHelper.setup(lightningRepo)` is called during app startup.

### "Payment timed out"
- Check network connectivity
- Verify Lightning node is synced
- Increase `paymentTimeoutMs` if needed

### "Payment failed"
- Check wallet balance
- Verify recipient address/invoice is valid
- Check Lightning channel capacity

### Native library not found
- Verify `.so` files are in correct `jniLibs/` subdirectories
- Check ABI filters in `build.gradle.kts`

## ProGuard Rules

If using R8/ProGuard, add these rules:

```proguard
# Paykit Mobile
-keep class com.paykit.mobile.** { *; }
-keep class uniffi.** { *; }
```

## API Reference

See inline KDoc in source files for detailed API reference.

## Phase 6: Production Hardening

### Logging & Monitoring

**PaykitLogger** provides structured logging with configurable log levels:

```kotlin
import to.bitkit.paykit.PaykitLogger
import to.bitkit.paykit.paykitInfo
import to.bitkit.paykit.paykitError

// Configure log level
PaykitConfigManager.logLevel = PaykitLogLevel.INFO  // DEBUG, INFO, WARNING, ERROR, NONE

// Basic logging
paykitInfo("Payment initiated", category = "payment")
paykitError("Payment failed", error = error, context = mapOf("invoice" to invoice))

// Payment flow logging
PaykitLogger.logPaymentFlow(
    event = "invoice_decoded",
    paymentMethod = "lightning",
    amount = 50000u,
    durationMs = 150
)

// Performance metrics
PaykitLogger.logPerformance(
    operation = "payInvoice",
    durationMs = 2500,
    success = true,
    context = mapOf("invoice" to invoice)
)
```

**Privacy:** Payment details are only logged in DEBUG builds. Set `logPaymentDetails = false` to disable.

### Error Reporting

Integrate with your error monitoring service (Sentry, Firebase Crashlytics, etc.):

```kotlin
// Set error reporter callback
PaykitConfigManager.errorReporter = { error, context ->
    FirebaseCrashlytics.getInstance().apply {
        recordException(error)
        context?.forEach { (key, value) ->
            setCustomKey(key, value.toString())
        }
    }
}

// Errors are automatically reported when logged
paykitError("Payment execution failed", error = error, context = context)
// → Automatically sent to Firebase with full context
```

### Retry Logic

Executors support automatic retry with exponential backoff:

```kotlin
// Configure retry behavior
PaykitConfigManager.maxRetryAttempts = 3
PaykitConfigManager.retryBaseDelayMs = 1000L  // milliseconds

// Retries are automatic for transient failures:
// - Network timeouts
// - Temporary Lightning routing failures
// - Rate limiting
```

### Performance Optimization

**Caching:** Payment method discovery results are cached for 60 seconds.

**Coroutine dispatching:** Executor operations run on `Dispatchers.IO` for optimal performance.

**Metrics:** All operations are automatically timed and logged at INFO level.

### Security Features

1. **Input Validation:**
   - All addresses/invoices validated before execution
   - Amount bounds checking
   - Fee rate sanity checks

2. **Rate Limiting:**
   - Configurable maximum retry attempts
   - Exponential backoff prevents request storms

3. **Privacy:**
   - Payment details not logged in production
   - Receipt data encrypted using `EncryptedSharedPreferences`
   - No telemetry without explicit opt-in

### Configuration Reference

```kotlin
// Environment (auto-configured based on build)
PaykitConfigManager.environment  // DEVELOPMENT, STAGING, PRODUCTION

// Logging
PaykitConfigManager.logLevel = PaykitLogLevel.INFO
PaykitConfigManager.logPaymentDetails  // true in DEBUG only

// Timeouts
PaykitConfigManager.defaultPaymentTimeoutMs = 60_000L  // milliseconds
PaykitConfigManager.lightningPollingIntervalMs = 500L  // milliseconds

// Retry configuration
PaykitConfigManager.maxRetryAttempts = 3
PaykitConfigManager.retryBaseDelayMs = 1000L  // milliseconds

// Monitoring
PaykitConfigManager.errorReporter = { error, context ->
    // Your error monitoring integration
}
```

### Production Deployment Guide

1. **Pre-deployment:**
   - Review security checklist in `BUILD_CONFIGURATION.md`
   - Configure error monitoring
   - Set log level to `WARNING` or `ERROR`
   - Test on testnet with production settings

2. **Deployment:**
   - Enable feature flag for 5% of users
   - Monitor error rates and performance metrics
   - Gradually increase to 100% over 7 days

3. **Monitoring:**
   - Track payment success/failure rates
   - Monitor average payment duration
   - Set up alerts for error rate spikes
   - Review logs daily during rollout

4. **Rollback triggers:**
   - Payment failure rate > 5%
   - Error rate > 1%
   - Average payment duration > 10s
   - User reports of stuck payments

### Known Limitations

1. **Transaction verification** requires external block explorer (not yet integrated)
2. **Payment method discovery** uses basic heuristics (Paykit URI support coming)
3. **Receipt format** may change in future protocol versions

### ProGuard Rules

Required rules are documented in `BUILD_CONFIGURATION.md`. Ensure these are added to your `proguard-rules.pro`:

```proguard
-keep class com.paykit.mobile.** { *; }
-keep class to.bitkit.paykit.** { *; }
```

See `CHANGELOG.md` for version history and migration guides.
