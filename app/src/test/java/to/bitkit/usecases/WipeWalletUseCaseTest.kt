package to.bitkit.usecases

import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import to.bitkit.data.AppDb
import to.bitkit.data.CacheStore
import to.bitkit.data.SettingsStore
import to.bitkit.data.keychain.Keychain
import to.bitkit.repositories.ActivityRepo
import to.bitkit.repositories.BackupRepo
import to.bitkit.repositories.BlocktankRepo
import to.bitkit.repositories.LightningRepo
import to.bitkit.services.CoreService
import to.bitkit.test.BaseUnitTest
import kotlin.test.assertTrue

class WipeWalletUseCaseTest : BaseUnitTest() {

    private val backupRepo = mock<BackupRepo>()
    private val keychain = mock<Keychain>()
    private val coreService = mock<CoreService>()
    private val db = mock<AppDb>()
    private val settingsStore = mock<SettingsStore>()
    private val cacheStore = mock<CacheStore>()
    private val blocktankRepo = mock<BlocktankRepo>()
    private val activityRepo = mock<ActivityRepo>()
    private val lightningRepo = mock<LightningRepo>()

    private lateinit var sut: WipeWalletUseCase

    private var onWipeCalled = false
    private var onSetWalletExistsStateCalled = false

    @Before
    fun setUp() {
        wheneverBlocking { lightningRepo.wipeStorage(0) }.thenReturn(Result.success(Unit))
        onWipeCalled = false
        onSetWalletExistsStateCalled = false

        sut = WipeWalletUseCase(
            backupRepo = backupRepo,
            keychain = keychain,
            coreService = coreService,
            db = db,
            settingsStore = settingsStore,
            cacheStore = cacheStore,
            blocktankRepo = blocktankRepo,
            activityRepo = activityRepo,
            lightningRepo = lightningRepo,
        )
    }

    @Test
    fun `invoke should reset all app state in correct order`() = runTest {
        val result = sut.invoke(
            resetWalletState = { onWipeCalled = true },
            onSuccess = { onSetWalletExistsStateCalled = true },
        )

        assertTrue(result.isSuccess)
        verify(backupRepo).setWiping(true)
        verify(backupRepo).reset()
        verify(keychain).wipe()
        verify(coreService).wipeData()
        verify(db).clearAllTables()
        verify(settingsStore).reset()
        verify(cacheStore).reset()
        verify(blocktankRepo).resetState()
        verify(activityRepo).resetState()
        assertTrue(onWipeCalled)
        verify(lightningRepo).wipeStorage(0)
        assertTrue(onSetWalletExistsStateCalled)
        verify(backupRepo).setWiping(false)
    }

    @Test
    fun `invoke should pass walletIndex to lightningRepo wipeStorage`() = runTest {
        val walletIndex = 5
        wheneverBlocking { lightningRepo.wipeStorage(walletIndex) }.thenReturn(Result.success(Unit))

        val result = sut.invoke(
            walletIndex = walletIndex,
            resetWalletState = { onWipeCalled = true },
            onSuccess = { onSetWalletExistsStateCalled = true },
        )

        assertTrue(result.isSuccess)
        verify(lightningRepo).wipeStorage(walletIndex)
    }

    @Test
    fun `invoke should set wiping to false even on failure`() = runTest {
        whenever(keychain.wipe()).thenThrow(RuntimeException("Test error"))

        val result = sut.invoke(
            resetWalletState = { onWipeCalled = true },
            onSuccess = { onSetWalletExistsStateCalled = true },
        )

        assertTrue(result.isFailure)
        verify(backupRepo).setWiping(true)
        verify(backupRepo).setWiping(false)
    }

    @Test
    fun `invoke should return failure when lightningRepo wipeStorage fails`() = runTest {
        val error = RuntimeException("Lightning wipe failed")
        wheneverBlocking { lightningRepo.wipeStorage(0) }.thenReturn(Result.failure(error))

        val result = sut.invoke(
            resetWalletState = { onWipeCalled = true },
            onSuccess = { onSetWalletExistsStateCalled = true },
        )

        assertTrue(result.isFailure)
        verify(backupRepo).setWiping(false)
    }

    @Test
    fun `invoke should return failure when database clear fails`() = runTest {
        whenever(db.clearAllTables()).thenThrow(RuntimeException("DB clear failed"))

        val result = sut.invoke(
            resetWalletState = { onWipeCalled = true },
            onSuccess = { onSetWalletExistsStateCalled = true },
        )

        assertTrue(result.isFailure)
        verify(backupRepo).setWiping(false)
    }
}
