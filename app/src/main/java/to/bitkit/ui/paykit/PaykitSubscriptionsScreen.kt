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
import androidx.compose.ui.res.stringResource
import to.bitkit.R
import to.bitkit.ui.components.BodyM
import to.bitkit.ui.components.BodyMSB
import to.bitkit.ui.components.Caption
import to.bitkit.ui.theme.AppSwitchDefaults
import to.bitkit.ui.theme.Colors

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
            titleText = stringResource(R.string.paykit__subscriptions), // TODO: Localize via Transifex
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
                text = { Text(stringResource(R.string.paykit__my_subscriptions)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    viewModel.loadIncomingProposals()
                },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.paykit__proposals))
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
                        Text(stringResource(R.string.paykit__sent))
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
            CircularProgressIndicator(color = Colors.Brand)
        }
    } else if (subscriptions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BodyM(
                text = stringResource(R.string.paykit__no_subscriptions_yet),
                    color = Colors.White64,
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
            CircularProgressIndicator(color = Colors.Brand)
        }
    } else if (uiState.incomingProposals.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BodyM(
                text = stringResource(R.string.paykit__no_incoming_proposals),
                    color = Colors.White64,
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
                Caption(
                    text = uiState.cleanupResult,
                        color = Colors.White64,
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
                Text(stringResource(R.string.paykit__cleanup_orphaned))
            }
        }

        if (uiState.sentProposals.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BodyM(
                    text = stringResource(R.string.paykit__no_sent_proposals),
                        color = Colors.White64,
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
                    BodyMSB(
                        text = "To: ${proposal.recipientPubkey.take(12)}...",
                    )
                    Caption(
                        text = "${proposal.amountSats} sats / ${proposal.frequency}",
                            color = Colors.White64,
                    )
                }
                if (proposal.status.name == stringResource(R.string.paykit__pending_upper)) {
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
                                tint = Colors.Red,
                            )
                        }
                    }
                }
            }
            proposal.description?.let {
                Caption(
                    text = it,
                        color = Colors.White64,
                )
            }
            Text(
                text = "Status: ${proposal.status.name}",
                style = MaterialTheme.typography.labelSmall,
                color = when (proposal.status.name) {
                    stringResource(R.string.paykit__accepted) -> MaterialTheme.colorScheme.primary
                    stringResource(R.string.paykit__expired) -> Colors.Red
                    else -> Colors.White64
                },
            )
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(R.string.paykit__cancel_proposal_question)) },
            text = { Text(stringResource(R.string.paykit__this_will_delete_the_proposal_from_the_homeserver)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        onClickCancel()
                    },
                ) {
                    Text(stringResource(R.string.paykit__cancel_proposal), color = Colors.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(R.string.paykit__keep))
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
                    BodyM(
                        text = subscription.providerName,
                    )
                    Caption(
                        text = subscription.description,
                            color = Colors.White64,
                    )
                    Caption(
                        text = "${subscription.amountSats} ${subscription.currency} / ${subscription.frequency}",
                            color = Colors.White64,
                    )
                }
                Switch(
                    checked = subscription.isActive,
                    onCheckedChange = { onToggleActive() },
                    colors = AppSwitchDefaults.colors,
                )
            }

            if (subscription.paymentCount > 0) {
                Caption(
                    text = "${subscription.paymentCount} payments made",
                        color = Colors.White64,
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
            BodyM(
                text = "From: ${proposal.providerPubkey.take(16)}...",
            )
            proposal.description?.let {
                Caption(
                    text = it,
                        color = Colors.White64,
                )
            }
            BodyM(
                text = "${proposal.amountSats} sats / ${proposal.frequency}",
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
                        CircularProgressIndicator(Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(stringResource(R.string.paykit__decline))
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
                        CircularProgressIndicator(Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(stringResource(R.string.paykit__accept))
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
        title = { Text(stringResource(R.string.paykit__accept_subscription)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("You are accepting a subscription of ${proposal.amountSats} sats/${proposal.frequency}.")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.paykit__enable_autopay))
                    Switch(checked = enableAutopay, onCheckedChange = { enableAutopay = it }, colors = AppSwitchDefaults.colors)
                }

                if (enableAutopay) {
                    OutlinedTextField(
                        value = autopayLimit,
                        onValueChange = { autopayLimit = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.paykit__spending_limit_sats)) },
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
                Text(stringResource(R.string.paykit__accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.paykit__cancel)) }
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
        title = { Text(stringResource(R.string.paykit__create_subscription)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.paykit__recipient), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onShowContactPicker) {
                        Text(stringResource(R.string.paykit__contacts))
                    }
                }
                OutlinedTextField(
                    value = recipientPubkey,
                    onValueChange = { recipientPubkey = it.trim() },
                    label = { Text(stringResource(R.string.paykit__pubkey_z32)) },
                    placeholder = { Text(stringResource(R.string.paykit__enter_or_paste_pubkey)) },
                    singleLine = true,
                    enabled = !isSending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_sub_recipient"),
                )

                OutlinedTextField(
                    value = amountSats,
                    onValueChange = { amountSats = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.paykit__amount_sats)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isSending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_sub_amount"),
                )

                Text(stringResource(R.string.paykit__frequency), style = MaterialTheme.typography.bodySmall)
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
                    label = { Text(stringResource(R.string.paykit__description_optional)) },
                    enabled = !isSending,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.paykit__request_autopay))
                    Switch(checked = enableAutopay, onCheckedChange = { enableAutopay = it }, enabled = !isSending, colors = AppSwitchDefaults.colors)
                }

                if (enableAutopay) {
                    OutlinedTextField(
                        value = autopayLimit,
                        onValueChange = { autopayLimit = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.paykit__limit_per_period_sats)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !isSending,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                error?.let {
                    Text(it, color = Colors.Red, style = MaterialTheme.typography.bodySmall)
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
                    CircularProgressIndicator(Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(stringResource(R.string.paykit__send_proposal))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) { Text(stringResource(R.string.paykit__cancel)) }
        },
    )
}
