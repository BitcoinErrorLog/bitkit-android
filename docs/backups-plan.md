# Backup Implementation Plan

## Overview

This document outlines the implementation plan for completing the backup system in bitkit-android. Currently, only **SETTINGS** and **WIDGETS** categories are fully implemented. This plan covers the remaining categories: **WALLET**, **METADATA**, **BLOCKTANK**, **ACTIVITY** (formerly LDK_ACTIVITY), and **LIGHTNING_CONNECTIONS** (display-only).

**Last Updated:** 2025-10-30

---

## Goals

1. **Backup all data stores comprehensively** - CacheStore, SettingsStore, WidgetsStore, Room DB (AppDb), and CoreService-managed data
2. **Use JSON serialization** (not raw database files) for maintainability, idempotency, and version migration
3. **Implement idempotent restore** - safe to restore multiple times without duplicates
4. **Category independence** - each category backs up and restores independently
5. **Display-only status for LIGHTNING_CONNECTIONS** - show ldk-node's native backup timing
6. **Descope SLASHTAGS** - comment out for v1, plan for v2

---

## Architecture

### Data Stores in bitkit-android

| Store | Type | Contents | Backed Up By |
|-------|------|----------|--------------|
| **SettingsStore** | DataStore | User preferences, UI state | SETTINGS ✅ |
| **WidgetsStore** | DataStore | Widget config, cached widget data | WIDGETS ✅ |
| **CacheStore** | DataStore | Boosted activities, tx metadata, paid orders, balance cache | WALLET, METADATA, BLOCKTANK |
| **AppDb** (Room) | SQLite | Transfers, tag metadata, config | WALLET, METADATA |
| **activity.db** (CoreService) | SQLite | All activities (onchain + lightning) | ACTIVITY |
| **blocktank.db** (CoreService) | SQLite | Orders, CJIT entries | BLOCKTANK |
| **LDK storage** | Native | Channel state, monitors | LDK (native backup) |

### Backup Categories

| Category | Status | Data Source | Backup Method | Change Detection |
|----------|--------|-------------|---------------|------------------|
| SETTINGS | ✅ Implemented | SettingsStore | JSON serialization | DataStore flow observer |
| WIDGETS | ✅ Implemented | WidgetsStore | JSON serialization | DataStore flow observer |
| WALLET | ⏳ To implement | CacheStore + TransferDao | JSON serialization | CacheStore + Room flows |
| METADATA | ⏳ To implement | CacheStore + TagMetadataDao | JSON serialization | CacheStore + Room flows |
| BLOCKTANK | ⏳ To implement | CacheStore + CoreService | JSON serialization | CacheStore + event callbacks |
| ACTIVITY | ⏳ To implement | CoreService.activity (ALL) | JSON serialization | Manual trigger only |
| LIGHTNING_CONNECTIONS | ⏳ Display-only | LightningService.status | N/A (ldk-node native) | Status timestamp observer |
| ~~SLASHTAGS~~ | 🚫 Descoped | N/A | N/A | N/A |

---

## Design Decisions

### Why JSON Serialization (Not Raw DB Files)?

**Advantages:**
- ✅ **No file locking issues** - read data via DAOs/APIs, not file I/O
- ✅ **Schema evolution** - easy to migrate between versions
- ✅ **Selective restore** - validate and transform data before mutation
- ✅ **Smaller size** - only serialize needed data, not DB overhead
- ✅ **Idempotency** - upsert by stable keys prevents duplicates
- ✅ **Testability** - can inspect and mock JSON payloads
- ✅ **Cross-version compatibility** - handles schema changes gracefully

**Rejected Alternative:** Copying raw `activity.db` and `blocktank.db` files
- ❌ Requires database locks/close
- ❌ Schema changes break restores
- ❌ Harder to deduplicate on restore
- ❌ Larger backup sizes

### Rename LDK_ACTIVITY → ACTIVITY

The category now backs up **ALL** activities (onchain + lightning), not just lightning activities. The name change reflects this scope accurately.

---

## Implementation Details

### 1. Payload Schemas

All payloads include version and timestamp for migration support.

```kotlin
@Serializable
data class WalletBackupV1(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val boostedActivities: List<PendingBoostActivity>,
    val transfers: List<TransferEntity>,
)

@Serializable
data class MetadataBackupV1(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val tagMetadata: List<TagMetadataEntity>,
    val transactionsMetadata: List<TransactionMetadata>,
)

@Serializable
data class BlocktankBackupV1(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val paidOrders: Map<String, String>, // orderId -> txId
    val orders: List<SerializableOrder>,
    val cjitEntries: List<SerializableCjitEntry>,
)

@Serializable
data class SerializableOrder(
    val id: String,
    val state: String,
    // Essential fields only
)

@Serializable
data class SerializableCjitEntry(
    val channelSizeSat: ULong,
    val invoice: String,
    // Essential fields only
)

@Serializable
data class ActivityBackupV1(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val activities: List<Activity>, // ALL activities (onchain + lightning)
)
```

