package to.bitkit.ui.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import to.bitkit.ui.components.Caption13Up
import to.bitkit.ui.shared.util.screen
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = Colors.White64,
    padding: PaddingValues = PaddingValues(top = 16.dp),
    height: Dp = 50.dp,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(padding)
            .height(height)
    ) {
        Caption13Up(text = title, color = color)
    }
}

@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    AppThemeSurface {
        Column(
            modifier = Modifier
                .screen(insets = WindowInsets.safeContent)
        ) {
            SectionHeader("Default")
            HorizontalDivider()
            SectionHeader(
                title = "Colors.Brand",
                color = Colors.Brand,
            )
            HorizontalDivider()
            SectionHeader(
                title = "Dp.Unspecified",
                height = Dp.Unspecified,
            )
            HorizontalDivider()
            SectionHeader(
                title = "PaddingValues.Zero",
                padding = PaddingValues.Zero,
            )
            HorizontalDivider()
            SectionHeader(
                title = "PaddingValues.Zero + Dp.Unspecified",
                padding = PaddingValues.Zero,
                height = Dp.Unspecified,
            )
            HorizontalDivider()
        }
    }
}
