package to.bitkit.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import to.bitkit.ui.components.BottomSheet
import to.bitkit.ui.components.BottomSheetPreview
import to.bitkit.ui.scaffold.SheetTopBar
import to.bitkit.ui.settings.backgroundPayments.BackgroundPaymentsIntroContent
import to.bitkit.ui.shared.modifiers.sheetHeight
import to.bitkit.ui.theme.AppThemeSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundPaymentsIntroSheet(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheet(onDismissRequest = onDismiss) {
        BackgroundPaymentsIntroSheetContent(onContinue)
    }
}

@Composable
private fun BackgroundPaymentsIntroSheetContent(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sheetHeight(isModal = true)
            .navigationBarsPadding()
            .testTag("background_payments_intro_sheet")
    ) {
        SheetTopBar(
            titleText = "Background Payments", // Todo Transifex
        )
        BackgroundPaymentsIntroContent (onContinue)
    }

}

@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    AppThemeSurface {
        Column {
            BottomSheetPreview {
                BackgroundPaymentsIntroSheetContent(
                    onContinue = {},
                )
            }
        }
    }
}
