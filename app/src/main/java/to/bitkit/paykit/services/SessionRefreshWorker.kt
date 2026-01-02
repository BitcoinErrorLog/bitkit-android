package to.bitkit.paykit.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import to.bitkit.utils.Logger
import java.util.concurrent.TimeUnit

/**
 * Background worker for refreshing Pubky sessions before expiration
 */
@HiltWorker
class SessionRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pubkySDKService: PubkySDKService
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SessionRefreshWorker"
        private const val WORK_NAME = "session_refresh_work"
        
        /**
         * Schedule periodic session refresh checks
         */
        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<SessionRefreshWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Logger.info("Scheduled periodic session refresh", context = TAG)
        }

        /**
         * Cancel session refresh work
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Logger.info("Cancelled session refresh work", context = TAG)
        }
    }

    override suspend fun doWork(): Result {
        Logger.info("Background session refresh started", context = TAG)

        return try {
            // Refresh expiring sessions using injected service
            pubkySDKService.refreshExpiringSessions()

            Logger.info("Background session refresh completed", context = TAG)
            Result.success()
        } catch (e: Exception) {
            Logger.error("Session refresh failed", e = e, context = TAG)
            Result.retry()
        }
    }
}

