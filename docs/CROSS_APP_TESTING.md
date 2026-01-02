# Cross-App Testing with Pubky-ring

This guide covers end-to-end testing of Bitkit Android's Paykit integration with the real Pubky-ring app.

## Overview

Cross-app testing verifies the complete integration between Bitkit and Pubky-ring, including:
- Session delegation and authentication
- Noise key derivation for interactive payments
- Profile and contact synchronization
- Backup/restore operations

## Prerequisites

### Development Environment

1. **Android Studio**: Hedgehog (2023.1.1) or later
2. **Android SDK**: API 34+
3. **Emulator**: Pixel 6+ with API 34
4. **Node.js**: 18+ (for Pubky-ring React Native app)
5. **JDK**: 17+

### Source Repositories

Clone these repositories into the same parent directory:

```bash
# Parent directory structure
vibes/
├── bitkit-android/
├── pubky-ring/
└── pubky-core/  # Optional: for local homeserver
```

### Network Requirements

- Local homeserver OR access to dev/staging Pubky homeserver
- Internet access for Electrum/Esplora backend (unless using local)

## Setup Instructions

### Step 1: Build Pubky-ring

```bash
cd pubky-ring

# Install dependencies
yarn install

# Build for Android
npx react-native run-android
```

**Alternative: Build with Android Studio**
1. Open `pubky-ring/android` in Android Studio
2. Sync Gradle
3. Run on emulator (Shift+F10)

### Step 2: Build Bitkit

```bash
cd bitkit-android

# Build debug variant
./gradlew installDevDebug

# Or run from Android Studio
# Open project, select devDebug variant, run
```

### Step 3: Configure Homeserver

Both apps must use the same homeserver. Options:

**Option A: Local Homeserver (Recommended for Testing)**
```bash
cd pubky-core

# Build and run homeserver
cargo run -p pubky-homeserver -- --port 8080

# Homeserver URL: http://10.0.2.2:8080 (from Android emulator)
```

**Option B: Development Homeserver**
```
URL: https://dev.homeserver.pubky.org
```

**Configure in Bitkit:**
```kotlin
// In Env.kt, set:
const val PUBKY_HOMESERVER_URL = "http://10.0.2.2:8080"
```

**Configure in Pubky-ring:**
```typescript
// In src/utils/config.ts, set:
export const HOMESERVER_URL = "http://10.0.2.2:8080";
```

**Note:** Android emulator uses `10.0.2.2` to access host machine's localhost.

## Test Scenarios

### Scenario 1: Session Authentication Flow

**Purpose**: Verify Bitkit can request and receive a delegated session from Pubky-ring.

**Steps:**

1. **In Bitkit:**
   - Navigate to Settings → Paykit → Sessions
   - Tap "Connect Pubky-ring"
   
2. **Automatic Handoff:**
   - Bitkit launches Pubky-ring via Intent
   - Intent data: `pubkyring://auth?callback=bitkit://paykit-session&scope=read,write`

3. **In Pubky-ring:**
   - Approve the session request
   - Grant requested capabilities (read, write)
   - Tap "Authorize"

4. **Return to Bitkit:**
   - Pubky-ring calls back: `bitkit://paykit-session?pubky=...&session_secret=...`
   - Verify session appears in Sessions list
   - Check session details (capabilities, expiry)

**Expected Results:**
- Session stored securely in EncryptedSharedPreferences
- Session appears in UI with correct capabilities
- Subsequent Paykit operations use the session

**Verification Code:**
```kotlin
// In PaykitE2ETest.kt
@Test
fun testSessionFlow_WithRealPubkyRing() {
    // Navigate and request
    WalletTestHelper.navigateToSessionManagement(composeTestRule)
    composeTestRule.onNodeWithText("Connect Pubky-ring").performClick()
    
    // Wait for Pubky-ring to launch
    // (In real test, would use UiAutomator for cross-app)
    Thread.sleep(5000)
    
    // Simulate callback (manual step in real scenario)
    // ...
    
    // Verify session
    assert(WalletTestHelper.hasActiveSession(composeTestRule))
}
```

### Scenario 2: Noise Key Derivation

**Purpose**: Verify Bitkit can request Noise keypairs for interactive payments.

**Prerequisites**: Active session from Scenario 1

**Steps:**

1. **In Bitkit:**
   - Navigate to Settings → Paykit → Direct Pay
   - Select a recipient with interactive payment support
   - Tap "Pay via Direct Channel"

2. **Key Request:**
   - Bitkit requests keypair: `pubkyring://noise-keypair?device_id=...&epoch=...`
   - Pubky-ring derives keypair from master seed

