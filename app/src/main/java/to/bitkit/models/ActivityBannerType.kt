package to.bitkit.models

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import to.bitkit.R
import to.bitkit.ui.theme.Colors

enum class ActivityBannerType(
    @DrawableRes val icon: Int,
    val color: Color,
) {
    SPENDING(
        color = Colors.Purple,
        icon = R.drawable.ic_transfer,
    ),
    SAVINGS(
        color = Colors.Brand,
        icon = R.drawable.ic_transfer,
    )
}
