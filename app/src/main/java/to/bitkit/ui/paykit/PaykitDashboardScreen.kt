package to.bitkit.ui.paykit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import to.bitkit.ui.shared.modifiers.clickableAlpha
import to.bitkit.ui.components.Caption
import to.bitkit.ui.components.BodySSB
import to.bitkit.ui.components.BodyMSB
import to.bitkit.ui.components.BodyMB
import to.bitkit.ui.components.BodyM
import to.bitkit.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.bitkit.paykit.viewmodels.DashboardViewModel
import to.bitkit.ui.components.Title
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn
import to.bitkit.ui.theme.Colors
import to.bitkit.ui.components.HorizontalSpacer
import to.bitkit.ui.components.VerticalSpacer

@Composable
fun PaykitDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReceipts: () -> Unit = {},
    onNavigateToContacts: () -> Unit = {},
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToAutoPay: () -> Unit = {},
    onNavigateToPaymentRequests: () -> Unit = {},
    onNavigateToNoisePayment: () -> Unit = {},
    onNavigateToContactDiscovery: () -> Unit = {},
    onNavigateToPrivateEndpoints: () -> Unit = {},
    onNavigateToRotationSettings: () -> Unit = {},
    onNavigateToPubkyRingAuth: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val recentReceipts by viewModel.recentReceipts.collectAsStateWithLifecycle()
    val contactCount by viewModel.contactCount.collectAsStateWithLifecycle()
    val totalSent by viewModel.totalSent.collectAsStateWithLifecycle()
    val totalReceived by viewModel.totalReceived.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val autoPayEnabled by viewModel.autoPayEnabled.collectAsStateWithLifecycle()
    val activeSubscriptions by viewModel.activeSubscriptions.collectAsStateWithLifecycle()
    val pendingRequests by viewModel.pendingRequests.collectAsStateWithLifecycle()
    val publishedMethodsCount by viewModel.publishedMethodsCount.collectAsStateWithLifecycle()

    val isPubkyRingInstalled by viewModel.isPubkyRingInstalled.collectAsStateWithLifecycle()

    ScreenColumn {
        AppTopBar(
            titleText = stringResource(R.string.paykit__paykit_dashboard), // TODO: Localize via Transifex
            onBackClick = onNavigateBack,
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Colors.Brand)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Overview Section (stats with tappable contacts/subscriptions)
                item {
                    OverviewSection(
                        totalSent = totalSent,
                        totalReceived = totalReceived,
                        contactCount = contactCount,
                        activeSubscriptions = activeSubscriptions,
                        onNavigateToContacts = onNavigateToContacts,
                        onNavigateToSubscriptions = onNavigateToSubscriptions,
                    )
                }

                // Actions Section (consolidated)
                item {
                    ActionsSection(
                        autoPayEnabled = autoPayEnabled,
                        pendingRequests = pendingRequests,
                        onNavigateToAutoPay = onNavigateToAutoPay,
                        onNavigateToPaymentRequests = onNavigateToPaymentRequests,
                        onNavigateToNoisePayment = onNavigateToNoisePayment,
                        onNavigateToContactDiscovery = onNavigateToContactDiscovery,
                    )
                }

                // Identity & Security Section
                item {
                    IdentitySection(
                        publishedMethodsCount = publishedMethodsCount,
                        isPubkyRingInstalled = isPubkyRingInstalled,
                        onNavigateToPrivateEndpoints = onNavigateToPrivateEndpoints,
                        onNavigateToRotationSettings = onNavigateToRotationSettings,
                        onNavigateToPubkyRingAuth = onNavigateToPubkyRingAuth,
                    )
                }

                // Recent Activity Section
                item {
                    RecentActivitySection(
                        recentReceipts = recentReceipts,
                        onNavigateToReceipts = onNavigateToReceipts,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewSection(
    totalSent: Long,
    totalReceived: Long,
    contactCount: Int,
    activeSubscriptions: Int,
    onNavigateToContacts: () -> Unit,
    onNavigateToSubscriptions: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title(text = stringResource(R.string.paykit__overview))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                title = stringResource(R.string.paykit__total_sent),
                value = formatSats(totalSent),
                icon = Icons.Default.ArrowUpward,
                color = Colors.Red,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = stringResource(R.string.paykit__total_received),
                value = formatSats(totalReceived),
                icon = Icons.Default.ArrowDownward,
                color = Colors.Green,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TappableStatCard(
                title = stringResource(R.string.paykit__contacts),
                value = contactCount.toString(),
                icon = Icons.Default.People,
                color = Colors.Blue,
                onClick = onNavigateToContacts,
                modifier = Modifier.weight(1f),
            )
            TappableStatCard(
                title = stringResource(R.string.paykit__subscriptions),
                value = activeSubscriptions.toString(),
                icon = Icons.Default.CalendarToday,
                color = Colors.Purple,
                onClick = onNavigateToSubscriptions,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActionsSection(
    autoPayEnabled: Boolean,
    pendingRequests: Int,
    onNavigateToAutoPay: () -> Unit,
    onNavigateToPaymentRequests: () -> Unit,
    onNavigateToNoisePayment: () -> Unit,
    onNavigateToContactDiscovery: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title(text = stringResource(R.string.paykit__actions))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickAccessCard(
                title = stringResource(R.string.paykit__auto_pay),
                subtitle = if (autoPayEnabled) "ON" else stringResource(R.string.paykit__off),
                icon = Icons.Default.Repeat,
                color = MaterialTheme.colorScheme.primary,
                onClick = onNavigateToAutoPay,
                modifier = Modifier.weight(1f),
            )
            QuickAccessCard(
                title = stringResource(R.string.paykit__payment_requests),
                subtitle = if (pendingRequests > 0) "$pendingRequests pending" else stringResource(R.string.paykit__view_all),
                icon = Icons.Default.Notifications,
                color = Colors.Yellow,
                onClick = onNavigateToPaymentRequests,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickAccessCard(
                title = stringResource(R.string.paykit__noise_payment),
                subtitle = stringResource(R.string.paykit__private_transfers),
                icon = Icons.Default.Waves,
                color = Colors.Blue,
                onClick = onNavigateToNoisePayment,
                modifier = Modifier.weight(1f),
            )
            QuickAccessCard(
                title = stringResource(R.string.paykit__discover),
                subtitle = stringResource(R.string.paykit__find_contacts),
                icon = Icons.Default.Search,
                color = Colors.Green,
                onClick = onNavigateToContactDiscovery,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun IdentitySection(
    publishedMethodsCount: Int,
    isPubkyRingInstalled: Boolean,
    onNavigateToPrivateEndpoints: () -> Unit,
    onNavigateToRotationSettings: () -> Unit,
    onNavigateToPubkyRingAuth: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title(text = stringResource(R.string.paykit__identity_security))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickAccessCard(
                title = stringResource(R.string.paykit__endpoints), // TODO: Localize via Transifex
                subtitle = if (publishedMethodsCount > 0) "$publishedMethodsCount published" else stringResource(R.string.paykit__setup),
                icon = Icons.Default.Link,
                color = Colors.Blue,
                onClick = onNavigateToPrivateEndpoints,
                modifier = Modifier.weight(1f),
            )
            QuickAccessCard(
                title = stringResource(R.string.paykit__key_rotation), // TODO: Localize via Transifex
                subtitle = stringResource(R.string.paykit__security), // TODO: Localize via Transifex
                icon = Icons.Default.Key,
                color = Colors.Yellow,
                onClick = onNavigateToRotationSettings,
                modifier = Modifier.weight(1f),
            )
        }

        // Pubky-ring connection card
        PubkyRingConnectionCard(
            isPubkyRingInstalled = isPubkyRingInstalled,
            onClick = onNavigateToPubkyRingAuth,
        )
    }
}

@Composable
private fun PubkyRingConnectionCard(
    isPubkyRingInstalled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickableAlpha { onClick() },
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (isPubkyRingInstalled) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPubkyRingInstalled) Icons.Default.Shield else Icons.Default.QrCode,
                    contentDescription = null,
                    tint = if (isPubkyRingInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalSpacer(16.dp)

            Column(modifier = Modifier.weight(1f)) {
                BodyMB(
                    text = if (isPubkyRingInstalled) stringResource(R.string.paykit__pubky_ring_connected) else stringResource(R.string.paykit__connect_pubky_ring),
                )
                Caption(
                    text = if (isPubkyRingInstalled) {
                        stringResource(R.string.paykit__pubky_ring_is_available_on_this_device)
                    } else {
                        stringResource(R.string.paykit__use_qr_code_to_authenticate_from_another_device)
                    },
                        color = Colors.White64,
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Colors.White64,
            )
        }
    }
}

@Composable
private fun RecentActivitySection(
    recentReceipts: List<to.bitkit.paykit.models.Receipt>,
    onNavigateToReceipts: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Title(text = stringResource(R.string.paykit__recent_activity))
            TextButton(onClick = onNavigateToReceipts) {
                Text(stringResource(R.string.paykit__see_all))
            }
        }

        if (recentReceipts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Colors.White64,
                    )
                    VerticalSpacer(12.dp)
                    BodyM(
                        text = stringResource(R.string.paykit__no_recent_activity),
                            color = Colors.White64,
                    )
                    Caption(
                        text = stringResource(R.string.paykit__your_paykit_transactions_will_appear_here),
                            color = Colors.White64.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            recentReceipts.take(5).forEach { receipt ->
                ReceiptRow(receipt = receipt)
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp),
            )
            Title(
                text = value,
            )
            Caption(
                text = title,
                    color = Colors.White64,
            )
        }
    }
}

@Composable
fun TappableStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp),
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Colors.White64,
                    modifier = Modifier.size(16.dp),
                )
            }
            Title(
                text = value,
            )
            Caption(
                text = title,
                    color = Colors.White64,
            )
        }
    }
}

@Composable
fun QuickAccessCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp),
            )
            BodySSB(
                text = title,
            )
            Caption(
                text = subtitle,
                    color = Colors.White64,
            )
        }
    }
}

@Composable
fun ReceiptRow(receipt: to.bitkit.paykit.models.Receipt) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                BodyMSB(
                    text = receipt.displayName,
                )
                Caption(
                    text = receipt.paymentMethod,
                        color = Colors.White64,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                BodyMSB(
                    text = "${receipt.amountSats} sats",
                        color = if (receipt.direction == to.bitkit.paykit.models.PaymentDirection.SENT) {
                        Colors.Red
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Caption(
                    text = receipt.status.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = Colors.White64,
                )
            }
        }
    }
}

fun formatSats(sats: Long): String {
    return when {
        sats >= 1_000_000 -> String.format("%.2fM", sats / 1_000_000.0)
        sats >= 1_000 -> String.format("%.1fK", sats / 1_000.0)
        else -> sats.toString()
    }
}
