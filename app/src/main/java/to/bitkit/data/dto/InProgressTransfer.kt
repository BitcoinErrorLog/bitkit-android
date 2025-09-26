package to.bitkit.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class InProgressTransfer(
    val id: String,
    val type: TransferType,
    val sats: ULong = 0u,
)
