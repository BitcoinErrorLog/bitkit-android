# Transfer System

## Overview
Transfers are first-class domain objects that track funds moving between onchain and lightning balances. They enable proper balance calculations and UI display of pending transfers.

## Transfer Types

### TransferType Enum
- `TO_SPENDING`: LSP channel purchase
- `MANUAL_SETUP`: Manual channel open
- `TO_SAVINGS`: Generic channel close
- `COOP_CLOSE`: Cooperative channel close
- `FORCE_CLOSE`: Force channel close

See: `TransferType.kt`

### Type Categories

Transfers are grouped into two categories:
- **toSpending**: `TO_SPENDING`, `MANUAL_SETUP` (funds moving from onchain → lightning)
- **toSavings**: `TO_SAVINGS`, `COOP_CLOSE`, `FORCE_CLOSE` (funds moving from lightning → onchain)

See: `TransferType.kt` for category extension functions

## Data Model

### TransferEntity

See: `TransferEntity.kt`

**Field Descriptions:**
- `id`: UUID primary key (immutable)
- `type`: Transfer type enum
- `amountSats`: Amount being transferred
- `channelId`: LDK channel ID (for manual channels only; LSP channels use lspOrderId)
- `fundingTxId`: Channel funding transaction ID
- `lspOrderId`: Blocktank order ID (LSP flow only)
- `isSettled`: false = affects balances, true = complete
- `createdAt`: Unix timestamp (ms) when transfer created
- `settledAt`: Unix timestamp (ms) when settled

## Lifecycle Flows

### LSP Channel Purchase (TO_SPENDING)

**Phase 1: Payment**
```
User confirms amount
    ↓
Create Blocktank order
    ↓
Send onchain payment to LSP
    ↓
CREATE TRANSFER:
  id = UUID
  type = TO_SPENDING
  amountSats = order.clientBalanceSat
  channelId = null (not available yet)
  fundingTxId = null
  lspOrderId = order.id
  isSettled = false
```

**Phase 2: Channel Appears**
```
Poll order status OR receive ChannelPending event
    ↓
Channel appears in LDK with fundingTxo
    ↓
Just-in-time resolution via lspOrderId:
  order.channel.fundingTx.id → match LDK channel.fundingTxo.txid
```

**Phase 3: Channel Ready**
```
ChannelReady event OR isChannelReady becomes true
    ↓
SETTLE TRANSFER:
  isSettled = true
  settledAt = now
```

### Manual Channel Open (MANUAL_SETUP)

**Phase 1: Open**
```
User enters node URI + amount
    ↓
Call lightningRepo.openChannel()
    ↓
Await ChannelPending event
    ↓
CREATE TRANSFER:
  id = UUID
  type = MANUAL_SETUP
  amountSats = channelValueSats
  channelId = event.channelId (available immediately!)
  fundingTxId = event.fundingTxo.txid
  lspOrderId = null
  isSettled = false
```

**Phase 2: Channel Ready**
```
ChannelReady event OR isChannelReady becomes true
    ↓
SETTLE TRANSFER:
  isSettled = true
  settledAt = now
```

### Channel Close (COOP_CLOSE / FORCE_CLOSE)

**Phase 1: Close Initiated**
```
User selects channels to close
    ↓
Call closeChannel(force = true/false)
    ↓
CREATE TRANSFER:
  id = UUID
  type = COOP_CLOSE or FORCE_CLOSE (WE KNOW which!)
  amountSats = channel.balanceSats
  channelId = channel.channelId
  fundingTxId = channel.fundingTxo.txid
  lspOrderId = null
  isSettled = false
```

**Phase 2: Channel Closed**
```
ChannelClosed event fires
    ↓
Channel removed from list_channels()
    ↓
Balance still in lightning_balances
    ↓
Transfer remains active
```

**Phase 3: Funds Swept**
```
Output sweeper broadcasts claim tx
    ↓
Claim tx confirms
    ↓
Balance removed from lightning_balances
    ↓
SETTLE TRANSFER:
  isSettled = true
  settledAt = now
```

## State Management

### Channel ID Resolution

**Just-in-time channelId resolution** for transfer state checks.

For LSP orders:
1. Fetch order from blocktankRepo using `transfer.lspOrderId`
2. Extract `fundingTxId` from `order.channel.fundingTx.id`
3. Match LDK channel by `fundingTxo.txid`
4. Return matched `channelId`

For manual transfers:
- Return `transfer.channelId` directly (already populated)

**Benefits:**
- Single source of truth: Blocktank order data
- No state updates needed
- Reliable fundingTxId matching
- Only resolves on-demand

