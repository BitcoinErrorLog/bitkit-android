package to.bitkit.data.dto

enum class TransferType {
    TO_SPENDING,
    TO_SAVINGS,
    FORCE_CLOSE,
    COOP_CLOSE;

    fun isToSavings(): Boolean = this == TO_SAVINGS || this == FORCE_CLOSE || this == COOP_CLOSE
}
