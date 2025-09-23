package to.bitkit.models

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class Language(
    val displayName: String,
    val languageCode: String,
    val countryCode: String? = null,
    val isSystemDefault: Boolean = false
) {
    fun toLocale(): Locale? {
        return when {
            isSystemDefault -> null
            countryCode != null -> Locale(languageCode, countryCode)
            else -> Locale(languageCode)
        }
    }

    companion object {
        val SYSTEM_DEFAULT = Language(
            displayName = "System Default",
            languageCode = "system",
            countryCode = null,
            isSystemDefault = true
        )

        // Common predefined languages for fallback
        val ENGLISH = Language("English", "en", "US")
        val SPANISH = Language("Español", "es", "ES")
        val FRENCH = Language("Français", "fr", "FR")
        val PORTUGUESE = Language("Português", "pt", "BR")


        fun createFromLocale(locale: Locale): Language {
            val displayName = locale.getDisplayName(locale).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(locale) else it.toString()
            }

            return Language(
                displayName = displayName,
                languageCode = locale.language,
                countryCode = if (locale.country.isNotEmpty()) locale.country else null
            )
        }

        // Get fallback languages if no system locales are available
        fun getCommonLanguages(): List<Language> {
            return listOf(
                ENGLISH,
                SPANISH,
                FRENCH,
                PORTUGUESE,
            )
        }
    }
}
