package to.bitkit.models

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CurrencyTest {

    @Test
    fun `formatToModernDisplay uses space grouping`() {
        val sats = 123_456_789L

        val formatted = sats.formatToModernDisplay(Locale.US)

        assertEquals("123 456 789", formatted)
    }

    @Test
    fun `formatToModernDisplay handles zero`() {
        val formatted = 0L.formatToModernDisplay(Locale.US)

        assertEquals("0", formatted)
    }

    @Test
    fun `formatToClassicDisplay always shows eight decimals`() {
        val formatted = 0L.formatToClassicDisplay(Locale.US)

        assertEquals("0.00000000", formatted)
    }

    @Test
    fun `formatToClassicDisplay converts sats to btc`() {
        val sats = 12_345L // 0.00012345 BTC

        val formatted = sats.formatToClassicDisplay(Locale.US)

        assertEquals("0.00012345", formatted)
    }
}
