package to.bitkit.models

import androidx.compose.runtime.Composable
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

enum class FeeRate {
    FAST, NORMAL, SLOW, CUSTOM;

    @Composable
    fun uiTitle(): String {
        return when (this) {
            FAST -> stringResource(R.string.fee__fast__title)
            NORMAL -> stringResource(R.string.fee__normal__title)
            SLOW -> stringResource(R.string.fee__slow__title)
            CUSTOM -> stringResource(R.string.fee__custom__title)
        }
    }

    @Composable
    fun uiDescription(): String {
        return when (this) {
            FAST -> stringResource(R.string.fee__fast__description)
            NORMAL -> stringResource(R.string.fee__normal__description)
            SLOW -> stringResource(R.string.fee__slow__description)
            CUSTOM -> stringResource(R.string.fee__custom__description)
        }
    }

    @Composable
    fun uiIcon(): Painter {
        return when (this) {
            FAST -> painterResource(R.drawable.ic_speed_fast)
            NORMAL -> painterResource(R.drawable.ic_speed_normal)
            SLOW -> painterResource(R.drawable.ic_speed_slow)
            CUSTOM -> painterResource(R.drawable.ic_settings)
        }
    }
}
