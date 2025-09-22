package to.bitkit.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the root of the JSON object.
 */
@Serializable
data class ReleaseInfoDTO(
    @SerialName("platforms")
    val platforms: Platforms,
)

@Serializable
data class Platforms(
    @SerialName("android")
    val android: PlatformDetails,
)

/**
 * Holds the specific version information for a single platform.
 */
@Serializable
data class PlatformDetails(
    @SerialName("version")
    val version: String,

    @SerialName("buildNumber")
    val buildNumber: Int,

    @SerialName("notes")
    val notes: String,

    @SerialName("pub_date")
    val pubDate: String,

    @SerialName("url")
    val url: String,

    @SerialName("critical")
    val isCritical: Boolean,
)
