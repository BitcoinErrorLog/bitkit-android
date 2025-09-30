package to.bitkit.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors
import to.bitkit.ui.theme.Shapes

@Composable
fun NotificationPreview(
    enabled: Boolean,
    title: String,
    description: String,
    showDetails: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(9.dp)
            .clip(Shapes.medium)
            .background(Colors.White)
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            BodyMSB(text = title)
            Caption(description)
        }

        Caption("3m ago")

    }
}


@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    AppThemeSurface {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NotificationPreview(
                enabled = true,
                title = "Payment Received",
                description = "₿ 21 000",
                showDetails = true,
                modifier = Modifier.fillMaxWidth()
            )
            NotificationPreview(
                enabled = false,
                title = "Payment Received",
                description = "₿ 21 000",
                showDetails = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
