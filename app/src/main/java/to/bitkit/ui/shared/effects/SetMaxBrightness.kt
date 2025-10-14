package to.bitkit.ui.shared.effects

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
fun SetMaxBrightness() {
    val window = (LocalActivity.current)?.window ?: return

    DisposableEffect(Unit) {
        val originalBrightness = window.attributes?.screenBrightness

        window.attributes = window.attributes?.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }

        onDispose {
            window.attributes = window.attributes?.apply {
                screenBrightness = originalBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
}
