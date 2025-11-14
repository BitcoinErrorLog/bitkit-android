package to.bitkit.ext

import com.synonym.bitkitcore.PreActivityMetadata
import to.bitkit.data.entities.TagMetadataEntity

// TODO use PreActivityMetadata
fun TagMetadataEntity.toActivityTagsMetadata() = PreActivityMetadata(
    paymentId = id,
    createdAt = createdAt.toULong(),
    tags = tags,
    paymentHash = paymentHash,
    txId = txId,
    address = address,
    isReceive = isReceive,
    feeRate = 0u,
    isTransfer = false,
    channelId = "",
)

fun PreActivityMetadata.toTagMetadataEntity() = TagMetadataEntity(
    id = paymentId,
    createdAt = createdAt.toLong(),
    tags = tags,
    paymentHash = paymentHash,
    txId = txId,
    address = address.orEmpty(),
    isReceive = isReceive,
    // feeRate = 0u,
    // isTransfer = false,
    // channelId = "",
)
