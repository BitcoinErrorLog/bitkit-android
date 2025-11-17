package to.bitkit.repositories

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import to.bitkit.R
import to.bitkit.data.AppDb
import to.bitkit.data.CacheStore
import to.bitkit.data.SettingsData
import to.bitkit.data.SettingsStore
import to.bitkit.data.WidgetsData
import to.bitkit.data.WidgetsStore
import to.bitkit.data.backup.VssBackupClient
import to.bitkit.data.resetPin
import to.bitkit.di.IoDispatcher
import to.bitkit.di.json
import to.bitkit.ext.formatPlural
import to.bitkit.ext.nowMillis
import to.bitkit.ext.toActivityTagsMetadata
import to.bitkit.ext.toTagMetadataEntity
import to.bitkit.models.ActivityBackupV1
import to.bitkit.models.BackupCategory
import to.bitkit.models.BackupItemStatus
import to.bitkit.models.BlocktankBackupV1
import to.bitkit.models.MetadataBackupV1
import to.bitkit.models.SettingsBackupV1
import to.bitkit.models.Toast
import to.bitkit.models.WalletBackupV1
import to.bitkit.models.WidgetsBackupV1
import to.bitkit.services.LightningService
import to.bitkit.ui.shared.toast.ToastEventBus
import to.bitkit.utils.Logger
import to.bitkit.utils.jsonLogOf
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("LongParameterList")
@Singleton
class BackupRepo @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val cacheStore: CacheStore,
    private val vssBackupClient: VssBackupClient,
    private val settingsStore: SettingsStore,
    private val widgetsStore: WidgetsStore,
    private val blocktankRepo: BlocktankRepo,
    private val activityRepo: ActivityRepo,
    private val lightningService: LightningService,
    private val clock: Clock,
    private val db: AppDb,
) {
    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val backupJobs = mutableMapOf<BackupCategory, Job>()
    private val statusObserverJobs = mutableListOf<Job>()
    private val dataListenerJobs = mutableListOf<Job>()
    private var periodicCheckJob: Job? = null
    private var isObserving = false
    private var lastNotificationTime = 0L

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _isWiping = MutableStateFlow(false)

    fun reset() {
        stopObservingBackups()
        vssBackupClient.reset()
    }

    fun setWiping(isWiping: Boolean) = _isWiping.update { isWiping }
    private fun currentTimeMillis(): Long = nowMillis(clock)
    private fun shouldSkipBackup(): Boolean = _isRestoring.value || _isWiping.value
    private fun BackupItemStatus.shouldBackup() = this.isRequired && !this.running && !shouldSkipBackup()

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
                        if (status.shouldBackup()) {
                            scheduleBackup(category)
                        }
                    }
            }
            statusObserverJobs.add(job)
        }

        Logger.debug("Started ${statusObserverJobs.size} backup status observers", context = TAG)
    }

    @Suppress("LongMethod")
    private fun startDataStoreListeners() {
        val settingsJob = scope.launch {
            settingsStore.data
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (shouldSkipBackup()) return@collect
                    markBackupRequired(BackupCategory.SETTINGS)
                }
        }
        dataListenerJobs.add(settingsJob)

        val widgetsJob = scope.launch {
            widgetsStore.data
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (shouldSkipBackup()) return@collect
                    markBackupRequired(BackupCategory.WIDGETS)
                }
        }
        dataListenerJobs.add(widgetsJob)

        // WALLET - Observe transfers
        val transfersJob = scope.launch {
            db.transferDao().observeAll()
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (shouldSkipBackup()) return@collect
                    markBackupRequired(BackupCategory.WALLET)
                }
        }
        dataListenerJobs.add(transfersJob)

        // METADATA - Observe tag metadata
        val tagMetadataJob = scope.launch {
            db.tagMetadataDao().observeAll()
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (shouldSkipBackup()) return@collect
                    markBackupRequired(BackupCategory.METADATA)
                }
        }
        dataListenerJobs.add(tagMetadataJob)

        // METADATA - Observe entire CacheStore excluding backup statuses
        // TODO use PreActivityMetadata
        val cacheMetadataJob = scope.launch {
            cacheStore.data
                .map { it.copy(backupStatuses = mapOf()) }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (shouldSkipBackup()) return@collect
                    markBackupRequired(BackupCategory.METADATA)
                }
        }
        dataListenerJobs.add(cacheMetadataJob)

        // BLOCKTANK - Observe blocktank state changes (orders, cjitEntries, info)
        val blocktankJob = scope.launch {
            blocktankRepo.blocktankState
                .drop(1)
                .collect {
                    if (shouldSkipBackup()) return@collect
                    markBackupRequired(BackupCategory.BLOCKTANK)
                }
        }
        dataListenerJobs.add(blocktankJob)

        // ACTIVITY - Observe activity changes
        val activityChangesJob = scope.launch {
            activityRepo.activitiesChanged
                .drop(1)
                .collect {
                    if (shouldSkipBackup()) return@collect
                    markBackupRequired(BackupCategory.ACTIVITY)
                }
        }
        dataListenerJobs.add(activityChangesJob)

        // LIGHTNING_CONNECTIONS - Only display sync timestamp, ldk-node manages its own backups
        val lightningConnectionsJob = scope.launch {
            lightningService.syncFlow()
                .collect {
                    val lastSync = lightningService.status?.latestLightningWalletSyncTimestamp?.toLong()
                        ?.let { it * 1000 } // Convert seconds to millis
                        ?: return@collect
                    if (shouldSkipBackup()) return@collect
                    cacheStore.updateBackupStatus(BackupCategory.LIGHTNING_CONNECTIONS) {
                        it.copy(required = lastSync, synced = lastSync, running = false)
                    }
                }
        }
        dataListenerJobs.add(lightningConnectionsJob)

        Logger.debug("Started ${dataListenerJobs.size} data store listeners", context = TAG)
    }

    private fun startPeriodicBackupFailureCheck() {
        periodicCheckJob = scope.launch {
            while (currentCoroutineContext().isActive) {
                delay(BACKUP_CHECK_INTERVAL)
                checkForFailedBackups()
            }
        }
    }

    private fun markBackupRequired(category: BackupCategory) {
        scope.launch {
            cacheStore.updateBackupStatus(category) {
                it.copy(required = currentTimeMillis())
            }
            Logger.verbose("Marked backup required for: '$category'", context = TAG)
        }
    }

    private fun scheduleBackup(category: BackupCategory) {
        // Cancel existing backup job for this category
        backupJobs[category]?.cancel()

        Logger.verbose("Scheduling backup for: '$category'", context = TAG)

        backupJobs[category] = scope.launch {
            // Set running immediately to prevent UI showing failure during debounce
            cacheStore.updateBackupStatus(category) {
                it.copy(running = true)
            }

            delay(BACKUP_DEBOUNCE)

            // Double-check if backup is still needed
            val status = cacheStore.backupStatuses.first()[category] ?: BackupItemStatus()
            if (status.shouldBackup()) {
                triggerBackup(category)
            } else {
                // Backup no longer needed, reset running flag
                cacheStore.updateBackupStatus(category) {
                    it.copy(running = false)
                }
            }
        }
    }

    private fun checkForFailedBackups() {
        val currentTime = currentTimeMillis()

        // find if there are any backup categories that have been failing for more than 30 minutes
        scope.launch {
            val backupStatuses = cacheStore.backupStatuses.first()
            val hasFailedBackups = BackupCategory.entries.any { category ->
                val status = backupStatuses[category] ?: BackupItemStatus()

                val isPendingAndOverdue = status.isRequired &&
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
                    mapOf("interval" to (BACKUP_CHECK_INTERVAL / MINUTE_IN_MS))
                ),
            )
        }
    }

    suspend fun triggerBackup(category: BackupCategory) = withContext(ioDispatcher) {
        Logger.debug("Backup starting for: '$category'", context = TAG)

        cacheStore.updateBackupStatus(category) {
            it.copy(running = true, required = currentTimeMillis())
        }

        vssBackupClient.putObject(key = category.name, data = getBackupDataBytes(category))
            .onSuccess {
                cacheStore.updateBackupStatus(category) {
                    it.copy(
                        running = false,
                        synced = currentTimeMillis(),
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
            val payload = SettingsBackupV1(
                createdAt = currentTimeMillis(),
                settings = data,
            )
            json.encodeToString(payload).toByteArray()
        }

        BackupCategory.WIDGETS -> {
            val data = widgetsStore.data.first()
            val payload = WidgetsBackupV1(
                createdAt = currentTimeMillis(),
                widgets = data,
            )
            json.encodeToString(payload).toByteArray()
        }

        BackupCategory.WALLET -> {
            val transfers = db.transferDao().getAll()

            val payload = WalletBackupV1(
                createdAt = currentTimeMillis(),
                transfers = transfers
            )

            json.encodeToString(payload).toByteArray()
        }

        BackupCategory.METADATA -> {
            val tagMetadata = db.tagMetadataDao().getAll().map { it.toActivityTagsMetadata() }
            val cacheData = cacheStore.data.first()
            // TODO use PreActivityMetadata
            // val preActivityMetadata = activityRepo.getAllPreActivityMetadata().getOrDefault(emptyList())

            val payload = MetadataBackupV1(
                createdAt = currentTimeMillis(),
                tagMetadata = tagMetadata,
                cache = cacheData,
            )

            json.encodeToString(payload).toByteArray()
        }

        BackupCategory.BLOCKTANK -> {
            val blocktankState = blocktankRepo.blocktankState.first()

            val payload = BlocktankBackupV1(
                createdAt = currentTimeMillis(),
                orders = blocktankState.orders,
                cjitEntries = blocktankState.cjitEntries,
                info = blocktankState.info,
            )

            json.encodeToString(payload).toByteArray()
        }

        BackupCategory.ACTIVITY -> {
            val activities = activityRepo.getActivities().getOrDefault(emptyList())
            val closedChannels = activityRepo.getClosedChannels().getOrDefault(emptyList())
            val activityTags = activityRepo.getAllActivitiesTags().getOrDefault(emptyList())

            val payload = ActivityBackupV1(
                createdAt = currentTimeMillis(),
                activities = activities,
                activityTags = activityTags,
                closedChannels = closedChannels,
            )

            json.encodeToString(payload).toByteArray()
        }

        BackupCategory.LIGHTNING_CONNECTIONS -> throw NotImplementedError("LIGHTNING backup is managed by ldk-node")
    }

    suspend fun performFullRestoreFromLatestBackup(
        onCacheRestored: suspend () -> Unit = {},
    ): Result<Unit> = withContext(ioDispatcher) {
        Logger.debug("Full restore starting", context = TAG)

        _isRestoring.update { true }

        return@withContext try {
            performRestore(BackupCategory.METADATA) { dataBytes ->
                val parsed = json.decodeFromString<MetadataBackupV1>(String(dataBytes))
                val cleanedUp = parsed.cache.copy(onchainAddress = "") // Force address rotation
                cacheStore.update { cleanedUp }
                Logger.debug("Restored caches: ${jsonLogOf(parsed.cache.copy(cachedRates = emptyList()))}", TAG)
                onCacheRestored()
                // TODO use PreActivityMetadata
                // activityRepo.upsertPreActivityMetadata(parsed.tagMetadata)
                val tagMetadata = parsed.tagMetadata.map { it.toTagMetadataEntity() }
                db.tagMetadataDao().upsert(tagMetadata)
                Logger.debug("Restored ${tagMetadata.size} pre-activity metadata", TAG)
            }

            performRestore(BackupCategory.SETTINGS) { dataBytes ->
                val parsed = json.decodeFromString<SettingsData>(String(dataBytes)).resetPin()
                settingsStore.update { parsed }
                val parsed = json.decodeFromString<SettingsBackupV1>(String(dataBytes))
                settingsStore.restoreFromBackup(parsed)
            }
            performRestore(BackupCategory.WIDGETS) { dataBytes ->
                val parsed = json.decodeFromString<WidgetsData>(String(dataBytes))
                widgetsStore.update { parsed }
                val parsed = json.decodeFromString<WidgetsBackupV1>(String(dataBytes))
                widgetsStore.restoreFromBackup(parsed)
            }
            performRestore(BackupCategory.WALLET) { dataBytes ->
                val parsed = json.decodeFromString<WalletBackupV1>(String(dataBytes))
                db.transferDao().upsert(parsed.transfers)
                Logger.debug("Restored ${parsed.transfers.size} transfers", context = TAG)
            }
            performRestore(BackupCategory.BLOCKTANK) { dataBytes ->
                val parsed = json.decodeFromString<BlocktankBackupV1>(String(dataBytes))
                blocktankRepo.restoreFromBackup(parsed).onSuccess {
                    Logger.debug("Restored ${parsed.orders.size} orders, ${parsed.cjitEntries.size} CJITs", TAG)
                }
            }
            performRestore(BackupCategory.ACTIVITY) { dataBytes ->
                val parsed = json.decodeFromString<ActivityBackupV1>(String(dataBytes))
                activityRepo.restoreFromBackup(parsed)
            }

            Logger.info("Full restore success", context = TAG)
            Result.success(Unit)
        } catch (e: Throwable) {
            Logger.warn("Full restore error", e = e, context = TAG)
            Result.failure(e)
        } finally {
            _isRestoring.update { false }
        }
    }

    fun scheduleFullBackup() {
        Logger.debug("Scheduling backups for all categories", context = TAG)
        BackupCategory.entries
            .filter { it != BackupCategory.LIGHTNING_CONNECTIONS }
            .forEach {
                scheduleBackup(it)
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

        val now = currentTimeMillis()
        cacheStore.updateBackupStatus(category) {
            it.copy(running = false, synced = now, required = now)
        }
    }

    companion object {
        private const val TAG = "BackupRepo"

        private const val MINUTE_IN_MS = 60_000
        private const val BACKUP_DEBOUNCE = 5000L // 5 seconds
        private const val BACKUP_CHECK_INTERVAL = 60 * 1000L // 1 minute
        private const val FAILED_BACKUP_CHECK_TIME = 30 * 60 * 1000L // 30 minutes
        private const val FAILED_BACKUP_NOTIFICATION_INTERVAL = 10 * 60 * 1000L // 10 minutes
    }
}
