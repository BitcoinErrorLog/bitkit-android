package to.bitkit.ui.screens.wallets.receive

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import to.bitkit.R
import to.bitkit.ui.screens.wallets.activity.components.TabItem

enum class ReceiveTab : TabItem {
    SAVINGS,
    AUTO,
    SPENDING;

    override val uiText: String
        @Composable
        get() = when (this) {
            SAVINGS -> stringResource(R.string.wallet__receive_tab_savings)
            AUTO -> stringResource(R.string.wallet__receive_tab_auto)
            SPENDING -> stringResource(R.string.wallet__receive_tab_spending)
        }
}