See: `TransferRepo.resolveChannelIdForTransfer()`

### Creation Points
- TO_SPENDING: When payment sent (`TransferViewModel.onTransferToSpendingConfirm`)
- MANUAL_SETUP: When ChannelPending event received (`ExternalNodeViewModel.onConfirm`)
- COOP_CLOSE: When close initiated (`TransferViewModel.closeSelectedChannels`)
- FORCE_CLOSE: When force close initiated (`TransferViewModel.forceTransfer`)

### Update Points
- All types: When sync detects state changes (`TransferRepo.syncTransferStates`)
- Just-in-time channelId resolution via `resolveChannelIdForTransfer()`

### Settlement Conditions
- toSpending (TO_SPENDING/MANUAL_SETUP): When channel.isChannelReady == true
- toSavings (TO_SAVINGS/COOP_CLOSE/FORCE_CLOSE): When balance.channelId not in lightning_balances

### Event-Driven Updates
Listen to LDK events for immediate state transitions:
- ChannelReady → Check and settle toSpending transfers
- ChannelClosed → Already created transfer, no action

### Polling Updates
`syncTransferStates()` runs periodically to catch missed events:
- Check all active toSpending transfers for isChannelReady
- Check all active toSavings transfers for balance absence

## Activity Correlation

### Onchain Activities
Channel funding transactions are marked as transfers via `TransactionMetadata` stored in cache. Activity sync applies this metadata to display "Transfer to Spending" label in UI.

See: `ActivityRepo.kt` for metadata application logic

### Correlation Fields
- TO_SPENDING/MANUAL_SETUP: `Activity.txId == Transfer.fundingTxId`
- TO_SAVINGS/etc: `Activity.channelId == Transfer.channelId`

## Balance Calculation using Transfers

Transfer states are used to adjust displayed balances to match UX requirements.

**toSpending transfers:**
- Subtract pending channel balance from lightning total
- Only count channels that exist but aren't ready yet
- Prevents showing funds as "spendable" before channel is usable

**toSavings transfers:**
- Subtract closing channel balance from lightning total
- Prevents showing funds that are being swept to onchain

See: `WalletRepo.syncBalances()`

## Edge Cases & Failure Handling

### LSP Order Expires
```
Detection: Order state == EXPIRED
Action: Mark transfer as settled (it failed, no longer affects balance)
Consider: Add failureReason field to track this
```

### App Restarts During Transfer
```
Recovery: On app startup, syncTransferStates() checks all active transfers
Matches to current LDK state and settles if appropriate
```

### Channel ID Resolution (LSP Flow)
```
Just-in-time resolution via resolveChannelIdForTransfer():
1. If transfer.lspOrderId exists:
   - Fetch order from blocktankRepo
   - Extract fundingTxId from order.channel.fundingTx.id
   - Match LDK channel by fundingTxo.txid
   - Return matched channelId
2. Otherwise: return transfer.channelId (manual channels)

No state updates needed - channelId resolved on-demand when checking transfer state
```

### Coop Close Becomes Force Close
```
Update transfer type:
transferRepo.updateType(transferId, FORCE_CLOSE)
```

### Duplicate Transfer Prevention
```
Before creating transfer, check:
- For TO_SPENDING: No active transfer with same lspOrderId
- For MANUAL_SETUP: No active transfer with same channelId
- For closes: No active transfer with same channelId
```

## Backup & Restore

### Backup Category: "Boosts & Transfers"

Transfers are serialized to JSON and included in backup. Restore deserializes and inserts into database.

### Cleanup Policy
- Keep settled transfers for 30 days
- Allows backup to include recent history
- Automatic cleanup via periodic job

## Testing Scenarios

### TO_SPENDING Happy Path
1. Create order, send payment
2. Verify transfer created with lspOrderId
3. Wait for channel to appear
4. Wait for ChannelReady
5. Verify transfer settled

### MANUAL_SETUP Happy Path
1. Open channel
2. Verify transfer created on ChannelPending
3. Wait for ChannelReady
4. Verify transfer settled

### COOP_CLOSE Happy Path
1. Close channel
2. Verify transfer created
3. Wait for balance to disappear
4. Verify transfer settled

### App Restart During Transfer
1. Create transfer
2. Kill app
3. Restart app
4. Verify transfer still active and tracks correctly

### LSP Order Just-in-Time Resolution
1. Create transfer with lspOrderId (channelId = null)
2. Verify resolveChannelIdForTransfer() returns null initially
3. Wait for order.channel to populate
4. Verify resolveChannelIdForTransfer() resolves channelId via fundingTxId match
5. Verify balance calculated correctly throughout
