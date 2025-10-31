package to.bitkit.models

import kotlinx.serialization.Serializable
import to.bitkit.data.dto.PendingBoostActivity
import to.bitkit.data.entities.TransferEntity

/**
 * Wallet backup payload (v1)
 *
 * Contains:
 * - Boosted transaction activities from CacheStore
 * - Transfer entities from Room database
 */
@Serializable
data class WalletBackupV1(
    val version: Int = 1,
    val createdAt: Long,
    val boostedActivities: List<PendingBoostActivity>,
    val transfers: List<TransferEntity>,
)

