package to.bitkit.repositories

import android.content.Context
import com.synonym.vssclient.VssItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import to.bitkit.R
import to.bitkit.data.AppCacheData
import to.bitkit.data.AppDb
import to.bitkit.data.CacheStore
import to.bitkit.data.SettingsData
import to.bitkit.data.SettingsStore
import to.bitkit.data.WidgetsData
import to.bitkit.data.WidgetsStore
import to.bitkit.data.backup.VssBackupClient
import to.bitkit.data.dao.TransferDao
import to.bitkit.models.BackupCategory
import to.bitkit.models.BackupItemStatus
import to.bitkit.services.LightningService
import to.bitkit.test.BaseUnitTest
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class BackupRepoTest : BaseUnitTest() {

    private lateinit var sut: BackupRepo

    private val context: Context = mock()
    private val cacheStore: CacheStore = mock()
    private val vssBackupClient: VssBackupClient = mock()
    private val settingsStore: SettingsStore = mock()
    private val widgetsStore: WidgetsStore = mock()
    private val blocktankRepo: BlocktankRepo = mock()
    private val activityRepo: ActivityRepo = mock()
    private val preActivityMetadataRepo: PreActivityMetadataRepo = mock()
    private val lightningService: LightningService = mock()
    private val clock: Clock = Clock.System
    private val db: AppDb = mock()
    private val transferDao: TransferDao = mock()

    private val backupStatusesFlow = MutableStateFlow<Map<BackupCategory, BackupItemStatus>>(emptyMap())
    private val settingsDataFlow = MutableStateFlow(SettingsData())
    private val widgetsDataFlow = MutableStateFlow(WidgetsData())
    private val cacheDataFlow = MutableStateFlow(AppCacheData())
    private val blocktankStateFlow = MutableStateFlow(BlocktankState())
    private val activitiesChangedFlow = MutableStateFlow(System.currentTimeMillis())
    private val preActivityMetadataChangedFlow = MutableStateFlow(System.currentTimeMillis())
    private val syncStatusChangedFlow = MutableStateFlow(Unit)

    @Before
    fun setUp() {
        whenever(context.getString(R.string.settings__backup__failed_title)).thenReturn("Backup failed")
        whenever(context.getString(R.string.settings__backup__failed_message)).thenReturn("Backup failed message")
        whenever(cacheStore.backupStatuses).thenReturn(backupStatusesFlow)
        whenever(cacheStore.data).thenReturn(cacheDataFlow)
        whenever(settingsStore.data).thenReturn(settingsDataFlow)
        whenever(widgetsStore.data).thenReturn(widgetsDataFlow)
        whenever(blocktankRepo.blocktankState).thenReturn(blocktankStateFlow)
        whenever(activityRepo.activitiesChanged).thenReturn(activitiesChangedFlow)
        whenever(preActivityMetadataRepo.preActivityMetadataChanged).thenReturn(preActivityMetadataChangedFlow)
        whenever(lightningService.syncStatusChanged).thenReturn(syncStatusChangedFlow)
        whenever(db.transferDao()).thenReturn(transferDao)

        sut = BackupRepo(
            context = context,
            ioDispatcher = testDispatcher,
            cacheStore = cacheStore,
            vssBackupClient = vssBackupClient,
            settingsStore = settingsStore,
            widgetsStore = widgetsStore,
            blocktankRepo = blocktankRepo,
            activityRepo = activityRepo,
            preActivityMetadataRepo = preActivityMetadataRepo,
            lightningService = lightningService,
            clock = clock,
            db = db,
        )
    }

    @Test
    fun `isRestoring is initially false`() = test {
        assertFalse(sut.isRestoring.first())
    }

    @Test
    fun `reset calls vss client reset`() = test {
        sut.reset()
        verify(vssBackupClient).reset()
    }

    @Test
    fun `stopObservingBackups can be called safely`() = test {
        sut.stopObservingBackups()
        // No exception means success
    }

    @Test
    fun `triggerBackup calls vss client putObject`() = test {
        val mockVssItem: VssItem = mock()
        wheneverBlocking { vssBackupClient.putObject(any(), any()) }.thenReturn(Result.success(mockVssItem))

        sut.triggerBackup(BackupCategory.SETTINGS)
        advanceUntilIdle()

        verify(vssBackupClient).putObject(
            key = org.mockito.kotlin.eq(BackupCategory.SETTINGS.name),
            data = any(),
        )
    }

    @Test
    fun `triggerBackup handles success`() = test {
        val mockVssItem: VssItem = mock()
        wheneverBlocking { vssBackupClient.putObject(any(), any()) }.thenReturn(Result.success(mockVssItem))

        sut.triggerBackup(BackupCategory.SETTINGS)
        advanceUntilIdle()

        verify(cacheStore, atLeast(1)).updateBackupStatus(
            org.mockito.kotlin.eq(BackupCategory.SETTINGS),
            any(),
        )
    }

    @Test
    fun `triggerBackup handles failure`() = test {
        wheneverBlocking { vssBackupClient.putObject(any(), any()) }
            .thenReturn(Result.failure(Exception("Test error")))

        sut.triggerBackup(BackupCategory.SETTINGS)
        advanceUntilIdle()

        verify(cacheStore, atLeast(1)).updateBackupStatus(
            org.mockito.kotlin.eq(BackupCategory.SETTINGS),
            any(),
        )
    }

    @Test
    fun `backup categories except lightning are not empty`() = test {
        val categories = BackupCategory.entries.filter { it != BackupCategory.LIGHTNING_CONNECTIONS }
        assertTrue(categories.isNotEmpty())
    }

    @Test
    fun `performFullRestoreFromLatestBackup returns success on empty backup`() = test {
        @Suppress("UNCHECKED_CAST")
        wheneverBlocking { vssBackupClient.getObject(any()) }.thenReturn(Result.success(null) as Result<VssItem?>)
        wheneverBlocking { preActivityMetadataRepo.getAllPreActivityMetadata() }.thenReturn(Result.success(emptyList()))
        wheneverBlocking { activityRepo.getActivities() }.thenReturn(Result.success(emptyList()))
        wheneverBlocking { activityRepo.getClosedChannels() }.thenReturn(Result.success(emptyList()))
        wheneverBlocking { activityRepo.getAllActivitiesTags() }.thenReturn(Result.success(emptyList()))

        val result = sut.performFullRestoreFromLatestBackup()
        assertTrue(result.isSuccess)
    }

}

