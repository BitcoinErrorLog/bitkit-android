package to.bitkit.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import to.bitkit.R
import to.bitkit.ui.components.BottomSheet
import to.bitkit.ui.components.BottomSheetPreview
import to.bitkit.ui.scaffold.SheetTopBar
import to.bitkit.ui.settings.quickPay.QuickPayIntroContent
import to.bitkit.ui.shared.modifiers.sheetHeight
import to.bitkit.ui.shared.util.gradientBackground
import to.bitkit.ui.theme.AppThemeSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPayIntroSheet(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheet(onDismissRequest = onDismiss) {
        QuickPayIntroSheetContent(onContinue)
    }
}

@Composable
private fun QuickPayIntroSheetContent(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sheetHeight(isModal = true)
            .gradientBackground()
            .navigationBarsPadding()
            .testTag("quick_pay_intro_sheet")
    ) {
        SheetTopBar(stringResource(R.string.settings__quickpay__nav_title))
        QuickPayIntroContent(onContinue)
    }
}

@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    AppThemeSurface {
        Column {
            BottomSheetPreview {
                QuickPayIntroSheetContent(
                    onContinue = {},
                )
            }
        }
    }
}
