package to.bitkit.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import to.bitkit.models.NewTransactionSheetDetails
import to.bitkit.ui.screens.scanner.QrScanningScreen
import to.bitkit.ui.screens.wallets.send.AddTagScreen
import to.bitkit.ui.screens.wallets.send.PIN_CHECK_RESULT_KEY
import to.bitkit.ui.screens.wallets.send.SendAddressScreen
import to.bitkit.ui.screens.wallets.send.SendAmountScreen
import to.bitkit.ui.screens.wallets.send.SendCoinSelectionScreen
import to.bitkit.ui.screens.wallets.send.SendConfirmScreen
import to.bitkit.ui.screens.wallets.send.SendErrorScreen
import to.bitkit.ui.screens.wallets.send.SendFeeCustomScreen
import to.bitkit.ui.screens.wallets.send.SendFeeRateScreen
import to.bitkit.ui.screens.wallets.send.SendPinCheckScreen
import to.bitkit.ui.screens.wallets.send.SendQuickPayScreen
import to.bitkit.ui.screens.wallets.send.SendRecipientScreen
import to.bitkit.ui.screens.wallets.withdraw.WithDrawErrorScreen
import to.bitkit.ui.screens.wallets.withdraw.WithdrawConfirmScreen
import to.bitkit.ui.settings.support.SupportScreen
import to.bitkit.ui.shared.modifiers.sheetHeight
import to.bitkit.ui.utils.composableWithDefaultTransitions
import to.bitkit.viewmodels.AppViewModel
import to.bitkit.viewmodels.SendEffect
import to.bitkit.viewmodels.SendEvent
import to.bitkit.viewmodels.WalletViewModel

