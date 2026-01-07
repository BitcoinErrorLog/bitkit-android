package to.bitkit.fcm

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.lightningdevkit.ldknode.Event
import to.bitkit.R
import to.bitkit.data.CacheStore
import to.bitkit.data.SettingsStore
import to.bitkit.di.json
import to.bitkit.ext.amountOnClose
import to.bitkit.models.BITCOIN_SYMBOL
import to.bitkit.models.BlocktankNotificationType
import to.bitkit.models.BlocktankNotificationType.cjitPaymentArrived
import to.bitkit.models.BlocktankNotificationType.incomingHtlc
import to.bitkit.models.BlocktankNotificationType.mutualClose
import to.bitkit.models.BlocktankNotificationType.orderPaymentConfirmed
import to.bitkit.models.BlocktankNotificationType.paykitAutoPayExecuted
import to.bitkit.models.BlocktankNotificationType.paykitPaymentRequest
import to.bitkit.models.BlocktankNotificationType.paykitSubscriptionDue
import to.bitkit.models.BlocktankNotificationType.paykitSubscriptionFailed
import to.bitkit.models.BlocktankNotificationType.wakeToTimeout
import to.bitkit.models.NewTransactionSheetDetails
import to.bitkit.models.NewTransactionSheetDirection
import to.bitkit.models.NewTransactionSheetType
import to.bitkit.models.NotificationDetails
import to.bitkit.repositories.ActivityRepo
import to.bitkit.repositories.BlocktankRepo
import to.bitkit.repositories.LightningRepo
import to.bitkit.services.CoreService
import to.bitkit.ui.pushNotification
import to.bitkit.utils.Logger
import to.bitkit.utils.withPerformanceLogging
import kotlin.time.Duration.Companion.minutes

