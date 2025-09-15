package to.bitkit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.bitkit.ui.theme.AppThemeSurface

private val matrix = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
)

@Composable
fun NumberPadSimple(
    onPress: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        matrix.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                row.forEach { number ->
                    NumberPadKeyButton(
                        text = number,
                        onPress = onPress,
                        height = 75.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Box(modifier = Modifier.weight(1f))
            NumberPadKeyButton(
                text = "0",
                onPress = onPress,
                height = 75.dp,
                modifier = Modifier.weight(1f)
            )

            NumberPadDeleteButton(
                onPress = { onPress(KEY_DELETE) },
                height = 75.dp,
                modifier = Modifier
                    .weight(1f)
                    .testTag("NRemove")
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
    AppThemeSurface {
        NumberPadSimple(
            onPress = {},
            modifier = Modifier.height(310.dp)
        )
    }
}
