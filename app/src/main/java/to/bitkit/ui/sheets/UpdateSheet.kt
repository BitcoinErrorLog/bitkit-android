package to.bitkit.ui.sheets

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.bitkit.R
import to.bitkit.ui.components.BodyM
import to.bitkit.ui.components.BottomSheetPreview
import to.bitkit.ui.components.Display
import to.bitkit.ui.components.PrimaryButton
import to.bitkit.ui.components.SecondaryButton
import to.bitkit.ui.components.SheetSize
import to.bitkit.ui.components.VerticalSpacer
import to.bitkit.ui.scaffold.SheetTopBar
import to.bitkit.ui.shared.modifiers.sheetHeight
import to.bitkit.ui.shared.util.gradientBackground
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors
import to.bitkit.ui.utils.withAccent

@Composable
fun UpdateSheet(
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .sheetHeight(SheetSize.LARGE)
            .gradientBackground()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {

        SheetTopBar(titleText = stringResource(R.string.other__update_nav_title))
        VerticalSpacer(16.dp)

        Image(
            painter = painterResource(R.drawable.wand),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .aspectRatio(1.0f)
                .weight(1f)
        )

        Display(
            text = stringResource(R.string.other__update_title)
                .withAccent(accentColor = Colors.Brand),
            color = Colors.White,
        )

        BodyM(
            text = stringResource(R.string.other__update_text),
            color = Colors.White64,
        )

        VerticalSpacer(32.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("buttons_row"),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SecondaryButton(
                text = stringResource(R.string.common__cancel),
                fullWidth = false,
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f),
            )

            PrimaryButton(
                text = stringResource(R.string.other__update_button),
                fullWidth = false,
                onClick = {}, // TODO
                modifier = Modifier
                    .weight(1f),
            )
        }
        VerticalSpacer(16.dp)
    }
}

@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    AppThemeSurface {
        BottomSheetPreview {
            UpdateSheet(
                onCancel = {},
            )
        }
    }
}
