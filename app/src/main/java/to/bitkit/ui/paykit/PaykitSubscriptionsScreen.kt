package to.bitkit.ui.paykit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.bitkit.paykit.models.Subscription
import to.bitkit.paykit.storage.SentProposal
import to.bitkit.paykit.viewmodels.SubscriptionsUiState
import to.bitkit.paykit.viewmodels.SubscriptionsViewModel
import to.bitkit.paykit.workers.DiscoveredSubscriptionProposal
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaykitSubscriptionsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSubscriptionDetail: (String) -> Unit = {},
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showContactPicker by remember { mutableStateOf(false) }
    var selectedRecipientPubkey by remember { mutableStateOf("") }

    LaunchedEffect(uiState.sendSuccess) {
        if (uiState.sendSuccess) {
            showCreateDialog = false
            selectedRecipientPubkey = ""
            viewModel.clearSendSuccess()
        }
    }

    ScreenColumn {
        AppTopBar(
            titleText = "Subscriptions", // TODO: Localize via Transifex
            onBackClick = onNavigateBack,
            actions = {
                IconButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("subscriptions_create_button"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Subscription")
                }
            },
        )

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("My Subscriptions") },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    viewModel.loadIncomingProposals()
                },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Proposals")
                        if (uiState.incomingProposals.isNotEmpty()) {
                            Spacer(Modifier.width(4.dp))
                            Badge { Text("${uiState.incomingProposals.size}") }
                        }
                    }
                },
                modifier = Modifier.testTag("subscriptions_tab_proposals"),
            )
            Tab(
                selected = selectedTab == 2,
                onClick = {
                    selectedTab = 2
                    viewModel.loadSentProposals()
                },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sent")
                        if (uiState.sentProposals.isNotEmpty()) {
                            Spacer(Modifier.width(4.dp))
                            Badge { Text("${uiState.sentProposals.size}") }
                        }
                    }
                },
                modifier = Modifier.testTag("subscriptions_tab_sent"),
            )
        }

        when (selectedTab) {
            0 -> SubscriptionsTab(
                subscriptions = subscriptions,
                isLoading = isLoading,
                onNavigateToDetail = onNavigateToSubscriptionDetail,
                onToggleActive = { viewModel.toggleActive(it) },
            )
            1 -> ProposalsTab(
                uiState = uiState,
                onAccept = { proposal, enableAutopay, limit ->
                    viewModel.acceptProposal(proposal, enableAutopay, limit)
                },
                onDecline = { viewModel.declineProposal(it) },
            )
            2 -> SentProposalsTab(
                uiState = uiState,
                onCancelProposal = { viewModel.cancelSentProposal(it) },
                onCleanupOrphaned = { viewModel.cleanupOrphanedProposals() },
                onClearCleanupResult = { viewModel.clearCleanupResult() },
            )
        }
    }

    if (showCreateDialog) {
        CreateSubscriptionDialog(
            onDismiss = {
                showCreateDialog = false
                selectedRecipientPubkey = ""
            },
            onSend = { recipient, amount, frequency, description, enableAutopay, limit ->
                viewModel.sendSubscriptionProposal(recipient, amount, frequency, description, enableAutopay, limit)
            },
            isSending = uiState.isSending,
            error = uiState.error,
            onClearError = { viewModel.clearError() },
            initialRecipientPubkey = selectedRecipientPubkey,
            onShowContactPicker = { showContactPicker = true },
        )
    }

    if (showContactPicker) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showContactPicker = false },
        ) {
            ContactPickerSheet(
                onBack = { showContactPicker = false },
                onContactSelected = { contact ->
                    selectedRecipientPubkey = contact.publicKeyZ32
                    showContactPicker = false
                },
            )
        }
    }
}

@Composable
private fun SubscriptionsTab(
    subscriptions: List<Subscription>,
    isLoading: Boolean,
    onNavigateToDetail: (String) -> Unit,
    onToggleActive: (Subscription) -> Unit,
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (subscriptions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No subscriptions yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(subscriptions, key = { it.id }) { subscription ->
                SubscriptionRow(
                    subscription = subscription,
                    onClick = { onNavigateToDetail(subscription.id) },
                    onToggleActive = { onToggleActive(subscription) },
                )
            }
        }
    }
}

