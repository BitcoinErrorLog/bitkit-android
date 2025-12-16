package to.bitkit.ui.paykit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import to.bitkit.paykit.viewmodels.DashboardViewModel
import to.bitkit.ui.components.Title
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn

@Composable
fun PaykitDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReceipts: () -> Unit = {},
    onNavigateToContacts: () -> Unit = {},
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToAutoPay: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val recentReceipts by viewModel.recentReceipts.collectAsState()
    val contactCount by viewModel.contactCount.collectAsState()
    val totalSent by viewModel.totalSent.collectAsState()
    val totalReceived by viewModel.totalReceived.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val autoPayEnabled by viewModel.autoPayEnabled.collectAsState()
    val activeSubscriptions by viewModel.activeSubscriptions.collectAsState()
    val pendingRequests by viewModel.pendingRequests.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadDashboard()
    }

    ScreenColumn {
        AppTopBar(
            titleText = "Paykit Dashboard",
            onBackClick = onNavigateBack
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Stats Section
                item {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            StatCard(
                                title = "Total Sent",
                                value = formatSats(totalSent),
                                icon = Icons.Default.ArrowUpward,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        item {
                            StatCard(
                                title = "Total Received",
                                value = formatSats(totalReceived),
                                icon = Icons.Default.ArrowDownward,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        item {
                            StatCard(
                                title = "Contacts",
                                value = contactCount.toString(),
                                icon = Icons.Default.People,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        item {
                            StatCard(
                                title = "Pending",
                                value = pendingCount.toString(),
                                icon = Icons.Default.Schedule,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                // Quick Access Section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Title(text = "Quick Access")

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (autoPayEnabled) {
                                item {
                                    QuickAccessCard(
                                        title = "Auto-Pay",
                                        subtitle = "ON",
                                        icon = Icons.Default.Repeat,
                                        onClick = onNavigateToAutoPay
                                    )
                                }
                            }

                            if (activeSubscriptions > 0) {
                                item {
                                    QuickAccessCard(
                                        title = "Subscriptions",
                                        subtitle = "$activeSubscriptions active",
                                        icon = Icons.Default.CalendarToday,
                                        onClick = onNavigateToSubscriptions
                                    )
                                }
                            }

                            if (pendingRequests > 0) {
                                item {
                                    QuickAccessCard(
                                        title = "Requests",
                                        subtitle = "$pendingRequests pending",
                                        icon = Icons.Default.Notifications,
                                        onClick = { /* TODO: Navigate to payment requests */ }
                                    )
                                }
                            }
                        }
                    }
                }

                // Recent Activity Section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Title(text = "Recent Activity")
                            TextButton(onClick = onNavigateToReceipts) {
                                Text("See All")
                            }
                        }

                        if (recentReceipts.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No recent activity",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun QuickAccessCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
