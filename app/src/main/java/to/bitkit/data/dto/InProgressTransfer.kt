package to.bitkit.data.dto

import kotlinx.serialization.Serializable
import to.bitkit.data.dto.TransferType.COOP_CLOSE
import to.bitkit.data.dto.TransferType.FORCE_CLOSE
import to.bitkit.data.dto.TransferType.MANUAL_SETUP
import to.bitkit.data.dto.TransferType.TO_SAVINGS
import to.bitkit.data.dto.TransferType.TO_SPENDING

@Serializable
data class InProgressTransfer(
    val id: String,
    val type: TransferType,
    val sats: ULong = 0u,
) {
    fun isToSavings(): Boolean = type == TO_SAVINGS || type == FORCE_CLOSE || type == COOP_CLOSE
    fun isToSpending(): Boolean = type == TO_SPENDING || type == MANUAL_SETUP
}
