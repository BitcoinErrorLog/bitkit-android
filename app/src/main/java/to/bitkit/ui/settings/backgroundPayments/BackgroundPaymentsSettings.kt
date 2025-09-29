package to.bitkit.ui.settings.backgroundPayments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.bitkit.ui.components.BodyM
import to.bitkit.ui.components.settings.SettingsSwitchRow
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.CloseNavIcon
import to.bitkit.ui.shared.util.screen
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.viewmodels.SettingsViewModel

@Composable
fun BackgroundPaymentsSettings(
    onBack: () -> Unit,
    onClose: () -> Unit,
    settingsViewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val notificationsGranted by settingsViewModel.notificationsGranted.collectAsStateWithLifecycle()

    Content(
        onBack = onBack,
        onClose = onClose,
        onSystemSettingsClick = {
            // val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            //     data = Uri.fromParts("package", context.packageName, null)
            // }
            // context.startActivity(intent)
        },
        hasPermission = notificationsGranted
    )
}

@Composable
private fun Content(
    onBack: () -> Unit,
    onClose: () -> Unit,
    onSystemSettingsClick: () -> Unit,
    hasPermission: Boolean,
) {
    Column(
        modifier = Modifier.screen()
    ) {

        AppTopBar(
            titleText = "Background Payments",
            onBackClick = onBack,
            actions = { CloseNavIcon(onClick = onClose) },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            SettingsSwitchRow(
                title = "Get paid when Bitkit is closed",
                isChecked = hasPermission,
                onClick = onSystemSettingsClick
            )

            BodyM(text = "Background payments are enabled. You can receive funds even when the app is closed (if your device is connected to the internet).")
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun Preview1() {
    AppThemeSurface {
        Content(
            onBack = {},
            onClose = {},
            onSystemSettingsClick = {},
            hasPermission = true
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun Preview2() {
    AppThemeSurface {
        Content(
            onBack = {},
            onClose = {},
            onSystemSettingsClick = {},
            hasPermission = false
        )
    }
}

