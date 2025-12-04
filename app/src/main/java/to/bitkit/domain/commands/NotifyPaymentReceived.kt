package to.bitkit.domain.commands

import org.lightningdevkit.ldknode.Event
import to.bitkit.models.NewTransactionSheetDetails
import to.bitkit.models.NotificationDetails

sealed interface NotifyPaymentReceived {

    sealed interface Command : NotifyPaymentReceived {
        val includeNotification: Boolean

        data class Lightning(
            val event: Event.PaymentReceived,
            override val includeNotification: Boolean = false,
        ) : Command

        data class Onchain(
            val event: Event.OnchainTransactionReceived,
            override val includeNotification: Boolean = false,
        ) : Command

        companion object {
            fun from(event: Event, includeNotification: Boolean = false): Command? =
                when (event) {
                    is Event.PaymentReceived -> Lightning(
                        event = event,
                        includeNotification = includeNotification,
                    )

                    is Event.OnchainTransactionReceived -> Onchain(
                        event = event,
                        includeNotification = includeNotification,
                    )

                    else -> null
                }
        }
    }

    sealed interface Result : NotifyPaymentReceived {
        data class ShowSheet(
            val sheet: NewTransactionSheetDetails,
        ) : Result

        data class ShowNotification(
            val sheet: NewTransactionSheetDetails,
            val notification: NotificationDetails,
        ) : Result

        data object Skip : Result
    }
}