@Composable
fun SendSheet(
    appViewModel: AppViewModel,
    walletViewModel: WalletViewModel,
    startDestination: SendRoute = SendRoute.Recipient,
    onComplete: (NewTransactionSheetDetails?) -> Unit,
) {
    // Reset on new user-initiated send
    LaunchedEffect(startDestination) {
        if (startDestination == SendRoute.Recipient) {
            appViewModel.resetSendState()
            appViewModel.resetQuickPayData()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sheetHeight()
            .imePadding()
    ) {
        val navController = rememberNavController()
        LaunchedEffect(appViewModel, navController) {
            appViewModel.sendEffect.collect {
                when (it) {
                    is SendEffect.NavigateToAmount -> navController.navigate(SendRoute.Amount)
                    is SendEffect.NavigateToAddress -> navController.navigate(SendRoute.Address)
                    is SendEffect.NavigateToScan -> navController.navigate(SendRoute.QrScanner)
                    is SendEffect.NavigateToCoinSelection -> navController.navigate(SendRoute.CoinSelection)
                    is SendEffect.NavigateToReview -> navController.navigate(SendRoute.Confirm)
                    is SendEffect.PopBack -> navController.popBackStack()
                    is SendEffect.PaymentSuccess -> onComplete(it.sheet)
                    is SendEffect.NavigateToQuickPay -> navController.navigate(SendRoute.QuickPay)
                    is SendEffect.NavigateToWithdrawConfirm -> navController.navigate(SendRoute.WithdrawConfirm)
                    is SendEffect.NavigateToWithdrawError -> navController.navigate(SendRoute.WithdrawError)
                    is SendEffect.NavigateToFee -> navController.navigate(SendRoute.FeeRate)
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            composableWithDefaultTransitions<SendRoute.Recipient> {
                SendRecipientScreen(
                    onEvent = { appViewModel.setSendEvent(it) }
                )
            }
            composableWithDefaultTransitions<SendRoute.Address> {
                val uiState by appViewModel.sendUiState.collectAsStateWithLifecycle()
                SendAddressScreen(
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onEvent = { appViewModel.setSendEvent(it) },
                )
            }
            composableWithDefaultTransitions<SendRoute.Amount> {
                val uiState by appViewModel.sendUiState.collectAsStateWithLifecycle()
                val walletUiState by walletViewModel.uiState.collectAsStateWithLifecycle()
                SendAmountScreen(
                    uiState = uiState,
                    walletUiState = walletUiState,
                    onBack = { navController.popBackStack() },
                    onEvent = { appViewModel.setSendEvent(it) }
                )
            }
            composableWithDefaultTransitions<SendRoute.QrScanner> {
                QrScanningScreen(
                    navController = navController,
                    inSheet = true,
                ) { qrCode ->
                    navController.popBackStack()
                    appViewModel.onScanResult(data = qrCode)
                }
            }
            composableWithDefaultTransitions<SendRoute.CoinSelection> {
                val sendUiState by appViewModel.sendUiState.collectAsStateWithLifecycle()
                SendCoinSelectionScreen(
                    requiredAmount = sendUiState.amount,
                    address = sendUiState.address,
                    onBack = { navController.popBackStack() },
                    onContinue = { utxos -> appViewModel.setSendEvent(SendEvent.CoinSelectionContinue(utxos)) },
                )
            }
            composableWithDefaultTransitions<SendRoute.FeeRate> {
                val sendUiState by appViewModel.sendUiState.collectAsStateWithLifecycle()
                SendFeeRateScreen(
                    sendUiState = sendUiState,
                    onBack = { navController.popBackStack() },
                    onContinue = { navController.popBackStack() },
                    onSelect = { appViewModel.setSendEvent(SendEvent.SpeedChange(it)) },
                )
            }
            composableWithDefaultTransitions<SendRoute.FeeCustom> {
                val sendUiState by appViewModel.sendUiState.collectAsStateWithLifecycle()
                SendFeeCustomScreen(
                    uiState = sendUiState,
                    onBack = { navController.popBackStack() },
                    onContinue = {}, // TODO
                )
            }
            composableWithDefaultTransitions<SendRoute.Confirm> {
                val uiState by appViewModel.sendUiState.collectAsStateWithLifecycle()
                SendConfirmScreen(
                    savedStateHandle = it.savedStateHandle,
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onEvent = { e -> appViewModel.setSendEvent(e) },
                    onClickAddTag = { navController.navigate(SendRoute.AddTag) },
                    onClickTag = { tag -> appViewModel.removeTag(tag) },
                    onNavigateToPin = { navController.navigate(SendRoute.PinCheck) },
                )
            }
            composableWithDefaultTransitions<SendRoute.WithdrawConfirm> {
                val uiState by appViewModel.sendUiState.collectAsStateWithLifecycle()
                WithdrawConfirmScreen(
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onConfirm = { appViewModel.onConfirmWithdraw() },
                )
            }
            composableWithDefaultTransitions<SendRoute.WithdrawError> {
                val uiState by appViewModel.sendUiState.collectAsStateWithLifecycle()
                WithDrawErrorScreen(
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onClickScan = { navController.navigate(SendRoute.QrScanner) },
                    onClickSupport = { navController.navigate(SendRoute.Support) },
                )
            }
            // TODO navigate to main support screen, not inside SEND sheet
            composableWithDefaultTransitions<SendRoute.Support> {
                SupportScreen(navController)
            }
            composableWithDefaultTransitions<SendRoute.AddTag> {
                AddTagScreen(
                    onBack = { navController.popBackStack() },
                    onTagSelected = { tag ->
                        appViewModel.addTagToSelected(tag)
                        navController.popBackStack()
                    },
                )
            }
            composableWithDefaultTransitions<SendRoute.PinCheck> {
                SendPinCheckScreen(
                    onBack = { navController.popBackStack() },
                    onSuccess = {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(PIN_CHECK_RESULT_KEY, true)
                        navController.popBackStack()
                        appViewModel.setSendEvent(SendEvent.PayConfirmed)
                    },
                )
            }
            composableWithDefaultTransitions<SendRoute.QuickPay> {
                val quickPayData by appViewModel.quickPayData.collectAsStateWithLifecycle()
                SendQuickPayScreen(
                    quickPayData = requireNotNull(quickPayData),
                    onPaymentComplete = {
                        onComplete(null)
                    },
                    onShowError = { errorMessage ->
                        navController.navigate(SendRoute.Error(errorMessage))
                    }
                )
            }
            composableWithDefaultTransitions<SendRoute.Error> {
                val route = it.toRoute<SendRoute.Error>()
                SendErrorScreen(
                    errorMessage = route.errorMessage,
                    onRetry = {
                        if (startDestination == SendRoute.Recipient) {
                            navController.navigate(SendRoute.Recipient) {
                                popUpTo<SendRoute.Recipient> { inclusive = true }
                            }
                        } else {
                            onComplete(null)
                        }
                    },
                    onClose = {
                        onComplete(null)
                    }
                )
            }
        }
    }
}

sealed interface SendRoute {
    @Serializable
    data object Recipient : SendRoute

    @Serializable
    data object Address : SendRoute

    @Serializable
    data object Amount : SendRoute

    @Serializable
    data object QrScanner : SendRoute

    @Serializable
    data object WithdrawConfirm : SendRoute

    @Serializable
    data object WithdrawError : SendRoute

    @Serializable
    data object Support : SendRoute

    @Serializable
    data object AddTag : SendRoute

    @Serializable
    data object PinCheck : SendRoute

    @Serializable
    data object CoinSelection : SendRoute

    @Serializable
    data object QuickPay : SendRoute

    @Serializable
    data object FeeRate : SendRoute

    @Serializable
    data object FeeCustom : SendRoute

    @Serializable
    data object Confirm : SendRoute

    @Serializable
    data class Error(val errorMessage: String) : SendRoute
}