@Composable
private fun ProposalsTab(
    uiState: SubscriptionsUiState,
    onAccept: (DiscoveredSubscriptionProposal, Boolean, Long?) -> Unit,
    onDecline: (DiscoveredSubscriptionProposal) -> Unit,
) {
    if (uiState.isLoadingProposals) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (uiState.incomingProposals.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No incoming proposals",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.incomingProposals, key = { it.subscriptionId }) { proposal ->
                ProposalRow(
                    proposal = proposal,
                    isAccepting = uiState.isAccepting,
                    isDeclining = uiState.isDeclining,
                    onAccept = onAccept,
                    onDecline = { onDecline(proposal) },
                )
            }
        }
    }
}

@Composable
private fun SentProposalsTab(
    uiState: SubscriptionsUiState,
    onCancelProposal: (SentProposal) -> Unit,
    onCleanupOrphaned: () -> Unit,
    onClearCleanupResult: () -> Unit,
) {
    LaunchedEffect(uiState.cleanupResult) {
        if (uiState.cleanupResult != null) {
            kotlinx.coroutines.delay(3000)
            onClearCleanupResult()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Cleanup button row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (uiState.cleanupResult != null) {
                Text(
                    text = uiState.cleanupResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
            }
            TextButton(
                onClick = onCleanupOrphaned,
                enabled = !uiState.isCleaningUp,
            ) {
                if (uiState.isCleaningUp) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Cleanup Orphaned")
            }
        }

        if (uiState.sentProposals.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No sent proposals",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.sentProposals, key = { it.id }) { proposal ->
                    SentProposalRow(
                        proposal = proposal,
                        onClickCancel = { onCancelProposal(proposal) },
                        isDeleting = uiState.isDeletingSentProposal,
                    )
                }
            }
        }
    }
}

@Composable
private fun SentProposalRow(
    proposal: SentProposal,
    onClickCancel: () -> Unit,
    isDeleting: Boolean,
) {
    var showCancelDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("sent_proposal_row_${proposal.id}"),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "To: ${proposal.recipientPubkey.take(12)}...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "${proposal.amountSats} sats / ${proposal.frequency}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (proposal.status.name == "PENDING") {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = { showCancelDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel proposal",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            proposal.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Status: ${proposal.status.name}",
                style = MaterialTheme.typography.labelSmall,
                color = when (proposal.status.name) {
                    "ACCEPTED" -> MaterialTheme.colorScheme.primary
                    "EXPIRED" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Proposal?") },
            text = { Text("This will delete the proposal from the homeserver. The recipient will no longer see it.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        onClickCancel()
                    },
                ) {
                    Text("Cancel Proposal", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Keep")
                }
            },
        )
    }
}

@Composable
fun SubscriptionRow(
    subscription: Subscription,
    onClick: () -> Unit,
    onToggleActive: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("subscription_row_${subscription.id}"),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subscription.providerName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = subscription.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${subscription.amountSats} ${subscription.currency} / ${subscription.frequency}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = subscription.isActive,
                    onCheckedChange = { onToggleActive() },
                )
            }

            if (subscription.paymentCount > 0) {
                Text(
                    text = "${subscription.paymentCount} payments made",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProposalRow(
    proposal: DiscoveredSubscriptionProposal,
    isAccepting: Boolean,
    isDeclining: Boolean,
    onAccept: (DiscoveredSubscriptionProposal, Boolean, Long?) -> Unit,
    onDecline: () -> Unit,
) {
    var showAcceptDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("proposal_row_${proposal.subscriptionId}"),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "From: ${proposal.providerPubkey.take(16)}...",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            proposal.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${proposal.amountSats} sats / ${proposal.frequency}",
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    enabled = !isDeclining && !isAccepting,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("proposal_decline_${proposal.subscriptionId}"),
                ) {
                    if (isDeclining) {
                        CircularProgressIndicator(Modifier.size(16.dp))
                    } else {
                        Text("Decline")
                    }
                }
                Button(
                    onClick = { showAcceptDialog = true },
                    enabled = !isAccepting && !isDeclining,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("proposal_accept_${proposal.subscriptionId}"),
                ) {
                    if (isAccepting) {
                        CircularProgressIndicator(Modifier.size(16.dp))
                    } else {
                        Text("Accept")
                    }
                }
            }
        }
    }

    if (showAcceptDialog) {
        AcceptProposalDialog(
            proposal = proposal,
            onDismiss = { showAcceptDialog = false },
            onAccept = { enableAutopay, limit ->
                showAcceptDialog = false
                onAccept(proposal, enableAutopay, limit)
            },
        )
    }
}

