package to.bitkit.ui.screens.recoveryMode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import to.bitkit.R
import to.bitkit.ui.components.BodyM
import to.bitkit.ui.components.SecondaryButton
import to.bitkit.ui.components.VerticalSpacer
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.shared.util.screen
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.viewmodels.WalletViewModel

@Composable
fun RecoveryModeScreen(
    walletViewModel: WalletViewModel,
    recoveryViewModel: RecoveryViewModel = hiltViewModel(),
    onNavigateToSeed: () -> Unit = {},
) {
    val uiState by recoveryViewModel.uiState.collectAsState()

    // Handle wipe confirmation result
    LaunchedEffect(uiState.wipeConfirmed) {
        if (uiState.wipeConfirmed) {
            walletViewModel.wipeWallet()
        }
    }

    Content(
        uiState = uiState,
        walletExists = walletViewModel.walletExists,
        onExportLogs = recoveryViewModel::onExportLogs,
        onShowSeed = {
            onNavigateToSeed()
        },
        onContactSupport = recoveryViewModel::onContactSupport,
        onWipeApp = {
            recoveryViewModel.showWipeConfirmation()
        },
        onWipeConfirmed = recoveryViewModel::onWipeConfirmed,
        onWipeCancelled = recoveryViewModel::hideWipeConfirmation,
    )
}

@Composable
private fun Content(
    uiState: RecoveryUiState,
    walletExists: Boolean,
    onExportLogs: () -> Unit,
    onShowSeed: () -> Unit,
    onContactSupport: () -> Unit,
    onWipeApp: () -> Unit,
    onWipeConfirmed: () -> Unit,
    onWipeCancelled: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .screen()
            .padding(horizontal = 16.dp)
    ) {
        AppTopBar(
            titleText = stringResource(R.string.security__recovery),
            onBackClick = null,
        )

        VerticalSpacer(16.dp)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            BodyM(text = stringResource(R.string.security__recovery_text))

            VerticalSpacer(32.dp)

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SecondaryButton(
                    text = stringResource(R.string.lightning__export_logs),
                    onClick = onExportLogs,
                    isLoading = uiState.isExportingLogs,
                )

                SecondaryButton(
                    text = stringResource(R.string.security__display_seed),
                    onClick = onShowSeed,
                    enabled = walletExists,
                )

                SecondaryButton(
                    text = stringResource(R.string.security__contact_support),
                    onClick = onContactSupport,
                )

                SecondaryButton(
                    text = stringResource(R.string.security__wipe_app),
                    onClick = onWipeApp,
                )
            }
        }
    }

    // Wipe confirmation dialog
    if (uiState.showWipeConfirmation) {
        AlertDialog(
            onDismissRequest = onWipeCancelled,
            title = {
                Text(text = stringResource(R.string.security__reset_dialog_title))
            },
            text = {
                Text(text = stringResource(R.string.security__reset_dialog_desc))
            },
            confirmButton = {
                TextButton(onClick = onWipeConfirmed) {
                    Text(text = stringResource(R.string.security__reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onWipeCancelled) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    AppThemeSurface {
        Content(
            uiState = RecoveryUiState(),
            walletExists = true,
            onExportLogs = {},
            onShowSeed = {},
            onContactSupport = {},
            onWipeApp = {},
            onWipeConfirmed = {},
            onWipeCancelled = {},
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun LockedPreview() {
    AppThemeSurface {
        Content(
            uiState = RecoveryUiState(),
            walletExists = true,
            onExportLogs = {},
            onShowSeed = {},
            onContactSupport = {},
            onWipeApp = {},
            onWipeConfirmed = {},
            onWipeCancelled = {},
        )
    }
}
