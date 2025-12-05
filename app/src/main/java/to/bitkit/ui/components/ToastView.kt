package to.bitkit.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.bitkit.R
import to.bitkit.models.Toast
import to.bitkit.ui.scaffold.ScreenColumn
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors

@Composable
fun ToastView(
    toast: Toast,
    onDismiss: () -> Unit,
) {
    val tintColor = toast.tintColor()

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .systemBarsPadding()
            .padding(horizontal = 16.dp)
            .background(tintColor.copy(alpha = 0.32f), shape = MaterialTheme.shapes.medium)
            .padding(16.dp)
            .then(toast.testTag?.let { Modifier.testTag(it) } ?: Modifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                BodyMSB(
                    text = toast.title,
                    color = tintColor,
                )
                toast.description?.let { description ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Caption(text = description)
                }
            }
            if (!toast.autoHide) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.common__close),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToastHost(
    toast: Toast?,
    onDismiss: () -> Unit,
) {
    AnimatedContent(
        targetState = toast,
        transitionSpec = {
            (fadeIn() + slideInVertically { -it })
                .togetherWith(fadeOut() + slideOutVertically { -it })
                .using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.TopCenter,
        label = "toastAnimation",
    ) {
        if (it != null) {
            ToastView(toast = it, onDismiss = onDismiss)
        }
    }
}

@Composable
fun ToastOverlay(
    toast: Toast?,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize(),
    ) {
        ToastHost(toast = toast, onDismiss = onDismiss)
    }
}

@Preview(showSystemUi = true)
@Composable
private fun ToastViewPreview() {
    AppThemeSurface {
        ScreenColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            ToastView(
                toast = Toast(
                    type = Toast.ToastType.WARNING,
                    title = "You’re still offline",
                    description = "Check your connection to keep using Bitkit.",
                    autoHide = true,
                ),
                onDismiss = {}
            )
            ToastView(
                toast = Toast(
                    type = Toast.ToastType.LIGHTNING,
                    title = "Instant Payments Ready",
                    description = "You can now pay anyone, anywhere, instantly.",
                    autoHide = true,
                ),
                onDismiss = {}
            )
            ToastView(
                toast = Toast(
                    type = Toast.ToastType.SUCCESS,
                    title = "You’re Back Online!",
                    description = "Successfully reconnected to the Internet.",
                    autoHide = true,
                ),
                onDismiss = {}
            )
            ToastView(
                toast = Toast(
                    type = Toast.ToastType.INFO,
                    title = "General Message",
                    description = "Used for neutral content to inform the user.",
                    autoHide = false,
                ),
                onDismiss = {}
            )
            ToastView(
                toast = Toast(
                    type = Toast.ToastType.ERROR,
                    title = "Error Toast",
                    description = "This is a toast message.",
                    autoHide = true,
                ),
                onDismiss = {}
            )
        }
    }
}

@ReadOnlyComposable
@Composable
private fun Toast.tintColor(): Color = when (type) {
    Toast.ToastType.SUCCESS -> Colors.Green
    Toast.ToastType.INFO -> Colors.Blue
    Toast.ToastType.LIGHTNING -> Colors.Purple
    Toast.ToastType.WARNING -> Colors.Brand
    Toast.ToastType.ERROR -> Colors.Red
}
