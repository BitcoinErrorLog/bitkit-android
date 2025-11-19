package to.bitkit.services.core

import com.synonym.bitkitcore.getBip39Suggestions
import com.synonym.bitkitcore.isValidBip39Word
import to.bitkit.async.ServiceQueue
import javax.inject.Inject

class Bip39Service @Inject constructor() {
    suspend fun getSuggestions(input: String, count: UInt): List<String> = ServiceQueue.CORE.background {
        getBip39Suggestions(input, count)
    }

    suspend fun isValidWord(word: String): Boolean = ServiceQueue.CORE.background {
        isValidBip39Word(word)
    }

    suspend fun validateMnemonic(mnemonic: String): Result<Unit> = ServiceQueue.CORE.background {
        runCatching { com.synonym.bitkitcore.validateMnemonic(mnemonic) }
    }

    fun isValidMnemonicSize(wordList: List<String>): Boolean = MnemonicSize.isValid(wordList)

    private enum class MnemonicSize(val wordCount: Int) {
        TWELVE(12), TWENTY_FOUR(24);

        companion object {
            fun isValid(wordList: List<String>): Boolean = entries.any { it.wordCount == wordList.size }
        }
    }
}
