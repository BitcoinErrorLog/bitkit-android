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
import androidx.compose.ui.res.stringResource
import to.bitkit.R
import to.bitkit.ui.components.BodyM
import to.bitkit.ui.components.Subtitle
import to.bitkit.ui.components.Title
import to.bitkit.ui.theme.AppSwitchDefaults
import to.bitkit.ui.theme.Colors

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
            titleText = stringResource(R.string.paykit__subscription_details), // TODO: Localize via Transifex
            onBackClick = onNavigateBack,
            actions = {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            },
        )

        if (subscription == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.paykit__subscription_not_found))
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
            title = { Text(stringResource(R.string.paykit__delete_subscription)) },
            text = { Text(stringResource(R.string.paykit__are_you_sure_you_want_to_delete_this_subscription)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSubscription(subscription)
                        showDeleteConfirm = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Colors.Red),
                ) {
                    Text(stringResource(R.string.paykit__delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.paykit__cancel)) }
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
                    Title(
                        text = subscription.providerName,
                    )
                    Switch(
                        checked = subscription.isActive,
                        onCheckedChange = { onToggleActive() },
                        colors = AppSwitchDefaults.colors,
                    )
                }

                if (subscription.description.isNotBlank()) {
                    BodyM(
                        text = subscription.description,
                        color = Colors.White64,
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.paykit__payment_details), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                HorizontalDivider()
                DetailRow(stringResource(R.string.paykit__amount), "${subscription.amountSats} ${subscription.currency}")
                DetailRow(stringResource(R.string.paykit__frequency), subscription.frequency.replaceFirstChar { it.uppercase() })
                DetailRow(stringResource(R.string.paykit__method), subscription.methodId)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.paykit__provider), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                HorizontalDivider()
                DetailRow(stringResource(R.string.paykit__pubkey), subscription.providerPubkey.take(24) + "...")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.paykit__history), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                HorizontalDivider()
                DetailRow(stringResource(R.string.paykit__total_payments), subscription.paymentCount.toString())
                DetailRow(stringResource(R.string.paykit__created), dateFormat.format(Date(subscription.createdAt)))
                subscription.lastPaymentAt?.let {
                    DetailRow(stringResource(R.string.paykit__last_payment), dateFormat.format(Date(it)))
                }
                subscription.nextPaymentAt?.let {
                    DetailRow(stringResource(R.string.paykit__next_due), dateFormat.format(Date(it)))
                }
            }
        }

        if (subscription.lastPaymentHash != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Subtitle(
                        stringResource(R.string.paykit__last_payment_info),
                    )
                    HorizontalDivider()
                    subscription.lastPaymentHash?.let {
                        DetailRow(stringResource(R.string.paykit__payment_hash), it.take(24) + "...")
                    }
                    subscription.lastPreimage?.let {
                        DetailRow(stringResource(R.string.paykit__preimage), it.take(24) + "...")
                    }
                    subscription.lastFeeSats?.let {
                        DetailRow(stringResource(R.string.paykit__fee), "$it sats")
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
        Text(label, color = Colors.White64)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
