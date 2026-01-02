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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Stats Section
                item {
                    StatsSection(
                        totalSent = totalSent,
                        totalReceived = totalReceived,
                        contactCount = contactCount,
                        pendingCount = pendingCount,
                    )
                }

                // Quick Access Section
                item {
                    QuickAccessSection(
                        autoPayEnabled = autoPayEnabled,
                        activeSubscriptions = activeSubscriptions,
                        onNavigateToAutoPay = onNavigateToAutoPay,
                        onNavigateToSubscriptions = onNavigateToSubscriptions,
                    )
                }

                // Payments Section
                item {
                    PaymentsSection(
                        pendingRequests = pendingRequests,
                        contactCount = contactCount,
                        onNavigateToPaymentRequests = onNavigateToPaymentRequests,
                        onNavigateToNoisePayment = onNavigateToNoisePayment,
                        onNavigateToContacts = onNavigateToContacts,
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
private fun StatsSection(
    totalSent: Long,
    totalReceived: Long,
    contactCount: Int,
    pendingCount: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title(text = "Overview")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                title = "Total Sent", // TODO: Localize via Transifex
                value = formatSats(totalSent),
                icon = Icons.Default.ArrowUpward,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "Total Received", // TODO: Localize via Transifex
                value = formatSats(totalReceived),
                icon = Icons.Default.ArrowDownward,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                title = "Contacts", // TODO: Localize via Transifex
                value = contactCount.toString(),
                icon = Icons.Default.People,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "Pending", // TODO: Localize via Transifex
                value = pendingCount.toString(),
                icon = Icons.Default.Schedule,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickAccessSection(
    autoPayEnabled: Boolean,
    activeSubscriptions: Int,
    onNavigateToAutoPay: () -> Unit,
    onNavigateToSubscriptions: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title(text = "Quick Access")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickAccessCard(
                title = "Auto-Pay", // TODO: Localize via Transifex
                subtitle = if (autoPayEnabled) "ON" else "OFF",
                icon = Icons.Default.Repeat,
                color = Colors.Brand,
                onClick = onNavigateToAutoPay,
                modifier = Modifier.weight(1f),
            )
            QuickAccessCard(
                title = "Subscriptions", // TODO: Localize via Transifex
                subtitle = if (activeSubscriptions > 0) "$activeSubscriptions active" else "Manage",
                icon = Icons.Default.CalendarToday,
                color = MaterialTheme.colorScheme.primary,
                onClick = onNavigateToSubscriptions,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PaymentsSection(
    pendingRequests: Int,
    contactCount: Int,
    onNavigateToPaymentRequests: () -> Unit,
    onNavigateToNoisePayment: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToContactDiscovery: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title(text = "Payments")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickAccessCard(
                title = "Payment Requests", // TODO: Localize via Transifex
                subtitle = if (pendingRequests > 0) "$pendingRequests pending" else "View all",
                icon = Icons.Default.Notifications,
                color = MaterialTheme.colorScheme.primary,
                onClick = onNavigateToPaymentRequests,
                modifier = Modifier.weight(1f),
            )
            QuickAccessCard(
                title = "Noise Payment", // TODO: Localize via Transifex
                subtitle = "Private transfers", // TODO: Localize via Transifex
                icon = Icons.Default.Waves,
                color = MaterialTheme.colorScheme.secondary,
                onClick = onNavigateToNoisePayment,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickAccessCard(
                title = "Contacts", // TODO: Localize via Transifex
                subtitle = if (contactCount > 0) "$contactCount saved" else "Manage",
                icon = Icons.Default.Person,
                color = MaterialTheme.colorScheme.tertiary,
                onClick = onNavigateToContacts,
                modifier = Modifier.weight(1f),
            )
            QuickAccessCard(
                title = "Discover", // TODO: Localize via Transifex
                subtitle = "Find contacts", // TODO: Localize via Transifex
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
                color = MaterialTheme.colorScheme.secondary,
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
