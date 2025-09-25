package to.bitkit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.bitkit.R
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors

@Composable
fun IncomingTransfer(
    amount: ULong,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_transfer),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Colors.White64,
        )
        CaptionB(
            text = stringResource(R.string.wallet__details_transfer_subtitle),
            color = Colors.White64,
        )
        CaptionB(
            text = rememberMoneyText(sats = amount.toLong()).orEmpty(),
            color = Colors.White64,
        )
    }
}

@Preview
@Composable
private fun Preview() {
    AppThemeSurface {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IncomingTransfer(amount = 85_967u)
            IncomingTransfer(amount = 15_231_648u)
        }
    }
}
