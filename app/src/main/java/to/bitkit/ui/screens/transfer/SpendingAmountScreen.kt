package to.bitkit.ui.screens.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices.NEXUS_5
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import okhttp3.internal.toLongOrDefault
import to.bitkit.R
import to.bitkit.models.PrimaryDisplay
import to.bitkit.ui.LocalCurrencies
import to.bitkit.ui.components.AmountInputHandler
import to.bitkit.ui.components.Display
import to.bitkit.ui.components.FillHeight
import to.bitkit.ui.components.FillWidth
import to.bitkit.ui.components.Keyboard
import to.bitkit.ui.components.MoneySSB
import to.bitkit.ui.components.NumberPadActionButton
import to.bitkit.ui.components.NumberPadTextField
import to.bitkit.ui.components.PrimaryButton
import to.bitkit.ui.components.Text13Up
import to.bitkit.ui.components.UnitButton
import to.bitkit.ui.components.VerticalSpacer
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.CloseNavIcon
import to.bitkit.ui.scaffold.ScreenColumn
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors
import to.bitkit.ui.utils.withAccent
import to.bitkit.viewmodels.CurrencyUiState
import to.bitkit.viewmodels.TransferEffect
import to.bitkit.viewmodels.TransferToSpendingUiState
import to.bitkit.viewmodels.TransferViewModel

@Composable
fun SpendingAmountScreen(
    viewModel: TransferViewModel,
    onBackClick: () -> Unit = {},
    onCloseClick: () -> Unit = {},
    onOrderCreated: () -> Unit = {},
    toastException: (Throwable) -> Unit,
    toast: (title: String, description: String) -> Unit,
) {
    val currencies = LocalCurrencies.current
    val uiState by viewModel.spendingUiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.updateLimits(retry = true)
    }

    LaunchedEffect(Unit) {
        viewModel.transferEffects.collect { effect ->
            when (effect) {
                TransferEffect.OnOrderCreated -> onOrderCreated()
                is TransferEffect.ToastError -> toast(effect.title, effect.description)
                is TransferEffect.ToastException -> toastException(effect.e)
            }
        }
    }

    AmountInputHandler(
        input = uiState.input,
        overrideSats = uiState.overrideSats,
        primaryDisplay = currencies.primaryDisplay,
        displayUnit = currencies.displayUnit,
        onInputChanged = viewModel::onInputChanged,
        onAmountCalculated = { sats ->
            viewModel.handleCalculatedAmount(sats.toLongOrDefault(0))
        },
    )

    Content(
        uiState = uiState,
        currencies = currencies,
        onBackClick = onBackClick,
        onCloseClick = onCloseClick,
        onClickQuarter = viewModel::onClickQuarter,
        onClickMaxAmount = viewModel::onClickMaxAmount,
        onConfirmAmount = viewModel::onConfirmAmount,
        onInputChange = viewModel::onInputChanged,
    )
}

@Composable
private fun Content(
    uiState: TransferToSpendingUiState,
    currencies: CurrencyUiState,
    onBackClick: () -> Unit,
    onCloseClick: () -> Unit,
    onClickQuarter: () -> Unit,
    onClickMaxAmount: () -> Unit,
    onConfirmAmount: () -> Unit,
    onInputChange: (String) -> Unit,
) {
    ScreenColumn {
        AppTopBar(
            titleText = stringResource(R.string.lightning__transfer__nav_title),
            onBackClick = onBackClick,
            actions = { CloseNavIcon(onCloseClick) },
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .imePadding()
                .testTag("SpendingAmount")
        ) {
            VerticalSpacer(16.dp)
            Display(
                text = stringResource(R.string.lightning__spending_amount__title)
                    .withAccent(accentColor = Colors.Purple)
            )

            NumberPadTextField(
                input = uiState.input,
                displayUnit = currencies.displayUnit,
                showSecondaryField = false,
                primaryDisplay = currencies.primaryDisplay,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("SpendingAmountNumberField")
            )

            FillHeight()

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .testTag("SendAmountNumberPad")
            ) {
                Column {
                    Text13Up(
                        text = stringResource(R.string.wallet__send_available),
                        color = Colors.White64,
                        modifier = Modifier.testTag("SpendingAmountAvailable")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MoneySSB(sats = uiState.balanceAfterFee, modifier = Modifier.testTag("SpendingAmountUnit"))
                }
                FillWidth()
                UnitButton(color = Colors.Purple)
                // 25% Button
                NumberPadActionButton(
                    text = stringResource(R.string.lightning__spending_amount__quarter),
                    color = Colors.Purple,
                    onClick = onClickQuarter,
                    modifier = Modifier.testTag("SpendingAmountQuarter")
                )
                // Max Button
                NumberPadActionButton(
                    text = stringResource(R.string.common__max),
                    color = Colors.Purple,
                    onClick = onClickMaxAmount,
                    modifier = Modifier.testTag("SpendingAmountMax")
                )
            }
            HorizontalDivider()

            VerticalSpacer(16.dp)

            Keyboard(
                onClick = { number ->
                    onInputChange(if (uiState.input == "0") number else uiState.input + number)
                },
                onClickBackspace = {
                    onInputChange(if (uiState.input.length > 1) uiState.input.dropLast(1) else "0")
                },
                isDecimal = currencies.primaryDisplay == PrimaryDisplay.FIAT,
                modifier = Modifier
                    .fillMaxWidth()
            )

            VerticalSpacer(8.dp)

            PrimaryButton(
                text = stringResource(R.string.common__continue),
                onClick = onConfirmAmount,
                enabled = uiState.satsAmount != 0L && uiState.satsAmount <= uiState.maxAllowedToSend,
                isLoading = uiState.isLoading,
                modifier = Modifier.testTag("SpendingAmountContinue")
            )

            VerticalSpacer(16.dp)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
    AppThemeSurface {
        Content(
            uiState = TransferToSpendingUiState(input = "5 000"),
            currencies = CurrencyUiState(),
            onBackClick = {},
            onCloseClick = {},
            onClickQuarter = {},
            onClickMaxAmount = {},
            onConfirmAmount = {},
            onInputChange = {},
        )
    }
}

@Preview(showBackground = true, device = NEXUS_5)
@Composable
private fun Preview2() {
    AppThemeSurface {
        Content(
            uiState = TransferToSpendingUiState(input = "5 000"),
            currencies = CurrencyUiState(),
            onBackClick = {},
            onCloseClick = {},
            onClickQuarter = {},
            onClickMaxAmount = {},
            onConfirmAmount = {},
            onInputChange = {},
        )
    }
}
