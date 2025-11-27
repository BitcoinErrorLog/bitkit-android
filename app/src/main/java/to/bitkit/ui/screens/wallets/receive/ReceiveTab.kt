package to.bitkit.ui.screens.wallets.receive

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import to.bitkit.R

enum class ReceiveTab {
    SAVINGS,    // Pure onchain (BIP21 without Lightning)
    AUTO,       // Unified (BIP21 with Lightning or CJIT invoice)
    SPENDING;   // Pure Lightning (bolt11 or CJIT invoice)

    val uiText: String
        @Composable
        get() = when (this) {
            SAVINGS -> stringResource(R.string.wallet__receive_tab_savings)
            AUTO -> stringResource(R.string.wallet__receive_tab_auto)
            SPENDING -> stringResource(R.string.wallet__receive_tab_spending)
        }
}
