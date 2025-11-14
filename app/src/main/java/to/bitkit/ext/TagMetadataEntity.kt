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
    feeRate = 0u, // TODO: update room db entity or drop it in favour of bitkit-core
    isTransfer = false, // TODO: update room db entity or drop it in favour of bitkit-core
    channelId = "", // TODO: update room db entity or drop it in favour of bitkit-core
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
