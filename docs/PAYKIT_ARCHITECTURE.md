# Paykit Architecture - Android

This document describes the architecture of the Paykit integration in Bitkit Android.

## Overview

```
┌─────────────────────────────────────────────────────────────┐
│                 UI Layer (Jetpack Compose)                  │
│  PaykitDashboardScreen, PaymentRequestsScreen, etc.         │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                  ViewModels                                  │
│  DashboardViewModel, AutoPayViewModel, etc.                 │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                  Services Layer                              │
│  PaykitPaymentService, DirectoryService, SpendingLimitMgr   │
│  PubkyRingBridge                                            │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                  Background Workers                          │
│  SubscriptionCheckWorker, PaykitPollingWorker               │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                  Storage Layer                               │
│  PaykitKeychainStorage, SubscriptionStorage, AutoPayStorage │
│  ContactStorage, PaykitReceiptStore                         │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                  Integration Layer                           │
│  PaykitManager, PaykitIntegrationHelper                     │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                  External Dependencies                       │
│  PaykitMobile FFI, LDK Node, Pubky Directory                │
└─────────────────────────────────────────────────────────────┘
```

## Core Components

### PaykitManager

The central coordinator for Paykit functionality.

**Location:** `to.bitkit.paykit.PaykitManager`

**Responsibilities:**
- Initialize Paykit with wallet credentials
- Manage session state with Pubky-ring
- Coordinate between services
- Handle lifecycle events

```kotlin
@Singleton
class PaykitManager @Inject constructor(
    private val lightningRepo: LightningRepo
) {
    var isReady: Boolean
    var sessionState: SessionState
    
    fun setup()
    fun getSession(): PubkySession?
}
```

### PaykitPaymentService

High-level payment execution service.

**Location:** `to.bitkit.paykit.services.PaykitPaymentService`

**Responsibilities:**
- Detect payment types (Lightning, onchain, Paykit)
- Execute payments with appropriate method
- Enforce spending limits
- Generate and store receipts

**Payment Flow:**
```
1. Receive payment request
2. Detect payment type
3. Check spending limits (if peer pubkey provided)
4. Reserve spending amount (atomic operation)
5. Execute payment via LDK or Paykit
6. Commit or rollback spending
7. Store receipt
8. Return result
```

### DirectoryService

Handles Pubky directory operations.

**Location:** `to.bitkit.paykit.services.DirectoryService`

**Responsibilities:**
- Discover payment methods for recipients
- Publish payment endpoints
- Import profiles from Pubky-app
- Manage follows/contacts

**Directory Schema:**

The Pubky directory uses a hierarchical structure for storing Paykit-related data:

```
/pub/paykit.app/v0/
  ├── endpoints/{pubkey}/              # Payment endpoints
  │   └── {methodId}.json            # Endpoint configuration (Lightning, onchain, etc.)
  ├── subscriptions/
  │   ├── requests/{recipientPubkey}/ # Payment requests
  │   │   └── {requestId}.json      # Request metadata (amount, memo, expiry)
  │   └── proposals/{recipientPubkey}/ # Subscription proposals
  │       └── {proposalId}.json      # Proposal metadata (amount, frequency, etc.)
  └── profiles/{pubkey}/              # User profiles
      └── profile.json               # Profile data (name, bio, avatar URL)
```

**Directory Path Conventions:**
- All paths use z-base32 encoded pubkeys
- JSON files contain UTF-8 encoded metadata
- Files are versioned and can be updated atomically
- Directory listings return file names, not contents (for privacy)
- Authentication required for write operations
- Read operations are public (unauthenticated)

**Example Request File Structure:**
```json
{
  "requestId": "abc123...",
  "fromPubkey": "ybndrfg8...",
  "toPubkey": "ybndrfg8...",
  "amountSats": 10000,
  "memo": "Payment for services",
  "methodId": "lightning",
  "createdAt": 1234567890,
  "expiresAt": 1234567890
}
```

### SpendingLimitManager

Thread-safe spending limit enforcement.

**Location:** `to.bitkit.paykit.services.SpendingLimitManager`

**Responsibilities:**
- Reserve spending amounts atomically
- Commit successful payments
- Rollback failed payments
- Track per-peer limits

**Usage:**
```kotlin
spendingLimitManager.executeWithSpendingLimit(
    peerPubkey = pubkey,
    amountSats = amount
) {
    // Payment execution
    paymentResult
}
```

### PubkyRingBridge

Handles communication with Pubky-ring app.

**Location:** `to.bitkit.paykit.services.PubkyRingBridge`

**Responsibilities:**
- Request sessions via Intent
- Handle session callbacks
- Generate QR codes for cross-device auth
- Poll for cross-device session responses

