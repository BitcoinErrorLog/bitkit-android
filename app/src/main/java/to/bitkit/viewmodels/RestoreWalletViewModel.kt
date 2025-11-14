package to.bitkit.viewmodels

import androidx.lifecycle.ViewModel
import com.synonym.bitkitcore.getBip39Suggestions
import com.synonym.bitkitcore.isValidBip39Word
import com.synonym.bitkitcore.validateMnemonic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

private const val WORDS_MIN = 12
private const val WORDS_MAX = 24

@HiltViewModel
class RestoreWalletViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(RestoreWalletUiState())
    val uiState: StateFlow<RestoreWalletUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(focusedIndex = 0) }
    }

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

    fun onPassphraseChanged(passphrase: String) = _uiState.update { it.copy(bip39Passphrase = passphrase) }

    fun onBackspaceInEmpty(index: Int) {
        if (index > 0) {
            _uiState.update { it.copy(focusedIndex = index - 1) }
        }
    }

    fun onKeyboardDismissed() = _uiState.update { it.copy(shouldDismissKeyboard = false) }

    fun onScrollCompleted() = _uiState.update { it.copy(scrollToFieldIndex = null) }

    private fun handlePastedWords(pastedText: String) {
        val separators = Regex("\\s+") // any whitespace chars to account for different sources like password managers
        val pastedWords = pastedText
            .split(separators)
            .filter { it.isNotBlank() }
        if (pastedWords.size == WORDS_MIN || pastedWords.size == WORDS_MAX) {
            val invalidIndices = pastedWords.withIndex()
                .filter { !isValidBip39Word(it.value) }
                .map { it.index }
                .toSet()

            val newWords = _uiState.value.words.toMutableList().apply {
                pastedWords.forEachIndexed { index, word -> this[index] = word }
                for (index in pastedWords.size until WORDS_MAX) {
                    this[index] = ""
                }
            }

            _uiState.update {
                it.copy(
                    words = newWords,
                    invalidWordIndices = invalidIndices,
                    is24Words = pastedWords.size == WORDS_MAX,
                    shouldDismissKeyboard = invalidIndices.isEmpty(),
                    focusedIndex = null,
                    suggestions = emptyList(),
                )
            }
        }
    }

    private fun updateWordValidity(index: Int, value: String) {
        val newWords = _uiState.value.words.toMutableList().apply {
            this[index] = value
        }

        val newInvalidIndices = _uiState.value.invalidWordIndices.toMutableSet()
        if (!isValidBip39Word(value) && value.isNotEmpty()) {
            newInvalidIndices.add(index)
        } else {
            newInvalidIndices.remove(index)
        }

        _uiState.update {
            it.copy(
                words = newWords,
                invalidWordIndices = newInvalidIndices,
            )
        }
    }

    private fun updateSuggestions(input: String, index: Int?) {
        if (index == null || input.length < 2) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }

        val suggestions = getBip39Suggestions(input.lowercase(), 3u)
        val filtered = if (suggestions.size == 1 && suggestions.firstOrNull() == input) {
            emptyList()
        } else {
            suggestions
        }

        _uiState.update { it.copy(suggestions = filtered) }
    }
}

data class RestoreWalletUiState(
    val words: List<String> = List(WORDS_MAX) { "" },
    val invalidWordIndices: Set<Int> = emptySet(),
    val suggestions: List<String> = emptyList(),
    val focusedIndex: Int? = null,
    val bip39Passphrase: String = "",
    val showingPassphrase: Boolean = false,
    val is24Words: Boolean = false,
    val shouldDismissKeyboard: Boolean = false,
    val scrollToFieldIndex: Int? = null,
) {
    val wordCount: Int get() = if (is24Words) WORDS_MAX else WORDS_MIN
    val wordsPerColumn: Int get() = if (is24Words) WORDS_MIN else 6

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

private fun List<String>.validBip39Checksum(): Boolean {
    if (this.size != 12 && this.size != 24) return false
    if (this.any { !isValidBip39Word(it) }) return false

    return runCatching { validateMnemonic(this.joinToString(" ")) }.isSuccess
}
