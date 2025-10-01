package to.bitkit.ui.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat.startActivity
import to.bitkit.utils.Logger

object NotificationUtils {
    /**
     * Opens the Android system notification settings for the app.
     * On Android 8.0+ (API 26+), opens the app's notification settings.
     * On older versions, opens the general app settings.
     */
    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching {
            context.startActivity(intent)
        }.onFailure { e ->
            Logger.error("Failed to open notification settings", e = e, context = "NotificationUtils")
        }
    }
}
