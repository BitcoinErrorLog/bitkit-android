package to.bitkit.models

import kotlinx.serialization.Serializable
import to.bitkit.R

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

@Serializable
enum class BackupCategory {
    LIGHTNING_CONNECTIONS,
    BLOCKTANK,
    LDK_ACTIVITY,
    WALLET,
    SETTINGS,
    WIDGETS,
    METADATA,
    // PROFILE, // descoped in v1, will return in v2
    // CONTACTS, // descoped in v1, will return in v2
}

fun BackupCategory.uiIcon(): Int {
    return when (this) {
        BackupCategory.LIGHTNING_CONNECTIONS -> R.drawable.ic_lightning
        BackupCategory.BLOCKTANK -> R.drawable.ic_note
        BackupCategory.LDK_ACTIVITY -> R.drawable.ic_transfer
        BackupCategory.WALLET -> R.drawable.ic_timer_alt
        BackupCategory.SETTINGS -> R.drawable.ic_settings
        BackupCategory.WIDGETS -> R.drawable.ic_rectangles_two
        BackupCategory.METADATA -> R.drawable.ic_tag
        // BackupCategory.PROFILE -> R.drawable.ic_user // descoped in v1
        // BackupCategory.SLASHTAGS -> R.drawable.ic_users // descoped in v1
    }
}

fun BackupCategory.uiTitle(): Int {
    return when (this) {
        BackupCategory.LIGHTNING_CONNECTIONS -> R.string.settings__backup__category_connections
        BackupCategory.BLOCKTANK -> R.string.settings__backup__category_connection_receipts
        BackupCategory.LDK_ACTIVITY -> R.string.settings__backup__category_transaction_log
        BackupCategory.WALLET -> R.string.settings__backup__category_wallet
        BackupCategory.SETTINGS -> R.string.settings__backup__category_settings
        BackupCategory.WIDGETS -> R.string.settings__backup__category_widgets
        BackupCategory.METADATA -> R.string.settings__backup__category_tags
        // BackupCategory.PROFILE -> R.string.settings__backup__category_profile // descoped in v1
        // BackupCategory.SLASHTAGS -> R.string.settings__backup__category_contacts // descoped in v1
    }
}
