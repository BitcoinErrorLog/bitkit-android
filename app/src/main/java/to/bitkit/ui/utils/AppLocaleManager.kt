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
        return Language.getSupportedLanguages()
    }
}