### 2. WALLET Backup Implementation

**Data sources:**
- `CacheStore.pendingBoostActivities`
- `TransferDao.getAll()`

**Backup:**
```kotlin
BackupCategory.WALLET -> {
    val boostedActivities = cacheStore.data.first().pendingBoostActivities
    val transfers = transferDao.getAll()
    
    val payload = WalletBackupV1(
        boostedActivities = boostedActivities,
        transfers = transfers
    )
    
    json.encodeToString(payload).toByteArray()
}
```

**Restore:**
```kotlin
performRestore(BackupCategory.WALLET) { dataBytes ->
    val payload = json.decodeFromString<WalletBackupV1>(String(dataBytes))
    
    // Restore transfers (idempotent via primary key)
    db.withTransaction {
        payload.transfers.forEach { transfer ->
            transferDao.upsert(transfer)
        }
    }
    
    // Restore boosted activities (idempotent via txId)
    payload.boostedActivities.forEach { activity ->
        cacheStore.addActivityToPendingBoost(activity)
    }
}
```

**Change detection:**
```kotlin
// Observe boosted activities
val boostJob = scope.launch {
    cacheStore.data
        .map { it.pendingBoostActivities }
        .distinctUntilChanged()
        .drop(1)
        .collect {
            if (!isRestoring) {
                markBackupRequired(BackupCategory.WALLET)
            }
        }
}

// Observe transfers
val transfersJob = scope.launch {
    transferDao.observeAll()
        .distinctUntilChanged()
        .drop(1)
        .collect {
            if (!isRestoring) {
                markBackupRequired(BackupCategory.WALLET)
            }
        }
}
```

**New DAO methods needed:**
```kotlin
@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers")
    suspend fun getAll(): List<TransferEntity>
    
    @Query("SELECT * FROM transfers")
    fun observeAll(): Flow<List<TransferEntity>>
    
    @Upsert
    suspend fun upsert(entity: TransferEntity)
}
```

---

### 3. METADATA Backup Implementation

**Data sources:**
- `TagMetadataDao.getAll()`
- `CacheStore.transactionsMetadata`

**Backup:**
```kotlin
BackupCategory.METADATA -> {
    val tagMetadata = db.tagMetadataDao().getAll()
    val txMetadata = cacheStore.data.first().transactionsMetadata
    
    val payload = MetadataBackupV1(
        tagMetadata = tagMetadata,
        transactionsMetadata = txMetadata
    )
    
    json.encodeToString(payload).toByteArray()
}
```

**Restore:**
```kotlin
performRestore(BackupCategory.METADATA) { dataBytes ->
    val payload = json.decodeFromString<MetadataBackupV1>(String(dataBytes))
    
    // Restore tag metadata (idempotent via primary key)
    db.withTransaction {
        payload.tagMetadata.forEach { entity ->
            db.tagMetadataDao().upsert(entity)
        }
    }
    
    // Restore transaction metadata (idempotent via txId)
    payload.transactionsMetadata.forEach { metadata ->
        cacheStore.addTransactionMetadata(metadata)
    }
}
```

**Change detection:**
```kotlin
// Observe tag metadata
val metadataJob = scope.launch {
    db.tagMetadataDao().observeAll()
        .distinctUntilChanged()
        .drop(1)
        .collect {
            if (!isRestoring) {
                markBackupRequired(BackupCategory.METADATA)
            }
        }
}

// Observe transaction metadata
val txMetadataJob = scope.launch {
    cacheStore.data
        .map { it.transactionsMetadata }
        .distinctUntilChanged()
        .drop(1)
        .collect {
            if (!isRestoring) {
                markBackupRequired(BackupCategory.METADATA)
            }
        }
}
```

**New DAO methods needed:**
```kotlin
@Dao
interface TagMetadataDao {
    @Query("SELECT * FROM tag_metadata")
    suspend fun getAll(): List<TagMetadataEntity>
    
    @Query("SELECT * FROM tag_metadata")
    fun observeAll(): Flow<List<TagMetadataEntity>>
    
    @Upsert
    suspend fun upsert(entity: TagMetadataEntity)
}
```

---

### 4. BLOCKTANK Backup Implementation

