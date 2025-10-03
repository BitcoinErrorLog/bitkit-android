# Balance Management in Bitkit

## LDK-Node Balance Mechanics

### BalanceDetails Structure
LDK-node provides balance information through `BalanceDetails`:
- `total_onchain_balance_sats`: Total onchain wallet balance (all UTXOs including unconfirmed)
- `spendable_onchain_balance_sats`: Spendable balance (minus anchor reserves)
- `total_lightning_balance_sats`: Sum of all claimable lightning balances
- `lightning_balances`: Detailed list of balance types per channel
- `pending_balances_from_channel_closures`: Delayed outputs being swept (CSV timelock cases)

### Lightning Balance Types
`lightning_balances` contains different balance variants:

**ClaimableOnChannelClose**
- Appears: Channel is open (pending or ready)
- Meaning: Amount claimable if channel force-closes now
- Key insight: Appears IMMEDIATELY when channel enters pending state
- Stays until: Channel closes

**ClaimableAwaitingConfirmations**
- Appears: After channel close, output awaiting confirmations
- Meaning: Balance is ours, waiting for blockchain confirmations

**ContentiousClaimable**
- Appears: Dispute scenario
- Meaning: Must claim before timeout or counterparty might take it

**MaybeTimeoutClaimableHTLC / MaybePreimageClaimableHTLC**
- HTLCs that might be claimable under certain conditions

**CounterpartyRevokedOutputClaimable**
- Counterparty broadcast revoked state
- We can claim all outputs

### Balance State Machine - Channel Open

```
User initiates channel open
         ↓
Funding tx broadcast
         ↓
ChannelPending event fires
         ↓
Monitor created with funding_spend_confirmed = None
         ↓
get_claimable_balances() returns ClaimableOnChannelClose
         ↓
total_lightning_balance_sats INCLUDES channel value ← IMMEDIATE
         ↓
total_onchain_balance_sats INCLUDES funding UTXO
         ↓
[Funding tx confirms]
         ↓
ChannelReady event fires
         ↓
Channel becomes usable
         ↓
total_lightning_balance_sats STILL includes channel value
total_onchain_balance_sats STILL includes funding UTXO
```

**Key Insight:** LDK does NOT remove funding UTXO from onchain balance. It's filtered via `is_funding_transaction()` when building spend transactions, but remains in `balance.total()`.

**Double Counting Issue:** During channel open, the same sats appear in:
1. `total_onchain_balance_sats` (as funding UTXO)
2. `total_lightning_balance_sats` (as ClaimableOnChannelClose)

### Balance State Machine - Channel Close

```
Channel close initiated
         ↓
ChannelClosed event fires
         ↓
Channel removed from list_channels()
         ↓
Balance remains in lightning_balances (various types)
         ↓
Output sweeper claims funds
         ↓
[Normal case: NO entry in pending_balances_from_channel_closures]
[CSV delay case: Entry in pending_balances_from_channel_closures]
         ↓
Sweep tx confirms
         ↓
Balance removed from lightning_balances
         ↓
Funds appear in total_onchain_balance_sats
```

**Key Insight:** `pending_balances_from_channel_closures` is ONLY for delayed outputs (CSV timelocks). Normal coop/force closes show balances in `lightning_balances` until swept.

## How ClaimableOnChannelClose Is Calculated

### The Formula

The claimable amount equals our total channel balance (`to_self_value_sat`) plus any pending inbound HTLCs, excluding on-chain fees.

### Understanding Channel Reserves

Lightning channels have two reserves:

**holder_selected_channel_reserve** (aka `unspendable_punishment_reserve`):
- The reserve **WE** must hold for ourselves
- Cannot be spent over Lightning
- Ensures counterparty can punish us if we broadcast revoked state
- Maps to `ChannelDetails.unspendablePunishmentReserve`

**counterparty_selected_channel_reserve** (aka `counterparty_unspendable_punishment_reserve`):
- The reserve **THEY** must hold for themselves
- We cannot spend it (it's their minimum balance)
- Ensures we can punish them if they broadcast revoked state
- Maps to `ChannelDetails.counterpartyUnspendablePunishmentReserve`

### Channel Balance Decomposition

The total channel value is split between our balance and the remote peer's balance.

Our balance in millisatoshis consists of our outbound capacity, pending outbound HTLCs, and the counterparty's reserve.

**Why counterparty's reserve is included:**
- `outbound_capacity_msat` excludes their reserve (we can't spend it while channel is open)
- On channel close, their reserve comes back to us as part of our final balance
- Therefore our value on close equals outbound capacity plus counterparty's reserve

### Reconstructing value_to_self_sat from ChannelDetails

To calculate our balance in the channel (what we'd get on close):
- Start with `outboundCapacityMsat / 1000` (what we can spend now)
- Add `counterpartyUnspendablePunishmentReserve` (their reserve, returned to us on close)

**Common Mistake:** Adding `unspendablePunishmentReserve` instead of `counterpartyUnspendablePunishmentReserve`:
- `unspendablePunishmentReserve` is OUR reserve (already excluded from capacity)
- `counterpartyUnspendablePunishmentReserve` is THEIR reserve (we get it back on close)

See: `ChannelDetails.valueToSelfSats` extension in `ChannelDetails.kt`

## Bitkit UX Requirements

### Displayed Balances Should Reflect Usable/Spendable Amounts

**Problem:** LDK Node's raw balances don't match our UX specs:
- Pending channel funding shows in lightning balance before usable
- Channel funding shows in both onchain and lightning (double count)

**Solution:** Adjust balances based on Transfer state

### Balance Adjustments

**toSpending transfers (TO_SPENDING, MANUAL_SETUP):**
- Filter active transfers by type
- For each: resolve channelId, find matching channel
- If channel exists but not ready: subtract its balance from lightning total
- **Result:** User doesn't see pending channel funds as "spendable"

**toSavings transfers (TO_SAVINGS, COOP_CLOSE, FORCE_CLOSE):**
- Filter active transfers by type
- For each: resolve channelId, find matching lightning balance
- Subtract from lightning total
- **Result:** User doesn't see funds being swept as "available"

See: `WalletRepo.syncBalances()`

### Channel State for Incoming Transfers

**Do NOT use `isUsable`** - it becomes false when peer disconnects.

**Use `isChannelReady`** - only becomes true after:
- Funding tx confirmed
- channel_ready messages exchanged
- Stays true even if peer disconnects

## Edge Cases

**LSP Channel (TO_SPENDING):**
- Payment sent, waiting for LSP to open channel
- Channel appears in pending state
- Balance shows in total_lightning_balance_sats
- We subtract it until isChannelReady

**Manual Channel (MANUAL_SETUP):**
- Same as LSP but we have channelId immediately

**Coop Close:**
- Balance stays in lightning_balances as ClaimableAwaitingConfirmations
- We subtract it until it disappears (swept)

**Force Close:**
- Balance may have CSV delay
- Shows in lightning_balances with delay info
- We subtract it until it disappears

**Breach Remedy:**
- CounterpartyRevokedOutputClaimable appears
- We treat it as outgoing transfer and subtract
