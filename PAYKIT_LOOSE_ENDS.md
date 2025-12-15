# Paykit Integration - Loose Ends and Missing Features

## Critical Missing Features

### 1. Payment Request Waking ⚠️ **NOT IMPLEMENTED**

**Status**: Payment request waking with autopay evaluation is **NOT implemented** in Bitkit.

**What's Missing**:
- `PaymentRequestService` from paykit-rs not integrated
- Deep link handlers for payment requests
- Background processing for waking on payment requests
- Autopay evaluation on wake
- Push notification integration for payment requests

**Location in paykit-rs**:
- `paykit-rs-master/paykit-mobile/swift/PaymentRequestService.swift` (iOS)
- `paykit-rs-master/paykit-mobile/kotlin/PaymentRequestService.kt` (Android)
- `paykit-rs-master/paykit-mobile/BITKIT_AUTOPAY_INTEGRATION.md` (integration guide)

**Required Implementation**:
1. Copy `PaymentRequestService` to Bitkit
2. Implement deep link handlers (iOS: URL scheme, Android: Intent filters)
3. Connect to `AutoPayViewModel` for evaluation
4. Integrate with push notifications (FCM/APNs)
5. Add background processing for waking

### 2. iOS Placeholder Implementations ⚠️ **NEEDS FIXING**

**DirectoryService.swift** (line 63):
```swift
// TODO: Implement actual discovery from Pubky follows directory
```
- Currently placeholder, needs real Pubky SDK integration

**PubkyRingIntegration.swift** (line 20, 32):
```swift
/// Note: This is a placeholder - in production would communicate with Pubky Ring app
// For now, generate a mock key (32 bytes for X25519)
```
- Currently uses mock keys, needs real FFI-based derivation

**PubkyStorageAdapter.swift** (line 21):
```swift
// For now, this is a placeholder
```
- Needs real HTTP transport implementation (like Android version)

**PaykitPaymentService.swift** (line 62, 118):
```swift
// TODO: Query Paykit for recipient's payment methods
// TODO: Implement Paykit URI payment
```
- Missing Paykit URI parsing and payment method discovery

### 3. Android Server Mode TODO ⚠️ **PARTIALLY IMPLEMENTED**

**NoisePaymentService.kt** (line 268):
```kotlin
// TODO: Implement server mode with ServerSocket and FfiNoiseManager.newServer
```

**Status**: Server mode IS actually implemented (see `startServer()`, `stopServer()`, `handleClientConnection()` methods), but the TODO comment is misleading. The `receivePaymentRequest()` method has a TODO but server infrastructure exists.

**Action**: Remove misleading TODO, verify server mode works end-to-end.

## Build and Test Status

### Builds ❌ **NOT TESTED**
- Android: Not built (requires SDK configuration)
- iOS: Not built (requires XCFramework)
- No compilation verification beyond linter checks

### E2E Tests ❌ **NOT RUN**
- Tests created but not executed
- No payment request flow tests
- No waking functionality tests
- No actual payment execution tests

## Required Actions

### Immediate (Critical)

1. **Implement Payment Request Waking**:
   ```bash
   # Copy PaymentRequestService
   cp paykit-rs-master/paykit-mobile/swift/PaymentRequestService.swift \
      bitkit-ios/Bitkit/PaykitIntegration/Services/
   
   cp paykit-rs-master/paykit-mobile/kotlin/PaymentRequestService.kt \
      bitkit-android/app/src/main/java/to/bitkit/paykit/services/
   ```

2. **Fix iOS Placeholders**:
   - Update `DirectoryService.swift` to use real Pubky SDK
   - Update `PubkyRingIntegration.swift` to use real FFI (like Android)
   - Update `PubkyStorageAdapter.swift` to use real HTTP (like Android)
   - Implement Paykit URI parsing in `PaykitPaymentService.swift`

3. **Add Deep Link Handlers**:
   - iOS: Add URL scheme handler in `AppDelegate` or `SceneDelegate`
   - Android: Add Intent filters in `AndroidManifest.xml`

4. **Integrate with Push Notifications**:
   - iOS: Connect to APNs for payment request notifications
   - Android: Connect to FCM (already exists in `FcmService.kt`)

### Testing Required

1. **Build Verification**:
   ```bash
   # Android
   cd bitkit-android && ./gradlew assembleDebug
   
   # iOS
   cd bitkit-ios && xcodebuild -scheme Bitkit -configuration Debug build
   ```

2. **E2E Test Execution**:
   ```bash
   # Android
   ./gradlew connectedAndroidTest --tests "*Paykit*"
   
   # iOS
   xcodebuild test -scheme Bitkit -destination 'platform=iOS Simulator,name=iPhone 15'
   ```

3. **Payment Request Waking Tests**:
   - Test deep link → payment request → autopay evaluation
   - Test push notification → wake → payment request → autopay
   - Test manual approval flow when autopay doesn't match

## Summary

**Critical Missing**: Payment request waking is completely missing and is a core Paykit feature.

**High Priority**: iOS placeholder implementations need to be replaced with real code.

**Medium Priority**: Build and test verification needed.

**Low Priority**: Clean up misleading TODOs.

