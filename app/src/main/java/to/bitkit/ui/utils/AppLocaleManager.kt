package to.bitkit.ui.utils

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import to.bitkit.models.Language
import to.bitkit.models.Language.Companion.ARABIC
import to.bitkit.models.Language.Companion.CATALAN
import to.bitkit.models.Language.Companion.CZECH
import to.bitkit.models.Language.Companion.DUTCH
import to.bitkit.models.Language.Companion.ENGLISH
import to.bitkit.models.Language.Companion.FRENCH
import to.bitkit.models.Language.Companion.GERMAN
import to.bitkit.models.Language.Companion.GREEK
import to.bitkit.models.Language.Companion.ITALIAN
import to.bitkit.models.Language.Companion.POLISH
import to.bitkit.models.Language.Companion.PORTUGUESE
import to.bitkit.models.Language.Companion.RUSSIAN
import to.bitkit.models.Language.Companion.SPANISH
import to.bitkit.models.Language.Companion.SPANISH_LATIN_AMERICA
import to.bitkit.models.Language.Companion.SYSTEM_DEFAULT
import to.bitkit.models.toLanguage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLocaleManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun changeLanguage(language: Language) {
        val languageTag = if (language.isSystemDefault) {
            // Clear application locales to use system default
            ""
        } else {
            // Create language tag from language code and optional country code
            if (language.countryCode != null) {
                "${language.languageCode}-${language.countryCode}"
            } else {
                language.languageCode
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (languageTag.isEmpty()) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(languageTag)
        } else {
            AppCompatDelegate.setApplicationLocales(
                if (languageTag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(languageTag)
            )
        }
    }

    fun getCurrentLanguage(): Language {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeList = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (localeList != null && !localeList.isEmpty) localeList.get(0) else null
        } else {
            val localeListCompat = AppCompatDelegate.getApplicationLocales()
            if (!localeListCompat.isEmpty) localeListCompat.get(0) else null
        }

        return locale?.toLanguage() ?: Language.SYSTEM_DEFAULT
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
