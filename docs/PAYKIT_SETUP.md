# Paykit Setup Guide - Android

This guide covers setting up Paykit integration for Bitkit Android.

## Prerequisites

1. **Android Studio** (latest stable)
2. **API 26+** (Android 8.0) minimum SDK
3. **Pubky-ring app** installed on test device (optional but recommended)
4. **Regtest environment** for development testing

## Installation

### 1. Gradle Dependencies

The Paykit integration uses generated FFI bindings. Ensure these dependencies are in `app/build.gradle.kts`:

```kotlin
dependencies {
    // Paykit FFI bindings (generated)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}
```

### 2. AndroidManifest Configuration

#### Intent Filters

```xml
<activity
    android:name=".ui.MainActivity"
    android:exported="true">
    
    <!-- Paykit URL schemes -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="bitkit" />
        <data android:scheme="paykit" />
        <data android:scheme="pip" />
    </intent-filter>
</activity>
```

#### Background Services

```xml
<service
    android:name=".paykit.workers.SubscriptionCheckWorker"
    android:permission="android.permission.BIND_JOB_SERVICE"
    android:exported="false" />

<service
    android:name=".paykit.workers.PaykitPollingWorker"
    android:permission="android.permission.BIND_JOB_SERVICE"
    android:exported="false" />
```

### 3. WorkManager Setup

In `App.kt`:

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize WorkManager for background tasks
        WorkManager.initialize(this, workManagerConfig)
    }
}
```

## Configuration

### Pubky Homeserver

The default Pubky homeserver is configured in `DirectoryService.kt`:

```kotlin
private const val PUBKY_HOMESERVER = "8pinxxgqs41n4aididenw5apqp1urfmzdztr8jt4abrkdn435ewo"
```

For development, set via BuildConfig:
```kotlin
buildConfigField("String", "PUBKY_HOMESERVER_URL", "\"localhost:8080\"")
```

### Electrum/Esplora

Configure backend URLs in `local.properties`:

```properties
ELECTRUM_URL=localhost:50001
RGS_URL=localhost:8080
```

## Pubky-Ring Integration

### Installing Pubky-Ring

1. Download Pubky-ring from Play Store or sideload APK
2. Install on the same device as Bitkit
3. Launch Pubky-ring and complete initial setup

### Cross-Device Authentication

If Pubky-ring is on a different device:

1. Open Bitkit → Paykit Settings → Connect Pubky-ring
2. Select "QR Code" option
3. Scan the QR code with the device running Pubky-ring
4. Approve the session request in Pubky-ring

### Manual Session Entry

For development or fallback:

1. Open Bitkit → Paykit Settings → Connect Pubky-ring
2. Select "Manual Entry" option
3. Enter the pubkey and session secret from Pubky-ring

## Hilt Dependency Injection

Paykit services are provided via Hilt:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object PaykitModule {
    
    @Provides
    @Singleton
    fun provideSubscriptionStorage(keychain: PaykitKeychainStorage): SubscriptionStorage {
        return SubscriptionStorage(keychain)
    }
    
    @Provides
    @Singleton
    fun provideAutoPayStorage(keychain: PaykitKeychainStorage): AutoPayStorage {
        return AutoPayStorage(keychain)
    }
}
```

## Troubleshooting

### Common Issues

**Issue: "Paykit not initialized"**
- Ensure the wallet has been created and Lightning node is running
- Check that `PaykitIntegrationHelper.setup(lightningRepo)` completes successfully

**Issue: "Session expired"**
- Re-authenticate with Pubky-ring
- Sessions have limited validity and must be refreshed

**Issue: "Directory query failed"**
- Check network connectivity
- Verify Pubky homeserver is reachable
- Ensure session is valid

**Issue: "Payment failed"**
- Check Lightning channel capacity
- Verify recipient payment method is valid
- Check spending limits are not exceeded

### Debug Logging

Enable verbose logging:

```kotlin
// In debug builds
Logger.setLevel(LogLevel.DEBUG)
```

Logs are tagged with context:
- `PaykitManager` - Core manager operations
- `PaykitPaymentService` - Payment execution
- `DirectoryService` - Directory queries
- `SpendingLimitManager` - Spending limit operations

## Development Workflow

### Running Unit Tests

```bash
./gradlew testDevDebugUnitTest
```

### Running Instrumented Tests

```bash
./gradlew connectedDevDebugAndroidTest
```

### Building for E2E Tests

```bash
E2E=true ./gradlew assembleDevRelease
```

### Testing Without Pubky-Ring

The app supports simulated sessions for development:

```kotlin
// In test code, inject PubkyRingBridge and call handleCallback
@Inject constructor(private val pubkyRingBridge: PubkyRingBridge)

// Then simulate a session callback:
pubkyRingBridge.handleCallback(
    Uri.parse("bitkit://paykit-session?pubkey=...&session_secret=...")
)
```

> **Note:** `PubkyRingBridge.getInstance()` is deprecated. Use dependency injection instead.

## Next Steps

- [Architecture Overview](PAYKIT_ARCHITECTURE.md)
- [Testing Guide](PAYKIT_TESTING.md)
- [Release Checklist](PAYKIT_RELEASE_CHECKLIST.md)