**Authentication Methods:**
1. Same-device (Intent)
2. Cross-device QR code
3. Manual session entry

## Background Workers

### SubscriptionCheckWorker

Processes subscription payments in background.

**Trigger:** WorkManager PeriodicWorkRequest
**Interval:** Every 15 minutes

**Flow:**
1. Check for due subscriptions
2. Wait for node ready
3. Evaluate auto-pay rules
4. Execute approved payments
5. Send notifications

### PaykitPollingWorker

Polls Pubky directory for updates.

**Trigger:** WorkManager PeriodicWorkRequest
**Interval:** Every 15 minutes

**Checks:**
- New payment requests
- New subscription proposals
- Profile updates

## Storage Architecture

### PaykitKeychainStorage

Secure storage using Android Keystore + EncryptedSharedPreferences.

**Stored Data:**
- Receipts
- Contacts
- Subscriptions
- Auto-pay settings
- Spending limits

### Data Models

```kotlin
// Receipt
data class PaykitReceipt(
    val id: String,
    val type: PaykitReceiptType,
    val recipient: String,
    val amountSats: ULong,
    val feeSats: ULong,
    val paymentHash: String?,
    val preimage: String?,
    val timestamp: Date,
    var status: PaykitReceiptStatus
)

// Subscription
@Serializable
data class Subscription(
    val id: String,
    var providerPubkey: String,
    var amountSats: Long,
    var frequency: String,
    var isActive: Boolean,
    var nextPaymentAt: Long?
)

// Auto-Pay Rule
data class AutoPayRule(
    val id: String,
    val name: String,
    val peerPubkey: String?,
    var maxAmountSats: Long?,
    var enabled: Boolean
)
```

## Dependency Injection

Paykit uses Hilt for dependency injection:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object PaykitModule {
    
    @Provides
    @Singleton
    fun providePaykitKeychainStorage(
        @ApplicationContext context: Context
    ): PaykitKeychainStorage {
        return PaykitKeychainStorage(context)
    }
    
    @Provides
    @Singleton
    fun provideSubscriptionStorage(
        keychain: PaykitKeychainStorage
    ): SubscriptionStorage {
        return SubscriptionStorage(keychain)
    }
}
```

## Data Flow

### Payment Request Flow

```
User                    Bitkit                    Pubky Directory
  │                        │                              │
  ├─── Create Request ────▶│                              │
  │                        ├──── Publish Request ────────▶│
  │                        │                              │
  │                        │◀──── Request Published ──────┤
  │◀── Show QR/Share ──────┤                              │
```

### Payment Execution Flow

```
Payer                   Bitkit                    Recipient
  │                        │                              │
  ├─── Scan/Enter ────────▶│                              │
  │                        ├──── Discover Methods ───────▶│
  │                        │◀────── Methods List ─────────┤
  │                        │                              │
  │                        ├──── Reserve Spending ────────│
  │                        │                              │
  │                        ├──── Execute Payment ────────▶│
  │                        │◀──── Payment Preimage ───────┤
  │                        │                              │
  │                        ├──── Commit Spending ─────────│
  │                        │                              │
  │◀── Payment Success ────┤                              │
```

## Error Handling

### Error Types

```kotlin
sealed class PaykitPaymentError(override val message: String) : Exception(message) {
    object NotInitialized : PaykitPaymentError("Payment service not initialized")
    data class InvalidRecipient(val recipient: String) : PaykitPaymentError("Invalid recipient")
    object AmountRequired : PaykitPaymentError("Amount is required")
    data class PaymentFailed(override val message: String) : PaykitPaymentError(message)
    data class SpendingLimitExceeded(val remaining: Long) : PaykitPaymentError("Limit exceeded")
}
```

### Recovery Strategies

1. **Not Initialized:** Wait for node ready, retry
2. **Invalid Recipient:** Validate format, show user feedback
3. **Spending Limit:** Request manual approval
4. **Payment Failed:** Log error, offer retry

## Threading Model

- **UI:** Main thread (Compose)
- **Payment Execution:** Coroutines on `Dispatchers.IO`
- **Storage:** Coroutines on `Dispatchers.IO`
- **FFI Calls:** Dedicated thread pool

## Security Considerations

1. **Session Management:** Sessions expire and require refresh
2. **Spending Limits:** Atomic operations prevent race conditions
3. **Keystore:** Sensitive data encrypted with hardware-backed keys
4. **Preimage Handling:** Preimages are stored securely for proof-of-payment

## Related Documentation

- [Setup Guide](PAYKIT_SETUP.md)
- [Testing Guide](PAYKIT_TESTING.md)
- [Release Checklist](PAYKIT_RELEASE_CHECKLIST.md)

