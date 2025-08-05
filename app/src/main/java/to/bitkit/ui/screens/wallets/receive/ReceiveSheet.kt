package to.bitkit.ui.screens.wallets.receive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import to.bitkit.repositories.LightningState
import to.bitkit.ui.appViewModel
import to.bitkit.ui.blocktankViewModel
import to.bitkit.ui.screens.wallets.send.AddTagScreen
import to.bitkit.ui.shared.modifiers.sheetHeight
import to.bitkit.ui.walletViewModel
import to.bitkit.viewmodels.MainUiState
import to.bitkit.viewmodels.WalletViewModelEffects

@Composable
fun ReceiveSheet(
    navigateToExternalConnection: () -> Unit,
    walletState: MainUiState,
) {
    val app = appViewModel ?: return
    val wallet = walletViewModel ?: return
    val blocktank = blocktankViewModel ?: return

    val navController = rememberNavController()

    val cjitInvoice = remember { mutableStateOf<String?>(null) }
    val showCreateCjit = remember { mutableStateOf(false) }
    val cjitEntryDetails = remember { mutableStateOf<CjitEntryDetails?>(null) }
    val lightningState: LightningState by wallet.lightningState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        try {
            coroutineScope {
                launch { wallet.refreshBip21() }
                launch { blocktank.refreshInfo() }
            }
        } catch (e: Exception) {
            app.toast(e)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sheetHeight()
            .imePadding()
    ) {
        NavHost(
            navController = navController,
            startDestination = ReceiveRoutes.QR,
        ) {
            composable(ReceiveRoutes.QR) {
                LaunchedEffect(cjitInvoice.value) {
                    showCreateCjit.value = !cjitInvoice.value.isNullOrBlank()
                }

                LaunchedEffect(Unit) {
                    wallet.walletEffect.collect { effect ->
                        when (effect) {
                            WalletViewModelEffects.NavigateGeoBlockScreen -> {
                                navController.navigate(ReceiveRoutes.LOCATION_BLOCK)
                            }
                        }
                    }
                }

                ReceiveQrScreen(
                    cjitInvoice = cjitInvoice,
                    cjitActive = showCreateCjit,
                    walletState = walletState,
                    onCjitToggle = { active ->
                        when {
                            active && lightningState.shouldBlockLightning -> navController.navigate(ReceiveRoutes.LOCATION_BLOCK)

                            !active -> {
                                showCreateCjit.value = false
                                cjitInvoice.value = null
                            }

                            active && cjitInvoice.value == null -> {
                                showCreateCjit.value = true
                                navController.navigate(ReceiveRoutes.AMOUNT)
                            }
                        }
                    },
                    onClickEditInvoice = { navController.navigate(ReceiveRoutes.EDIT_INVOICE) },
                    onClickReceiveOnSpending = { wallet.toggleReceiveOnSpending() }
                )
            }
            composable(ReceiveRoutes.AMOUNT) {
                ReceiveAmountScreen(
                    onCjitCreated = { entry ->
                        cjitEntryDetails.value = entry
                        navController.navigate(ReceiveRoutes.CONFIRM)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ReceiveRoutes.LOCATION_BLOCK) {
                LocationBlockScreen(
                    onBackPressed = { navController.popBackStack() },
                    navigateAdvancedSetup = navigateToExternalConnection
                )
            }
            composable(ReceiveRoutes.CONFIRM) {
                cjitEntryDetails.value?.let { entryDetails ->
                    ReceiveConfirmScreen(
                        entry = entryDetails,
                        onLearnMore = { navController.navigate(ReceiveRoutes.LIQUIDITY) },
                        onContinue = { invoice ->
                            cjitInvoice.value = invoice
                            navController.navigate(ReceiveRoutes.QR) { popUpTo(ReceiveRoutes.QR) { inclusive = true } }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(ReceiveRoutes.CONFIRM_INCREASE_INBOUND) {
                cjitEntryDetails.value?.let { entryDetails ->
                    ReceiveConfirmScreen(
                        entry = entryDetails,
                        onLearnMore = { navController.navigate(ReceiveRoutes.LIQUIDITY_ADDITIONAL) },
                        onContinue = { invoice ->
                            cjitInvoice.value = invoice
                            navController.navigate(ReceiveRoutes.QR) { popUpTo(ReceiveRoutes.QR) { inclusive = true } }
                        },
                        isAdditional = true,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(ReceiveRoutes.LIQUIDITY) {
                cjitEntryDetails.value?.let { entryDetails ->
                    ReceiveLiquidityScreen(
                        entry = entryDetails,
                        onContinue = { navController.popBackStack() },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(ReceiveRoutes.LIQUIDITY_ADDITIONAL) {
                cjitEntryDetails.value?.let { entryDetails ->
                    ReceiveLiquidityScreen(
                        entry = entryDetails,
                        onContinue = { navController.popBackStack() },
                        isAdditional = true,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(ReceiveRoutes.EDIT_INVOICE) {
                val walletUiState by wallet.walletState.collectAsStateWithLifecycle()
                EditInvoiceScreen(
                    walletUiState = walletUiState,
                    onBack = { navController.popBackStack() },
                    updateInvoice = { sats ->
                        wallet.updateBip21Invoice(amountSats = sats)
                    },
                    onClickAddTag = {
                        navController.navigate(ReceiveRoutes.ADD_TAG)
                    },
                    onClickTag = { tagToRemove ->
                        wallet.removeTag(tagToRemove)
                    },
                    onDescriptionUpdate = { newText ->
                        wallet.updateBip21Description(newText = newText)
                    },
                    onInputUpdated = { newText ->
                        wallet.updateBalanceInput(newText)
                    },
                    navigateReceiveConfirm = { entry ->
                        cjitEntryDetails.value = entry
                        navController.navigate(ReceiveRoutes.CONFIRM_INCREASE_INBOUND)
                    }
                )
            }
            composable(ReceiveRoutes.ADD_TAG) {
                AddTagScreen(
                    onBack = {
                        navController.popBackStack()
                    },
                    onTagSelected = { tag ->
                        wallet.addTagToSelected(tag)
                        navController.popBackStack()
                    }
                )

            }
        }
    }
}

private object ReceiveRoutes {
    const val QR = "qr"
    const val AMOUNT = "amount"
    const val CONFIRM = "confirm"
    const val CONFIRM_INCREASE_INBOUND = "confirm_increase_inbound"
    const val LIQUIDITY = "liquidity"
    const val LIQUIDITY_ADDITIONAL = "liquidity_additional"
    const val EDIT_INVOICE = "edit_invoice"
    const val ADD_TAG = "add_tag"
    const val LOCATION_BLOCK = "location_block"
}
