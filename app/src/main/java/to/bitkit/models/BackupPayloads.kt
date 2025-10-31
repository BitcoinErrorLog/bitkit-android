package to.bitkit.models

import com.synonym.bitkitcore.Activity
import com.synonym.bitkitcore.IBtInfo
import com.synonym.bitkitcore.IBtOrder
import com.synonym.bitkitcore.IcJitEntry
import kotlinx.serialization.Serializable
import to.bitkit.data.dto.PendingBoostActivity
import to.bitkit.data.entities.TagMetadataEntity
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

/**
 * Metadata backup payload (v1)
 *
 * Contains:
 * - Tag metadata entities from Room database
 * - Transaction metadata from CacheStore
 */
@Serializable
data class MetadataBackupV1(
    val version: Int = 1,
    val createdAt: Long,
    val tagMetadata: List<TagMetadataEntity>,
    val transactionsMetadata: List<TransactionMetadata>,
)

/**
 * Blocktank backup payload (v1)
 *
 * Contains:
 * - Paid orders map from CacheStore
 * - List of IBtOrder from bitkit-core
 * - List of IcJitEntry from bitkit-core
 * - IBtInfo from bitkit-core
 */
@Serializable
data class BlocktankBackupV1(
    val version: Int = 1,
    val createdAt: Long,
    val paidOrders: Map<String, String>, // orderId -> txId
    val orders: List<IBtOrder>,
    val cjitEntries: List<IcJitEntry>,
    val info: IBtInfo? = null,
)

