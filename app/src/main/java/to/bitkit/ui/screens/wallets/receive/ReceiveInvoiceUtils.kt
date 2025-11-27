package to.bitkit.ui.screens.wallets.receive

import org.lightningdevkit.ldknode.ChannelDetails
import to.bitkit.R
import to.bitkit.models.NodeLifecycleState

/**
 * Strips the Lightning invoice parameter from a BIP21 URI, returning pure onchain address.
 *
 * Example:
 * Input: "bitcoin:bc1q...?amount=0.001&lightning=lnbc..."
 * Output: "bitcoin:bc1q...?amount=0.001"
 *
 * @param bip21 The full BIP21 URI
 * @return BIP21 URI without lightning parameter
 */
fun stripLightningFromBip21(bip21: String): String {
    if (bip21.isEmpty()) return bip21

    // Remove lightning parameter and its value
    // Pattern: &lightning=... or ?lightning=...
    val lightningParamRegex = Regex("[?&]lightning=[^&]*")
    var result = bip21.replace(lightningParamRegex, "")

    // If we removed the first param (started with ?), convert & to ?
    if (result.contains("&") && !result.contains("?")) {
        result = result.replaceFirst("&", "?")
    }

    // Clean up trailing ? or &
    result = result.trimEnd('?', '&')

    return result
}

/**
 * Returns the appropriate invoice/address for the selected tab.
 *
 * @param tab The selected receive tab
 * @param bip21 Full BIP21 invoice (onchain + lightning)
 * @param bolt11 Lightning invoice
 * @param cjitInvoice CJIT invoice from Blocktank (if active)
 * @param onchainAddress Pure Bitcoin address (fallback)
 * @return The invoice string to display/encode in QR
 */
fun getInvoiceForTab(
    tab: ReceiveTab,
    bip21: String,
    bolt11: String,
    cjitInvoice: String?,
    onchainAddress: String
): String {
    return when (tab) {
        ReceiveTab.SAVINGS -> {
            // Pure onchain: strip lightning from BIP21
            val strippedBip21 = stripLightningFromBip21(bip21)
            strippedBip21.ifEmpty { onchainAddress }
        }
        ReceiveTab.AUTO -> {
            // Unified: prefer CJIT > full BIP21
            cjitInvoice?.takeIf { it.isNotEmpty() }
                ?: bip21.ifEmpty { onchainAddress }
        }
        ReceiveTab.SPENDING -> {
            // Lightning only: prefer CJIT > bolt11
            cjitInvoice?.takeIf { it.isNotEmpty() }
                ?: bolt11.ifEmpty { onchainAddress }
        }
    }
}

/**
 * Returns the appropriate QR code logo resource for the selected tab.
 *
 * @param tab The selected receive tab
 * @param hasCjit Whether a CJIT invoice is active
 * @return Drawable resource ID for QR logo
 */
fun getQrLogoResource(tab: ReceiveTab, hasCjit: Boolean): Int {
    return when (tab) {
        ReceiveTab.SAVINGS -> R.drawable.ic_btc_circle
        ReceiveTab.AUTO -> {
            // Unified logo if CJIT or standard unified
            if (hasCjit) R.drawable.ic_unified_circle
            else R.drawable.ic_unified_circle
        }
        ReceiveTab.SPENDING -> R.drawable.ic_ln_circle
    }
}

/**
 * Determines whether the Auto (unified) tab should be visible.
 *
 * Logic:
 * - Node must be running
 * - If geoblocked: only show if user has existing channels (grandfathered)
 * - If not geoblocked: always show
 *
 * @param channels List of Lightning channels
 * @param isGeoblocked Whether Lightning is geoblocked for this user
 * @param nodeRunning Whether the Lightning node is running
 * @return true if Auto tab should be visible
 */
fun shouldShowAutoTab(
    channels: List<ChannelDetails>,
    isGeoblocked: Boolean,
    nodeRunning: Boolean
): Boolean {
    if (!nodeRunning) return false

    return if (isGeoblocked) {
        // Geoblocked users can still use Auto if they have existing channels
        channels.isNotEmpty()
    } else {
        // Not geoblocked: always show Auto tab
        true
    }
}

/**
 * Extension: Check if node lifecycle state is running.
 */
fun NodeLifecycleState.isRunning(): Boolean {
    return this == NodeLifecycleState.Running
}

/**
 * Extension: Check if node lifecycle state is starting.
 */
fun NodeLifecycleState.isStarting(): Boolean {
    return this == NodeLifecycleState.Starting
}
