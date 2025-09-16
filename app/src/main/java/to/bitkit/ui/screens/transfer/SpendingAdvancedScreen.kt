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
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices.NEXUS_5
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.bitkit.R
import to.bitkit.ext.mockOrder
import to.bitkit.ui.LocalCurrencies
import to.bitkit.ui.appViewModel
import to.bitkit.ui.components.AmountInput
import to.bitkit.ui.components.Caption13Up
import to.bitkit.ui.components.Display
import to.bitkit.ui.components.MoneySSB
import to.bitkit.ui.components.NumberPadActionButton
import to.bitkit.ui.components.PrimaryButton
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.CloseNavIcon
import to.bitkit.ui.scaffold.ScreenColumn
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors
import to.bitkit.ui.utils.withAccent
import to.bitkit.viewmodels.TransferEffect
import to.bitkit.viewmodels.TransferToSpendingUiState
import to.bitkit.viewmodels.TransferValues
import to.bitkit.viewmodels.TransferViewModel

@Composable
fun SpendingAdvancedScreen(
    viewModel: TransferViewModel,
    onBackClick: () -> Unit = {},
    onCloseClick: () -> Unit = {},
    onOrderCreated: () -> Unit = {},
) {
    val app = appViewModel ?: return
    val state by viewModel.spendingUiState.collectAsStateWithLifecycle()
    val order = state.order ?: return
    val transferValues by viewModel.transferValues.collectAsState()

    LaunchedEffect(order.clientBalanceSat) {
        viewModel.updateTransferValues(order.clientBalanceSat)
    }

    LaunchedEffect(Unit) {
        viewModel.transferEffects.collect { effect ->
            when (effect) {
                TransferEffect.OnOrderCreated -> onOrderCreated()
                is TransferEffect.ToastException -> app.toast(effect.e)
                is TransferEffect.ToastError -> app.toast(
                    type = to.bitkit.models.Toast.ToastType.ERROR,
                    title = effect.title,
                    description = effect.description,
                )
            }
        }
    }

    val isValid = transferValues.let {
        val isAboveMin = state.receivingAmount.toULong() >= it.minLspBalance
        val isBelowMax = state.receivingAmount.toULong() <= it.maxLspBalance
        state.receivingAmount > 0 && isAboveMin && isBelowMax
    }

    Content(
        uiState = state,
        transferValues = transferValues,
        isValid = isValid,
        onBack = onBackClick,
        onClose = onCloseClick,
        onAmountChange = viewModel::onReceivingAmountChange,
        onContinue = viewModel::onSpendingAdvancedContinue,
    )
}

@Composable
private fun Content(
    uiState: TransferToSpendingUiState,
    transferValues: TransferValues,
    isValid: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onAmountChange: (Long) -> Unit,
    onContinue: () -> Unit,
) {
    val currencies = LocalCurrencies.current
    uiState.order ?: return

    ScreenColumn {
        AppTopBar(
            titleText = stringResource(R.string.lightning__transfer__nav_title),
            onBackClick = onBack,
            actions = { CloseNavIcon(onClose) },
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .imePadding()
                .testTag("SpendingAdvanced")
        ) {
            var overrideSats: Long? by remember { mutableStateOf(null) }
            var isLoading by remember { mutableStateOf(false) }

            Spacer(modifier = Modifier.height(32.dp))
            Display(
                text = stringResource(R.string.lightning__spending_advanced__title)
                    .withAccent(accentColor = Colors.Purple)
            )
            Spacer(modifier = Modifier.height(32.dp))

            AmountInput(
                defaultValue = uiState.receivingAmount,
                primaryDisplay = currencies.primaryDisplay,
                overrideSats = overrideSats,
                onSatsChange = { sats ->
                    onAmountChange(sats)
                    overrideSats = null
                },
                modifier = Modifier.testTag("SpendingAdvancedNumberField")
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.requiredHeight(20.dp),
            ) {
                Caption13Up(
                    text = stringResource(R.string.lightning__spending_advanced__fee),
                    color = Colors.White64,
                )
                Spacer(modifier = Modifier.width(4.dp))
                uiState.feeEstimate?.let {
                    MoneySSB(it)
                } ?: run {
                    Caption13Up(text = "—", color = Colors.White64)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Actions Row
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // Min Button
                NumberPadActionButton(
                    text = stringResource(R.string.common__min),
                    color = Colors.Purple,
                    onClick = {
                        overrideSats = transferValues.minLspBalance.toLong()
                    },
                    modifier = Modifier.testTag("SpendingAdvancedMin")
                )
                // Default Button
                NumberPadActionButton(
                    text = stringResource(R.string.common__default),
                    color = Colors.Purple,
                    onClick = {
                        overrideSats = transferValues.defaultLspBalance.toLong()
                    },
                    modifier = Modifier.testTag("SpendingAdvancedDefault")
                )
                // Max Button
                NumberPadActionButton(
                    text = stringResource(R.string.common__max),
                    color = Colors.Purple,
                    onClick = {
                        overrideSats = transferValues.maxLspBalance.toLong()
                    },
                    modifier = Modifier.testTag("SpendingAdvancedMax")
                )
            }
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            PrimaryButton(
                text = stringResource(R.string.common__continue),
                onClick = {
                    isLoading = true
                    onContinue()
                },
                enabled = !isLoading && isValid,
                isLoading = isLoading,
                modifier = Modifier.testTag("SpendingAdvancedContinue")
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    AppThemeSurface {
        Content(
            uiState = TransferToSpendingUiState(
                order = mockOrder().copy(clientBalanceSat = 100_000u),
                receivingAmount = 55_000L,
                feeEstimate = 2_500L,
            ),
            transferValues = TransferValues(
                defaultLspBalance = 50_000u,
                minLspBalance = 10_000u,
                maxLspBalance = 90_000u,
            ),
            isValid = true,
            onBack = {},
            onClose = {},
            onAmountChange = {},
            onContinue = {},
        )
    }
}

@Preview(showSystemUi = true, device = NEXUS_5)
@Composable
private fun PreviewSmall() {
    AppThemeSurface {
        Content(
            uiState = TransferToSpendingUiState(
                order = mockOrder().copy(clientBalanceSat = 50_000u),
                receivingAmount = 120_521L,
                feeEstimate = 12_461L,
            ),
            transferValues = TransferValues(
                defaultLspBalance = 50_000u,
                minLspBalance = 10_000u,
                maxLspBalance = 90_000u,
            ),
            isValid = true,
            onBack = {},
            onClose = {},
            onAmountChange = {},
            onContinue = {},
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun PreviewLoading() {
    AppThemeSurface {
        Content(
            uiState = TransferToSpendingUiState(
                order = mockOrder().copy(clientBalanceSat = 50_000u),
                receivingAmount = 20_000L,
                feeEstimate = null,
                isLoading = true,
            ),
            transferValues = TransferValues(
                defaultLspBalance = 25_000u,
                minLspBalance = 10_000u,
                maxLspBalance = 40_000u,
            ),
            isValid = true,
            onBack = {},
            onClose = {},
            onAmountChange = {},
            onContinue = {},
        )
    }
}
