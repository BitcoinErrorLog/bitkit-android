package to.bitkit.viewmodels

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import to.bitkit.utils.bip39Words
import to.bitkit.utils.isBip39
import to.bitkit.utils.validBip39Checksum
import javax.inject.Inject

@HiltViewModel
class RestoreWalletViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(RestoreWalletUiState())
    val uiState: StateFlow<RestoreWalletUiState> = _uiState.asStateFlow()

    fun onWordChanged(index: Int, value: String) {
        if (value.contains(" ")) {
            handlePastedWords(value)
        } else {
            updateWordValidity(index, value)
            updateSuggestions(value, _uiState.value.focusedIndex)
            _uiState.update { it.copy(scrollToFieldIndex = index) }
        }
    }

    fun onWordFocusChanged(index: Int, focused: Boolean) {
        if (focused) {
            _uiState.update {
                it.copy(
                    focusedIndex = index,
                    scrollToFieldIndex = index
                )
            }
            updateSuggestions(_uiState.value.words[index], index)
        } else if (_uiState.value.focusedIndex == index) {
            _uiState.update {
                it.copy(
                    focusedIndex = null,
                    suggestions = emptyList()
                )
            }
        }
    }

    fun onSuggestionSelected(suggestion: String) {
        _uiState.value.focusedIndex?.let { index ->
            updateWordValidity(index, suggestion)
            _uiState.update { it.copy(suggestions = emptyList()) }
        }
    }

    fun onAdvancedClick() {
        _uiState.update {
            it.copy(
                showingPassphrase = !it.showingPassphrase,
                bip39Passphrase = ""
            )
        }
    }

    fun onPassphraseChanged(passphrase: String) {
        _uiState.update { it.copy(bip39Passphrase = passphrase) }
    }

    fun onKeyboardDismissed() {
        _uiState.update { it.copy(shouldDismissKeyboard = false) }
    }

    fun onScrollCompleted() {
        _uiState.update { it.copy(scrollToFieldIndex = null) }
    }

    private fun handlePastedWords(pastedText: String) {
        // Splits on one or more whitespace characters (spaces, tabs, newlines) in case user pastes from pass managers
        val pastedWords = pastedText.split(Regex("\\s+")).filter(String::isNotBlank)
        if (pastedWords.size == 12 || pastedWords.size == 24) {
            val invalidIndices = pastedWords.withIndex()
                .filter { !it.value.isBip39() }
                .map { it.index }
                .toSet()

            val newWords = _uiState.value.words.toMutableList().apply {
                pastedWords.forEachIndexed { index, word -> this[index] = word }
                for (index in pastedWords.size until 24) {
                    this[index] = ""
                }
            }

            _uiState.update {
                it.copy(
                    words = newWords,
                    invalidWordIndices = invalidIndices,
                    is24Words = pastedWords.size == 24,
                    shouldDismissKeyboard = invalidIndices.isEmpty(),
                    focusedIndex = null,
                    suggestions = emptyList()
                )
            }
        }
    }

    private fun updateWordValidity(index: Int, value: String) {
        val newWords = _uiState.value.words.toMutableList().apply {
            this[index] = value
        }

        val newInvalidIndices = _uiState.value.invalidWordIndices.toMutableSet()
        if (!value.isBip39() && value.isNotEmpty()) {
            newInvalidIndices.add(index)
        } else {
            newInvalidIndices.remove(index)
        }

        _uiState.update {
            it.copy(
                words = newWords,
                invalidWordIndices = newInvalidIndices
            )
        }
    }

    private fun updateSuggestions(input: String, index: Int?) {
        if (index == null || input.length < 2) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }

        val filtered = bip39Words.filter { it.startsWith(input.lowercase()) }.take(3)
        val suggestions = if (filtered.size == 1 && filtered.firstOrNull() == input) {
            emptyList()
        } else {
            filtered
        }

        _uiState.update { it.copy(suggestions = suggestions) }
    }
}

data class RestoreWalletUiState(
    val words: List<String> = List(24) { "" },
    val invalidWordIndices: Set<Int> = emptySet(),
    val suggestions: List<String> = emptyList(),
    val focusedIndex: Int? = null,
    val bip39Passphrase: String = "",
    val showingPassphrase: Boolean = false,
    val is24Words: Boolean = false,
    val shouldDismissKeyboard: Boolean = false,
    val scrollToFieldIndex: Int? = null,
) {
    val wordCount: Int get() = if (is24Words) 24 else 12
    val wordsPerColumn: Int get() = if (is24Words) 12 else 6

    val checksumErrorVisible: Boolean
        get() {
            val activeWords = words.subList(0, wordCount)
            return activeWords.none { it.isBlank() } &&
                invalidWordIndices.isEmpty() &&
                !activeWords.validBip39Checksum()
        }

    val bip39Mnemonic: String
        get() = words.subList(0, wordCount).joinToString(" ").trim()

    val areButtonsEnabled: Boolean
        get() {
            val activeWords = words.subList(0, wordCount)
            return activeWords.none { it.isBlank() } &&
                invalidWordIndices.isEmpty() &&
                !checksumErrorVisible
        }
}