3. **In Pubky-ring:**
   - Approve keypair derivation
   - Return derived keypair to Bitkit

4. **Payment Execution:**
   - Bitkit establishes Noise channel
   - Payment completes

**Expected Results:**
- Keypair cached for device/epoch
- Subsequent requests for same device/epoch use cache
- Payment succeeds with interactive handshake

### Scenario 3: Profile Synchronization

**Purpose**: Verify profile data synchronizes between apps.

**Prerequisites**: Active session

**Steps:**

1. **In Pubky-ring:**
   - Set profile name to "Test User"
   - Set bio to "Testing cross-app sync"
   - Save profile

2. **In Bitkit:**
   - Navigate to Settings → Paykit → Profile
   - Tap "Sync Profile"

3. **Verify:**
   - Profile name matches "Test User"
   - Bio matches "Testing cross-app sync"
   - Avatar displays correctly

**Verification:**
```kotlin
@Test
fun testProfileSync_FromPubkyRing() {
    // Setup profile in Pubky-ring first (manual)
    
    WalletTestHelper.navigateToPaykit(composeTestRule)
    composeTestRule.onNodeWithText("Profile").performClick()
    composeTestRule.onNodeWithText("Sync Profile").performClick()
    
    // Wait for sync
    Thread.sleep(5000)
    
    composeTestRule.onNodeWithTag("ProfileName")
        .assertTextEquals("Test User")
}
```

### Scenario 4: Contact Discovery via Follows

**Purpose**: Verify Bitkit can import contacts from Pubky-ring follows.

**Prerequisites**: Active session, follows configured in Pubky-ring

**Steps:**

1. **In Pubky-ring:**
   - Follow several users (friends, contacts)
   - Ensure they have payment endpoints published

2. **In Bitkit:**
   - Navigate to Settings → Paykit → Contacts
   - Tap "Sync from Pubky-ring"

3. **Verify:**
   - Contacts appear with names from follows
   - Payment methods are detected
   - Contacts are usable for payments

**Expected Results:**
- Contacts sync from follows list
- Payment-enabled contacts show payment options
- Contacts persist after app restart

### Scenario 5: Cross-Device QR Authentication

**Purpose**: Verify session can be established via QR code when Pubky-ring is on a different device.

**Steps:**

1. **In Bitkit (Device A):**
   - Navigate to Settings → Paykit → Sessions
   - Tap "Connect Pubky-ring"
   - Select "Use QR Code"

2. **Display QR:**
   - Bitkit displays QR code containing auth URL
   - QR encodes: `pubkyring://auth?callback=https://relay.bitkit.to/callback/...`

3. **In Pubky-ring (Device B):**
   - Open QR scanner
   - Scan the QR code from Device A
   - Approve session

4. **Session Callback:**
   - Pubky-ring sends session to relay
   - Bitkit polls relay or receives push
   - Session established

**Expected Results:**
- QR code displays correctly
- Cross-device handshake completes
- Session works same as direct integration

### Scenario 6: Backup and Restore

**Purpose**: Verify session backup/restore between devices.

**Steps:**

1. **Export (Device A):**
   - Navigate to Settings → Paykit → Backup
   - Tap "Export Sessions"
   - Save encrypted backup file

2. **Import (Device B):**
   - Fresh Bitkit installation
   - Navigate to Settings → Paykit → Backup
   - Tap "Import Sessions"
   - Select backup file

3. **Verify:**
   - Sessions restored correctly
   - Sessions are functional
   - Paykit operations work

**Expected Results:**
- Backup file is encrypted
- Import prompts for decryption
- All sessions restore correctly

## Automated Test Execution

### Running Cross-App Tests

```bash
# Ensure both apps are installed on emulator
# Run cross-app test suite
./gradlew connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=to.bitkit.paykit.PaykitE2ETest
```

### Using UiAutomator for Cross-App Testing

For full cross-app automation, use UiAutomator:

```kotlin
// In PaykitE2ETest.kt
@Test
fun testCrossAppSessionFlow() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    
    // Start session request in Bitkit
    WalletTestHelper.navigateToSessionManagement(composeTestRule)
    composeTestRule.onNodeWithText("Connect Pubky-ring").performClick()
    
    // Wait for Pubky-ring to launch
    device.wait(Until.hasObject(By.pkg("to.pubky.ring")), 10000)
    
    // Find and click Authorize button in Pubky-ring
    val authorizeButton = device.findObject(UiSelector().text("Authorize"))
    if (authorizeButton.exists()) {
        authorizeButton.click()
    }
    
    // Wait for callback and verify
    device.wait(Until.hasObject(By.pkg("to.bitkit")), 10000)
    assert(WalletTestHelper.hasActiveSession(composeTestRule))
}
```

