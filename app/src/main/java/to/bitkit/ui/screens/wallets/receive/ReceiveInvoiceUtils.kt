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
            onchainAddress
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
