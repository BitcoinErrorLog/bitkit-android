# Paykit Integration Discovery - Android

This document outlines the integration points for connecting Paykit-rs with Bitkit Android.

## Repository Structure

### Key Repositories

#### LightningRepo
- **Location**: `app/src/main/java/to/bitkit/repositories/LightningRepo.kt`
- **Type**: Singleton (via Dagger/Hilt: `@Singleton`)
- **Purpose**: Manages Lightning Network operations via LightningService
- **Dependencies**: `LightningService`, `CoreService`, `LdkNodeEventBus`

**Key Methods for Paykit Integration**:

1. **Lightning Payment**:
   ```kotlin
   suspend fun payInvoice(bolt11: String, sats: ULong? = null): Result<PaymentId>
   ```
   - **Location**: Line 521
   - **Returns**: `Result<PaymentId>` (from LDKNode)
   - **Usage**: Pay Lightning invoices
   - **Error Handling**: Returns `Result.failure(Exception)` on error

2. **Onchain Payment**:
   ```kotlin
   suspend fun sendOnChain(
       address: Address,
       sats: ULong,
       speed: TransactionSpeed? = null,
       utxosToSpend: List<SpendableUtxo>? = null,
       feeRates: FeeRates? = null,
       isTransfer: Boolean = false,
       channelId: String? = null,
       isMaxAmount: Boolean = false
   ): Result<Txid>
   ```
   - **Location**: Line 529
   - **Returns**: `Result<Txid>` (from LDKNode)
   - **Usage**: Send Bitcoin on-chain
   - **Error Handling**: Returns `Result.failure(Exception)` on error

3. **Payment Access**:
   ```kotlin
   suspend fun getPayments(): Result<List<PaymentDetails>>
   ```
   - **Location**: Line 608
   - **Returns**: List of payment details
   - **Usage**: Get payment status and preimage

4. **Fee Estimation**:
   ```kotlin
   suspend fun estimateRoutingFees(bolt11: String): Result<ULong>
   ```
   - **Location**: Line 843
   - **Returns**: Routing fee in millisatoshis

5. **Onchain Fee Calculation**:
   ```kotlin
   suspend fun calculateTotalFee(
       amountSats: ULong,
       address: Address? = null,
       speed: TransactionSpeed? = null,
       utxosToSpend: List<SpendableUtxo>? = null,
       feeRates: FeeRates? = null
   ): Result<ULong>
   ```
   - **Location**: Line 618
   - **Returns**: Total fee in satoshis

#### WalletRepo
- **Location**: `app/src/main/java/to/bitkit/repositories/WalletRepo.kt`
- **Type**: Singleton (via Dagger/Hilt: `@Singleton`)
- **Purpose**: Manages wallet state and balance operations
- **Dependencies**: `CoreService`, `LightningRepo`

**Key Methods for Paykit Integration**:

1. **Transaction Lookup**: Use `ActivityRepo` or `CoreService` for transaction queries
2. **Balance Management**: Access via `balanceState` Flow

#### CoreService
- **Location**: `app/src/main/java/to/bitkit/services/CoreService.kt`
- **Type**: Singleton (via Dagger/Hilt)
- **Purpose**: Core wallet operations and activity management

## API Mappings for Paykit Executors

### BitcoinExecutorFfi Implementation

#### sendToAddress
- **Bitkit API**: `LightningRepo.sendOnChain(address:sats:speed:utxosToSpend:feeRates:isTransfer:channelId:isMaxAmount:)`
- **Suspend Pattern**: `suspend -> Result<Txid>`
- **Bridging**: Use `runBlocking` with `withTimeout`
- **Return Mapping**: 
  - `Result<Txid>` → Extract `.hex` on success
  - Need to query transaction for fee, vout, confirmations

