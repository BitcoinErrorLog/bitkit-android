package to.bitkit.ui.screens.wallets.send

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.bitkit.R
import to.bitkit.models.BITCOIN_SYMBOL
import to.bitkit.models.ConvertedAmount
import to.bitkit.models.TransactionSpeed
import to.bitkit.ui.components.BodyM
import to.bitkit.ui.components.BottomSheetPreview
import to.bitkit.ui.components.FillHeight
import to.bitkit.ui.components.KEY_DELETE
import to.bitkit.ui.components.LargeRow
import to.bitkit.ui.components.NumberPadSimple
import to.bitkit.ui.components.PrimaryButton
import to.bitkit.ui.components.VerticalSpacer
import to.bitkit.ui.components.settings.SectionHeader
import to.bitkit.ui.currencyViewModel
import to.bitkit.ui.scaffold.SheetTopBar
import to.bitkit.ui.shared.modifiers.sheetHeight
import to.bitkit.ui.shared.util.gradientBackground
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors

@Composable
fun SendFeeCustomScreen(
    onBack: () -> Unit,
    onContinue: (TransactionSpeed) -> Unit,
    viewModel: SendFeeViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currency = currencyViewModel
    var converted: ConvertedAmount? by remember { mutableStateOf(null) }
    var totalFee by remember { mutableStateOf(0uL) }

    var input by remember { mutableStateOf(uiState.custom?.satsPerVByte?.toString() ?: "") }

    LaunchedEffect(input) {
        val satsPerVByte = input.toUIntOrNull() ?: 0u
        totalFee = if (satsPerVByte > 0u) {
            viewModel.calculateTotalFee(satsPerVByte)
        } else {
            0u
        }

        converted = if (totalFee == 0uL || currency == null) {
            null
        } else {
            currency.convert(totalFee.toLong())
        }
    }

    val totalFeeText = converted
        ?.let {
            stringResource(R.string.wallet__send_fee_total_fiat)
                .replace("{feeSats}", "$totalFee")
                .replace("{fiatSymbol}", it.symbol)
                .replace("{fiatFormatted}", it.formatted)
        }
        ?: stringResource(R.string.wallet__send_fee_total).replace("{feeSats}", "$totalFee")

    Content(
        input = input,
        totalFeeText = totalFeeText,
        onKeyPress = { key ->
            when (key) {
                KEY_DELETE -> input = if (input.isNotEmpty()) input.dropLast(1) else ""
                else -> if (input.length < 3) input = (input + key).trimStart('0')
            }
            viewModel.onInputChange(input)
        },
        onBack = onBack,
        onContinue = {
            val feeRate = input.toUIntOrNull() ?: 0u
            onContinue(TransactionSpeed.Custom(feeRate))
        },
    )
}

@Composable
private fun Content(
    input: String,
    totalFeeText: String,
    modifier: Modifier = Modifier,
    onKeyPress: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onContinue: () -> Unit = {},
) {
    val feeRate = input.toUIntOrNull() ?: 0u
    val isValid = feeRate != 0u

    Column(
        modifier = modifier
            .fillMaxSize()
            .gradientBackground()
            .navigationBarsPadding()
            .testTag("fee_screen")
    ) {
        SheetTopBar(stringResource(R.string.wallet__send_fee_custom), onBack = onBack)
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxSize()
        ) {
            SectionHeader(title = stringResource(R.string.common__sat_vbyte))
            LargeRow(
                prefix = null,
                text = input.ifEmpty { "0" },
                symbol = BITCOIN_SYMBOL,
                showSymbol = true,
            )

            if (isValid) {
                VerticalSpacer(28.dp)
                BodyM(totalFeeText, color = Colors.White64)
            }

            FillHeight()

            NumberPadSimple(
                onPress = onKeyPress,
                modifier = Modifier.height(350.dp)
            )
            PrimaryButton(
                onClick = onContinue,
                enabled = isValid,
                text = stringResource(R.string.common__continue),
                modifier = Modifier.testTag("continue_btn")
            )
            VerticalSpacer(16.dp)
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    AppThemeSurface {
        BottomSheetPreview {
            Content(
                input = "5",
                totalFeeText = "₿ 256 for average transaction ($0.25)",
                modifier = Modifier.sheetHeight(),
            )
        }
    }
}
