package to.bitkit.ui.sheets

import kotlinx.serialization.Serializable

internal const val IMAGE_WIDTH_FRACTION = 0.8f

@Serializable
sealed interface GiftRoute {
    @Serializable
    data object Loading : GiftRoute

    @Serializable
    data object Used : GiftRoute

    @Serializable
    data object UsedUp : GiftRoute

    @Serializable
    data object Error : GiftRoute

    @Serializable
    data object Success : GiftRoute
}
