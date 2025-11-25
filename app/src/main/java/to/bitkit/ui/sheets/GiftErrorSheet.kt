package to.bitkit.ui.sheets

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import to.bitkit.R
import to.bitkit.ui.components.BodyM
import to.bitkit.ui.components.FillHeight
import to.bitkit.ui.components.PrimaryButton
import to.bitkit.ui.components.SheetSize
import to.bitkit.ui.components.VerticalSpacer
import to.bitkit.ui.scaffold.SheetTopBar
import to.bitkit.ui.shared.modifiers.sheetHeight
import to.bitkit.ui.shared.util.gradientBackground
import to.bitkit.ui.theme.Colors

@Composable
fun GiftErrorSheet(
    @StringRes titleRes: Int,
    @StringRes textRes: Int,
    testTag: String,
    onDismiss: () -> Unit,
) {
    Content(
        titleRes = titleRes,
        textRes = textRes,
        testTag = testTag,
        onDismiss = onDismiss,
    )
}

@Composable
private fun Content(
    @StringRes titleRes: Int,
    @StringRes textRes: Int,
    testTag: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .sheetHeight(SheetSize.LARGE)
            .gradientBackground()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        SheetTopBar(titleText = stringResource(titleRes))
        VerticalSpacer(16.dp)

        BodyM(
            text = stringResource(textRes),
            color = Colors.White64,
        )

        FillHeight()

        Image(
            painter = painterResource(R.drawable.exclamation_mark),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth(IMAGE_WIDTH_FRACTION)
                .aspectRatio(1.0f)
                .align(Alignment.CenterHorizontally)
        )

        FillHeight()

        PrimaryButton(
            text = stringResource(R.string.common__ok),
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
        )
        VerticalSpacer(16.dp)
    }
}
