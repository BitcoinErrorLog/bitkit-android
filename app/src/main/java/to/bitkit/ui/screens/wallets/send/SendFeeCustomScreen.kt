package to.bitkit.ui.screens.wallets.send

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.bitkit.R
import to.bitkit.ui.components.BottomSheetPreview
import to.bitkit.ui.components.Display
import to.bitkit.ui.components.FillHeight
import to.bitkit.ui.components.PrimaryButton
import to.bitkit.ui.components.VerticalSpacer
import to.bitkit.ui.components.settings.SectionHeader
import to.bitkit.ui.scaffold.SheetTopBar
import to.bitkit.ui.shared.modifiers.sheetHeight
import to.bitkit.ui.shared.util.gradientBackground
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.viewmodels.SendUiState

@Composable
fun SendFeeCustomScreen(
    uiState: SendUiState,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    Content(
        uiState = uiState,
        onBack = onBack,
        onContinue = onContinue,
    )
}

@Composable
private fun Content(
    uiState: SendUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onContinue: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .gradientBackground()
            .navigationBarsPadding()
            .testTag("fee_screen")
    ) {
        SheetTopBar(stringResource(R.string.wallet__send_fee_custom), onBack = onBack)
        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            SectionHeader(stringResource(R.string.wallet__send_fee_and_speed))
            Display("TODO")

            FillHeight(min = 16.dp)

            PrimaryButton(
                text = stringResource(R.string.common__continue),
                onClick = onContinue,
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
                uiState = SendUiState(),
                modifier = Modifier.sheetHeight(),
            )
        }
    }
}

// TODO nav
