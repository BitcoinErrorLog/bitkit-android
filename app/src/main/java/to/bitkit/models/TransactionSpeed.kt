package to.bitkit.models

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import to.bitkit.R
import to.bitkit.ui.theme.Colors

@Serializable(with = TransactionSpeedSerializer::class)
sealed class TransactionSpeed {
    object Fast : TransactionSpeed()
    object Medium : TransactionSpeed()
    object Slow : TransactionSpeed()
    data class Custom(val satsPerVByte: UInt) : TransactionSpeed()

    fun serialized(): String = when (this) {
        is Fast -> "fast"
        is Medium -> "medium"
        is Slow -> "slow"
        is Custom -> "custom_$satsPerVByte"
    }

    companion object {
        fun entries() = listOf(Fast, Medium, Slow, Custom(0u))

        fun fromString(value: String): TransactionSpeed = when {
            value == "fast" -> Fast
            value == "medium" -> Medium
            value == "slow" -> Slow
            value.matches(Regex("custom_\\d+")) -> {
                value.substringAfter("custom_")
                    .toUIntOrNull()
                    ?.let { Custom(it) }
                    ?: Medium
            }

            else -> Medium
        }
    }
}

private object TransactionSpeedSerializer : KSerializer<TransactionSpeed> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TransactionSpeed", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TransactionSpeed) {
        encoder.encodeString(value.serialized())
    }

    override fun deserialize(decoder: Decoder): TransactionSpeed {
        return TransactionSpeed.fromString(decoder.decodeString())
    }
}

@Composable
fun TransactionSpeed.transactionSpeedUiText(): String {
    return when (this) {
        is TransactionSpeed.Fast -> stringResource(R.string.settings__fee__fast__value)
        is TransactionSpeed.Medium -> stringResource(R.string.settings__fee__normal__value)
        is TransactionSpeed.Slow -> stringResource(R.string.settings__fee__slow__value)
        is TransactionSpeed.Custom -> stringResource(R.string.settings__fee__custom__value)
    }
}

enum class FeeRate(
    @StringRes val title: Int,
    @StringRes val description: Int,
    @DrawableRes val icon: Int,
    val color: Color,
) {
    FAST(
        title = R.string.fee__fast__title,
        description = R.string.fee__fast__description,
        color = Colors.Brand,
        icon = R.drawable.ic_speed_fast,
    ),
    NORMAL(
        title = R.string.fee__normal__title,
        description = R.string.fee__normal__description,
        color = Colors.Brand,
        icon = R.drawable.ic_speed_normal,
    ),
    SLOW(
        title = R.string.fee__slow__title,
        description = R.string.fee__slow__description,
        color = Colors.Brand,
        icon = R.drawable.ic_speed_slow,
    ),
    CUSTOM(
        title = R.string.fee__custom__title,
        description = R.string.fee__custom__description,
        color = Colors.White64,
        icon = R.drawable.ic_settings,
    );

    fun toSpeed(): TransactionSpeed {
        return when (this) {
            FAST -> TransactionSpeed.Fast
            NORMAL -> TransactionSpeed.Medium
            SLOW -> TransactionSpeed.Slow
            CUSTOM -> TransactionSpeed.Custom(0u)
        }
    }

    companion object {
        fun fromSpeed(speed: TransactionSpeed): FeeRate {
            return when (speed) {
                is TransactionSpeed.Fast -> FAST
                is TransactionSpeed.Medium -> NORMAL
                is TransactionSpeed.Slow -> SLOW
                is TransactionSpeed.Custom -> CUSTOM
            }
        }
    }
}
