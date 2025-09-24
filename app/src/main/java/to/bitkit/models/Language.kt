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

        val ARABIC = Language("العربية", "ar")
        val CATALAN = Language("Català", "ca")
        val CZECH = Language("Čeština", "cs")
        val GERMAN = Language("Deutsch", "de")
        val GREEK = Language("Ελληνικά", "el")
        val ENGLISH = Language("English", "en", "US")
        val SPANISH = Language("Español", "es", "ES")
        val SPANISH_LATIN_AMERICA = Language("Español (Latinoamérica)", "es", "419")
        val FRENCH = Language("Français", "fr", "FR")
        val ITALIAN = Language("Italiano", "it")
        val DUTCH = Language("Nederlands", "nl")
        val POLISH = Language("Polski", "pl")
        val PORTUGUESE = Language("Português", "pt", "BR")
        val RUSSIAN = Language("Русский", "ru")

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

        fun getSupportedLanguages(): List<Language> {
            return listOf(
                SYSTEM_DEFAULT,
                ARABIC,
                CATALAN,
                CZECH,
                DUTCH,
                ENGLISH,
                FRENCH,
                GERMAN,
                GREEK,
                ITALIAN,
                POLISH,
                PORTUGUESE,
                RUSSIAN,
                SPANISH,
                SPANISH_LATIN_AMERICA
            ).sortedWith(compareBy<Language> { !it.isSystemDefault }.thenBy { it.displayName })
        }
    }
}
