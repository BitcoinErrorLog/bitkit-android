# Bitkit Android Tests

End-to-end tests for Paykit integration with Pubky-ring.

## Test Structure

### Test Files

- **`java/to/bitkit/paykit/PaykitE2ETest.kt`** - Comprehensive E2E tests for all Paykit features
  - Session management flows
  - Noise key derivation
  - Profile and contacts
  - Backup and restore
  - Cross-app integration

### Test Helpers

- **`java/to/bitkit/helpers/PubkyRingTestHelper.kt`** - Simulates Pubky-ring app interactions
  - Test session/keypair creation
  - Intent-based callback simulation
  - App detection utilities
  - Test data factory

- **`java/to/bitkit/helpers/WalletTestHelper.kt`** - Wallet operation helpers
  - Compose-based navigation
  - Wallet state verification
  - Session and contact verification
  - Payment flow helpers
  - UI assertion utilities

## Running Tests

### Via Android Studio

1. Open project in Android Studio
2. Navigate to `app/src/androidTest/java/to/bitkit/paykit/PaykitE2ETest.kt`
3. Right-click on test class or method
4. Select "Run 'PaykitE2ETest'" or "Run 'testName'"
5. Select emulator or connected device

### Via Command Line

```bash
# Run all instrumented tests
./gradlew connectedDevDebugAndroidTest

# Run specific test class
./gradlew connectedDevDebugAndroidTest \
  --tests "to.bitkit.paykit.PaykitE2ETest"

# Run specific test method
./gradlew connectedDevDebugAndroidTest \
  --tests "to.bitkit.paykit.PaykitE2ETest.testSessionFlow_RequestAndReceive"

# Run with test reports
./gradlew connectedDevDebugAndroidTest
# Reports at: app/build/reports/androidTests/connected/index.html
```

## Test Coverage

| Feature | Test Count | Status |
|---------|-----------|--------|
| Session Management | 4 | ✅ Complete |
| Noise Key Derivation | 1 | ✅ Complete |
| Profile & Contacts | 2 | ✅ Complete |
| Backup & Restore | 2 | ✅ Complete |
| Cross-App Integration | 3 | ✅ Complete |
| **Total** | **12 tests** | ✅ Complete |

## Test Scenarios

### Session Management

1. **Request and Receive** - Full session request flow
   - Tests Pubky-ring integration
   - Verifies intent-based callback handling
   - Checks session persistence

2. **Persistence** - Session restoration after app restart
   - Tests session storage
   - Verifies session restored from DataStore

3. **Expiration Handling** - Session expiration warnings
   - Checks for expiration warnings
   - Verifies refresh button availability

4. **Graceful Degradation** - Behavior when Pubky-ring not installed
   - Tests fallback UI (QR code option)
   - Verifies install prompts

### Noise Key Derivation

1. **Derivation Flow** - Key derivation via Pubky-ring
   - Tests keypair request flow
   - Verifies cache integration
   - Handles Pubky-ring not installed scenario

### Profile & Contacts

1. **Profile Fetching** - Profile data retrieval
   - Tests profile request from Pubky-ring
   - Verifies fallback to Pubky SDK
   - Checks directory lookup

2. **Follows Sync** - Contact synchronization
   - Tests follows list retrieval
   - Verifies contact import
   - Checks sync completion

### Backup & Restore

1. **Export** - Session/key backup
   - Verifies export UI
   - Tests backup file creation

2. **Import** - Session/key restoration
   - Verifies import UI
   - Tests backup file validation

### Cross-App Integration

1. **Cross-Device Auth** - QR code authentication
   - Tests QR code generation
   - Verifies link sharing

2. **Payment Flow** - End-to-end payment
   - Tests Paykit payment option
   - Verifies payment completion

3. **Contact Discovery** - Directory-based discovery
   - Tests contact discovery
   - Verifies results display

## Test Prerequisites

### Required Setup

1. **Emulator/Device Configuration**
   - Android API 28 (Android 9) or later
   - Pixel 4 or similar device
   - Sufficient storage for app + test data
   - Internet connectivity for network tests

2. **Wallet Setup**
   - Tests assume wallet is initialized
   - Some tests require active Lightning node
   - Background work should be enabled

3. **Gradle Configuration**
   - Use `devDebug` build variant for tests
   - Ensure test dependencies are available

### Optional Setup

1. **Pubky-ring App**
   - Install Pubky-ring on emulator/device for full integration tests
   - Tests gracefully degrade if not installed
   - Package name: `to.pubky.ring`

2. **Test Data**
   - Use consistent test pubkey: `z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK`
   - Test device IDs and sessions are generated automatically

## Test Configuration

### Test Dependencies

```kotlin
// In app/build.gradle.kts
androidTestImplementation("androidx.test.ext:junit:1.1.5")
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.5.4")
androidTestImplementation("androidx.test.uiautomator:uiautomator:2.2.0")
```

