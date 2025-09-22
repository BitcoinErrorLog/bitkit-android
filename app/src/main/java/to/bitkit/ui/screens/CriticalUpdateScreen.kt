package to.bitkit.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.bitkit.R
import to.bitkit.ui.components.BodyM
import to.bitkit.ui.components.Display
import to.bitkit.ui.components.PrimaryButton
import to.bitkit.ui.components.VerticalSpacer
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors
import to.bitkit.ui.utils.withAccent

@Composable
fun CriticalUpdateScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .systemBarsPadding()
    ) {
        AppTopBar(
            titleText = stringResource(R.string.other__update_critical_nav_title),
            onBackClick = null
        )

        Image(
            painter = painterResource(R.drawable.exclamation_mark),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .aspectRatio(1.0f)
                .weight(1f)
        )

        Display(
            text = stringResource(R.string.other__update_critical_title)
                .withAccent(accentColor = Colors.Brand),
            color = Colors.White,
        )

        BodyM(
            text = stringResource(R.string.other__update_critical_text),
            color = Colors.White64,
        )

        VerticalSpacer(32.dp)

        PrimaryButton(
            text = stringResource(R.string.other__update_critical_button),
            fullWidth = true,
            onClick = {}, // TODO
        )

        VerticalSpacer(16.dp)
    }
}

@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    AppThemeSurface {
        CriticalUpdateScreen()
    }
}
