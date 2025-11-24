package to.bitkit.ui.utils

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import to.bitkit.utils.Logger

object GooglePlayServicesUtils {
    /**
     * Checks if Firebase Cloud Messaging (and by extension Google Play Services) is available.
     * This indicates whether the device can receive FCM notifications.
     * Returns true if FCM is available, false otherwise.
     */
    fun isAvailable(context: Context): Boolean {
        return runCatching {
            // Try to get FirebaseMessaging instance
            // This will fail if Google Play Services is not available
            FirebaseMessaging.getInstance()
            Logger.debug(
                "Firebase Messaging available (Google Play Services present)",
                context = "GooglePlayServicesUtils"
            )
            true
        }.getOrElse { error ->
            Logger.debug(
                "Firebase Messaging not available: ${error.message}",
                context = "GooglePlayServicesUtils"
            )
            false
        }
    }
}
