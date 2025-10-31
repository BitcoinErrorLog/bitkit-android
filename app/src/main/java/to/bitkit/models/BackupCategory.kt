package to.bitkit.models

import kotlinx.serialization.Serializable
import to.bitkit.R

@Serializable
enum class BackupCategory(
    val uiIcon: Int,
    val uiTitle: Int,
) {
    LIGHTNING_CONNECTIONS(
        uiIcon = R.drawable.ic_lightning,
        uiTitle = R.string.settings__backup__category_connections,
    ),
    BLOCKTANK(
        uiIcon = R.drawable.ic_note,
        uiTitle = R.string.settings__backup__category_connection_receipts,
    ),
    LDK_ACTIVITY(
        uiIcon = R.drawable.ic_transfer,
        uiTitle = R.string.settings__backup__category_transaction_log,
    ),
    WALLET(
        uiIcon = R.drawable.ic_timer_alt,
        uiTitle = R.string.settings__backup__category_wallet,
    ),
    SETTINGS(
        uiIcon = R.drawable.ic_settings,
        uiTitle = R.string.settings__backup__category_settings,
    ),
    WIDGETS(
        uiIcon = R.drawable.ic_rectangles_two,
        uiTitle = R.string.settings__backup__category_widgets,
    ),
    METADATA(
        uiIcon = R.drawable.ic_tag,
        uiTitle = R.string.settings__backup__category_tags,
    ),
    // Descoped in v1, will return in v2:
    // PROFILE(
    //     uiIcon = R.drawable.ic_user,
    //     uiTitle = R.string.settings__backup__category_profile,
    // ),
    // CONTACTS(
    //     uiIcon = R.drawable.ic_users,
    //     uiTitle = R.string.settings__backup__category_contacts,
    // ),
}

/**
 * @property running In progress
 * @property synced Timestamp in ms of last time this backup was synced
 * @property required Timestamp in ms of last time this backup was required
 */
@Serializable
data class BackupItemStatus(
    val running: Boolean = false,
    val synced: Long = 0L,
    val required: Long = 0L,
)
