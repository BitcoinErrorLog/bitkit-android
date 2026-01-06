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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import to.bitkit.R
import to.bitkit.models.NodeLifecycleState
import to.bitkit.paykit.PaykitIntegrationHelper
import to.bitkit.paykit.PaykitManager
import to.bitkit.paykit.models.PaymentRequest
import to.bitkit.paykit.models.PaymentRequestStatus
import to.bitkit.paykit.models.RequestDirection
import to.bitkit.paykit.services.AutoPayEvaluator
import to.bitkit.paykit.services.DirectoryService
import to.bitkit.paykit.services.PaykitPaymentService
import to.bitkit.paykit.storage.PaymentRequestStorage
import to.bitkit.repositories.LightningRepo
import to.bitkit.utils.Logger
import java.util.concurrent.TimeUnit

/**
 * Worker for periodically polling the Pubky directory for pending payment requests.
 *
 * This worker:
 * - Discovers pending payment requests from the Pubky directory
 * - Evaluates auto-pay rules and executes approved payments
 * - Sends local notifications for requests needing manual approval
 */
@HiltWorker
class PaykitPollingWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val directoryService: DirectoryService,
    private val autoPayEvaluator: AutoPayEvaluator,
    private val lightningRepo: LightningRepo,
    private val paykitManager: PaykitManager,
    private val paymentService: PaykitPaymentService,
    private val paymentRequestStorage: PaymentRequestStorage,
    private val proposalStorage: to.bitkit.paykit.storage.SubscriptionProposalStorage,
    private val keyManager: to.bitkit.paykit.KeyManager,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "PaykitPollingWorker"
        private const val WORK_NAME = "PaykitPolling"
        private const val NOTIFICATION_CHANNEL_ID = "paykit_requests"
        private const val NOTIFICATION_CHANNEL_NAME = "Payment Requests"
        private const val NOTIFICATION_CHANNEL_DESCRIPTION = "Notifications for incoming payment requests"
        private const val NODE_READY_TIMEOUT_MS = 30_000L
        private const val MIN_BACKOFF_MILLIS = 10_000L

        // Set of seen payment request IDs to avoid duplicate notifications (in-memory cache only)
        private val seenRequestIds = mutableSetOf<String>()

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build()

            val pollingRequest = PeriodicWorkRequestBuilder<PaykitPollingWorker>(
                repeatInterval = 15, // Minimum interval for periodic work
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                pollingRequest,
            )
            Logger.info("Scheduled periodic polling worker", context = TAG)

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
                    Logger.debug("PaykitPollingWorker: Worker verified as scheduled", context = TAG)
                } else {
                    Logger.warn("PaykitPollingWorker: Worker not found in scheduled work", context = TAG)
                }
            }
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Logger.info("Cancelled periodic polling worker", context = TAG)
        }
    }

    override suspend fun doWork(): Result {
        Logger.info("Starting polling worker", context = TAG)

        createNotificationChannel()

        return runCatching {
            // Ensure Paykit is ready
            if (!PaykitIntegrationHelper.isReady) {
                Logger.info("Paykit not ready, attempting setup...", context = TAG)
                PaykitIntegrationHelper.setup(lightningRepo)
            }

            // Get our pubkey from manager
            val ownerPubkey = paykitManager.ownerPubkey
            if (ownerPubkey == null) {
                Logger.info("No owner pubkey configured, skipping poll", context = TAG)
                return@runCatching Result.success()
            }

            // Discover pending requests
            val pendingRequests = discoverPendingRequests(ownerPubkey)
            Logger.info("Found ${pendingRequests.size} pending requests", context = TAG)

            // Filter to new requests only
            val newRequests = pendingRequests.filter { request ->
                if (request.requestId in seenRequestIds) {
                    false
                } else {
                    seenRequestIds.add(request.requestId)
                    true
                }
            }
            Logger.info("${newRequests.size} new requests to process", context = TAG)

            // Persist discovered requests to storage for UI display
            persistDiscoveredRequests(newRequests, ownerPubkey)

            // Process each new request
            for (request in newRequests) {
                processRequest(request)
            }

            Result.success()
        }.onFailure { error ->
            Logger.error("Polling worker failed", error, context = TAG)
        }.getOrElse {
            Result.retry()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = NOTIFICATION_CHANNEL_NAME
            val descriptionText = NOTIFICATION_CHANNEL_DESCRIPTION
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private suspend fun discoverPendingRequests(ownerPubkey: String): List<DiscoveredRequest> {
        val requests = mutableListOf<DiscoveredRequest>()

        // Get list of known peers (follows) to poll
        val knownPeers = runCatching {
            directoryService.fetchFollows(appContext)
        }.getOrElse { error ->
            Logger.error("Failed to fetch follows for peer polling", error, context = TAG)
            emptyList()
        }

        // Poll each peer's storage for requests/proposals addressed to us
        for (peerPubkey in knownPeers) {
            // Discover payment requests from peer's storage
            runCatching {
                val paymentRequests = directoryService.discoverPendingRequestsFromPeer(peerPubkey, ownerPubkey)
                requests.addAll(paymentRequests)
            }.onFailure { error ->
                Logger.debug(
                    "Failed to discover requests from peer ${peerPubkey.take(12)}: ${error.message}",
                    context = TAG
                )
            }

            // Discover subscription proposals from peer's storage
            runCatching {
                val proposals = directoryService.discoverSubscriptionProposalsFromPeer(peerPubkey, ownerPubkey)
                for (proposal in proposals) {
                    requests.add(
                        DiscoveredRequest(
                            requestId = proposal.subscriptionId,
                            type = RequestType.SubscriptionProposal,
                            fromPubkey = proposal.providerPubkey,
                            amountSats = proposal.amountSats,
                            description = proposal.description,
                            createdAt = proposal.createdAt,
                            frequency = proposal.frequency,
                        ),
                    )
                }
            }.onFailure { error ->
                Logger.debug(
                    "Failed to discover proposals from peer ${peerPubkey.take(12)}: ${error.message}",
                    context = TAG
                )
            }
        }

        Logger.debug("Discovered ${requests.size} pending requests from ${knownPeers.size} peers", context = TAG)
        return requests
    }

    private suspend fun persistDiscoveredRequests(requests: List<DiscoveredRequest>, ownerPubkey: String) {
        for (request in requests) {
            // Only persist payment requests, not subscription proposals
            if (request.type != RequestType.PaymentRequest) continue

            val paymentRequest = PaymentRequest(
                id = request.requestId,
                fromPubkey = request.fromPubkey,
                toPubkey = ownerPubkey,
                amountSats = request.amountSats,
                currency = "BTC",
                methodId = "lightning",
                description = request.description ?: "",
                status = PaymentRequestStatus.PENDING,
                direction = RequestDirection.INCOMING,
                createdAt = request.createdAt,
            )

            // Don't overwrite if already exists
            if (paymentRequestStorage.getRequest(request.requestId) == null) {
                paymentRequestStorage.addRequest(paymentRequest)
                Logger.debug("Persisted discovered request ${request.requestId} to storage", context = TAG)
            }
        }
    }

    private suspend fun processRequest(request: DiscoveredRequest) {
        Logger.info("Processing request ${request.requestId} of type ${request.type}", context = TAG)

        when (request.type) {
            RequestType.PaymentRequest -> processPaymentRequest(request)
            RequestType.SubscriptionProposal -> processSubscriptionProposal(request)
        }
    }

    private suspend fun processPaymentRequest(request: DiscoveredRequest) {
        // Evaluate auto-pay rules
        val decision = autoPayEvaluator.evaluate(
            peerPubkey = request.fromPubkey,
            amountSats = request.amountSats,
            methodId = null,
        )

        when (decision) {
            is AutoPayDecision.Approved -> {
                Logger.info("Auto-pay approved for request ${request.requestId}", context = TAG)

                // Try to execute payment
                val paymentResult = executePayment(request)
                if (paymentResult.isSuccess) {
                    sendPaymentSuccessNotification(request)
                    // Clean up processed request from directory
                    cleanupProcessedRequest(request)
                } else {
                    sendPaymentFailureNotification(request, paymentResult.exceptionOrNull())
                }
            }
            is AutoPayDecision.Denied -> {
                Logger.info("Auto-pay denied for request ${request.requestId}: ${decision.reason}", context = TAG)
                sendManualApprovalNotification(request)
            }
            is AutoPayDecision.NeedsManualApproval -> {
                sendManualApprovalNotification(request)
            }
        }
    }

    private suspend fun processSubscriptionProposal(request: DiscoveredRequest) {
        val identityPubkey = keyManager.getCurrentPublicKeyZ32()
        if (identityPubkey.isNullOrBlank()) {
            Logger.warn("No identity pubkey, skipping proposal processing", context = TAG)
            return
        }

        // Check if already seen (prevents duplicate notifications across restarts)
        if (proposalStorage.hasSeen(identityPubkey, request.requestId)) {
            Logger.debug("Proposal ${request.requestId} already seen, skipping notification", context = TAG)
            return
        }

        // Persist the proposal and mark as seen
        val proposal = DiscoveredSubscriptionProposal(
            subscriptionId = request.requestId,
            providerPubkey = request.fromPubkey,
            amountSats = request.amountSats,
            description = request.description,
            frequency = request.frequency ?: "monthly",
            createdAt = request.createdAt,
        )

        val isNew = proposalStorage.saveProposal(identityPubkey, proposal)
        proposalStorage.markSeen(identityPubkey, request.requestId)

        // Only notify for new proposals
        if (isNew) {
            sendSubscriptionProposalNotification(request)
        }
    }

    private suspend fun executePayment(request: DiscoveredRequest): kotlin.Result<Unit> = runCatching {
        // Wait for node to be ready
        val nodeReady = withTimeoutOrNull(NODE_READY_TIMEOUT_MS) {
            lightningRepo.lightningState.first { it.nodeLifecycleState == NodeLifecycleState.Running }
            true
        }

        if (nodeReady != true) {
            throw PaykitPollingException("Node not ready within timeout")
        }

        // Construct paykit: URI for proper payment routing
        val paykitUri = "paykit:${request.fromPubkey}"

        // Execute payment via Paykit payment service with spending limit enforcement
        val result = paymentService.pay(
            lightningRepo = lightningRepo,
            recipient = paykitUri,
            amountSats = request.amountSats.toULong(),
            peerPubkey = request.fromPubkey, // Use peer pubkey for spending limit
        )

        if (!result.success) {
            throw PaykitPollingException(result.error?.message ?: "Payment failed")
        }
    }

    /**
     * Cleanup processed request.
     *
     * NOTE: In the v0 sender-storage model, requests are stored on the sender's homeserver.
     * Recipients cannot delete requests from sender storage. Deduplication is handled locally
     * via [seenRequestIds] and [paymentRequestStorage].
     *
     * This method is intentionally a no-op - we only log for diagnostics.
     */
    @Suppress("UnusedParameter")
    private fun cleanupProcessedRequest(request: DiscoveredRequest) {
        Logger.debug(
            "Request ${request.requestId} processed (no remote delete in sender-storage model)",
            context = TAG,
        )
    }

    private fun sendManualApprovalNotification(request: DiscoveredRequest) {
        if (ActivityCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Logger.info("Notification permission not granted", context = TAG)
            return
        }

        val notificationId = request.requestId.hashCode()
        val builder = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(R.string.notification_payment_request_title))
            .setContentText(
                appContext.getString(
                    R.string.notification_payment_request_body_with_amount,
                    formatPubkey(request.fromPubkey),
                    formatSats(request.amountSats),
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        NotificationManagerCompat.from(appContext).notify(notificationId, builder.build())
        Logger.info("Sent manual approval notification for request ${request.requestId}", context = TAG)
    }

    private fun sendPaymentSuccessNotification(request: DiscoveredRequest) {
        if (ActivityCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationId = "success_${request.requestId}".hashCode()
        val builder = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(R.string.notification_autopay_executed_title))
            .setContentText(
                appContext.getString(
                    R.string.notification_payment_sent_body,
                    formatSats(request.amountSats),
                    formatPubkey(request.fromPubkey),
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        NotificationManagerCompat.from(appContext).notify(notificationId, builder.build())
    }

    private fun sendPaymentFailureNotification(request: DiscoveredRequest, error: Throwable?) {
        if (ActivityCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationId = "failure_${request.requestId}".hashCode()
        val builder = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(R.string.notification_subscription_failed_title))
            .setContentText(
                appContext.getString(
                    R.string.notification_payment_failed_body,
                    error?.message ?: "Unknown error",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        NotificationManagerCompat.from(appContext).notify(notificationId, builder.build())
    }

    private fun sendSubscriptionProposalNotification(request: DiscoveredRequest) {
        if (ActivityCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationId = "sub_${request.requestId}".hashCode()
        val builder = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(R.string.notification_subscription_proposal_title))
            .setContentText(
                appContext.getString(
                    R.string.notification_subscription_proposal_body,
                    formatPubkey(request.fromPubkey),
                    formatSats(request.amountSats),
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        NotificationManagerCompat.from(appContext).notify(notificationId, builder.build())
    }

    private fun formatPubkey(pubkey: String): String =
        if (pubkey.length > 12) {
            "${pubkey.take(6)}...${pubkey.takeLast(6)}"
        } else {
            pubkey
        }

    private fun formatSats(sats: Long): String {
        return "$sats sats"
    }
}

/**
 * A discovered payment request or subscription proposal
 */
data class DiscoveredRequest(
    val requestId: String,
    val type: RequestType,
    val fromPubkey: String,
    val amountSats: Long,
    val description: String?,
    val createdAt: Long,
    val frequency: String? = null,
)

enum class RequestType {
    PaymentRequest,
    SubscriptionProposal,
}

/**
 * Result of auto-pay evaluation
 */
sealed class AutoPayDecision {
    data class Approved(val ruleName: String?) : AutoPayDecision()
    data class Denied(val reason: String) : AutoPayDecision()
    data object NeedsManualApproval : AutoPayDecision()
}

/**
 * Subscription proposal discovered from directory
 */
data class DiscoveredSubscriptionProposal(
    val subscriptionId: String,
    val providerPubkey: String,
    val amountSats: Long,
    val description: String?,
    val frequency: String,
    val createdAt: Long,
)

class PaykitPollingException(message: String) : Exception(message)
