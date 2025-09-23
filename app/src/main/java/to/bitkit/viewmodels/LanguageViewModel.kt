package to.bitkit.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import to.bitkit.models.Language
import to.bitkit.ui.utils.AppLocaleManager
import javax.inject.Inject

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val appLocaleManager: AppLocaleManager,
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
                    languages = getAvailableLanguages()
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    private fun loadInitialLanguage() {
        val currentLanguage = appLocaleManager.getLanguageCode()
        _uiState.update { it.copy(selectedLanguage = language) }
    }

    fun changeLanguage(language: Language) {
        appLocaleManager.changeLanguage(language.code)
        _uiState.update { it.copy(selectedLanguage = language) }
    }
}

data class LanguageUiState(
    val selectedLanguage: Language = Language.SYSTEM_DEFAULT,
    val languages: List<Language> = listOf(),
    val isLoading: Boolean = false,
)
