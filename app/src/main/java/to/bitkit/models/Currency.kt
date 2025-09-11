package to.bitkit.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

const val BITCOIN_SYMBOL = "₿"
const val SATS_IN_BTC = 100_000_000
const val BTC_SCALE = 8
const val GROUPING_SEPARATOR = ' '

@Serializable
data class FxRateResponse(
    val tickers: List<FxRate>,
)

@Serializable
data class FxRate(
    val symbol: String,
    val lastPrice: String,
    val base: String,
    val baseName: String,
    val quote: String,
    val quoteName: String,
    val currencySymbol: String,
    val currencyFlag: String,
    val lastUpdatedAt: Long,
) {
    val rate: Double
        get() = lastPrice.toDoubleOrNull() ?: 0.0

    val timestamp: Instant
        get() = Instant.fromEpochMilliseconds(lastUpdatedAt)
}

/** aka. Unit */
enum class PrimaryDisplay {
    BITCOIN, FIAT;

    operator fun not() = when (this) {
        BITCOIN -> FIAT
        FIAT -> BITCOIN
    }
}

/** aka. Denomination */
enum class BitcoinDisplayUnit {
    MODERN, CLASSIC;

    operator fun not() = when (this) {
        MODERN -> CLASSIC
        CLASSIC -> MODERN
    }

    fun isModern() = this == MODERN
}

data class ConvertedAmount(
    val value: BigDecimal,
    val formatted: String,
    val symbol: String,
    val currency: String,
    val flag: String,
    val sats: Long,
    val locale: Locale = Locale.getDefault(),
) {
    data class BitcoinDisplayComponents(
        val symbol: String,
        val value: String,
    )

    fun bitcoinDisplay(unit: BitcoinDisplayUnit): BitcoinDisplayComponents {
        val formattedValue = when (unit) {
            BitcoinDisplayUnit.MODERN -> sats.formatToModernDisplay(locale)
            BitcoinDisplayUnit.CLASSIC -> sats.formatToClassicDisplay(locale)
        }
        return BitcoinDisplayComponents(
            symbol = BITCOIN_SYMBOL,
            value = formattedValue,
        )
    }
}

fun Long.formatToModernDisplay(locale: Locale = Locale.getDefault()): String {
    val sats = this
    val formatSymbols = DecimalFormatSymbols(locale).apply {
        groupingSeparator = GROUPING_SEPARATOR
    }
    val formatter = DecimalFormat("#,###", formatSymbols).apply {
        isGroupingUsed = true
    }
    return formatter.format(sats)
}

fun ULong.formatToModernDisplay(locale: Locale = Locale.getDefault()): String = toLong().formatToModernDisplay(locale)

fun Long.formatToClassicDisplay(locale: Locale = Locale.getDefault()): String {
    val sats = this
    val formatSymbols = DecimalFormatSymbols(locale)
    val formatter = DecimalFormat("###.########", formatSymbols)
    return formatter.format(sats.asBtc())
}

/** Represent this sat value in Bitcoin BigDecimal. */
fun Long.asBtc(): BigDecimal = BigDecimal(this).divide(BigDecimal(SATS_IN_BTC), BTC_SCALE, RoundingMode.HALF_UP)
