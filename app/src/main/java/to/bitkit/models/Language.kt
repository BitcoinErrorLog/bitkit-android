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

        fun fromLanguageCode(code: String, countryCode: String? = null): Language {
            return when (code) {
                "system" -> SYSTEM_DEFAULT
                "en" -> ENGLISH
                "es" -> SPANISH
                "fr" -> FRENCH
                "pt" -> PORTUGUESE
                else -> createFromLocale(Locale(code, countryCode ?: ""))
            }
        }

        fun fromLocale(locale: Locale): Language {
            // Check for predefined languages first
            val predefined = fromLanguageCode(locale.language, locale.country)
            if (predefined.languageCode != "system" &&
                predefined.languageCode == locale.language) {
                return predefined
            }

            // Create dynamic language
            return createFromLocale(locale)
        }

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

        fun createFromDisplayName(displayName: String, languageCode: String, countryCode: String? = null): Language {
            return Language(
                displayName = displayName,
                languageCode = languageCode,
                countryCode = countryCode
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Language) return false

        return languageCode == other.languageCode &&
               countryCode == other.countryCode &&
               isSystemDefault == other.isSystemDefault
    }

    override fun hashCode(): Int {
        var result = languageCode.hashCode()
        result = 31 * result + (countryCode?.hashCode() ?: 0)
        result = 31 * result + isSystemDefault.hashCode()
        return result
    }
}
