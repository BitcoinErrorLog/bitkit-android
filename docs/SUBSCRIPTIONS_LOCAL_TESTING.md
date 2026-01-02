# Subscriptions Local Testing Runbook

This document describes how to test the Paykit subscriptions feature locally using the production Pubky homeserver while keeping payments in dry-run mode.

## Prerequisites

1. **Two Pubky Identities**: You need two devices or app installs, each with a different Pubky identity
2. **Pubky-ring Integration**: Both devices should have Pubky-ring installed and sessions established
3. **Dev Build**: Run the app with `./gradlew installDevDebug`

## Understanding Dry-Run Mode

The app defaults to **dry-run mode enabled**, which means:
- All subscription/autopay evaluation flows execute normally
- Notifications are sent for due payments
- **Actual payment execution is blocked** - no real sats are sent

### Verifying Dry-Run is Enabled

In Android Studio Logcat, filter by `PaykitFeatureFlags`:
```
PaykitFeatureFlags: Payment blocked: dry-run mode enabled
```

### Disabling Dry-Run (Production Testing Only)

To execute real payments, set in code or via ADB:
```kotlin
PaykitFeatureFlags.isDryRunEnabled = false
```

Or via SharedPreferences:
```bash
adb shell am broadcast -a android.intent.action.RUN \
    -p to.bitkit \
    --es key "paykit_dry_run" \
    --ez value false
```

**Warning**: Only disable dry-run when testing with regtest/testnet funds.

## Test Scenarios

### Scenario 1: Create and Send Subscription Proposal

**Device A (Provider):**

1. Open Bitkit and navigate to **Paykit Dashboard** (hamburger menu → Paykit)
2. Tap **Subscriptions**
3. Tap the **+** button to create a new subscription
4. Fill in the form:
   - **Recipient Pubkey**: Enter Device B's z32 pubkey
   - **Amount**: e.g., 1000 sats
   - **Frequency**: monthly/weekly/daily
   - **Description**: "Test subscription"
5. Tap **Send Proposal**

**Expected Result:**
- Toast shows "Proposal sent successfully"
- The proposal is published to the Pubky directory at:
  `/pub/paykit.app/v0/subscriptions/proposals/{recipientPubkey}/{proposalId}`

### Scenario 2: Discover and Accept Proposal

**Device B (Recipient):**

1. Navigate to **Paykit Dashboard → Subscriptions**
2. Tap the **Proposals** tab
3. Pull to refresh or wait for the proposals to load
4. The proposal from Device A should appear
5. Tap **Accept**
6. In the dialog:
   - Optionally enable **Autopay**
   - Set a spending limit if autopay is enabled
7. Tap **Accept**

**Expected Result:**
- Subscription appears in "My Subscriptions" tab
- Proposal is removed from Proposals tab
- If autopay enabled: AutoPayRule is created

### Scenario 3: Manage Subscription

1. In the **My Subscriptions** tab, tap on a subscription
2. View the subscription details:
   - Provider info
   - Payment amount and frequency
   - Payment history
   - Next due date
3. Toggle the subscription active/inactive with the switch
4. Delete the subscription via the trash icon

### Scenario 4: Verify Autopay Evaluation

1. Accept a subscription with autopay enabled
2. Manually trigger the subscription check worker or wait for the scheduled run
3. Check Logcat for:
   ```
   SubscriptionCheckWorker: Processing payment for subscription...
   AutoPayEvaluatorService: evaluate called for pk:provider...
   SubscriptionCheckWorker: DRY-RUN: Payment would execute...
   ```
4. Verify a notification appears: "Subscription Dry-Run"

### Scenario 5: Decline Proposal

**Device B:**

1. Navigate to **Subscriptions → Proposals**
2. Find an incoming proposal
3. Tap **Decline**
4. The proposal is removed from the list and deleted from the directory

## Verifying Directory Operations

Use the Pubky homeserver API to verify proposals are correctly stored:

```bash
# List proposals for a recipient
curl "https://homeserver.pubky.app/pub/paykit.app/v0/subscriptions/proposals/{recipientPubkey}/" \
  -H "pubky-host: {recipientPubkey}"

# Fetch a specific proposal
curl "https://homeserver.pubky.app/pub/paykit.app/v0/subscriptions/proposals/{recipientPubkey}/{proposalId}" \
  -H "pubky-host: {recipientPubkey}"
```

## Troubleshooting

### Proposals Not Appearing

1. Check that both devices are using the same homeserver
2. Verify the recipient pubkey is correct (z32 format)
3. Check Logcat for `DirectoryService` errors
4. Ensure Pubky-ring session is valid and not expired

### Autopay Not Evaluating

1. Verify `AutoPaySettings.isEnabled = true` in storage
2. Check that an `AutoPayRule` was created for the subscription
3. Verify the rule's `peerPubkey` matches the subscription's `providerPubkey`

### Worker Not Running

1. Check WorkManager status:
   ```kotlin
   WorkManager.getInstance(context)
       .getWorkInfosForUniqueWorkLiveData(SubscriptionCheckWorker.WORK_NAME)
   ```
2. Verify constraints (network required) are met

## Related Files

- `PaykitSubscriptionsScreen.kt` - Main UI
- `SubscriptionDetailScreen.kt` - Detail view
- `SubscriptionsViewModel.kt` - Business logic
- `DirectoryService.kt` - Pubky directory operations
- `SubscriptionCheckWorker.kt` - Background due processing
- `PaykitFeatureFlags.kt` - Dry-run gate
- `AutoPayEvaluatorService.kt` - Autopay rule evaluation

