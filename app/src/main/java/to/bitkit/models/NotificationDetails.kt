package to.bitkit.models

import kotlinx.serialization.Serializable

@Serializable
data class NotificationDetails(
    val title: String = "",
    val body: String = "",
)