@Composable
private fun AcceptProposalDialog(
    proposal: DiscoveredSubscriptionProposal,
    onDismiss: () -> Unit,
    onAccept: (Boolean, Long?) -> Unit,
) {
    var enableAutopay by remember { mutableStateOf(false) }
    var autopayLimit by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accept Subscription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("You are accepting a subscription of ${proposal.amountSats} sats/${proposal.frequency}.")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Enable Autopay")
                    Switch(checked = enableAutopay, onCheckedChange = { enableAutopay = it })
                }

                if (enableAutopay) {
                    OutlinedTextField(
                        value = autopayLimit,
                        onValueChange = { autopayLimit = it.filter { c -> c.isDigit() } },
                        label = { Text("Spending limit (sats)") },
                        placeholder = { Text("${proposal.amountSats}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val limit = autopayLimit.toLongOrNull() ?: if (enableAutopay) proposal.amountSats else null
                onAccept(enableAutopay, limit)
            }) {
                Text("Accept")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun CreateSubscriptionDialog(
    onDismiss: () -> Unit,
    onSend: (String, Long, String, String?, Boolean, Long?) -> Unit,
    isSending: Boolean,
    error: String?,
    onClearError: () -> Unit,
    initialRecipientPubkey: String = "",
    onShowContactPicker: () -> Unit = {},
) {
    var recipientPubkey by remember(initialRecipientPubkey) { mutableStateOf(initialRecipientPubkey) }
    var amountSats by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("monthly") }
    var description by remember { mutableStateOf("") }
    var enableAutopay by remember { mutableStateOf(false) }
    var autopayLimit by remember { mutableStateOf("") }

    val frequencyOptions = listOf("daily", "weekly", "monthly", "yearly")

    LaunchedEffect(error) {
        if (error != null) {
            kotlinx.coroutines.delay(10_000) // Keep error visible longer for debugging
            onClearError()
        }
    }

    // Update recipientPubkey when initialRecipientPubkey changes (from contact picker)
    LaunchedEffect(initialRecipientPubkey) {
        if (initialRecipientPubkey.isNotBlank()) {
            recipientPubkey = initialRecipientPubkey
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text("Create Subscription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Recipient", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onShowContactPicker) {
                        Text("Contacts")
                    }
                }
                OutlinedTextField(
                    value = recipientPubkey,
                    onValueChange = { recipientPubkey = it.trim() },
                    label = { Text("Pubkey (z32)") },
                    placeholder = { Text("Enter or paste pubkey") },
                    singleLine = true,
                    enabled = !isSending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_sub_recipient"),
                )

                OutlinedTextField(
                    value = amountSats,
                    onValueChange = { amountSats = it.filter { c -> c.isDigit() } },
                    label = { Text("Amount (sats)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isSending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_sub_amount"),
                )

                Text("Frequency", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    frequencyOptions.forEach { option ->
                        FilterChip(
                            selected = frequency == option,
                            onClick = { frequency = option },
                            label = { Text(option.replaceFirstChar { it.uppercase() }) },
                            enabled = !isSending,
                        )
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    enabled = !isSending,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Request Autopay")
                    Switch(checked = enableAutopay, onCheckedChange = { enableAutopay = it }, enabled = !isSending)
                }

                if (enableAutopay) {
                    OutlinedTextField(
                        value = autopayLimit,
                        onValueChange = { autopayLimit = it.filter { c -> c.isDigit() } },
                        label = { Text("Limit per period (sats)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !isSending,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountSats.toLongOrNull() ?: return@Button
                    val limit = autopayLimit.toLongOrNull()
                    onSend(
                        recipientPubkey,
                        amount,
                        frequency,
                        description.takeIf { it.isNotBlank() },
                        enableAutopay,
                        limit,
                    )
                },
                enabled = !isSending && recipientPubkey.isNotBlank() && amountSats.toLongOrNull() != null,
                modifier = Modifier.testTag("create_sub_send"),
            ) {
                if (isSending) {
                    CircularProgressIndicator(Modifier.size(16.dp))
                } else {
                    Text("Send Proposal")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) { Text("Cancel") }
        },
    )
}
