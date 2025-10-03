package to.bitkit.data.dto

enum class TransferType {
    TO_SPENDING,
    TO_SAVINGS,
    MANUAL_SETUP,
    FORCE_CLOSE,
    COOP_CLOSE;

    fun isToSavings() = this in listOf(TO_SAVINGS, COOP_CLOSE, FORCE_CLOSE)
    fun isToSpending() = this in listOf(TO_SPENDING, MANUAL_SETUP)
}
