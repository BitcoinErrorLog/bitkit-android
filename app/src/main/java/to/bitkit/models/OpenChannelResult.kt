package to.bitkit.models

import org.lightningdevkit.ldknode.ChannelConfig
import org.lightningdevkit.ldknode.UserChannelId

data class OpenChannelResult(
    val userChannelId: UserChannelId,
    val peer: LnPeer,
    val channelAmountSats: ULong,
    val pushToCounterpartySats: ULong? = null,
    val channelConfig: ChannelConfig? = null,
)
