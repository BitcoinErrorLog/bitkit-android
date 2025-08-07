package to.bitkit.models

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import to.bitkit.R
import to.bitkit.ui.theme.Colors

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
