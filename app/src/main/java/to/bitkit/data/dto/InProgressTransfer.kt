package to.bitkit.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class InProgressTransfer(
    val activityId: String,
    val type: TransferType,
)
