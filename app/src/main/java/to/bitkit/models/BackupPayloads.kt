package to.bitkit.models

import com.synonym.bitkitcore.Activity
import com.synonym.bitkitcore.ActivityTags
import com.synonym.bitkitcore.ClosedChannelDetails
import com.synonym.bitkitcore.IBtInfo
import com.synonym.bitkitcore.IBtOrder
import com.synonym.bitkitcore.IcJitEntry
import com.synonym.bitkitcore.PreActivityMetadata
import kotlinx.serialization.Serializable
import to.bitkit.data.AppCacheData
import to.bitkit.data.entities.TransferEntity

@Serializable
data class WalletBackupV1(
    val version: Int = 1,
    val createdAt: Long,
    val transfers: List<TransferEntity>,
)

@Serializable
data class MetadataBackupV1(
    val version: Int = 1,
    val createdAt: Long,
    val tagMetadata: List<PreActivityMetadata>,
    val cache: AppCacheData,
)

@Serializable
data class BlocktankBackupV1(
    val version: Int = 1,
    val createdAt: Long,
    val orders: List<IBtOrder>,
    val cjitEntries: List<IcJitEntry>,
    val info: IBtInfo? = null,
)

@Serializable
data class ActivityBackupV1(
    val version: Int = 1,
    val createdAt: Long,
    val activities: List<Activity>,
    val activityTags: List<ActivityTags>,
    val closedChannels: List<ClosedChannelDetails>,
)