**Data sources:**
- `CacheStore.paidOrders`
- `CoreService.blocktank.orders(refresh = false)`
- `CoreService.blocktank.cjitEntries(refresh = false)`

**Backup:**
```kotlin
BackupCategory.BLOCKTANK -> {
    val paidOrders = cacheStore.data.first().paidOrders
    val orders = coreService.blocktank.orders(refresh = false)
    val cjitEntries = coreService.blocktank.cjitEntries(refresh = false)
    
    val payload = BlocktankBackupV1(
        paidOrders = paidOrders,
        orders = orders.map { it.toSerializable() },
        cjitEntries = cjitEntries.map { it.toSerializable() }
    )
    
    json.encodeToString(payload).toByteArray()
}
```

**Restore:**
```kotlin
performRestore(BackupCategory.BLOCKTANK) { dataBytes ->
    val payload = json.decodeFromString<BlocktankBackupV1>(String(dataBytes))
    
    // Restore paid orders (idempotent via orderId)
    payload.paidOrders.forEach { (orderId, txId) ->
        cacheStore.addPaidOrder(orderId, txId)
    }
    
    // Note: Orders and CJIT entries are refreshed from server
    // We mainly need paidOrders to track payment status locally
}
```

**Change detection:**
```kotlin
// Observe paid orders
val blocktankJob = scope.launch {
    cacheStore.data
        .map { it.paidOrders }
        .distinctUntilChanged()
        .drop(1)
        .collect {
            if (!isRestoring) {
                markBackupRequired(BackupCategory.BLOCKTANK)
            }
        }
}
```

**Serializable representations:**
```kotlin
fun IBtOrder.toSerializable() = SerializableOrder(
    id = this.id,
    state = this.state2.name,
    // Add other essential fields
)

fun IcJitEntry.toSerializable() = SerializableCjitEntry(
    channelSizeSat = this.channelSizeSat,
    invoice = this.invoice.request,
    // Add other essential fields
)
```

---

### 5. ACTIVITY Backup Implementation

**Data source:**
- `CoreService.activity.get(filter = ActivityFilter.ALL)` - **ALL activities**

**Backup:**
```kotlin
BackupCategory.ACTIVITY -> {
    val allActivities = coreService.activity.get(
        filter = ActivityFilter.ALL,
        txType = null,
        tags = null,
        search = null,
        minDate = null,
        maxDate = null,
        limit = null,
        sortDirection = null
    )
    
    val payload = ActivityBackupV1(
        activities = allActivities
    )
    
    json.encodeToString(payload).toByteArray()
}
```

**Restore:**
```kotlin
performRestore(BackupCategory.ACTIVITY) { dataBytes ->
    val payload = json.decodeFromString<ActivityBackupV1>(String(dataBytes))
    
    // Restore all activities (idempotent via activity ID)
    payload.activities.forEach { activity ->
        runCatching {
            // Try to insert; if exists, skip or update
            coreService.activity.insert(activity)
        }.onFailure { e ->
            // Activity might already exist; log and continue
            Logger.debug("Activity already exists or failed: ${e.message}")
        }
    }
}
```

**Change detection:**
- **Manual backup only** (no auto-trigger)
- Reason: Activity list can be large; user initiates backup manually
- Keep `disableRetry = true` in BackupsViewModel for this category

**Note:** Ensure `Activity` (both `Activity.Lightning` and `Activity.Onchain`) are `@Serializable` from bitkit-core.

---

### 6. LIGHTNING_CONNECTIONS (Display-Only)

**Purpose:** Display ldk-node's native backup status, not perform manual backup

**Data source:**
- `lightningService.status.latestLightningWalletSyncTimestamp`

**Implementation:**
```kotlin
// In BackupRepo.getBackupDataBytes()
BackupCategory.LIGHTNING_CONNECTIONS -> {
    throw NotImplementedError(
        "LIGHTNING_CONNECTIONS backup is handled by ldk-node's native backup system"
    )
}

// In BackupRepo.startDataStoreListeners()
private fun observeLdkBackupStatus() {
    val ldkStatusJob = scope.launch {
        lightningRepo.lightningState
            .map { it.status.latestLightningWalletSyncTimestamp }
            .distinctUntilChanged()
            .collect { syncTimestamp ->
                // Update backup status to display LDK's sync time
                cacheStore.updateBackupStatus(BackupCategory.LIGHTNING_CONNECTIONS) {
                    it.copy(
                        synced = syncTimestamp,
                        required = syncTimestamp, // Always "synced"
                        running = false
                    )
                }
            }
    }
    statusObserverJobs.add(ldkStatusJob)
}
```

