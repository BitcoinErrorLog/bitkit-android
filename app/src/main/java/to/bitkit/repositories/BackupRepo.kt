package to.bitkit.repositories

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.bitkit.R
import to.bitkit.data.AppDb
import to.bitkit.data.CacheStore
import to.bitkit.data.SettingsData
import to.bitkit.data.SettingsStore
import to.bitkit.data.WidgetsData
import to.bitkit.data.WidgetsStore
import to.bitkit.data.backup.VssBackupClient
import to.bitkit.data.resetPin
import to.bitkit.di.BgDispatcher
import to.bitkit.di.json
import to.bitkit.ext.formatPlural
import to.bitkit.models.BackupCategory
import to.bitkit.models.BackupItemStatus
import to.bitkit.models.BlocktankBackupV1
import to.bitkit.models.MetadataBackupV1
import to.bitkit.models.Toast
import to.bitkit.models.WalletBackupV1
import to.bitkit.ui.shared.toast.ToastEventBus
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepo @Inject constructor(
    @ApplicationContext private val context: Context,
    @BgDispatcher private val bgDispatcher: CoroutineDispatcher,
    private val cacheStore: CacheStore,
    private val vssBackupClient: VssBackupClient,
    private val settingsStore: SettingsStore,
    private val widgetsStore: WidgetsStore,
    private val blocktankRepo: BlocktankRepo,
    private val db: AppDb,
) {
    private val scope = CoroutineScope(bgDispatcher + SupervisorJob())

    private val backupJobs = mutableMapOf<BackupCategory, Job>()
    private val statusObserverJobs = mutableListOf<Job>()
    private val dataListenerJobs = mutableListOf<Job>()
    private var periodicCheckJob: Job? = null
    private var isObserving = false
    private var isRestoring = false

    private var lastNotificationTime = 0L

    fun startObservingBackups() {
        if (isObserving) return

        isObserving = true
        Logger.debug("Start observing backup statuses and data store changes", context = TAG)

        scope.launch { vssBackupClient.setup() }
        startBackupStatusObservers()
        startDataStoreListeners()
        startPeriodicBackupFailureCheck()
    }

    fun stopObservingBackups() {
        if (!isObserving) return

        isObserving = false

        // Cancel all backup jobs
        backupJobs.values.forEach { it.cancel() }
        backupJobs.clear()

        // Cancel backup status observer jobs
        statusObserverJobs.forEach { it.cancel() }
        statusObserverJobs.clear()

        // Cancel data store listener jobs
        dataListenerJobs.forEach { it.cancel() }
        dataListenerJobs.clear()

        // Cancel periodic check job
        periodicCheckJob?.cancel()
        periodicCheckJob = null

        Logger.debug("Stopped observing backup statuses and data store changes", context = TAG)
    }

    private fun startBackupStatusObservers() {
        // Observe backup status changes for each category
        BackupCategory.entries.forEach { category ->
            val job = scope.launch {
                cacheStore.backupStatuses
                    .map { statuses -> statuses[category] ?: BackupItemStatus() }
                    .distinctUntilChanged { old, new ->
                        // restart scheduling when synced or required timestamps change
                        old.synced == new.synced && old.required == new.required
                    }
                    .collect { status ->
                        if (status.synced < status.required && !status.running && !isRestoring) {
                            scheduleBackup(category)
                        }
                    }
            }
            statusObserverJobs.add(job)
        }

        Logger.debug("Started ${statusObserverJobs.size} backup status observers", context = TAG)
    }

    private fun startDataStoreListeners() {
        val settingsJob = scope.launch {
            settingsStore.data
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (!isRestoring) {
                        markBackupRequired(BackupCategory.SETTINGS)
                    }
                }
        }
        dataListenerJobs.add(settingsJob)

        val widgetsJob = scope.launch {
            widgetsStore.data
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (!isRestoring) {
                        markBackupRequired(BackupCategory.WIDGETS)
                    }
                }
        }
        dataListenerJobs.add(widgetsJob)

        // WALLET - Observe boosted activities
        val boostJob = scope.launch {
            // TODO concat into one job using combine of boosts + transfers
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
        dataListenerJobs.add(boostJob)

        // WALLET - Observe transfers
        val transfersJob = scope.launch {
            // TODO concat into one job using combine of boosts + transfers
            db.transferDao().observeAll()
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (!isRestoring) {
                        markBackupRequired(BackupCategory.WALLET)
                    }
                }
        }
        dataListenerJobs.add(transfersJob)

        // METADATA - Observe tag metadata
        val tagMetadataJob = scope.launch {
            // TODO concat into one job using combine of tagMetadataDao + transactionsMetadata
            db.tagMetadataDao().observeAll()
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (!isRestoring) {
                        markBackupRequired(BackupCategory.METADATA)
                    }
                }
        }
        dataListenerJobs.add(tagMetadataJob)

        // METADATA - Observe transaction metadata
        val txMetadataJob = scope.launch {
            // TODO concat into one job using combine of tagMetadataDao + transactionsMetadata
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
        dataListenerJobs.add(txMetadataJob)

        // BLOCKTANK - Observe paid orders
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
        dataListenerJobs.add(blocktankJob)

        Logger.debug("Started ${dataListenerJobs.size} data store listeners", context = TAG)
    }

    private fun startPeriodicBackupFailureCheck() {
        periodicCheckJob = scope.launch {
            while (true) {
                delay(BACKUP_CHECK_INTERVAL)
                checkForFailedBackups()
            }
        }
    }

    private fun markBackupRequired(category: BackupCategory) {
        scope.launch {
            cacheStore.updateBackupStatus(category) {
                it.copy(required = System.currentTimeMillis())
            }
            Logger.verbose("Marked backup required for: '$category'", context = TAG)
        }
    }

    private fun scheduleBackup(category: BackupCategory) {
        // Cancel existing backup job for this category
        backupJobs[category]?.cancel()

        Logger.verbose("Scheduling backup for: '$category'", context = TAG)

        backupJobs[category] = scope.launch {
            delay(BACKUP_DEBOUNCE)

            // Double-check if backup is still needed
            val status = cacheStore.backupStatuses.first()[category] ?: BackupItemStatus()
            if (status.synced < status.required && !status.running && !isRestoring) {
                triggerBackup(category)
            }
        }
    }

    private fun checkForFailedBackups() {
        val currentTime = System.currentTimeMillis()

        // find if there are any backup categories that have been failing for more than 30 minutes
        scope.launch {
            val backupStatuses = cacheStore.backupStatuses.first()
            val hasFailedBackups = BackupCategory.entries.any { category ->
                val status = backupStatuses[category] ?: BackupItemStatus()

                val isPendingAndOverdue = status.synced < status.required &&
                    currentTime - status.required > FAILED_BACKUP_CHECK_TIME
                return@any isPendingAndOverdue
            }

            if (hasFailedBackups) {
                showBackupFailureNotification(currentTime)
            }
        }
    }

    private fun showBackupFailureNotification(currentTime: Long) {
        // Throttle notifications to avoid spam
        if (currentTime - lastNotificationTime < FAILED_BACKUP_NOTIFICATION_INTERVAL) return

        lastNotificationTime = currentTime

        scope.launch {
            ToastEventBus.send(
                type = Toast.ToastType.ERROR,
                title = context.getString(R.string.settings__backup__failed_title),
                description = context.getString(R.string.settings__backup__failed_message).formatPlural(
                    mapOf("interval" to (BACKUP_CHECK_INTERVAL / 60000)) // displayed in minutes
                ),
            )
        }
    }

    suspend fun triggerBackup(category: BackupCategory) = withContext(bgDispatcher) {
        Logger.debug("Backup starting for: '$category'", context = TAG)

        cacheStore.updateBackupStatus(category) {
            it.copy(running = true, required = System.currentTimeMillis())
        }

        vssBackupClient.putObject(key = category.name, data = getBackupDataBytes(category))
            .onSuccess {
                cacheStore.updateBackupStatus(category) {
                    it.copy(
                        running = false,
                        synced = System.currentTimeMillis(),
                    )
                }
                Logger.info("Backup succeeded for: '$category'", context = TAG)
            }
            .onFailure { e ->
                cacheStore.updateBackupStatus(category) {
                    it.copy(running = false)
                }
                Logger.error("Backup failed for: '$category'", e = e, context = TAG)
            }
    }

    private suspend fun getBackupDataBytes(category: BackupCategory): ByteArray = when (category) {
        BackupCategory.SETTINGS -> {
            val data = settingsStore.data.first().resetPin()
            json.encodeToString(data).toByteArray()
        }

        BackupCategory.WIDGETS -> {
            val data = widgetsStore.data.first()
            json.encodeToString(data).toByteArray()
        }

        BackupCategory.WALLET -> {
            val boostedActivities = cacheStore.data.first().pendingBoostActivities
            val transfers = db.transferDao().getAll()

            val payload = WalletBackupV1(
                createdAt = System.currentTimeMillis(),
                boostedActivities = boostedActivities,
                transfers = transfers
            )

            json.encodeToString(payload).toByteArray()
        }

        BackupCategory.METADATA -> {
            val tagMetadata = db.tagMetadataDao().getAll()
            val txMetadata = cacheStore.data.first().transactionsMetadata

            val payload = MetadataBackupV1(
                createdAt = System.currentTimeMillis(),
                tagMetadata = tagMetadata,
                transactionsMetadata = txMetadata
            )

            json.encodeToString(payload).toByteArray()
        }

        BackupCategory.BLOCKTANK -> {
            val paidOrders = cacheStore.data.first().paidOrders
            // Fetch all orders, CJIT entries, and info from BlocktankRepo state
            val blocktankState = blocktankRepo.blocktankState.first()

            val payload = BlocktankBackupV1(
                createdAt = System.currentTimeMillis(),
                paidOrders = paidOrders,
                orders = blocktankState.orders,
                cjitEntries = blocktankState.cjitEntries,
                info = blocktankState.info,
            )

        BackupCategory.SLASHTAGS -> {
            throw NotImplementedError("Slashtags backup not yet implemented")
        }

        BackupCategory.LDK_ACTIVITY -> {
            throw NotImplementedError("LDK activity backup not yet implemented")
        }

        BackupCategory.LIGHTNING_CONNECTIONS -> {
            throw NotImplementedError("Lightning connections backup not yet implemented")
        }
    }

    suspend fun performFullRestoreFromLatestBackup(): Result<Unit> = withContext(bgDispatcher) {
        Logger.debug("Full restore starting", context = TAG)

        isRestoring = true

        return@withContext try {
            performRestore(BackupCategory.SETTINGS) { dataBytes ->
                val parsed = json.decodeFromString<SettingsData>(String(dataBytes)).resetPin()
                settingsStore.update { parsed }
            }
            performRestore(BackupCategory.WIDGETS) { dataBytes ->
                val parsed = json.decodeFromString<WidgetsData>(String(dataBytes))
                widgetsStore.update { parsed }
            }
            performRestore(BackupCategory.WALLET) { dataBytes ->
                val parsed = json.decodeFromString<WalletBackupV1>(String(dataBytes))

                parsed.transfers.forEach { transfer ->
                    // TODO add transferDao().upsert() and use it instead
                    db.transferDao().insert(transfer)
                }

                // Restore boosted activities (idempotent via txId)
                parsed.boostedActivities.forEach { activity ->
                    // TODO add addActivityToPendingBoost(vararg) and use it instead
                    cacheStore.addActivityToPendingBoost(activity)
                }

                Logger.debug(
                    "Restored ${parsed.transfers.size} transfers and ${parsed.boostedActivities.size} boosted activities",
                    context = TAG
                )
            }
            performRestore(BackupCategory.METADATA) { dataBytes ->
                val parsed = json.decodeFromString<MetadataBackupV1>(String(dataBytes))

                // Restore tag metadata (idempotent via primary key with INSERT OR REPLACE)
                parsed.tagMetadata.forEach { entity ->
                    // TODO add tagMetadataDao().upsert() and use it instead
                    db.tagMetadataDao().saveTagMetadata(entity)
                }

                // Restore transaction metadata (idempotent via txId)
                parsed.transactionsMetadata.forEach { metadata ->
                    // TODO add addTransactionMetadata(vararg) and use it instead
                    cacheStore.addTransactionMetadata(metadata)
                }

                Logger.debug(
                    "Restored ${parsed.tagMetadata.size} tag metadata entries and ${parsed.transactionsMetadata.size} transaction metadata",
                    context = TAG
                )
            }
            performRestore(BackupCategory.BLOCKTANK) { dataBytes ->
                val parsed = json.decodeFromString<BlocktankBackupV1>(String(dataBytes))

                // Restore paid orders (idempotent via orderId)
                parsed.paidOrders.forEach { (orderId, txId) ->
                    // TODO add addPaidOrder(vararg) and use it instead
                    cacheStore.addPaidOrder(orderId, txId)
                }

                // TODO: Restore orders, CJIT entries, and info to bitkit-core storage
                // This requires bitkit-core to expose an API for restoring orders/cjitEntries/info
                // For now, trigger a refresh from the Blocktank server to sync the data
                // Data is preserved in backup: ${parsed.orders.size} orders, ${parsed.cjitEntries.size} CJIT entries, info=${parsed.info != null}
                blocktankRepo.refreshInfo()
                blocktankRepo.refreshOrders()

                Logger.debug(
                    "Restored ${parsed.paidOrders.size} paid orders (${parsed.orders.size} orders, ${parsed.cjitEntries.size} CJIT entries, info=${parsed.info != null} backed up)",
                    context = TAG,
                )
            }
            // TODO: Add other backup categories as they get implemented:
            // performSlashtagsRestore()
            // performLdkActivityRestore()

            Logger.info("Full restore completed", context = TAG)
            Result.success(Unit)
        } catch (e: Throwable) {
            Logger.warn("Full restore error", e = e, context = TAG)
            Result.failure(e)
        } finally {
            isRestoring = false
        }
    }

    private suspend fun performRestore(
        category: BackupCategory,
        restoreAction: suspend (ByteArray) -> Unit,
    ): Result<Unit> = runCatching {
        vssBackupClient.getObject(category.name).map { it?.value }
            .onSuccess { dataBytes ->
                if (dataBytes == null) {
                    Logger.warn("Restore null for: '$category'", context = TAG)
                } else {
                    restoreAction(dataBytes)
                    Logger.info("Restore success for: '$category'", context = TAG)
                }
            }
            .onFailure {
                Logger.debug("Restore error for: '$category'", context = TAG)
            }

        cacheStore.updateBackupStatus(category) {
            it.copy(running = false, synced = System.currentTimeMillis())
        }
    }

    companion object {
        private const val TAG = "BackupRepo"

        private const val BACKUP_DEBOUNCE = 5000L // 5 seconds
        private const val BACKUP_CHECK_INTERVAL = 60 * 1000L // 1 minute
        private const val FAILED_BACKUP_CHECK_TIME = 30 * 60 * 1000L // 30 minutes
        private const val FAILED_BACKUP_NOTIFICATION_INTERVAL = 10 * 60 * 1000L // 10 minutes
    }
}
