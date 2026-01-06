package to.bitkit.ui.paykit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.bitkit.paykit.models.Subscription
import to.bitkit.paykit.viewmodels.SubscriptionsViewModel
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SubscriptionDetailScreen(
    subscriptionId: String,
    onNavigateBack: () -> Unit,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val subscription = remember(subscriptions, subscriptionId) {
        subscriptions.firstOrNull { it.id == subscriptionId }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    ScreenColumn {
        AppTopBar(
            titleText = "Subscription Details", // TODO: Localize via Transifex
            onBackClick = onNavigateBack,
            actions = {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            },
        )

        if (subscription == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Subscription not found")
            }
        } else {
            SubscriptionDetailContent(
                subscription = subscription,
                onToggleActive = { viewModel.toggleActive(subscription) },
            )
        }
    }

    if (showDeleteConfirm && subscription != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Subscription?") },
            text = { Text("Are you sure you want to delete this subscription? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSubscription(subscription)
                        showDeleteConfirm = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SubscriptionDetailContent(
    subscription: Subscription,
    onToggleActive: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = subscription.providerName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Switch(checked = subscription.isActive, onCheckedChange = { onToggleActive() })
                }

                if (subscription.description.isNotBlank()) {
                    Text(
                        text = subscription.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Payment Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                HorizontalDivider()
                DetailRow("Amount", "${subscription.amountSats} ${subscription.currency}")
                DetailRow("Frequency", subscription.frequency.replaceFirstChar { it.uppercase() })
                DetailRow("Method", subscription.methodId)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Provider", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                HorizontalDivider()
                DetailRow("Pubkey", subscription.providerPubkey.take(24) + "...")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                HorizontalDivider()
                DetailRow("Total Payments", subscription.paymentCount.toString())
                DetailRow("Created", dateFormat.format(Date(subscription.createdAt)))
                subscription.lastPaymentAt?.let {
                    DetailRow("Last Payment", dateFormat.format(Date(it)))
                }
                subscription.nextPaymentAt?.let {
                    DetailRow("Next Due", dateFormat.format(Date(it)))
                }
            }
        }

        if (subscription.lastPaymentHash != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Last Payment Info",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    HorizontalDivider()
                    subscription.lastPaymentHash?.let {
                        DetailRow("Payment Hash", it.take(24) + "...")
                    }
                    subscription.lastPreimage?.let {
                        DetailRow("Preimage", it.take(24) + "...")
                    }
                    subscription.lastFeeSats?.let {
                        DetailRow("Fee", "$it sats")
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