**UI behavior:**
- Show sync timestamp from ldk-node
- No "Backup Now" button
- No retry or running states
- Display as "Automatically backed up by Lightning"

---

### 7. SLASHTAGS (Descoped)

**Status:** Descoped for v1, planned for v2

**Implementation:**
```kotlin
// In BackupStatus.kt
@Serializable
enum class BackupCategory {
    LIGHTNING_CONNECTIONS,
    BLOCKTANK,
    ACTIVITY, // Renamed from LDK_ACTIVITY
    WALLET,
    SETTINGS,
    WIDGETS,
    METADATA,
    // SLASHTAGS, // Descoped for v1, will return in v2
}
```

Remove from:
- BackupRepo.getBackupDataBytes()
- performFullRestoreFromLatestBackup()
- UI screens (BackupSettingsScreen, etc.)

---

## Restore Orchestration

### Restore Order

Recommended order for dependencies:
1. **METADATA** (tags and tx metadata)
2. **WALLET** (transfers and boosts)
3. **BLOCKTANK** (orders and paid orders)
4. **ACTIVITY** (all activities)

### Idempotency Strategy

Each category uses stable keys for upsert:
- **WALLET**: Transfer by `id` (primary key), boost by `txId`
- **METADATA**: TagMetadata by `id` or composite key, tx metadata by `txId`
- **BLOCKTANK**: Paid orders by `orderId`
- **ACTIVITY**: Activity by `id` field

Use Room's `@Upsert` (or `onConflict = REPLACE`) and CacheStore deduplication.

### Error Handling

```kotlin
suspend fun performFullRestoreFromLatestBackup(): Result<Unit> = withContext(bgDispatcher) {
    isRestoring = true
    
    val results = mutableMapOf<BackupCategory, Result<Unit>>()
    
    val categories = listOf(
        BackupCategory.METADATA,
        BackupCategory.WALLET,
        BackupCategory.BLOCKTANK,
        BackupCategory.ACTIVITY
    )
    
    for (category in categories) {
        val result = runCatching {
            performRestore(category) { dataBytes ->
                // Category-specific restore logic
            }
        }
        results[category] = result
        
        if (result.isFailure) {
            Logger.warn("Restore failed for $category", result.exceptionOrNull())
            // Continue with other categories
        }
    }
    
    isRestoring = false
    
    // Return success if at least one category restored
    val anySuccess = results.values.any { it.isSuccess }
    if (anySuccess) Result.success(Unit) else Result.failure(Exception("All restores failed"))
}
```

---

## Testing Strategy

### Unit Tests

For each category, test:
1. **Serialization roundtrip** - backup → deserialize → verify equality
2. **Empty data** - backup with no data returns valid empty payload
3. **Large datasets** - 1000+ items serialize/deserialize correctly
4. **Version migration** - older payload version can be migrated

Example:
```kotlin
@Test
fun `wallet backup serialization roundtrip`() = runTest {
    // Create test data
    val transfers = listOf(
        TransferEntity(id = "1", type = TransferType.TO_SAVINGS, ...),
        TransferEntity(id = "2", type = TransferType.TO_SPENDING, ...)
    )
    val boosts = listOf(
        PendingBoostActivity(txId = "abc", ...)
    )
    
    // Serialize
    val payload = WalletBackupV1(
        boostedActivities = boosts,
        transfers = transfers
    )
    val json = Json.encodeToString(payload)
    
    // Deserialize
    val restored = Json.decodeFromString<WalletBackupV1>(json)
    
    // Verify
    assertEquals(payload.transfers.size, restored.transfers.size)
    assertEquals(payload.boostedActivities.size, restored.boostedActivities.size)
}
```

### Integration Tests

For each category, test:
1. **Backup → restore → verify** - data persists correctly
2. **Idempotent restore** - restore twice, no duplicates
3. **Partial data** - restore with some missing data succeeds
4. **Failed restore** - one category fails, others continue

Example:
```kotlin
@Test
fun `wallet restore is idempotent`() = runTest {
    // Insert initial data
    transferDao.insert(TransferEntity(id = "1", ...))
    
    // Backup
    val backupBytes = backupRepo.triggerBackup(BackupCategory.WALLET)
    
    // Restore twice
    backupRepo.performRestore(BackupCategory.WALLET) { backupBytes }
    backupRepo.performRestore(BackupCategory.WALLET) { backupBytes }
    
    // Verify no duplicates
    val transfers = transferDao.getAll()
    assertEquals(1, transfers.size)
}
```

### Manual Tests

