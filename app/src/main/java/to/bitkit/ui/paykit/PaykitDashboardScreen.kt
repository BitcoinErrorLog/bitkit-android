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
            titleText = "Paykit Dashboard", // TODO: Localize via Transifex
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
        Title(text = "Overview")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                title = "Total Sent",
                value = formatSats(totalSent),
                icon = Icons.Default.ArrowUpward,
                color = Colors.Red,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "Total Received",
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
                title = "Contacts",
                value = contactCount.toString(),
                icon = Icons.Default.People,
                color = Colors.Blue,
                onClick = onNavigateToContacts,
                modifier = Modifier.weight(1f),
            )
            TappableStatCard(
                title = "Subscriptions",
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
        Title(text = "Actions")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickAccessCard(
                title = "Auto-Pay",
                subtitle = if (autoPayEnabled) "ON" else "OFF",
                icon = Icons.Default.Repeat,
                color = Colors.Brand,
                onClick = onNavigateToAutoPay,
                modifier = Modifier.weight(1f),
            )
            QuickAccessCard(
                title = "Payment Requests",
                subtitle = if (pendingRequests > 0) "$pendingRequests pending" else "View all",
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
                title = "Noise Payment",
                subtitle = "Private transfers",
                icon = Icons.Default.Waves,
                color = Colors.Blue,
                onClick = onNavigateToNoisePayment,
                modifier = Modifier.weight(1f),
            )
            QuickAccessCard(
                title = "Discover",
                subtitle = "Find contacts",
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
        Title(text = "Identity & Security")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickAccessCard(
                title = "Endpoints", // TODO: Localize via Transifex
                subtitle = if (publishedMethodsCount > 0) "$publishedMethodsCount published" else "Setup",
                icon = Icons.Default.Link,
                color = Colors.Blue,
                onClick = onNavigateToPrivateEndpoints,
                modifier = Modifier.weight(1f),
            )
            QuickAccessCard(
                title = "Key Rotation", // TODO: Localize via Transifex
                subtitle = "Security", // TODO: Localize via Transifex
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
            .clickable(onClick = onClick),
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
                            Colors.Brand.copy(alpha = 0.2f)
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPubkyRingInstalled) Icons.Default.Shield else Icons.Default.QrCode,
                    contentDescription = null,
                    tint = if (isPubkyRingInstalled) MaterialTheme.colorScheme.primary else Colors.Brand,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isPubkyRingInstalled) "Pubky-ring Connected" else "Connect Pubky-ring",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (isPubkyRingInstalled) {
                        "Pubky-ring is available on this device"
                    } else {
                        "Use QR code to authenticate from another device"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Title(text = "Recent Activity")
            TextButton(onClick = onNavigateToReceipts) {
                Text("See All")
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No recent activity",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Your Paykit transactions will appear here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Text(
                    text = receipt.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = receipt.paymentMethod,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${receipt.amountSats} sats",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (receipt.direction == to.bitkit.paykit.models.PaymentDirection.SENT) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Text(
                    text = receipt.status.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
