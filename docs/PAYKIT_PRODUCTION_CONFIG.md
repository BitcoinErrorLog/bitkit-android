# Paykit Production Configuration Guide - Android

This document outlines the configuration required before releasing Paykit features to production.

## Pre-Release Configuration Checklist

### 1. Pubky Homeserver Configuration

**Location:** `app/src/main/java/to/bitkit/paykit/services/DirectoryService.kt`

**Current Setting:**
```kotlin
private const val PUBKY_HOMESERVER = "8pinxxgqs41n4aididenw5apqp1urfmzdztr8jt4abrkdn435ewo"
```

**Verification:**
- [ ] Confirm this is the production homeserver pubkey
- [ ] Test directory queries against production homeserver
- [ ] Verify homeserver is accessible and stable

**Environment-Specific Configuration:**
- Development: Can use staging homeserver via BuildConfig
- Production: Must use production homeserver

### 2. Relay Server Configuration

**Location:** `app/src/main/java/to/bitkit/paykit/services/PubkyRingBridge.kt`

**Current Setting:**
```kotlin
private const val DEFAULT_RELAY_URL = "https://relay.pubky.app/sessions"

fun getRelayUrl(): String {
    return System.getProperty("PUBKY_RELAY_URL", DEFAULT_RELAY_URL)
}
```

**Verification:**
- [ ] Confirm `https://relay.pubky.app/sessions` is production relay
- [ ] Test cross-device authentication with production relay
- [ ] Verify relay server is accessible and stable

### 3. Cross-Device Authentication URL

**Location:** `app/src/main/java/to/bitkit/paykit/services/PubkyRingBridge.kt`

**Current Setting:**
```kotlin
const val CROSS_DEVICE_WEB_URL = "https://pubky.app/auth"
```

**Verification:**
- [ ] Confirm `https://pubky.app/auth` is production URL
- [ ] Test QR code generation and sharing
- [ ] Verify web page loads correctly

### 4. Network Configuration

**Location:** Build variants and configuration

**Verification:**
- [ ] Network is set to mainnet for release builds (currently regtest)
- [ ] Electrum server URLs are production endpoints
- [ ] Esplora server URLs are production endpoints
- [ ] RGS (Rapid Gossip Sync) URLs are production endpoints

### 5. WorkManager Configuration

**Location:** `App.kt` or Application class

**Verification:**
- [ ] `SubscriptionCheckWorker.schedule(context)` is called
- [ ] `PaykitPollingWorker.schedule(context)` is called
- [ ] WorkManager is initialized correctly
- [ ] Background work constraints are appropriate

### 6. Intent Filter Configuration

**Location:** `AndroidManifest.xml`

**Verification:**
- [ ] `bitkit://` scheme is registered
- [ ] `paykit:` scheme is registered
- [ ] `pip:` scheme is registered
- [ ] Deep link handlers are implemented in `MainActivity`

### 7. Feature Flags

**Verification:**
- [ ] Paykit features are enabled for production
- [ ] Debug logging is disabled
- [ ] Test data is removed
- [ ] Mock services are replaced with real implementations

### 8. Security Configuration

**Verification:**
- [ ] EncryptedSharedPreferences is configured correctly
- [ ] Sensitive data is stored securely (not SharedPreferences)
- [ ] Session secrets are handled securely
- [ ] Preimages are not logged
- [ ] ProGuard rules preserve Paykit classes

### 9. Error Monitoring

**Verification:**
- [ ] Crash reporting is configured (Firebase Crashlytics)
- [ ] Paykit errors are logged appropriately
- [ ] User-facing error messages are clear and actionable

### 10. Performance

**Verification:**
- [ ] WorkManager tasks complete within time budget
- [ ] Directory queries don't block UI
- [ ] Payment execution is responsive
- [ ] Storage operations are efficient

## Build Configuration

For production builds:

```kotlin
// In build.gradle.kts
buildTypes {
    release {
        isMinifyEnabled = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        // Ensure Paykit classes are not obfuscated
    }
}
```

## Testing Checklist

Before releasing to production:

- [ ] Test on physical device (not just emulator)
- [ ] Test with real funds (small amounts)
- [ ] Test cross-device authentication
- [ ] Test background worker execution
- [ ] Test network failure scenarios
- [ ] Test session expiration and refresh
- [ ] Test Pubky-ring not installed scenario

## Rollback Plan

If issues arise in production:

1. **Immediate**: Disable Paykit features via remote config (if implemented)
2. **Short-term**: Push app update disabling Paykit
3. **Data**: Receipt and subscription data is stored locally and will persist

## Related Documentation

- [Setup Guide](PAYKIT_SETUP.md)
- [Architecture Overview](PAYKIT_ARCHITECTURE.md)
- [Release Checklist](PAYKIT_RELEASE_CHECKLIST.md)

