package to.bitkit.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Devices.NEXUS_5
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.bitkit.R
import to.bitkit.ui.shared.util.clickableAlpha
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors

private val maxKeyboardHeight = 300.dp
private val idealButtonHeight = 75.dp
private val minButtonHeight = 50.dp
private const val KEYBOARD_ROWS_NUMBER = 4
private const val KEYBOARD_COLLUMNS_NUMBER = 3
val keyButtonHaptic = HapticFeedbackType.VirtualKey

@Composable
fun Keyboard(
    onClick: (String) -> Unit,
    onClickBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    isDecimal: Boolean = true,
    availableHeight: Dp? = null,
) {
    BoxWithConstraints(modifier = modifier) {
        val constraintsHeight = this.maxHeight
        val effectiveHeight = availableHeight ?: constraintsHeight
        val idealTotalHeight = idealButtonHeight * KEYBOARD_ROWS_NUMBER

        val maxAllowedHeight = minOf(maxKeyboardHeight, effectiveHeight)

        val buttonHeight = when {
            // If we have plenty of space, use ideal height
            maxAllowedHeight >= idealTotalHeight -> idealButtonHeight
            // If space is limited, calculate proportional height but ensure minimum
            maxAllowedHeight >= (minButtonHeight * KEYBOARD_ROWS_NUMBER) -> maxAllowedHeight / KEYBOARD_ROWS_NUMBER
            // If extremely limited, use absolute minimum
            else -> minButtonHeight
        }

        val totalKeyboardHeight = buttonHeight * KEYBOARD_ROWS_NUMBER

        LazyVerticalGrid(
            columns = GridCells.Fixed(KEYBOARD_COLLUMNS_NUMBER),
            userScrollEnabled = false,
            modifier = Modifier.height(totalKeyboardHeight),
        ) {
            item { KeyTextButton(text = "1", onClick = onClick, buttonHeight = buttonHeight) }
            item { KeyTextButton(text = "2", onClick = onClick, buttonHeight = buttonHeight) }
            item { KeyTextButton(text = "3", onClick = onClick, buttonHeight = buttonHeight) }
            item { KeyTextButton(text = "4", onClick = onClick, buttonHeight = buttonHeight) }
            item { KeyTextButton(text = "5", onClick = onClick, buttonHeight = buttonHeight) }
            item { KeyTextButton(text = "6", onClick = onClick, buttonHeight = buttonHeight) }
            item { KeyTextButton(text = "7", onClick = onClick, buttonHeight = buttonHeight) }
            item { KeyTextButton(text = "8", onClick = onClick, buttonHeight = buttonHeight) }
            item { KeyTextButton(text = "9", onClick = onClick, buttonHeight = buttonHeight) }
            item { KeyTextButton(text = if (isDecimal) "." else "000", onClick = onClick, buttonHeight = buttonHeight) }
            item { KeyTextButton(text = "0", onClick = onClick, buttonHeight = buttonHeight) }
            item {
                KeyIconButton(
                    icon = R.drawable.ic_backspace,
                    contentDescription = stringResource(R.string.common__delete),
                    onClick = onClickBackspace,
                    buttonHeight = buttonHeight,
                    modifier = Modifier.testTag("KeyboardButton_backspace"),
                )
            }
        }
    }
}

@Composable
fun KeyIconButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    buttonHeight: Dp = idealButtonHeight,
    modifier: Modifier = Modifier,
) {
    KeyButtonBox(
        onClick = onClick,
        buttonHeight = buttonHeight,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
        )
    }
}

@Composable
fun KeyTextButton(
    text: String,
    onClick: (String) -> Unit,
    buttonHeight: Dp = idealButtonHeight,
    modifier: Modifier = Modifier,
) {
    KeyButtonBox(
        onClick = { onClick(text) },
        buttonHeight = buttonHeight,
        modifier = modifier.testTag("KeyboardButton_$text"),
    ) {
        Text(
            text = text,
            fontSize = when {
                buttonHeight < 60.dp -> 20.sp
                buttonHeight < 70.dp -> 22.sp
                else -> 24.sp
            },
            textAlign = TextAlign.Center,
            color = Colors.White,
        )
    }
}

@Composable
private fun KeyButtonBox(
    onClick: () -> Unit,
    buttonHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (BoxScope.() -> Unit),
) {
    val haptic = LocalHapticFeedback.current
    Box(
        content = content,
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(buttonHeight)
            .fillMaxWidth()
            .clickableAlpha(0.2f) {
                haptic.performHapticFeedback(keyButtonHaptic)
                onClick()
            },
    )
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
    AppThemeSurface {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Keyboard(
                onClick = {},
                onClickBackspace = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun Preview2() {
    AppThemeSurface {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Keyboard(
                isDecimal = false,
                onClick = {},
                onClickBackspace = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true, device = Devices.PIXEL_TABLET)
@Composable
private fun Preview3() {
    AppThemeSurface {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Keyboard(
                isDecimal = false,
                onClick = {},
                onClickBackspace = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true, device = NEXUS_5)
@Composable
private fun PreviewShortScreen() {
    AppThemeSurface {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Keyboard(
                onClick = {},
                onClickBackspace = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
