package to.bitkit.models

import com.synonym.bitkitcore.Activity
import com.synonym.bitkitcore.IBtInfo
import com.synonym.bitkitcore.IBtOrder
import com.synonym.bitkitcore.IcJitEntry
import kotlinx.serialization.Serializable
import to.bitkit.data.AppCacheData
import to.bitkit.data.entities.TagMetadataEntity
import to.bitkit.data.entities.TransferEntity

/**
 * Wallet backup payload (v1)
 *
 * Contains:
 * - Transfer entities from Room database
 */
@Serializable
data class WalletBackupV1(
    val version: Int = 1,
    val createdAt: Long,
    val transfers: List<TransferEntity>,
)

/**
 * Metadata backup payload (v1)
 *
 * Contains:
 * - Tag metadata entities from Room database
 * - Entire AppCacheData from CacheStore
 */
@Serializable
data class MetadataBackupV1(
    val version: Int = 1,
    val createdAt: Long,
    val tagMetadata: List<TagMetadataEntity>,
    val cache: AppCacheData,
)

/**
 * Blocktank backup payload (v1)
 *
 * Contains:
 * - List of IBtOrder from bitkit-core
 * - List of IcJitEntry from bitkit-core
 * - IBtInfo from bitkit-core
 */
@Serializable
data class BlocktankBackupV1(
    val version: Int = 1,
    val createdAt: Long,
    val orders: List<IBtOrder>,
    val cjitEntries: List<IcJitEntry>,
    val info: IBtInfo? = null,
)

/**
 * Activity backup payload (v1)
 *
 * Contains:
 * - ALL activities (onchain + lightning) from bitkit-core
 */
@Serializable
data class ActivityBackupV1(
    val version: Int = 1,
    val createdAt: Long,
    val activities: List<Activity>,
)
