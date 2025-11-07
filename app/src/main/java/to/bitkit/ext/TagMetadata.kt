package to.bitkit.ext

import com.synonym.bitkitcore.ActivityTagsMetadata
import to.bitkit.data.entities.TagMetadataEntity

fun TagMetadataEntity.toActivityTagsMetadata() = ActivityTagsMetadata(
    id,
    paymentHash,
    txId,
    address,
    isReceive,
    tags,
    createdAt.toULong(),
)

fun ActivityTagsMetadata.TagMetadataEntity() = TagMetadataEntity(
    id,
    paymentHash,
    txId,
    address,
    isReceive,
    tags,
    createdAt.toLong(),
)
