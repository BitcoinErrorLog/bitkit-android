package to.bitkit.ui.utils

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun RequestNotificationPermissions(
    onPermissionChange: (Boolean) -> Unit,
    showPermissionDialog: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPermissionChange by rememberUpdatedState(onPermissionChange)

    // Check if permission is required (Android 13+)
    val requiresPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    var isGranted by remember {
        mutableStateOf(NotificationUtils.areNotificationsEnabled(context))
    }

    // Permission request launcher
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        isGranted = granted
        onPermissionChange(granted)
    }

    // Request permission on first composition if needed
    LaunchedEffect(Unit) {
        val currentPermissionState = NotificationUtils.areNotificationsEnabled(context)
        isGranted = currentPermissionState
        currentOnPermissionChange(currentPermissionState)

        if (!currentPermissionState && requiresPermission && showPermissionDialog) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Monitor lifecycle to check permission when returning from settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentPermissionState = NotificationUtils.areNotificationsEnabled(context)
                if (currentPermissionState != isGranted) {
                    isGranted = currentPermissionState
                    currentOnPermissionChange(currentPermissionState)
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
