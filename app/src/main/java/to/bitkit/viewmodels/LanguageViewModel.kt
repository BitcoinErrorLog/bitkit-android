package to.bitkit.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
            loadInitialLanguage()
        }
    }

    private fun loadInitialLanguage() {
        val currentLanguage = appLocaleManager.getCurrentLanguage()

        _uiState.update {
            it.copy(
                selectedLanguage = currentLanguage,
                languages = appLocaleManager.getSupportedLanguages()
            )
        }
    }

    fun selectLanguage(language: Language) {
        appLocaleManager.changeLanguage(language)
        _uiState.update { it.copy(selectedLanguage = language) }
    }
}

data class LanguageUiState(
    val selectedLanguage: Language = Language.SYSTEM_DEFAULT,
    val languages: List<Language> = listOf(),
    val isLoading: Boolean = false,
)
