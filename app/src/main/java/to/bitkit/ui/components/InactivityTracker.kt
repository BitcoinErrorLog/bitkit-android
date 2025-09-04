package to.bitkit.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.bitkit.utils.Logger
import to.bitkit.viewmodels.AppViewModel
import to.bitkit.viewmodels.SettingsViewModel

@Composable
fun InactivityTracker(
    app: AppViewModel,
    settings: SettingsViewModel,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val isPinEnabled by settings.isPinEnabled.collectAsStateWithLifecycle()
    val isPinOnIdleEnabled by settings.isPinOnIdleEnabled.collectAsStateWithLifecycle()
    val isAuthenticated by app.isAuthenticated.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (isPinEnabled && isPinOnIdleEnabled && isAuthenticated) {
                        Logger.debug("App resumed, resetting isAuthenticated.")
                        app.setIsAuthenticated(false)
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = modifier
    ) {
        content()
    }
}