- [ ] Create wallet data, backup, wipe app data, restore, verify
- [ ] Add tags, backup, restore on another device, verify
- [ ] Create blocktank order, pay, backup, restore, verify paid status
- [ ] Generate activities, backup, restore, verify count and details
- [ ] Check LIGHTNING_CONNECTIONS displays correct timestamp
- [ ] Trigger backup failure (network error), verify UI shows error
- [ ] Restore with missing category backup, verify graceful handling

---

## Implementation Checklist

### Phase 1: Setup (1 day)
- [ ] Create `docs/backups-plan.md` and commit
- [ ] Rename `BackupCategory.LDK_ACTIVITY` → `ACTIVITY`
- [ ] Comment out `SLASHTAGS` from enum
- [ ] Define all payload data classes (`WalletBackupV1`, etc.)

### Phase 2: DAO Extensions (0.5 day)
- [ ] Add `TransferDao.getAll()` and `observeAll()`
- [ ] Add `TransferDao.upsert()`
- [ ] Add `TagMetadataDao.getAll()` and `observeAll()`
- [ ] Add `TagMetadataDao.upsert()`
- [ ] Ensure all entities are `@Serializable`

### Phase 3: WALLET Implementation (1 day)
- [ ] Implement `getBackupDataBytes` for WALLET
- [ ] Implement `performRestore` for WALLET
- [ ] Add change detection listeners
- [ ] Write unit tests
- [ ] Write integration tests

### Phase 4: METADATA Implementation (1 day)
- [ ] Implement `getBackupDataBytes` for METADATA
- [ ] Implement `performRestore` for METADATA
- [ ] Add change detection listeners
- [ ] Write unit tests
- [ ] Write integration tests

### Phase 5: BLOCKTANK Implementation (1 day)
- [ ] Create `SerializableOrder` and `SerializableCjitEntry`
- [ ] Implement `getBackupDataBytes` for BLOCKTANK
- [ ] Implement `performRestore` for BLOCKTANK
- [ ] Add change detection listener
- [ ] Write unit tests
- [ ] Write integration tests

### Phase 6: ACTIVITY Implementation (1 day)
- [ ] Ensure `Activity` types are `@Serializable` in bitkit-core
- [ ] Implement `getBackupDataBytes` for ACTIVITY (ALL activities)
- [ ] Implement `performRestore` for ACTIVITY
- [ ] Keep manual-only (no auto-trigger)
- [ ] Write unit tests
- [ ] Write integration tests

### Phase 7: LIGHTNING_CONNECTIONS Display (0.5 day)
- [ ] Add LDK status observer
- [ ] Implement display-only status updates
- [ ] Throw `NotImplementedError` in `getBackupDataBytes`
- [ ] Update UI to show "Automatically backed up"
- [ ] Test timestamp updates

### Phase 8: Restore Orchestration (1 day)
- [ ] Implement `performFullRestoreFromLatestBackup` with ordering
- [ ] Add per-category error handling
- [ ] Test partial restore scenarios
- [ ] Test failure isolation

### Phase 9: Polish & Documentation (1 day)
- [ ] Add code comments documenting payloads and behavior
- [ ] Update UI strings for renamed category
- [ ] Test all categories end-to-end
- [ ] Performance test with large datasets
- [ ] Update this document with any changes

**Total Estimated Time:** 8-9 days

---

## Open Questions

1. **Activity serialization:** Are `Activity.Lightning` and `Activity.Onchain` already `@Serializable` in bitkit-core?
2. **Blocktank restore:** Do we need to restore full order details, or just `paidOrders`?
3. **Activity backup size:** Should we limit the number of activities backed up (e.g., last 1000)?
4. **Restore conflicts:** If local data is newer than backup, should we skip or overwrite?
5. **Backup frequency:** Should ACTIVITY be manual-only, or add auto-backup with longer debounce?

---

## References

- **Current implementation:** `app/src/main/java/to/bitkit/repositories/BackupRepo.kt`
- **Backup categories:** `app/src/main/java/to/bitkit/models/BackupStatus.kt`
- **VSS client:** `app/src/main/java/to/bitkit/data/backup/VssBackupClient.kt`
- **Data stores:**
  - `app/src/main/java/to/bitkit/data/SettingsStore.kt`
  - `app/src/main/java/to/bitkit/data/WidgetsStore.kt`
  - `app/src/main/java/to/bitkit/data/CacheStore.kt`
  - `app/src/main/java/to/bitkit/data/AppDb.kt`
- **CoreService:** `app/src/main/java/to/bitkit/services/CoreService.kt`

---

## Changelog

| Date | Author | Changes |
|------|--------|---------|
| 2025-10-30 | AI Assistant | Initial plan created |

---

**End of Plan**