@Suppress("LongParameterList")
@HiltWorker
class WakeNodeWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val coreService: CoreService,
    private val lightningRepo: LightningRepo,
    private val blocktankRepo: BlocktankRepo,
    private val activityRepo: ActivityRepo,
    private val settingsStore: SettingsStore,
    private val cacheStore: CacheStore,
) : CoroutineWorker(appContext, workerParams) {
    private val self = this

    private var bestAttemptContent: NotificationDetails? = null
    private var bestAttemptDeepLink: Uri? = null

    private var notificationType: BlocktankNotificationType? = null
    private var notificationPayload: JsonObject? = null

    private val timeout = 2.minutes
    private val deliverSignal = CompletableDeferred<Unit>()

    override suspend fun doWork(): Result {
        Logger.debug("Node wakeup from notification…")

        notificationType = workerParams.inputData.getString("type")?.let { BlocktankNotificationType.valueOf(it) }
        notificationPayload = workerParams.inputData.getString("payload")?.let {
            runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull()
        }

        Logger.debug("${this::class.simpleName} notification type: $notificationType")
        Logger.debug("${this::class.simpleName} notification payload: $notificationPayload")

        try {
            withPerformanceLogging {
                lightningRepo.start(
                    walletIndex = 0,
                    timeout = timeout,
                    eventHandler = { event -> handleLdkEvent(event) }
                )
                lightningRepo.connectToTrustedPeers()

                // Once node is started, handle the manual channel opening if needed
                if (self.notificationType == orderPaymentConfirmed) {
                    val orderId = (notificationPayload?.get("orderId") as? JsonPrimitive)?.contentOrNull

                    if (orderId == null) {
                        Logger.error("Missing orderId")
                    } else {
                        try {
                            Logger.info("Open channel request for order $orderId")
                            coreService.blocktank.open(orderId = orderId)
                        } catch (e: Exception) {
                            Logger.error("failed to open channel", e)
                            self.bestAttemptContent = NotificationDetails(
                                title = appContext.getString(R.string.notification_channel_open_failed_title),
                                body = e.message ?: appContext.getString(R.string.notification_unknown_error),
                            )
                            self.deliver()
                        }
                    }
                }

                // Handle Paykit notification types
                handlePaykitNotification()
            }
            withTimeout(timeout) { deliverSignal.await() } // Stops node on timeout & avoids notification replay by OS
            return Result.success()
        } catch (e: Exception) {
            val reason = e.message ?: appContext.getString(R.string.notification_unknown_error)

            self.bestAttemptContent = NotificationDetails(
                title = appContext.getString(R.string.notification_lightning_error_title),
                body = reason,
            )
            Logger.error("Lightning error", e)
            self.deliver()

            return Result.failure(workDataOf("Reason" to reason))
        }
    }

    /**
     * Listens for LDK events and delivers the notification if the event matches the notification type.
     * @param event The LDK event to check.
     */
    private suspend fun handleLdkEvent(event: Event) {
        val showDetails = settingsStore.data.first().showNotificationDetails
        val hiddenBody = appContext.getString(R.string.notification_received_body_hidden)
        when (event) {
            is Event.PaymentReceived -> onPaymentReceived(event, showDetails, hiddenBody)

            is Event.ChannelPending -> {
                self.bestAttemptContent = NotificationDetails(
                    title = appContext.getString(R.string.notification_channel_opened_title),
                    body = appContext.getString(R.string.notification_channel_pending_body),
                )
                // Don't deliver, give a chance for channelReady event to update the content if it's a turbo channel
            }

            is Event.ChannelReady -> onChannelReady(event, showDetails, hiddenBody)
            is Event.ChannelClosed -> onChannelClosed(event)

            is Event.PaymentFailed -> {
                self.bestAttemptContent = NotificationDetails(
                    title = appContext.getString(R.string.notification_payment_failed_title),
                    body = "⚡ ${event.reason}",
                )

                if (self.notificationType == wakeToTimeout) {
                    self.deliver()
                }
            }

            else -> Unit
        }
    }

    private suspend fun onChannelClosed(event: Event.ChannelClosed) {
        self.bestAttemptContent = when (self.notificationType) {
            mutualClose -> NotificationDetails(
                title = appContext.getString(R.string.notification_channel_closed_title),
                body = appContext.getString(R.string.notification_channel_closed_mutual_body),
            )

            orderPaymentConfirmed -> NotificationDetails(
                title = appContext.getString(R.string.notification_channel_open_bg_failed_title),
                body = appContext.getString(R.string.notification_please_try_again_body),
            )

            else -> NotificationDetails(
                title = appContext.getString(R.string.notification_channel_closed_title),
                body = appContext.getString(R.string.notification_channel_closed_reason_body, event.reason),
            )
        }

        self.deliver()
    }

    private suspend fun onPaymentReceived(
        event: Event.PaymentReceived,
        showDetails: Boolean,
        hiddenBody: String,
    ) {
        val sats = event.amountMsat / 1000u
        // Save for UI to pick up
        cacheStore.setBackgroundReceive(
            NewTransactionSheetDetails(
                type = NewTransactionSheetType.LIGHTNING,
                direction = NewTransactionSheetDirection.RECEIVED,
                paymentHashOrTxId = event.paymentHash,
                sats = sats.toLong(),
            )
        )
        val content = if (showDetails) "$BITCOIN_SYMBOL $sats" else hiddenBody
        bestAttemptContent = NotificationDetails(
            title = appContext.getString(R.string.notification_received_title),
            body = content,
        )
        if (self.notificationType == incomingHtlc) {
            self.deliver()
        }
    }

    private suspend fun onChannelReady(
        event: Event.ChannelReady,
        showDetails: Boolean,
        hiddenBody: String,
    ) {
        val viaNewChannel = appContext.getString(R.string.notification_via_new_channel_body)
        if (self.notificationType == cjitPaymentArrived) {
            self.bestAttemptContent = NotificationDetails(
                title = appContext.getString(R.string.notification_received_title),
                body = viaNewChannel,
            )

            lightningRepo.getChannels()?.find { it.channelId == event.channelId }?.let { channel ->
                val sats = channel.amountOnClose
                val content = if (showDetails) "$BITCOIN_SYMBOL $sats" else hiddenBody
                self.bestAttemptContent = NotificationDetails(
                    title = content,
                    body = viaNewChannel,
                )
                val cjitEntry = channel.let { blocktankRepo.getCjitEntry(it) }
                if (cjitEntry != null) {
                    // Save for UI to pick up
                    cacheStore.setBackgroundReceive(
                        NewTransactionSheetDetails(
                            type = NewTransactionSheetType.LIGHTNING,
                            direction = NewTransactionSheetDirection.RECEIVED,
                            sats = sats.toLong(),
                        )
                    )
                    activityRepo.insertActivityFromCjit(cjitEntry = cjitEntry, channel = channel)
                }
            }
        } else if (self.notificationType == orderPaymentConfirmed) {
            self.bestAttemptContent = NotificationDetails(
                title = appContext.getString(R.string.notification_channel_opened_title),
                body = appContext.getString(R.string.notification_channel_ready_body),
            )
        }
        self.deliver()
    }

    private suspend fun handlePaykitNotification() {
        when (self.notificationType) {
            paykitPaymentRequest -> {
                val requestId = (notificationPayload?.get("requestId") as? JsonPrimitive)?.contentOrNull
                val fromPubkey = (notificationPayload?.get("from") as? JsonPrimitive)?.contentOrNull
                if (requestId == null) {
                    Logger.error("Missing requestId for payment request")
                    return
                }

                Logger.info("Processing Paykit payment request $requestId")

                // Node is already started, Paykit can process the payment request
                // The actual processing will happen in foreground when app opens
                self.bestAttemptContent = NotificationDetails(
                    title = appContext.getString(R.string.notification_payment_request_title),
                    body = appContext.getString(R.string.notification_payment_request_body),
                )
                // Set deep link for payment request detail (with from if available, else general requests screen)
                self.bestAttemptDeepLink = if (fromPubkey != null) {
                    Uri.parse("bitkit://payment-request?requestId=$requestId&from=$fromPubkey")
                } else {
                    Uri.parse("bitkit://payment-requests")
                }
                self.deliver()
            }

            paykitSubscriptionDue -> {
                val subscriptionId = (notificationPayload?.get("subscriptionId") as? JsonPrimitive)?.contentOrNull
                if (subscriptionId == null) {
                    Logger.error("Missing subscriptionId for subscription")
                    return
                }

                Logger.info("Processing subscription payment $subscriptionId")

                // Node is started, subscription payment can be processed
                self.bestAttemptContent = NotificationDetails(
                    title = appContext.getString(R.string.notification_subscription_due_title),
                    body = appContext.getString(R.string.notification_subscription_due_body),
                )
                self.bestAttemptDeepLink = Uri.parse("bitkit://subscriptions")
                self.deliver()
            }

            paykitAutoPayExecuted -> {
                val amount = (notificationPayload?.get("amount") as? JsonPrimitive)?.contentOrNull?.toULongOrNull()
                if (amount == null) {
                    Logger.error("Missing amount for auto-pay")
                    return
                }

                Logger.info("Auto-pay executed for amount $amount")

                self.bestAttemptContent = NotificationDetails(
                    title = appContext.getString(R.string.notification_autopay_executed_title),
                    body = "$BITCOIN_SYMBOL $amount ${appContext.getString(R.string.notification_sent)}",
                )
                self.bestAttemptDeepLink = Uri.parse("bitkit://payment-requests")
                self.deliver()
            }

            paykitSubscriptionFailed -> {
                val subscriptionId = (notificationPayload?.get("subscriptionId") as? JsonPrimitive)?.contentOrNull
                val reason = (notificationPayload?.get("reason") as? JsonPrimitive)?.contentOrNull
                if (subscriptionId == null || reason == null) {
                    Logger.error("Missing details for subscription failure")
                    return
                }

                Logger.info("Subscription payment failed $subscriptionId: $reason")

                self.bestAttemptContent = NotificationDetails(
                    title = appContext.getString(R.string.notification_subscription_failed_title),
                    body = reason,
                )
                self.bestAttemptDeepLink = Uri.parse("bitkit://subscriptions")
                self.deliver()
            }

            else -> {
                // Not a Paykit notification, do nothing
            }
        }
    }

    private suspend fun deliver() {
        lightningRepo.stop()

        bestAttemptContent?.run {
            appContext.pushNotification(
                title = title,
                text = body,
                deepLinkUri = bestAttemptDeepLink,
            )
            Logger.info("Delivered notification")
        }

        deliverSignal.complete(Unit)
    }
}
