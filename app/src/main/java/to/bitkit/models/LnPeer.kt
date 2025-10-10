package to.bitkit.models

import kotlinx.serialization.Serializable
import org.lightningdevkit.ldknode.PeerDetails

/**
 * TODO replace with direct usage of the now-serializable [PeerDetails] and extract utilities to extension methods
 */
@Serializable
data class LnPeer(
    val nodeId: String,
    val host: String,
    val port: String,
    val isConnected: Boolean = false,
    val isPersisted: Boolean = false,
) {
    constructor(
        nodeId: String,
        address: String,
    ) : this(
        nodeId,
        address.substringBefore(":"),
        address.substringAfter(":"),
    )

    constructor(peerDetails: PeerDetails) : this(
        nodeId = peerDetails.nodeId,
        host = peerDetails.address.substringBefore(":"),
        port = peerDetails.address.substringAfter(":"),
        isConnected = peerDetails.isConnected,
        isPersisted = peerDetails.isPersisted,
    )

    val address get() = "$host:$port"

    override fun toString() = "$nodeId@$address"

    companion object {
        fun parseUri(uriString: String): Result<LnPeer> {
            val uriComponents = uriString.split("@")
            val nodeId = uriComponents[0]

            if (uriComponents.size != 2) {
                return Result.failure(Exception("Invalid peer uri"))
            }

            val address = uriComponents[1].split(":")

            if (address.size < 2) {
                return Result.failure(Exception("Invalid peer uri"))
            }

            val ip = address[0]
            val port = address[1]

            return Result.success(
                LnPeer(
                    nodeId = nodeId,
                    host = ip,
                    port = port,
                )
            )
        }
    }
}
