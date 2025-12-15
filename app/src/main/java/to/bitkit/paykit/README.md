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
