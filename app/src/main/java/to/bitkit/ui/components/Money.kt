package to.bitkit.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import to.bitkit.models.BITCOIN_SYMBOL
import to.bitkit.models.PrimaryDisplay
import to.bitkit.models.formatToModernDisplay
import to.bitkit.ui.LocalCurrencies
import to.bitkit.ui.currencyViewModel
import to.bitkit.ui.shared.util.clickableAlpha
import to.bitkit.ui.theme.Colors
import to.bitkit.ui.utils.withAccent
import to.bitkit.viewmodels.CurrencyUiState

@Composable
fun MoneyDisplay(
    sats: Long,
    onClick: (() -> Unit)? = null,
) {
    rememberMoneyText(sats)?.let { text ->
        Display(
            text = text.withAccent(accentColor = Colors.White64),
            modifier = Modifier.clickableAlpha(onClick = onClick)
        )
    }
}

@Composable
fun MoneySSB(
    sats: Long,
    unit: PrimaryDisplay = LocalCurrencies.current.primaryDisplay,
) {
    rememberMoneyText(sats = sats, unit = unit)?.let { text ->
        BodySSB(text = text.withAccent(accentColor = Colors.White64))
    }
}

@Composable
fun MoneyCaptionB(
    sats: Long,
    color: Color = MaterialTheme.colorScheme.primary,
    symbol: Boolean = false,
    symbolColor: Color = Colors.White64,
    modifier: Modifier = Modifier,
) {
    val isPreview = LocalInspectionMode.current
    if (isPreview) {
        val previewText = sats.formatToModernDisplay().let { if (symbol) "<accent>₿</accent> $it" else it }
        CaptionB(text = previewText.withAccent(accentColor = symbolColor), color = color)
        return
    }

    val currency = currencyViewModel ?: return
    val currencies = LocalCurrencies.current

    val displayText = remember(currencies, sats, symbol) {
        currency.convert(sats)?.let { converted ->
            val btc = converted.bitcoinDisplay(currencies.displayUnit)
            if (symbol) {
                "<accent>${btc.symbol}</accent> ${btc.value}"
            } else {
                btc.value
            }
        }
    }

    displayText?.let { text ->
        CaptionB(
            text = text.withAccent(accentColor = symbolColor),
            color = color,
            modifier = modifier,
        )
    }
}

@Composable
fun rememberMoneyText(
    sats: Long,
    reversed: Boolean = false,
    currencies: CurrencyUiState = LocalCurrencies.current,
    unit: PrimaryDisplay = if (reversed) currencies.primaryDisplay.not() else currencies.primaryDisplay,
): String? {
    val isPreview = LocalInspectionMode.current
    if (isPreview) {
        val symbol = if (unit == PrimaryDisplay.BITCOIN) BITCOIN_SYMBOL else "$"
        return "<accent>$symbol</accent> ${sats.formatToModernDisplay()}"
    }

    val currency = currencyViewModel ?: return null

    return remember(currencies, sats, reversed) {
        val converted = currency.convert(sats) ?: return@remember null

        if (unit == PrimaryDisplay.BITCOIN) {
            val btcComponents = converted.bitcoinDisplay(currencies.displayUnit)
            "<accent>${btcComponents.symbol}</accent> ${btcComponents.value}"
        } else {
            "<accent>${converted.symbol}</accent> ${converted.formatted}"
        }
    }
}
