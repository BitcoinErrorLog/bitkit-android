package to.bitkit.viewmodels

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import to.bitkit.data.SettingsStore
import to.bitkit.models.Language
import to.bitkit.utils.Logger
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LanguageUiState())
    val uiState: StateFlow<LanguageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsStore.data,
                _uiState
            ) { settings, currentState ->
                currentState.copy(
                    selectedLanguage = settings.selectedLanguage,
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun selectLanguage(language: Language) {
        viewModelScope.launch {
            runCatching {

                settingsStore.update { currentSettings ->
                    currentSettings.copy(selectedLanguage = language)
                }

                // Apply the language change
                applyLanguageChange(language)

                _uiState.value = _uiState.value.copy(
                    selectedLanguage = language,
                )
            }.onFailure { e ->
                Logger.warn("fail to set language", e = e)
            }
        }
    }

    private fun applyLanguageChange(language: Language) {
        val localeToSet = when (language) {
            Language.SYSTEM_DEFAULT -> LocaleListCompat.getEmptyLocaleList()
            else -> language.toLocale()?.let { LocaleListCompat.create(it) } ?: LocaleListCompat.getEmptyLocaleList()
        }

        AppCompatDelegate.setApplicationLocales(localeToSet)
    }

    fun getAvailableLanguages(): List<Language> {
        val availableLanguages = mutableListOf<Language>()

        // Always add System Default as the first option
        availableLanguages.add(Language.SYSTEM_DEFAULT)

        // Get available locales from application locales first
        val applicationLocales = AppCompatDelegate.getApplicationLocales()
        for (i in 0 until applicationLocales.size()) {
            val locale = applicationLocales[i]
            if (locale != null) {
                val language = Language.fromLocale(locale)
                if (!language.isSystemDefault && !availableLanguages.contains(language)) {
                    availableLanguages.add(language)
                }
            }
        }

        // If no application locales are set, get supported locales from resources
        if (availableLanguages.size == 1) { // Only System Default
            availableLanguages.addAll(getSupportedLanguagesFromResources())
        }

        return availableLanguages.distinctBy { "${it.languageCode}_${it.countryCode}" }
    }

    private fun getSupportedLanguagesFromResources(): List<Language> {
        return try {
            val supportedLanguages = mutableSetOf<Language>()

            // Try to get locales from app resources configuration
            val configuration = context.resources.configuration
            val locales = configuration.locales

            for (i in 0 until locales.size()) {
                val locale = locales[i]
                val language = Language.fromLocale(locale)
                if (!language.isSystemDefault) {
                    supportedLanguages.add(language)
                }
            }

            // Also check for available string resource locales
//            val availableLocales = getAvailableResourceLocales()
//            availableLocales.forEach { locale ->
//                val language = Language.fromLocale(locale)
//                if (!language.isSystemDefault) {
//                    supportedLanguages.add(language)
//                }
//            }

            // If still empty, add common fallback languages
            if (supportedLanguages.isEmpty()) {
                supportedLanguages.addAll(Language.getCommonLanguages().take(8)) // Limit to 8 common languages
            }

            supportedLanguages.toList()
        } catch (e: Exception) {
            // Ultimate fallback
            listOf(Language.ENGLISH)
        }
    }

    private fun getAvailableResourceLocales(): List<Locale> {
        return try {
            val assetManager = context.assets
            val availableLocales = mutableListOf<Locale>()

            // This is a simplified approach - in practice, you might want to
            // iterate through your actual string resource directories
            val localesArray = context.resources.getStringArray(
                context.resources.getIdentifier("supported_locales", "array", context.packageName)
            )

            localesArray.forEach { localeString ->
                try {
                    val parts = localeString.split("-")
                    val locale = if (parts.size >= 2) {
                        Locale(parts[0], parts[1])
                    } else {
                        Locale(parts[0])
                    }
                    availableLocales.add(locale)
                } catch (e: Exception) {
                    // Skip invalid locale strings
                }
            }

            availableLocales
        } catch (e: Exception) {
            // If no supported_locales array exists, return empty list
            emptyList()
        }
    }
}

data class LanguageUiState(
    val selectedLanguage: Language = Language.SYSTEM_DEFAULT,
)
