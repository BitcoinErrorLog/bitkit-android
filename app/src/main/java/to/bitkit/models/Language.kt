package to.bitkit.models

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class Language(
    val displayName: String,
    val languageCode: String,
    val countryCode: String? = null,
    val isSystemDefault: Boolean = false,
) {
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
    }
}

fun Locale.toLanguage(): Language {
    val displayName = this.getDisplayName(this).replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(this) else it.toString()
    }

    return Language(
        displayName = displayName,
        languageCode = this.language,
        countryCode = this.country.ifEmpty { null }
    )
}
