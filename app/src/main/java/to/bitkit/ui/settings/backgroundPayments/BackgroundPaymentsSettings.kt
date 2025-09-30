package to.bitkit.ui.settings.backgroundPayments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.bitkit.R
import to.bitkit.ui.components.BodyM
import to.bitkit.ui.components.BodyMB
import to.bitkit.ui.components.NotificationPreview
import to.bitkit.ui.components.SecondaryButton
import to.bitkit.ui.components.Text13Up
import to.bitkit.ui.components.VerticalSpacer
import to.bitkit.ui.components.settings.SettingsButtonRow
import to.bitkit.ui.components.settings.SettingsButtonValue
import to.bitkit.ui.components.settings.SettingsSwitchRow
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.CloseNavIcon
import to.bitkit.ui.shared.util.screen
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors
import to.bitkit.viewmodels.SettingsViewModel

@Composable
fun BackgroundPaymentsSettings(
    onBack: () -> Unit,
    onClose: () -> Unit,
    settingsViewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val notificationsGranted by settingsViewModel.notificationsGranted.collectAsStateWithLifecycle()
    val showNotificationDetails by settingsViewModel.showNotificationDetails.collectAsStateWithLifecycle()

    Content(
        onBack = onBack,
        onClose = onClose,
        onSystemSettingsClick = {
            // val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            //     data = Uri.fromParts("package", context.packageName, null)
            // }
            // context.startActivity(intent)
        },
        hasPermission = notificationsGranted,
        showDetails = showNotificationDetails,
        toggleNotificationDetails = settingsViewModel::toggleNotificationDetails,
    )
}

@Composable
private fun Content(
    onBack: () -> Unit,
    onClose: () -> Unit,
    onSystemSettingsClick: () -> Unit,
    toggleNotificationDetails: () -> Unit,
    hasPermission: Boolean,
    showDetails: Boolean,
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
            VerticalSpacer(16.dp)

            SettingsSwitchRow(
                title = "Get paid when Bitkit is closed",
                isChecked = hasPermission,
                onClick = onSystemSettingsClick
            )

            AnimatedVisibility(
                visible = hasPermission,
                modifier = Modifier.padding(vertical = 16.dp),
            ) {
                BodyM(
                    text = "Background payments are enabled. You can receive funds even when the app is closed (if your device is connected to the internet).",
                    color = Colors.White64,
                )
            }

            AnimatedVisibility(
                visible = !hasPermission,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                BodyMB(
                    text = "Background payments are disabled, because you have denied notifications.",
                    color = Colors.Red,
                )
            }

            NotificationPreview(
                enabled = hasPermission,
                title = "Payment Received",
                description = "₿ 21 000",
                showDetails = showDetails,
                modifier = Modifier.fillMaxWidth()
            )

            VerticalSpacer(32.dp)

            Text13Up(
                text = "Privacy",
                color = Colors.White64
            )

            SettingsButtonRow(
                "Include amount in notifications",
                value = SettingsButtonValue.BooleanValue(showDetails),
                onClick = toggleNotificationDetails,
            )

            VerticalSpacer(32.dp)

            Text13Up(
                text = "Privacy",
                color = Colors.White64
            )

            VerticalSpacer(16.dp)

            SecondaryButton(
                "Customize in Android Bitkit Settings",
                icon = {
                    Image(painter = painterResource(R.drawable.ic_bell), contentDescription = null)
                },
                onClick = onSystemSettingsClick
            )
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
            toggleNotificationDetails = {},
            hasPermission = true,
            showDetails = true,
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
            toggleNotificationDetails = {},
            hasPermission = false,
            showDetails = false,
        )
    }
}

