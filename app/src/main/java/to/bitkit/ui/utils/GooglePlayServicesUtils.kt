package to.bitkit.ui.utils

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import to.bitkit.utils.Logger

object GooglePlayServicesUtils {
    /**
     * Checks if Google Play Services are available on the device.
     * Returns true if available and up to date, false otherwise.
     */
    fun isAvailable(context: Context): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)

        return when (resultCode) {
            ConnectionResult.SUCCESS -> {
                Logger.debug("Google Play Services are available", context = "GooglePlayServicesUtils")
                true
            }
            else -> {
                Logger.debug(
                    "Google Play Services not available. Code: $resultCode",
                    context = "GooglePlayServicesUtils"
                )
                false
            }
        }
    }
}