#### estimateFee
- **Bitkit API**: `LightningRepo.calculateTotalFee(amountSats:address:speed:utxosToSpend:feeRates:)`
- **Return**: Fee in satoshis for target blocks
- **Mapping**: Convert `TransactionSpeed` to target blocks

#### getTransaction
- **Bitkit API**: Use `ActivityRepo` or `CoreService` to lookup transaction
- **Query**: By `txid` (String)
- **Return**: `BitcoinTxResultFfi` or `null`

#### verifyTransaction
- **Bitkit API**: Get transaction via `getTransaction`, verify outputs
- **Return**: Boolean

### LightningExecutorFfi Implementation

#### payInvoice
- **Bitkit API**: `LightningRepo.payInvoice(bolt11:sats:)`
- **Suspend Pattern**: `suspend -> Result<PaymentId>`
- **Bridging**: Use `runBlocking` with `withTimeout`
- **Payment Completion**: 
  - Poll `LightningRepo.getPayments()` every 100-500ms
  - Find payment by `PaymentId` or `paymentHash`
  - Extract preimage from `PaymentDetails.preimage`
- **Return Mapping**: 
  - `PaymentId` → Extract for payment lookup
  - Extract preimage from payment details

#### decodeInvoice
- **Bitkit API**: `com.synonym.bitkitcore.decode(invoice: String)` → `LightningInvoice`
- **Mapping**:
  - `LightningInvoice.paymentHash` → `DecodedInvoiceFfi.paymentHash`
  - `LightningInvoice.amountMsat` → `DecodedInvoiceFfi.amountMsat`
  - `LightningInvoice.description` → `DecodedInvoiceFfi.description`
  - `LightningInvoice.payeePubkey` → `DecodedInvoiceFfi.payee`
  - `LightningInvoice.expiry` → `DecodedInvoiceFfi.expiry`
  - `LightningInvoice.timestamp` → `DecodedInvoiceFfi.timestamp`

#### estimateFee
- **Bitkit API**: `LightningRepo.estimateRoutingFees(bolt11:)`
- **Return**: Fee in millisatoshis

#### getPayment
- **Bitkit API**: `LightningRepo.getPayments()` → `Result<List<PaymentDetails>>`
- **Find by**: `paymentHash` (compare hex strings)
- **Extract**: `PaymentDetails.preimage`, `amountMsat`, `feePaidMsat`, `status`

#### verifyPreimage
- **Implementation**: Use `java.security.MessageDigest.getInstance("SHA-256")`
- **Compute**: SHA256 of preimage bytes
- **Compare**: With payment hash (case-insensitive)

## Error Types

### Result<T> Pattern
- **Pattern**: Kotlin `Result<T>` type
- **Success**: `Result.success(value)`
- **Failure**: `Result.failure(exception)`
- **Mapping to PaykitMobileException**:
  ```kotlin
  result.fold(
      onSuccess = { value -> /* handle success */ },
      onFailure = { error ->
          throw PaykitMobileException.Transport(
              "Operation failed: ${error.message ?: error.toString()}"
          )
      }
  )
  ```

## Network Configuration

### Current Network Setup
- **Location**: `app/src/main/java/to/bitkit/env/Env.kt`
- **Property**: `Env.network: Network` (from LDKNode)
- **Values**: `Network.BITCOIN`, `Network.TESTNET`, `Network.REGTEST`, `Network.SIGNET`
- **Mapping to Paykit**:
  - `Network.BITCOIN` → `BitcoinNetworkFfi.MAINNET` / `LightningNetworkFfi.MAINNET`
  - `Network.TESTNET` → `BitcoinNetworkFfi.TESTNET` / `LightningNetworkFfi.TESTNET`
  - `Network.REGTEST` → `BitcoinNetworkFfi.REGTEST` / `LightningNetworkFfi.REGTEST`
  - `Network.SIGNET` → `BitcoinNetworkFfi.TESTNET` / `LightningNetworkFfi.TESTNET` (fallback)

## Async/Sync Patterns

