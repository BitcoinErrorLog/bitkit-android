package to.bitkit.ui.shared.modifiers

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

@SuppressLint("UnnecessaryComposedModifier")
fun Modifier.fillScreenHeight(
    excluding: Dp,
): Modifier = composed {
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val maxHeight = with(density) { windowSize.height.toDp() - excluding }

    return@composed this.then(Modifier.heightIn(max = maxHeight))
}