### Test Configuration

Tests automatically detect Pubky-ring installation:

```kotlin
// In PubkyRingTestHelper.kt
fun isPubkyRingInstalled(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo("to.pubky.ring", 0)
        true
    } catch (e: Exception) {
        false
    }
}
```

Tests gracefully degrade when Pubky-ring is not installed.

## Troubleshooting

### Common Issues

#### 1. Intent Not Resolved

**Symptom**: Bitkit cannot launch Pubky-ring

**Solution**: Verify Pubky-ring's `AndroidManifest.xml` has proper intent filter:
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="pubkyring" />
</intent-filter>
```

#### 2. Callback Not Received

**Symptom**: Bitkit doesn't receive session after Pubky-ring approval

**Solution**: 
1. Verify `bitkit://` scheme is registered in Bitkit's manifest
2. Check callback URL is correctly formatted
3. Verify activity is handling the Intent

#### 3. Homeserver Connection Failed

**Symptom**: Session works but profile/follows don't sync

**Solution**:
1. Verify both apps use same homeserver URL
2. Check homeserver is running and accessible
3. For emulator, use `10.0.2.2` not `localhost`
4. Test with: `adb shell curl http://10.0.2.2:8080/health`

#### 4. Emulator Network Issues

**Symptom**: Apps cannot communicate or reach homeserver

**Solution**:
1. Cold boot emulator: AVD Manager → Cold Boot Now
2. Clear app data and retry
3. Check emulator network: Settings → Network & internet

### Debug Logging

**Enable in Bitkit:**
```kotlin
// Add to test runner arguments
adb shell am instrument -e DEBUG_PAYKIT true ...
```

**Enable in Pubky-ring:**
```bash
# Enable React Native debug logging
adb logcat *:S ReactNativeJS:V
```

### Capturing Test Evidence

```kotlin
// Screenshot on test step
@Test
fun testWithScreenshots() {
    // Take screenshot
    val bitmap = InstrumentationRegistry.getInstrumentation()
        .uiAutomation.takeScreenshot()
    
    // Save to file
    val file = File(context.cacheDir, "screenshot_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
}
```

## CI/CD Integration

### GitHub Actions Workflow

```yaml
name: Cross-App E2E Tests

on:
  push:
    branches: [main]
  pull_request:

jobs:
  cross-app-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          path: bitkit-android
          
      - uses: actions/checkout@v4
        with:
          repository: synonymdev/pubky-ring
          path: pubky-ring
          
      - name: Setup JDK
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          
      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: '18'
          
      - name: Setup Android SDK
        uses: android-actions/setup-android@v3
          
      - name: Build Pubky-ring APK
        run: |
          cd pubky-ring
          yarn install
          cd android
          ./gradlew assembleRelease
          
      - name: Start Emulator
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          arch: x86_64
          script: |
            # Install Pubky-ring
            adb install pubky-ring/android/app/build/outputs/apk/release/app-release.apk
            
            # Run Bitkit tests
            cd bitkit-android
            ./gradlew connectedDevDebugAndroidTest \
              -Pandroid.testInstrumentationRunnerArguments.class=to.bitkit.paykit.PaykitE2ETest
              
      - name: Upload Test Results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: cross-app-test-results
          path: bitkit-android/app/build/reports/androidTests/
```

## Test Metrics

### Success Criteria

| Metric | Target |
|--------|--------|
| Session establishment | < 5 seconds |
| Key derivation | < 500ms (cached: < 10ms) |
| Profile sync | < 3 seconds |
| Contact discovery | < 10 seconds for 50 contacts |
| Backup export | < 2 seconds |
| Backup import | < 2 seconds |

### Coverage Goals

| Area | Tests | Status |
|------|-------|--------|
| Session Management | 4 | ✅ Complete |
| Key Derivation | 2 | ✅ Complete |
| Profile/Contacts | 2 | ✅ Complete |
| Backup/Restore | 2 | ✅ Complete |
| Cross-Device | 2 | ✅ Complete |
| Payment Flows | 2 | ✅ Complete |

## Related Documentation

- [Paykit Setup Guide](PAYKIT_SETUP.md)
- [Paykit Architecture](PAYKIT_ARCHITECTURE.md)
- [E2E Test IDs](e2e-test-ids.md)
- [Release Checklist](PAYKIT_RELEASE_CHECKLIST.md)

