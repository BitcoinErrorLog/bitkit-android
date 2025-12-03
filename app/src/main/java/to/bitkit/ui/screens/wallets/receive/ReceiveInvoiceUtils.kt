package to.bitkit.ui.screens.wallets.receive

import to.bitkit.R

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
    isNodeRunning: Boolean,
    onchainAddress: String,
): String {
    return when (tab) {
        ReceiveTab.SAVINGS -> {
            // Return BIP21 without lightning parameter to preserve amount and other parameters
            removeLightningFromBip21(bip21, onchainAddress)
        }

        ReceiveTab.AUTO -> {
            bip21.takeIf { isNodeRunning }.orEmpty()
        }

        ReceiveTab.SPENDING -> {
            // Lightning only: prefer CJIT > bolt11
            cjitInvoice?.takeIf { it.isNotEmpty() && isNodeRunning }
                ?: bolt11
        }
    }
}

/**
 * Removes the lightning parameter from a BIP21 URI while preserving all other parameters.
 *
 * @param bip21 Full BIP21 URI (e.g., bitcoin:address?amount=0.001&lightning=lnbc...)
 * @param fallbackAddress Fallback address if BIP21 is empty or invalid
 * @return BIP21 URI without the lightning parameter (e.g., bitcoin:address?amount=0.001)
 */
private fun removeLightningFromBip21(bip21: String, fallbackAddress: String): String {
    if (bip21.isBlank()) return fallbackAddress

    // Remove lightning parameter using regex
    // Handles both "?lightning=..." and "&lightning=..." cases
    val withoutLightning = bip21
        .replace(Regex("[?&]lightning=[^&]*"), "")
        .replace(Regex("\\?$"), "") // Remove trailing ? if it's the last char

    return withoutLightning.ifBlank { fallbackAddress }
}

/**
 * Returns the appropriate QR code logo resource for the selected tab.
 *
 * @param tab The selected receive tab
 * @return Drawable resource ID for QR logo
 */
fun getQrLogoResource(tab: ReceiveTab): Int {
    return when (tab) {
        ReceiveTab.SAVINGS -> R.drawable.ic_btc_circle
        ReceiveTab.AUTO -> R.drawable.ic_unified_circle
        ReceiveTab.SPENDING -> R.drawable.ic_ln_circle
    }
}
