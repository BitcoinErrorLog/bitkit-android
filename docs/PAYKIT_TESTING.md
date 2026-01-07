# Paykit Testing Guide - Android

This guide covers testing strategies and procedures for Paykit integration.

## Test Categories

### 1. Unit Tests

Located in `app/src/test/java/to/bitkit/paykit/`

**Run All Unit Tests:**
```bash
./gradlew testDevDebugUnitTest
```

**Run Specific Test File:**
```bash
./gradlew testDevDebugUnitTest --tests "to.bitkit.paykit.services.PubkyRingBridgeTest"
```

**Test Files:**
- `PubkyRingBridgeTest.kt` - Pubky-ring integration
- `SpendingLimitStorageTest.kt` - Spending limits
- `AutoPayStorageTest.kt` - Auto-pay rules
- `SubscriptionStorageTest.kt` - Subscription persistence
- `SubscriptionsViewModelTest.kt` - Subscription proposal workflows
- `PaymentRequestsViewModelTest.kt` - Payment request workflows (send/cancel/cleanup)

### 2. Instrumented Tests

Located in `app/src/androidTest/java/to/bitkit/paykit/`

**Run Instrumented Tests:**
```bash
./gradlew connectedDevDebugAndroidTest
```

**Run Specific Test:**
```bash
./gradlew connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=to.bitkit.paykit.PaykitE2ETest
```

### 3. E2E Tests

Located in `app/src/androidTest/java/to/bitkit/paykit/PaykitE2ETest.kt`

**Requirements:**
- E2E build: `E2E=true ./gradlew assembleDevRelease`
- Local Electrum server running
- Optional: Pubky-ring app installed for cross-app tests

## Test Environment Setup

### 1. Local Electrum/Esplora

```bash
# Start local regtest node
bitcoind -regtest -daemon

# Start Electrum server
electrs --network regtest
```

### 2. Pubky Directory (Development)

```bash
# Start local Pubky homeserver
pubky-homeserver --port 8080
```

### 3. Test Wallet

The E2E test suite automatically creates a test wallet if needed. For manual testing:

1. Install E2E build
2. Create new wallet
3. Fund with regtest coins

## E2E Test Cases

### Session Management

| Test | Description | Requires Pubky-ring |
|------|-------------|---------------------|
| `testSessionRequestFromPubkyRing` | Request session from Pubky-ring | Optional |
| `testPubkyRingNotInstalledGracefulDegradation` | Verify fallback options | No |
| `testSessionExpirationAndRefresh` | Session lifecycle | Yes |
| `testCrossDeviceQRAuthentication` | QR code auth flow | No |
| `testManualSessionEntry` | Manual session fallback | No |

### Payment Flows

| Test | Description |
|------|-------------|
| `testCreatePaymentRequest` | Create and verify payment request |
| `testPayPaymentRequest` | Execute payment to request |
| `testSpendingLimitEnforcement` | Verify limits are enforced |

### Subscriptions

| Test | Description |
|------|-------------|
| `testCreateSubscription` | Create recurring subscription |
| `testAutoPayConfiguration` | Configure auto-pay settings |

### Contacts

| Test | Description |
|------|-------------|
| `testContactDiscovery` | Discover contacts from directory |
| `testProfileImport` | Import profile from Pubky |

## Manual Testing Checklist

### Payment Request Flow

- [ ] Create payment request with amount
- [ ] Generate QR code
- [ ] Share request link
- [ ] Verify request appears in list
- [ ] Pay the request from another wallet
- [ ] Verify payment receipt created

### Sent Payment Requests (Outgoing)

The sender-storage model means requests are stored on the **sender's** homeserver:

- [ ] Navigate to Payment Requests → Sent tab
- [ ] Create new request (specify recipient, amount, method)
- [ ] Verify request appears in Sent list with status "Pending"
- [ ] Cancel a sent request → verify removed from Sent tab
- [ ] Cleanup orphaned requests → verify orphans (homeserver only) deleted