### Test Data

Test helpers provide factory methods for consistent data:

```kotlin
// Generate unique test pubkey
val pubkey = TestDataFactory.generatePubkey()

// Generate test device ID
val deviceId = TestDataFactory.generateDeviceId()

// Generate test session secret
val sessionSecret = TestDataFactory.generateSessionSecret()

// Generate hex keypair
val (secretKey, publicKey) = TestDataFactory.generateHexKeypair()
```

## Compose Testing

### Using Compose Test Rule

```kotlin
@get:Rule
val composeTestRule = createAndroidComposeRule<MainActivity>()

@Test
fun exampleTest() {
    // Find and interact with composables
    composeTestRule.onNodeWithText("Click Me").performClick()
    
    // Assert UI state
    composeTestRule.onNodeWithText("Success").assertIsDisplayed()
    
    // Wait for async operations
    composeTestRule.waitForIdle()
}
```

### Semantic Properties

Use semantic properties for better test stability:

```kotlin
// In composable:
Text(
    text = "Username",
    modifier = Modifier.testTag("username_text")
)

// In test:
composeTestRule.onNodeWithTag("username_text").assertIsDisplayed()
```

## Debugging Tests

### Common Issues

1. **Test Timeout**
   - Increase timeout values if network is slow
   - Check emulator performance settings
   - Use `Thread.sleep()` sparingly

2. **Element Not Found**
   - Verify UI implementation matches test expectations
   - Check for content descriptions and test tags
   - Use `composeTestRule.onRoot().printToLog("TAG")` to debug

3. **Intent Not Handled**
   - Ensure intent filter registered in AndroidManifest
   - Check scheme and action match expected values
   - Verify app is set as default handler

### Debug Tips

```kotlin
// Print composable tree
composeTestRule.onRoot().printToLog("UI_TREE")

// Take screenshot on failure
@get:Rule
val screenshotRule = ScreenshotTestRule()

// Add logs
android.util.Log.d("TEST", "Current state: $state")

// Use UiAutomator for system UI
val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
device.pressHome()
```

## Test Reports

### Viewing Results

After running tests, view reports at:

```
app/build/reports/androidTests/connected/index.html
```

### CI/CD Integration

Example GitHub Actions workflow:

```yaml
- name: Run Instrumented Tests
  uses: reactivecircus/android-emulator-runner@v2
  with:
    api-level: 29
    target: default
    arch: x86_64
    script: ./gradlew connectedDevDebugAndroidTest

- name: Upload Test Results
  uses: actions/upload-artifact@v3
  if: always()
  with:
    name: test-results
    path: app/build/reports/androidTests/
```

## Performance Considerations

### Test Execution Time

- Full test suite: ~10-15 minutes
- Individual test: 30-60 seconds
- Reduce execution time by:
  - Using faster emulator (x86_64 with HAXM/KVM)
  - Disabling animations: `adb shell settings put global window_animation_scale 0`
  - Running specific test classes instead of full suite

### Resource Usage

- Emulator RAM: 2GB minimum, 4GB recommended
- Disk space: 10GB for emulator + test artifacts
- CPU: Multi-core recommended for parallel test execution

## Maintenance

### Adding New Tests

1. Add test method to `PaykitE2ETest` class
2. Use test helpers for common operations
3. Follow existing naming conventions: `testFeature_Scenario()`
4. Add documentation to this README

### Updating Test Helpers

1. Modify helper files in `helpers/` package
2. Ensure backward compatibility
3. Update documentation if API changes
4. Add new utility methods as needed

### Test Data Management

1. Use `TestDataFactory` for generating test data
2. Clean up test data in `@After` methods
3. Avoid hard-coded values; use constants

## Troubleshooting

### Build Issues

```bash
# Clean build
./gradlew clean

# Clear Gradle cache
./gradlew cleanBuildCache

# Invalidate caches in Android Studio
# File > Invalidate Caches / Restart
```

### Emulator Issues

```bash
# List emulators
emulator -list-avds

# Start emulator
emulator -avd Pixel_4_API_29

# Clear emulator data
emulator -avd Pixel_4_API_29 -wipe-data
```

### Test Execution Issues

```bash
# Uninstall test APK
adb uninstall to.bitkit.debug.test

# Clear app data
adb shell pm clear to.bitkit.debug

# View logcat during test
adb logcat | grep -i bitkit
```

## Related Documentation

- [Paykit Setup Guide](../../../docs/PAYKIT_SETUP.md)
- [Paykit Testing Guide](../../../docs/PAYKIT_TESTING.md)
- [Architecture Overview](../../../docs/PAYKIT_ARCHITECTURE.md)
- [Android Testing Best Practices](https://developer.android.com/training/testing)

