package to.bitkit.ui.screens.wallets.receive

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices.NEXUS_5
import androidx.compose.ui.tooling.preview.Devices.PIXEL_TABLET
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import to.bitkit.R
import to.bitkit.models.PrimaryDisplay
import to.bitkit.ui.LocalCurrencies
import to.bitkit.ui.components.BalanceHeaderView
import to.bitkit.ui.components.BodyM
import to.bitkit.ui.components.BottomSheetPreview
import to.bitkit.ui.components.Caption13Up
import to.bitkit.ui.components.PrimaryButton
import to.bitkit.ui.components.SecondaryButton
import to.bitkit.ui.components.Title
import to.bitkit.ui.components.VerticalSpacer
import to.bitkit.ui.currencyViewModel
import to.bitkit.ui.scaffold.SheetTopBar
import to.bitkit.ui.shared.modifiers.sheetHeight
import to.bitkit.ui.shared.util.gradientLinearBackground
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors
import to.bitkit.ui.utils.withAccent

// TODO pass these to nav?
@Serializable
data class CjitEntryDetails(
    val networkFeeSat: Long,
    val serviceFeeSat: Long,
    val channelSizeSat: Long,
    val feeSat: Long,
    val receiveAmountSats: Long,
    val invoice: String,
)

@Composable
fun ReceiveConfirmScreen(
    entry: CjitEntryDetails,
    isAdditional: Boolean = false,
    onLearnMore: () -> Unit,
    onContinue: (String) -> Unit,
    onBack: () -> Unit,
) {
    val currency = currencyViewModel ?: return
    val currencies = LocalCurrencies.current

    val networkFeeFormatted = remember(entry.networkFeeSat) {
        currency.convert(entry.networkFeeSat)
            ?.let { converted -> "${converted.symbol}${converted.formatted}" }
            ?: entry.networkFeeSat.toString()
    }

    val serviceFeeFormatted = remember(entry.serviceFeeSat) {
        currency.convert(entry.serviceFeeSat)
            ?.let { converted -> "${converted.symbol}${converted.formatted}" }
            ?: entry.serviceFeeSat.toString()
    }

    val displayUnit = currencies.displayUnit
    val primaryDisplay = currencies.primaryDisplay
    val receiveAmountFormatted = remember(entry.receiveAmountSats, entry.feeSat, primaryDisplay, displayUnit) {
        val sats = entry.receiveAmountSats - entry.feeSat

        currency.convert(sats)?.let { converted ->
            if (primaryDisplay == PrimaryDisplay.BITCOIN) {
                val btcComponents = converted.bitcoinDisplay(displayUnit)
                "${btcComponents.symbol} ${btcComponents.value}"
            } else {
                "${converted.symbol} ${converted.formatted}"
            }
        } ?: sats.toString()
    }

    Content(
        receiveSats = entry.receiveAmountSats,
        networkFeeFormatted = networkFeeFormatted,
        serviceFeeFormatted = serviceFeeFormatted,
        receiveAmountFormatted = receiveAmountFormatted,
        onLearnMoreClick = onLearnMore,
        isAdditional = isAdditional,
        onContinueClick = { onContinue(entry.invoice) },
        onBackClick = onBack,
    )
}

@Composable
private fun Content(
    receiveSats: Long,
    isAdditional: Boolean,
    networkFeeFormatted: String,
    serviceFeeFormatted: String,
    receiveAmountFormatted: String,
    onLearnMoreClick: () -> Unit,
    onContinueClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .gradientLinearBackground()
            .navigationBarsPadding()
    ) {
        SheetTopBar(stringResource(R.string.wallet__receive_bitcoin), onBack = onBackClick)
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(R.drawable.lightning),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(bottom = 150.dp)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            )

            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                BalanceHeaderView(
                    sats = receiveSats,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
                val text = when (isAdditional) {
                    true -> stringResource(R.string.wallet__receive_connect_additional)
                    else -> stringResource(R.string.wallet__receive_connect_initial)
                }
                BodyM(
                    text = text
                        .replace("{networkFee}", networkFeeFormatted)
                        .replace("{serviceFee}", serviceFeeFormatted)
                        .withAccent(
                            defaultColor = Colors.White64,
                            accentStyle = SpanStyle(color = Colors.White, fontWeight = FontWeight.Bold)
                        )
                )
                Spacer(modifier = Modifier.height(32.dp))
                Column {
                    Caption13Up(text = stringResource(R.string.wallet__receive_will), color = Colors.White64)
                    Spacer(Modifier.height(4.dp))
                    Title(text = receiveAmountFormatted)
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SecondaryButton(
                        text = stringResource(R.string.common__learn_more),
                        onClick = onLearnMoreClick,
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryButton(
                        text = stringResource(R.string.common__continue),
                        onClick = onContinueClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                VerticalSpacer(16.dp)
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    AppThemeSurface {
        BottomSheetPreview {
            Content(
                receiveSats = 12500L,
                isAdditional = false,
                networkFeeFormatted = "$0.50",
                serviceFeeFormatted = "$1.00",
                receiveAmountFormatted = "$100.00",
                onLearnMoreClick = {},
                onContinueClick = {},
                onBackClick = {},
                modifier = Modifier.sheetHeight(),
            )
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun PreviewAdditional() {
    AppThemeSurface {
        BottomSheetPreview {
            Content(
                receiveSats = 12500L,
                isAdditional = true,
                networkFeeFormatted = "$0.50",
                serviceFeeFormatted = "$1.00",
                receiveAmountFormatted = "$100.00",
                onLearnMoreClick = {},
                onContinueClick = {},
                onBackClick = {},
                modifier = Modifier.sheetHeight(),
            )
        }
    }
}

@Preview(showBackground = true, device = NEXUS_5)
@Composable
private fun PreviewSmall() {
    AppThemeSurface {
        BottomSheetPreview {
            Content(
                receiveSats = 12500L,
                isAdditional = true,
                networkFeeFormatted = "$0.50",
                serviceFeeFormatted = "$1.00",
                receiveAmountFormatted = "$100.00",
                onLearnMoreClick = {},
                onContinueClick = {},
                onBackClick = {},
                modifier = Modifier.sheetHeight(),
            )
        }
    }
}

@Preview(showSystemUi = true, device = PIXEL_TABLET)
@Composable
private fun PreviewTablet() {
    AppThemeSurface {
        BottomSheetPreview {
            Content(
                receiveSats = 12500L,
                isAdditional = true,
                networkFeeFormatted = "$0.50",
                serviceFeeFormatted = "$1.00",
                receiveAmountFormatted = "$100.00",
                onLearnMoreClick = {},
                onContinueClick = {},
                onBackClick = {},
                modifier = Modifier.sheetHeight(),
            )
        }
    }
}
