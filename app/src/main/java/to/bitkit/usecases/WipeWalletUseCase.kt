package to.bitkit.usecases

import to.bitkit.data.AppDb
import to.bitkit.data.CacheStore
import to.bitkit.data.SettingsStore
import to.bitkit.data.keychain.Keychain
import to.bitkit.repositories.ActivityRepo
import to.bitkit.repositories.BackupRepo
import to.bitkit.repositories.BlocktankRepo
import to.bitkit.repositories.LightningRepo
import to.bitkit.services.CoreService
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WipeWalletUseCase @Inject constructor(
    private val backupRepo: BackupRepo,
    private val keychain: Keychain,
    private val coreService: CoreService,
    private val db: AppDb,
    private val settingsStore: SettingsStore,
    private val cacheStore: CacheStore,
    private val blocktankRepo: BlocktankRepo,
    private val activityRepo: ActivityRepo,
    private val lightningRepo: LightningRepo,
) {
    suspend operator fun invoke(
        walletIndex: Int = 0,
        resetWalletState: () -> Unit,
        onSuccess: () -> Unit,
    ): Result<Unit> {
        try {
            backupRepo.setWiping(true)
            backupRepo.reset()

            keychain.wipe()

            coreService.wipeData()
            db.clearAllTables()
            settingsStore.reset()
            cacheStore.reset()

            blocktankRepo.resetState()
            activityRepo.resetState()
            resetWalletState()

            return lightningRepo.wipeStorage(walletIndex)
                .onSuccess {
                    onSuccess()
                    Logger.reset()
                }
        } catch (e: Throwable) {
            Logger.error("Wipe wallet error", e, context = TAG)
            return Result.failure(e)
        } finally {
            backupRepo.setWiping(false)
        }
    }

    companion object Companion {
        const val TAG = "WipeWalletUseCase"
    }
}
