package to.bitkit.ui.utils

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import to.bitkit.models.Language
import java.util.Locale
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

        return if (locale == null) {
            Language.SYSTEM_DEFAULT
        } else {
            Language.createFromLocale(locale)
        }
    }

    fun getSupportedLanguages(): List<Language> {
        val languages = mutableListOf<Language>()

        // Always add system default as first option
        languages.add(Language.SYSTEM_DEFAULT)

        // Try to get available locales from the system
        val availableLocales = try {
            Locale.getAvailableLocales().toList()
        } catch (e: Exception) {
            emptyList()
        }

        if (availableLocales.isNotEmpty()) {
            // Filter and convert system locales to Language objects
            val systemLanguages = availableLocales
                .filter { locale ->
                    // Filter out locales without language codes or with empty display names
                    locale.language.isNotEmpty() &&
                        locale.getDisplayName(locale).isNotEmpty() &&
                        locale.getDisplayName(locale) != locale.toString()
                }
                .distinctBy { "${it.language}-${it.country}" } // Remove duplicates
                .sortedBy { it.getDisplayName(it) } // Sort alphabetically
                .map { Language.createFromLocale(it) }

            languages.addAll(systemLanguages)
        } else {
            // Fallback to common languages if system locales are not available
            languages.addAll(Language.getCommonLanguages())
        }

        return languages
    }

}