**Orphan Cleanup Flow:**
1. List all sent requests grouped by recipient
2. For each recipient, list requests on homeserver
3. Delete requests on homeserver not in local tracking
4. Critical: only compare within same recipient scope (prevents cross-recipient false matches)

### Notification Tap Routing

When tapping Paykit notifications:

- [ ] Tap manual approval notification → opens Payment Requests screen
- [ ] Tap payment success notification → opens Payment Requests screen  
- [ ] Tap subscription proposal notification → opens Subscriptions screen
- [ ] Verify deep link format: `bitkit://payment-request?requestId=xxx&from=yyy`

### Subscription Flow

- [ ] Create subscription
- [ ] Verify next payment date calculated
- [ ] Wait for WorkManager task (or trigger manually)
- [ ] Verify auto-pay processes (if enabled)
- [ ] Check notification received

### Cross-Device Auth

- [ ] Select QR code option
- [ ] Verify QR code displayed
- [ ] Scan with Pubky-ring on another device
- [ ] Approve session in Pubky-ring
- [ ] Verify session active in Bitkit

### Spending Limits

- [ ] Set daily spending limit
- [ ] Attempt payment below limit → Success
- [ ] Attempt payment above limit → Blocked
- [ ] Verify remaining limit displayed

## Debugging Tests

### Enable Verbose Logging

```kotlin
// In test setUp()
Logger.setLevel(LogLevel.DEBUG)
```

### Inspect Test State

```kotlin
// Print current state during test
println("Current subscriptions: ${subscriptionStorage.listSubscriptions()}")
```

### Capture Screenshots

```kotlin
// In Compose test
composeTestRule.onRoot().captureToImage()
```

## Test Data

### Test Pubkeys

```kotlin
const val TEST_PUBKEY = "ybndrfg8ejkmcpqxot1uwisza345h769ybndrfg8ejkmcpqxot1u"
```

### Test Invoices (Regtest)

```kotlin
const val TEST_INVOICE = "lnbcrt10u1..."
```

### Test Addresses (Regtest)

```kotlin
const val TEST_ADDRESS = "bcrt1q..."
```

## Mocking with mockito-kotlin

Example test structure:

```kotlin
@RunWith(MockitoJUnitRunner::class)
class PaykitPaymentServiceTest {

    @Mock
    private lateinit var lightningRepo: LightningRepo

    @Mock
    private lateinit var spendingLimitManager: SpendingLimitManager

    private lateinit var paymentService: PaykitPaymentService

    @Before
    fun setUp() {
        paymentService = PaykitPaymentService()
    }

    @Test
    fun `payment succeeds when under spending limit`() = runTest {
        // Given
        whenever(spendingLimitManager.tryReserveSpending(any(), any()))
            .thenReturn("reservation-id")

        // When
        val result = paymentService.pay(
            lightningRepo = lightningRepo,
            recipient = "lnbcrt...",
            amountSats = 1000uL
        )

        // Then
        assertTrue(result.success)
    }
}
```

## Continuous Integration

### GitHub Actions Workflow

```yaml
name: Paykit Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
          
      - name: Run Unit Tests
        run: ./gradlew testDevDebugUnitTest
        
      - name: Run Lint
        run: ./gradlew detekt
```

## Known Limitations

1. **E2E Cross-App Tests:** Require Pubky-ring to be installed
2. **Background Task Tests:** WorkManager tests require test configuration
3. **Real Payment Tests:** Require funded regtest wallet

## Reporting Issues

When reporting test failures:

1. Include full test log
2. Note device model and Android version
3. Specify if Pubky-ring was installed
4. Include any relevant screenshots/logcat

## Related Documentation

- [Setup Guide](PAYKIT_SETUP.md)
- [Architecture Overview](PAYKIT_ARCHITECTURE.md)
- [Release Checklist](PAYKIT_RELEASE_CHECKLIST.md)