### Current Pattern
- **Bitkit Repositories**: All use `suspend` functions (Kotlin coroutines)
- **Paykit FFI**: Synchronous methods
- **Bridging Strategy**: Use `runBlocking` with `withTimeout`

### Example Bridge Pattern
```kotlin
fun syncMethod(): Result {
    return runBlocking {
        withTimeout(30.seconds) {
            try {
                val result = suspendMethod()
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
```

## Thread Safety

- **LightningRepo**: Singleton via Hilt, thread-safe
- **CoreService**: Singleton via Hilt, thread-safe
- **FFI Methods**: May be called from any thread
- **Strategy**: `runBlocking` handles thread safety for suspend functions

## Payment Completion Handling

### Polling Approach (Required)
```kotlin
val paymentIdResult = lightningRepo.payInvoice(bolt11 = invoice, sats = sats)
val paymentId = paymentIdResult.getOrThrow()

// Poll for completion
var attempts = 0
while (attempts < 60) {
    val paymentsResult = lightningRepo.getPayments()
    paymentsResult.onSuccess { payments ->
        val payment = payments.find { it.paymentId == paymentId }
        if (payment != null && payment.status == PaymentStatus.Succeeded) {
            val preimage = payment.preimage
            // Payment completed
            return@runBlocking preimage
        }
    }
    delay(1000) // 1 second
    attempts++
}
throw PaykitMobileException.NetworkTimeout("Payment timeout")
```

## Transaction Details Extraction

### Challenge
- `LightningRepo.sendOnChain()` returns only `Txid`
- `BitcoinTxResultFfi` needs: fee, vout, confirmations, blockHeight

### Solution
1. Return initial result with `confirmations: 0`, `blockHeight: null`
2. Query transaction details after broadcast:
   ```kotlin
   val txidResult = lightningRepo.sendOnChain(...)
   val txid = txidResult.getOrThrow()
   delay(2000) // Wait for propagation
   val txDetails = activityRepo.getTransaction(txid)
   // Extract fee, vout, confirmations
   ```

## Dependency Injection

### Current Setup
- **Framework**: Hilt (Dagger)
- **Pattern**: Constructor injection with `@Inject`
- **Example**:
  ```kotlin
  @Singleton
  class BitkitBitcoinExecutor @Inject constructor(
      private val lightningRepo: LightningRepo,
      private val coreService: CoreService
  ) : BitcoinExecutorFfi {
      // Implementation
  }
  ```

## File Structure for Integration

### Proposed Structure
```
app/src/main/java/to/bitkit/paykit/
├── PaykitManager.kt
├── PaykitIntegrationHelper.kt
├── executors/
│   ├── BitkitBitcoinExecutor.kt
│   └── BitkitLightningExecutor.kt
└── services/
    └── PaykitPaymentService.kt
```

## Dependencies

### Current Dependencies
- `org.lightningdevkit.ldknode`: Lightning Network node implementation
- `com.synonym.bitkitcore`: Core wallet functionality
- `kotlinx.coroutines`: Coroutine support

### New Dependencies
- `com.paykit.mobile`: Generated UniFFI bindings (to be added)

## Build Configuration

### Gradle Setup
- **Location**: `app/build.gradle.kts`
- **NDK**: May need NDK configuration for Rust libraries
- **jniLibs**: Place `.so` files in `app/src/main/jniLibs/<arch>/`

### Build Targets
- `aarch64-linux-android` (arm64-v8a)
- `armv7-linux-androideabi` (armeabi-v7a)
- `x86_64-linux-android` (x86_64) - for emulator

## Next Steps

1. ✅ Discovery complete (this document)
2. ⏳ Set up Paykit-rs dependency
3. ⏳ Generate UniFFI bindings
4. ⏳ Configure Gradle build settings
5. ⏳ Implement executors
6. ⏳ Register executors with PaykitClient
7. ⏳ Integration testing
