package to.bitkit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.bitkit.R
import to.bitkit.ui.components.BodyM
import to.bitkit.ui.components.PrimaryButton
import to.bitkit.ui.components.VerticalSpacer
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.shared.util.screen
import to.bitkit.ui.theme.AppThemeSurface

@Composable
fun RecoveryModeScreen() {
    Column(
        modifier = Modifier
            .screen()
            .padding(horizontal = 16.dp)
    ) {
        AppTopBar(
            titleText = stringResource(R.string.security__recovery),
            onBackClick = null,
        )

        VerticalSpacer(16.dp)

        BodyM(text = stringResource(R.string.security__recovery_text))

        VerticalSpacer(32.dp)

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PrimaryButton(
                text = stringResource(R.string.lightning__export_logs),
                onClick = {},
            )

            PrimaryButton(
                text = stringResource(R.string.security__display_seed),
                onClick = {},
            )

            PrimaryButton(
                text = stringResource(R.string.security__contact_support),
                onClick = {},
            )

            PrimaryButton(
                text = stringResource(R.string.security__wipe_app),
                onClick = {},
            )
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    AppThemeSurface {
        RecoveryModeScreen()
    }
}
