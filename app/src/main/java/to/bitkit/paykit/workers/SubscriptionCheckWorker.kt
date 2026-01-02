package to.bitkit.paykit.workers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withTimeout
import to.bitkit.R
import to.bitkit.paykit.PaykitFeatureFlags
import to.bitkit.paykit.PaykitIntegrationHelper
import to.bitkit.paykit.models.Subscription
import to.bitkit.paykit.services.AutoPayEvaluatorService
import to.bitkit.paykit.services.AutopayEvaluationResult
import to.bitkit.paykit.services.PaykitPaymentService
import to.bitkit.paykit.storage.SubscriptionStorage
import to.bitkit.repositories.LightningRepo
import to.bitkit.utils.Logger
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

/**
 * Background worker for checking subscription payments due.
 * Runs periodically to process auto-pay for subscriptions.
 *
 * ## Retry Strategy
 *
 * Uses a two-tier retry approach:
 *
 * 1. **WorkManager Backoff**: When returning `Result.retry()`, WorkManager
 *    automatically reschedules with exponential backoff (starting at 1 minute).
 *    This is configured via `BackoffPolicy.EXPONENTIAL` in [schedule].
 *
 * 2. **Manual Retry Limit**: After 3 consecutive failures (`runAttemptCount >= 3`),
 *    returns `Result.failure()` to prevent infinite retry loops. WorkManager
 *    will still run the next scheduled periodic execution.
 *
 * The manual limit prevents battery drain from persistent network/node issues
 * while still allowing the periodic schedule to retry later.
 */
