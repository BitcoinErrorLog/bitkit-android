package to.bitkit.models

import androidx.core.os.LocaleListCompat
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class Language(
    val code: String,
    val displayLanguage: String
)