@HiltWorker
class SubscriptionCheckWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val subscriptionStorage: SubscriptionStorage,
    private val autoPayEvaluatorService: AutoPayEvaluatorService,
    private val lightningRepo: LightningRepo,
    private val paymentService: PaykitPaymentService,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SubscriptionCheckWorker"
        private const val WORK_NAME = "subscription_check"
        private const val CHANNEL_ID = "paykit_subscriptions"
        private const val NOTIFICATION_ID_BASE = 10000

        // Check interval in minutes
        private const val CHECK_INTERVAL_MINUTES = 15L

        // Node ready timeout
        private val NODE_TIMEOUT = 2.minutes

        /**
         * Schedule periodic subscription checks.
         * Call this from Application.onCreate() or when wallet is initialized.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SubscriptionCheckWorker>(
                CHECK_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Logger.info("Scheduled subscription check worker", context = TAG)
            
            // Verify scheduling
            verifyWorkerScheduled(context)
        }
        
        /**
         * Verify that the worker is actually scheduled
         */
        private fun verifyWorkerScheduled(context: Context) {
            WorkManager.getInstance(context).getWorkInfosForUniqueWork(WORK_NAME).get().let { workInfos ->
                val hasScheduledWork = workInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
                if (hasScheduledWork) {
                    Logger.debug("SubscriptionCheckWorker: Worker verified as scheduled", context = TAG)
                } else {
                    Logger.warn("SubscriptionCheckWorker: Worker not found in scheduled work", context = TAG)
                }
            }
        }

        /**
         * Cancel all scheduled subscription checks.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Logger.info("Cancelled subscription check worker", context = TAG)
        }
    }

    override suspend fun doWork(): Result {
        Logger.info("Starting subscription check", context = TAG)

        return try {
            // Ensure notification channel exists
            createNotificationChannel()

            // Check for due subscriptions
            val dueSubscriptions = checkDueSubscriptions()

            if (dueSubscriptions.isEmpty()) {
                Logger.info("No due subscriptions found", context = TAG)
                scheduleUpcomingNotifications()
                return Result.success()
            }

            // Wait for node to be ready (with timeout)
            val nodeReady = waitForNodeReady()
            if (!nodeReady) {
                Logger.error("Node not ready for subscription payments", context = TAG)
                // Send notifications that payments are pending
                for (subscription in dueSubscriptions) {
                    sendPaymentPendingNotification(subscription, "Wallet not ready")
                }
                return Result.retry()
            }

            // Process each due subscription
            for (subscription in dueSubscriptions) {
                processSubscriptionPayment(subscription)
            }

            // Schedule notifications for upcoming payments
            scheduleUpcomingNotifications()

            Result.success()
        } catch (e: Exception) {
            Logger.error("Subscription check failed", e, context = TAG)
            
            // Implement exponential backoff retry logic
            val runAttempt = runAttemptCount
            when {
                runAttempt < 3 -> {
                    Logger.debug("Retrying subscription check (attempt $runAttempt)", context = TAG)
                    Result.retry()
                }
                else -> {
                    Logger.warn("Max retry attempts reached for subscription check", context = TAG)
                    Result.failure()
                }
            }
        }
    }

    /**
     * Check for subscriptions that are due for payment.
     */
    private fun checkDueSubscriptions(): List<Subscription> {
        val activeSubscriptions = subscriptionStorage.activeSubscriptions()
        val now = System.currentTimeMillis()

        val dueSubscriptions = activeSubscriptions.filter { subscription ->
            subscription.nextPaymentAt?.let { it <= now } ?: false
        }

        Logger.info("Found ${dueSubscriptions.size} due subscriptions", context = TAG)
        return dueSubscriptions
    }

    /**
     * Get subscriptions due within the next N hours.
     */
    private fun getUpcomingSubscriptions(withinHours: Int = 24): List<Subscription> {
        val activeSubscriptions = subscriptionStorage.activeSubscriptions()
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.add(Calendar.HOUR, withinHours)
        val futureTime = calendar.timeInMillis

        return activeSubscriptions.filter { subscription ->
            subscription.nextPaymentAt?.let { nextPayment ->
                nextPayment > now && nextPayment <= futureTime
            } ?: false
        }
    }

    /**
     * Wait for the Lightning node to be ready.
     */
    private suspend fun waitForNodeReady(): Boolean {
        return try {
            withTimeout(NODE_TIMEOUT) {
                // Check if node is already running
                if (lightningRepo.lightningState.value.nodeLifecycleState.isRunning()) {
                    return@withTimeout true
                }

                // Try to start the node
                lightningRepo.start(walletIndex = 0, timeout = NODE_TIMEOUT)
                    .fold(
                        onSuccess = { true },
                        onFailure = { false }
                    )
            }
        } catch (e: Exception) {
            Logger.error("Timeout waiting for node", e, context = TAG)
            false
        }
    }

    /**
     * Process a single subscription payment.
     */
    private suspend fun processSubscriptionPayment(subscription: Subscription) {
        Logger.info("Processing payment for subscription ${subscription.id}", context = TAG)

        // Load settings before evaluation
        autoPayEvaluatorService.loadSettings()

        val evaluation = autoPayEvaluatorService.evaluate(
            peerPubkey = subscription.providerPubkey,
            amount = subscription.amountSats,
            methodId = subscription.methodId
        )

        when (evaluation) {
            is AutopayEvaluationResult.Approved -> {
                Logger.info("Auto-pay approved by rule: ${evaluation.ruleName}", context = TAG)
                executePayment(subscription)
            }

            is AutopayEvaluationResult.Denied -> {
                Logger.info("Auto-pay denied: ${evaluation.reason}", context = TAG)
                sendPaymentPendingNotification(subscription, evaluation.reason)
            }

            AutopayEvaluationResult.NeedsApproval -> {
                Logger.info("Payment needs manual approval", context = TAG)
                sendPaymentPendingNotification(subscription, "Manual approval required")
            }
        }
    }

    /**
     * Execute the actual payment for a subscription.
     */
    private suspend fun executePayment(subscription: Subscription) {
        // Check dry-run gate - if enabled, skip actual payment but log the evaluation
        if (!PaykitFeatureFlags.canExecutePayment()) {
            Logger.info(
                "DRY-RUN: Payment would execute for subscription ${subscription.id} " +
                    "(${subscription.amountSats} sats to ${subscription.providerPubkey})",
                context = TAG
            )
            sendDryRunNotification(subscription)
            return
        }

        // Initialize Paykit if needed
        runCatching {
            PaykitIntegrationHelper.setup(lightningRepo)
        }.onFailure {
            Logger.error("Failed to setup Paykit", it, context = TAG)
            sendPaymentFailedNotification(subscription, "Paykit not ready")
            return
        }

        // Determine the payment recipient from subscription
        val recipient: String = subscription.lastInvoice?.takeIf { it.isNotEmpty() }
            ?: "paykit:${subscription.providerPubkey}"

        // Execute payment via PaykitPaymentService with spending limit enforcement
        val result = paymentService.pay(
            lightningRepo = lightningRepo,
            recipient = recipient,
            amountSats = subscription.amountSats.toULong(),
            peerPubkey = subscription.providerPubkey,
        )

        if (result.success) {
            // Record the payment with receipt information
            subscriptionStorage.recordPayment(
                subscriptionId = subscription.id,
                paymentHash = result.receipt.paymentHash,
                preimage = result.receipt.preimage,
                feeSats = result.receipt.feeSats,
            )
            sendPaymentSuccessNotification(subscription)
            Logger.info(
                "Payment executed successfully for subscription ${subscription.id}, receipt: ${result.receipt.id}",
                context = TAG
            )
        } else {
            val errorMessage = result.error?.userMessage ?: "Unknown error"
            Logger.error("Payment failed: $errorMessage", context = TAG)
            sendPaymentFailedNotification(subscription, errorMessage)
        }
    }

    private fun sendDryRunNotification(subscription: Subscription) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Subscription Dry-Run")
            .setContentText("${subscription.providerName}: ₿ ${subscription.amountSats} (simulated)")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        showNotification(NOTIFICATION_ID_BASE + subscription.id.hashCode() + 1000, notification)
    }

    /**
     * Schedule notifications for upcoming subscription payments.
     */
    private fun scheduleUpcomingNotifications() {
        val upcomingSubscriptions = getUpcomingSubscriptions(withinHours = 24)

        for (subscription in upcomingSubscriptions) {
            sendUpcomingPaymentNotification(subscription)
        }
    }

    // MARK: - Notifications

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.notification_channel_subscriptions_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = appContext.getString(R.string.notification_channel_subscriptions_description)
            }

            val notificationManager = appContext.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendPaymentSuccessNotification(subscription: Subscription) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(R.string.notification_subscription_paid_title))
            .setContentText("${subscription.providerName}: ₿ ${subscription.amountSats}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        showNotification(NOTIFICATION_ID_BASE + subscription.id.hashCode(), notification)
    }

    private fun sendPaymentFailedNotification(subscription: Subscription, reason: String) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(R.string.notification_subscription_failed_title))
            .setContentText("${subscription.providerName}: $reason")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        showNotification(NOTIFICATION_ID_BASE + subscription.id.hashCode() + 1, notification)
    }

    private fun sendPaymentPendingNotification(subscription: Subscription, reason: String) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(R.string.notification_subscription_pending_title))
            .setContentText("${subscription.providerName}: ₿ ${subscription.amountSats} - $reason")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        showNotification(NOTIFICATION_ID_BASE + subscription.id.hashCode() + 2, notification)
    }

    private fun sendUpcomingPaymentNotification(subscription: Subscription) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(R.string.notification_subscription_due_title))
            .setContentText("${subscription.providerName}: ₿ ${subscription.amountSats}")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        showNotification(NOTIFICATION_ID_BASE + subscription.id.hashCode() + 3, notification)
    }

    private fun showNotification(id: Int, notification: android.app.Notification) {
        if (ActivityCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(appContext).notify(id, notification)
        }
    }
}
